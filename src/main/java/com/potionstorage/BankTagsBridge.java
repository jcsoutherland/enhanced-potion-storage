/*
 * Copyright (c) 2026, ziggle-dev
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.potionstorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTag;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.banktags.tabs.TabInterface;
import net.runelite.client.util.Text;

/**
 * Everything this plugin asks of RuneLite's Bank Tags plugin: the open tag and its layout, an item's tags, and
 * the tag and layout changes a stored potion needs. The plugin and the services bound in its own injector are
 * looked up once, and again whenever plugins change ({@link #refresh}); with Bank Tags off, every question
 * has the "no tag" answer.
 */
@Slf4j
final class BankTagsBridge
{
	/** A tag on every dose of a potion, written after it in the tag editor - the Bank Tags plugin's convention. */
	private static final String VARIATION_SUFFIX = "*";
	/** The Bank Tags plugin's config key for an item's tags; that plugin's own constant is not public. */
	private static final String ITEM_KEY_PREFIX = "item_";
	/** The tab bar's children before the tag tabs: a scroll catcher, the up and down buttons, and new-tab. */
	private static final int TAG_TAB_CHILD_OFFSET = 4;

	private final PluginManager pluginManager;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final ChatboxPanelManager chatboxPanelManager;
	private final ClientThread clientThread;

	private BankTagsPlugin plugin;
	private TagManager tagManager;
	private LayoutManager layoutManager;
	private TabInterface tabInterface;

	BankTagsBridge(PluginManager pluginManager, ConfigManager configManager, ItemManager itemManager,
		ChatboxPanelManager chatboxPanelManager, ClientThread clientThread)
	{
		this.pluginManager = pluginManager;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.chatboxPanelManager = chatboxPanelManager;
		this.clientThread = clientThread;
	}

