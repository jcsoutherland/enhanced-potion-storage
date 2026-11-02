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

import static com.potionstorage.BankWidgets.isWidgetOp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;

/**
 * Potion storage's own panel: tagging its potions, and dragging them onto tag tabs.
 * <p>
 * The store's rows cannot be dragged: the server marks them for clicks only, and a click on one acts the
 * moment the button goes down. So each potion's icon gets a draggable copy laid over it - in the store's
 * panel rather than its list, because every slot of the list carries the server's marks. The copy carries
 * the icon's own options, so a click opens the same doses menu (each pointed at the icon, the click the game
 * expects); held a moment and moved, it drags the potion's own icon. Dropped on a tag tab it tags the potion;
 * dropped anywhere else nothing happens. Not while the Potion Storage Customizer plugin is on, which rebuilds
 * the store's rows itself.
 * <p>
 * Every potion in the panel, copied or not, also gets the Bank Tags plugin's Edit-tags.
 */
@Slf4j
final class StorePanel
{
	/** A plugin that rebuilds potion storage's rows itself, which the draggable copies cannot live with. */
	private static final String CUSTOMIZER = "Potion Storage Customizer";
	private static final String EDIT_TAGS = "Edit-tags";
	/** A drag-parent depth of 2: the copy drags within its panel's parent, which holds the tag tabs. */
	private static final int DRAG_TWO_LEVELS_UP = 2 * WidgetConfig.DRAG;
	/** Client ticks a press is held before it becomes a drag. */
	private static final int HOLD_TO_DRAG_TICKS = 10;
	/** Pixels a press may wander before it counts as moved - a bank item's. */
	private static final int DRAG_DEAD_ZONE = 5;

	private final Client client;
	private final PotionStorageConfig config;
	private final PluginManager pluginManager;
	private final BankTagsBridge bankTags;
	private final Runnable relayout;

	// The copies, reused while the panel stands.
	private final List<Widget> copies = new ArrayList<>();
	private final Map<Integer, Integer> copyKind = new HashMap<>(); // a copy's child index -> its potion's kind
	/** The panel took a copy's slot for the server's: no copies this bank session. */
	private boolean refused;

	StorePanel(Client client, PotionStorageConfig config, PluginManager pluginManager, BankTagsBridge bankTags,
		Runnable relayout)
	{
		this.client = client;
		this.config = config;
		this.pluginManager = pluginManager;
		this.bankTags = bankTags;
		this.relayout = relayout;
	}

	/** The bank closed: its widgets, and the copies with them, are gone. */
	void reset()
	{
		copies.clear();
		copyKind.clear();
		refused = false;
	}

	/** The plugin is stopping: hide the copies, which outlive it while the bank stays open. */
	void remove()
	{
		copies.forEach(o -> o.setHidden(true));
		reset();
	}

	boolean isCopy(Widget w)
	{
		return w != null && w.getId() == InterfaceID.Bankmain.POTIONSTORE_CONTAINER && copyKind.containsKey(w.getIndex());
	}

	// ---- the copies ----