	/** The first active plugin {@code which} picks, or null. */
	static Plugin findActive(PluginManager pluginManager, Predicate<Plugin> which)
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (which.test(p) && pluginManager.isPluginActive(p))
			{
				return p;
			}
		}
		return null;
	}

	/** Look the Bank Tags plugin up again; its services live in its own injector, so they go with it. */
	void refresh()
	{
		plugin = (BankTagsPlugin) findActive(pluginManager, p -> p instanceof BankTagsPlugin);
		try
		{
			tagManager = plugin == null ? null : plugin.getInjector().getInstance(TagManager.class);
			layoutManager = plugin == null ? null : plugin.getInjector().getInstance(LayoutManager.class);
			tabInterface = plugin == null ? null : plugin.getInjector().getInstance(TabInterface.class);
		}
		catch (RuntimeException e)
		{
			// A Bank Tags plugin that is not the one this was written against: carry on as if it were off.
			log.warn("the Bank Tags plugin's services are not available", e);
			plugin = null;
			tagManager = null;
			layoutManager = null;
			tabInterface = null;
		}
	}

	boolean active()
	{
		return plugin != null;
	}

	BankTag activeBankTag()
	{
		return plugin == null ? null : plugin.getActiveBankTag();
	}

	Layout activeLayout()
	{
		return plugin == null ? null : plugin.getActiveLayout();
	}

	/** The "tag tab tab" - the grid of every tag - shows no bank items at all. */
	boolean tagGridShown()
	{
		return tabInterface != null && tabInterface.isTagTabActive();
	}

	/** A laid-out tag tab is open, and whoever opened it lets its layout change. */
	boolean layoutChangeable()
	{
		return plugin != null && plugin.getActiveLayout() != null && !tagGridShown()
			&& (plugin.getOptions() & BankTagsService.OPTION_ALLOW_MODIFICATIONS) != 0;
	}

	/** An item's tags as the Bank Tags plugin keeps them: on the item itself, or on its whole variation group. */
	private List<String> tags(int itemId, boolean variation)
	{
		int canonical = itemManager.canonicalize(itemId);
		int key = variation ? -ItemVariationMapping.map(canonical) : canonical;
		String csv = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, ITEM_KEY_PREFIX + key);
		return csv == null ? new ArrayList<>() : new ArrayList<>(Text.fromCSV(csv.toLowerCase()));
	}

	/** All of an item's tags, its own and its variation group's. */
	Set<String> tags(int itemId)
	{
		Set<String> all = new LinkedHashSet<>(tags(itemId, false));
		all.addAll(tags(itemId, true));
		return all;
	}

	/** How many of an item's tags its Edit-tags would show: those of hidden tabs are not counted. */
	long shownTagCount(int itemId)
	{
		return tagManager == null ? 0 : tags(itemId).stream().filter(t -> !tagManager.isHidden(t)).count();
	}

	void addTag(int itemId, String tag, boolean variation)
	{
		if (tagManager != null)
		{
			tagManager.addTag(itemId, tag, variation);
		}
	}

	/**
	 * Take every dose of a potion out of the open tag - its tags and its layout cells. The Bank Tags plugin's
	 * own Remove-tag takes out the dose the stored stack is drawn as and no other, so a cell or a tag holding
	 * another dose of it keeps drawing it. True when a tag was open to take them out of.
	 */
	boolean removeFromOpenTag(Collection<Integer> doseIds)
	{
		String tag = plugin == null ? null : plugin.getActiveTag();
		if (tag == null || tagManager == null)
		{
			return false;
		}
		Layout layout = plugin.getActiveLayout();
		for (int id : doseIds)
		{
			tagManager.removeTag(id, tag);
			if (layout != null)
			{
				layout.removeItem(id);
			}
		}
		if (layout != null)
		{
			layoutManager.saveLayout(layout);
		}
		log.debug("removed {} from '{}'", doseIds, tag);
		return true;
	}

	/** Place items in the open layout where the Bank Tags plugin puts a newly tagged bank item, and save it. */
	void addToOpenLayout(List<Integer> itemIds)
	{
		Layout layout = activeLayout();
		if (itemIds.isEmpty() || layout == null || layoutManager == null)
		{
			return;
		}
		boolean atBottom = (plugin.getOptions() & BankTagsService.OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM) != 0;
		for (int itemId : itemIds)
		{
			layout.addItemAfter(itemId, atBottom ? lastFilled(layout.getLayout()) + 1 : 0);
		}
		layoutManager.saveLayout(layout);
		log.debug("placed {} in the '{}' layout", itemIds, plugin.getActiveTag());
	}

	private static int lastFilled(int[] layout)
	{
		for (int i = layout.length - 1; i >= 0; i--)
		{
			if (layout[i] != -1)
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * The Bank Tags plugin's tag editor, for an item it cannot open one on: the same prompt, with {@code *}
	 * marking a tag on every dose. Tags of hidden tabs are not shown, and are kept. {@code afterwards} runs
	 * once the tags are saved.
	 */
	void editTags(int itemId, String name, Runnable afterwards)
	{
		if (tagManager == null)
		{
			return;
		}
		TagManager tm = tagManager;
		List<String> plain = tags(itemId, false).stream().filter(t -> !tm.isHidden(t)).collect(Collectors.toList());
		List<String> variation = tags(itemId, true).stream().filter(t -> !tm.isHidden(t)).collect(Collectors.toList());
		List<String> shown = new ArrayList<>(plain);
		variation.forEach(t -> shown.add(t + VARIATION_SUFFIX));
		chatboxPanelManager.openTextInput(name + " tags:<br>(append " + VARIATION_SUFFIX + " for variation tag)")
			.addCharValidator(TabInterface.FILTERED_CHARS)
			.value(Text.toCSV(shown))
			.onDone((Consumer<String>) value -> clientThread.invoke(() ->
			{
				List<String> newPlain = new ArrayList<>();
				List<String> newVariation = new ArrayList<>();
				for (String t : Text.fromCSV(value.toLowerCase()))
				{
					boolean v = t.endsWith(VARIATION_SUFFIX);
					String tag = Text.standardize(v ? t.substring(0, t.length() - VARIATION_SUFFIX.length()) : t);
					if (!tag.isEmpty())
					{
						(v ? newVariation : newPlain).add(tag);
					}
				}
				plain.forEach(t -> tm.removeTag(itemId, t));
				variation.forEach(t -> tm.removeTag(itemId, t));
				tm.addTags(itemId, newPlain, false);
				tm.addTags(itemId, newVariation, true);
				afterwards.run();
			}))
			.build();
	}

	/**
	 * One of the Bank Tags plugin's tag tabs, the way that plugin knows a drop on one: a named child of the
	 * tab bar past its first few buttons. The part a drag lands on is the tab's background, which carries the
	 * tag as its name and none of the tab's options.
	 */
	static boolean isTagTab(Widget w)
	{
		return w.getId() == InterfaceID.Bankmain.ITEMS_CONTAINER && w.getIndex() >= TAG_TAB_CHILD_OFFSET
			&& !Text.removeTags(w.getName() == null ? "" : w.getName()).isEmpty();
	}
}