	/** Lay a copy over every potion's icon the panel shows, reusing the copies it has. */
	void refresh()
	{
		copyKind.clear();
		Widget layer = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_CONTAINER);
		Widget list = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
		pruneCopies(layer);
		Widget[] rows = list == null ? null : list.getChildren();
		if (layer == null || rows == null || refused || !config.dragFromStore() || customizerActive())
		{
			hideCopiesFrom(0);
			return;
		}
		Widget dragArea = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		int used = 0;
		for (int kind = 0; PotionStore.iconChild(kind) < rows.length; kind++)
		{
			Widget row = rows[PotionStore.rowChild(kind)];
			Widget icon = rows[PotionStore.iconChild(kind)];
			if (row == null || icon == null || row.isSelfHidden() || icon.isSelfHidden() || icon.getItemId() <= 0 || row.getActions() == null)
			{
				continue;
			}
			Widget copy = copy(used, layer);
			if (copy == null)
			{
				return;
			}
			used++;
			dress(copy, icon, dragArea);
			copyKind.put(copy.getIndex(), kind);
		}
		hideCopiesFrom(used);
		sync();
	}

	/** Copies on a panel the game has rebuilt since went with it. */
	private void pruneCopies(Widget layer)
	{
		copies.removeIf(o -> layer == null || layer.getChild(o.getIndex()) != o);
	}

	/** The {@code n}th copy, made when there are not that many yet. Null when the panel will not take one. */
	private Widget copy(int n, Widget layer)
	{
		if (n < copies.size())
		{
			return copies.get(n);
		}
		Widget o = layer.createChild(-1, WidgetType.GRAPHIC);
		if (client.getWidgetConfig(o) != null)
		{
			// The server marks this slot too, which would make the copy as undraggable as the row.
			o.setHidden(true);
			refused = true;
			hideCopiesFrom(0);
			log.debug("potion storage's panel takes no draggable copies");
			return null;
		}
		copies.add(o);
		return o;
	}

	private static void dress(Widget o, Widget icon, Widget dragArea)
	{
		o.setOriginalWidth(icon.getOriginalWidth());
		o.setOriginalHeight(icon.getOriginalHeight());
		o.setItemId(icon.getItemId());
		o.setItemQuantity(icon.getItemQuantity());
		o.setItemQuantityMode(icon.getItemQuantityMode());
		o.setBorderType(icon.getBorderType());
		// The icon's own options - its doses - so a click opens the same menu it always did, and like the icon
		// it keeps the row's options from showing through.
		o.setName(icon.getName());
		o.clearActions();
		String[] actions = icon.getActions();
		for (int i = 0; actions != null && i < actions.length; i++)
		{
			if (actions[i] != null)
			{
				o.setAction(i, actions[i]);
			}
		}
		o.setHasListener(true);
		o.setNoClickThrough(true);
		o.setOnOpListener((JavaScriptCallback) ev ->
		{
		});
		// Dragged within the panel that holds both the store and the tag tabs - two levels up, as a bank item's
		// is - and only after a short hold, so a click is still a click.
		o.setClickMask(DRAG_TWO_LEVELS_UP);
		o.setDragDeadTime(HOLD_TO_DRAG_TICKS);
		o.setDragDeadZone(DRAG_DEAD_ZONE);
		o.setDragParent(dragArea);
	}

	private void hideCopiesFrom(int n)
	{
		for (int i = n; i < copies.size(); i++)
		{
			copies.get(i).setHidden(true);
		}
	}

	/** Keep each copy on its icon as the list scrolls, and hidden while its icon is scrolled out of view. */
	void sync()
	{
		if (copyKind.isEmpty())
		{
			return;
		}
		Widget layer = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_CONTAINER);
		Widget list = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
		if (layer == null || list == null || layer.isHidden() || isCopy(client.getDraggedWidget()))
		{
			return;
		}
		Point origin = layer.getCanvasLocation();
		int listTop = list.getCanvasLocation().getY();
		int listBottom = listTop + list.getHeight();
		for (Widget o : copies)
		{
			Integer kind = copyKind.get(o.getIndex());
			if (kind == null)
			{
				continue;
			}
			Widget icon = list.getChild(PotionStore.iconChild(kind));
			if (icon == null || icon.isSelfHidden())
			{
				o.setHidden(true);
				continue;
			}
			Point at = icon.getCanvasLocation();
			int x = at.getX() - origin.getX();
			int y = at.getY() - origin.getY();
			boolean visible = at.getY() + icon.getHeight() > listTop && at.getY() < listBottom;
			if (o.getOriginalX() != x || o.getOriginalY() != y || o.isSelfHidden() == visible)
			{
				o.setOriginalX(x);
				o.setOriginalY(y);
				o.setHidden(!visible);
				o.revalidate();
			}
		}
	}

	// ---- clicks, drops and menus ----

	/** A click on a copy is a click on the icon under it; the server never hears of the copy itself. */
	void click(MenuOptionClicked e)
	{
		if (!isWidgetOp(e.getMenuAction()))
		{
			return;
		}
		Integer kind = copyKind.get(e.getParam0());
		if (kind == null)
		{
			// One of this plugin's copies that no longer stands for a potion: nothing the server knows of.
			if (copies.contains(e.getWidget()))
			{
				e.consume();
			}
			return;
		}
		MenuEntry m = e.getMenuEntry();
		m.setParam1(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
		m.setParam0(PotionStore.iconChild(kind));
	}

	/** A copy dropped on a tag tab tags its potion; shift tags all of its doses, as for a bank item. */
	void drop(Widget src)
	{
		Widget dst = client.getDraggedOnWidget();
		if (client.isDraggingWidget() && dst != null && BankTagsBridge.isTagTab(dst))
		{
			String tag = Text.removeTags(dst.getName());
			bankTags.addTag(src.getItemId(), tag, client.isKeyPressed(KeyCode.KC_SHIFT));
			log.debug("tagged {} '{}' from potion storage", src.getItemId(), tag);
		}
		// Nothing about this drag is the server's business.
		client.setDraggedOnWidget(null);
	}

	/**
	 * Over a potion's icon only the copy's options belong in the menu, as only the icon's did before: drop
	 * whatever of the icon's or the row's still came through - theirs, and any a plugin added to them.
	 */
	void dedupeMenu()
	{
		if (copyKind.isEmpty() || client.isMenuOpen())
		{
			return;
		}
		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		Set<Integer> covered = new HashSet<>();
		for (MenuEntry m : entries)
		{
			Integer kind = m.getParam1() == InterfaceID.Bankmain.POTIONSTORE_CONTAINER ? copyKind.get(m.getParam0()) : null;
			if (kind != null)
			{
				covered.add(PotionStore.rowChild(kind));
				covered.add(PotionStore.iconChild(kind));
			}
		}
		if (covered.isEmpty())
		{
			return;
		}
		MenuEntry[] kept = Arrays.stream(entries)
			.filter(m -> !(m.getParam1() == InterfaceID.Bankmain.POTIONSTORE_ITEMS && covered.contains(m.getParam0())))
			.toArray(MenuEntry[]::new);
		if (kept.length != entries.length)
		{
			menu.setMenuEntries(kept);
		}
	}

	/**
	 * Edit-tags on a potion in the store, the Bank Tags plugin's own option for a bank item: on its row's menu,
	 * and on its copy's.
	 */
	void addEditTags(MenuEntryAdded e)
	{
		Widget icon = editableIcon(e);
		if (icon == null || icon.getItemId() <= 0 || !bankTags.active())
		{
			return;
		}
		int itemId = icon.getItemId();
		long count = bankTags.shownTagCount(itemId);
		client.getMenu().createMenuEntry(-2)
			.setParam0(e.getActionParam0())
			.setParam1(e.getActionParam1())
			.setTarget(e.getTarget())
			.setOption(EDIT_TAGS + (count > 0 ? " (" + count + ")" : ""))
			.setType(MenuAction.RUNELITE)
			.setIdentifier(e.getIdentifier())
			.setItemId(itemId)
			.onClick(m -> bankTags.editTags(itemId, client.getItemDefinition(itemId).getName(), relayout));
	}

	/**
	 * The potion icon a new menu entry is the last of: a row's last option is its Withdraw-All-but-1, and a
	 * copy's is whichever came last - one Edit-tags per menu.
	 */
	private Widget editableIcon(MenuEntryAdded e)
	{
		int child = e.getActionParam0();
		if (e.getActionParam1() == InterfaceID.Bankmain.POTIONSTORE_ITEMS)
		{
			if (!BankWidgets.WITHDRAW_ALL_BUT_ONE.equals(e.getOption()) || child % PotionStore.COMPONENTS_PER_POTION != 0)
			{
				return null;
			}
			Widget list = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
			return list == null ? null : list.getChild(PotionStore.iconChild(child / PotionStore.COMPONENTS_PER_POTION));
		}
		if (e.getActionParam1() == InterfaceID.Bankmain.POTIONSTORE_CONTAINER && copyKind.containsKey(child)
			&& Arrays.stream(client.getMenu().getMenuEntries()).noneMatch(m -> m.getParam1() == e.getActionParam1()
				&& m.getParam0() == child && m.getOption().startsWith(EDIT_TAGS)))
		{
			Widget layer = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_CONTAINER);
			return layer == null ? null : layer.getChild(child);
		}
		return null;
	}

	private boolean customizerActive()
	{
		return BankTagsBridge.findActive(pluginManager, p -> CUSTOMIZER.equals(p.getName())) != null;
	}
}
