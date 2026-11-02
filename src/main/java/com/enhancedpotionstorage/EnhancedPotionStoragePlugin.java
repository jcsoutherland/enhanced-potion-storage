/*
 * Copyright (c) 2026, ziggle-dev
 * Copyright (c) 2026, jcsoutherland
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
package com.enhancedpotionstorage;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.enhancedpotionstorage.PotionStore.Stack;
import com.enhancedpotionstorage.PotionVisibility.Where;
import com.enhancedpotionstorage.TemplateStore.Removed;
import static com.enhancedpotionstorage.BankWidgets.BANKTAB_POTIONSTORE;
import static com.enhancedpotionstorage.BankWidgets.LAST_WITHDRAW_OP;
import static com.enhancedpotionstorage.BankWidgets.SET_DOSE;
import static com.enhancedpotionstorage.BankWidgets.isWidgetOp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetDrag;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTag;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * The potion store, drawn into the bank as ordinary stacks - no slot of their own, the store's stock behind
 * every click.
 * <p>
 * The bank's item list is 1,410 widgets, one per bank slot, and every slot past the bank's last item is a
 * spare that the game already accepts clicks and drags on; a stored stack is drawn on one of those
 * ({@link BankView}). Its withdraw options are pointed at the potion store's own button when clicked - the
 * same rewrite the Bank Tags plugin makes for stored potions in a laid-out tag tab - so the game receives
 * exactly the click a player makes inside the potion store.
 * <p>
 * Four views:
 * <ul>
 * <li>The bank itself ("View all items" and the numbered tabs): every stack, real and stored, is laid out in
 * the saved order ({@link SavedOrder}), and drags rearrange it ({@link BankDrags}).</li>
 * <li>A search: the game draws what matches, and each tab's matching stored stacks are added to it.</li>
 * <li>A tag tab without a layout: the matching stored stacks go after what the game drew.</li>
 * <li>A laid-out tag tab: the Bank Tags plugin draws and withdraws stored potions there itself, but only ones
 * its layout already holds; a stored potion tagged since is given a place first.</li>
 * </ul>
 * With "Combine with bank stacks" on, a potion the bank holds too is not drawn twice: the bank's stack shows
 * both counts, and withdraws from whichever the setting says first. Potion storage's own panel gets
 * Edit-tags, and draggable copies of its icons to drop on a tag tab ({@link StorePanel}). Potion by potion,
 * any of them can be left out of the bank, tag tabs or searches, from a panel in the sidebar
 * ({@link VisibilityPanel}, {@link PotionVisibility}).
 * <p>
 * What is drawn is resolved on a copy; what is saved waits for a settled bank. The server's new item order
 * and its new tab sizes do not always reach a build together, and a build that sees one without the other
 * puts every tab's edge item in the neighbouring tab. So a build only draws, and the order is learned on a
 * game tick, once the bank has read the same for a whole tick.
 */
@Slf4j
@PluginDescriptor(
	name = "Enhanced Potion Storage",
	description = "Shows potion storage in the bank as normal items: in every tab, in searches and tag tabs, movable and withdrawable, without using bank space",
	tags = {"bank", "potion", "pots", "storage", "tags", "layout"}
)
public class EnhancedPotionStoragePlugin extends Plugin
{
	private static final String SEARCH_TITLE = "Showing items:";
	/** The Bank Tags plugin's option for taking an item out of the open tag; it may carry the tag's name after. */
	private static final String REMOVE_TAG = "Remove-tag";

	/** Before the Bank Tags plugin (0), to put tagged stored potions into a layout it then draws. */
	private static final int BEFORE_BANK_TAGS = 10;
	/** After the Bank Tags plugin (0) and the Bank plugin (1), which lay out and price first. */
	private static final int AFTER_BANK_TAGS = -10;
	/** A rewritten click should be what any plugin later in line sees. */
	private static final int EARLY = 1;

	/** How many deposited potions a warning names before "and N more". */
	private static final int DEPOSITS_NAMED = 3;

	/** A potion's dose at the end of its name: "Saradomin brew(4)". */
	private static final String DOSE_SUFFIX = "\\s*\\(\\d\\)$";

	/** Where the sidebar button goes among the others. */
	private static final int SIDEBAR_PRIORITY = 8;

	/** A full re-read of the store this often, whatever varp the store's own list does not name. */
	private static final int STORE_REREAD_TICKS = 50;

	/**
	 * ziggle-dev's Potion Storage, which this plugin is built from: it draws onto the same spare bank slots and
	 * points the same clicks at the store, so the two cannot run together.
	 */
	private static final String BASE_PLUGIN = "com.potionstorage.PotionStoragePlugin";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private BankSearch bankSearch;

	@Inject
	private EnhancedPotionStorageConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Gson gson;

	private PotionStore store;
	private SavedOrder savedOrder;
	private BankTagsBridge bankTags;
	private BankView view;
	private BankDrags drags;
	private StorePanel panel;
	private StoreButton storeButton;
	private PlaceholderRelease placeholderRelease;
	private PotionVisibility visibility;
	private VisibilityPanel sidebar;
	private NavigationButton sidebarButton;
	private TemplateStore templates;
	private TemplatePanel templatePanel;
	private AccountSettings accountSettings;
	private DoseLabelOverlay doseLabels;

	/** The template version and potion count the store's plan was made from. Client thread. */
	private int planVersion = -1;
	private int planPotions = -1;
	/** The missing slots last sent to the panel, for "Restore all". Client thread. */
	private List<TemplatePanel.Missing> missing = Collections.emptyList();

	/** What the last build drew. Client thread only. */
	private Frame frame = new Frame(Frame.Mode.NONE);

	/** Warned this login that deposited potions go into the bank. */
	private boolean depositWarned;
	/** The bank's count of each potion potion storage keeps, as last seen this bank session; null before. */
	private Map<Integer, Integer> bankPotions;

	// Learning the order.
	private boolean learnPending;
	private int bankSignature;
	private int learnedSignature;

	/**
	 * The second listener for the same event, at its own priority. Registered by hand: the event bus takes
	 * one {@code @Subscribe} method per event, named after it.
	 */
	private EventBus.Subscriber beforeBankTags;

	/** Which part of the bank is on show, as far as stored stacks go. */
	private enum View
	{
		/** No bank, or the potion store's own panel. */
		NONE,
		/** The Bank Tags plugin's grid of every tag: no items at all. */
		TAG_GRID,
		TAG_LAYOUT,
		TAG_PLAIN,
		SEARCH,
		BANK
	}

	@Provides
	EnhancedPotionStorageConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EnhancedPotionStorageConfig.class);
	}

	@Override
	protected void startUp()
	{
		stepAsideForBasePlugin();
		store = new PotionStore(client);
		savedOrder = new SavedOrder(configManager);
		savedOrder.setRank(key -> store.plan().rank(key));
		savedOrder.setStoredMoved(this::placementEdited);
		templates = new TemplateStore(templateStorage(), gson, savedOrder::encodeArrangement, savedOrder::placeStoredAs,
			store::potions);
		accountSettings = new AccountSettings(configManager);
		planVersion = -1;
		bankTags = new BankTagsBridge(pluginManager, configManager, itemManager, chatboxPanelManager, clientThread);
		view = new BankView(client, config, savedOrder, this::newSection);
		drags = new BankDrags(client, savedOrder, this::relayout);
		panel = new StorePanel(client, config, pluginManager, bankTags, this::relayout);
		storeButton = new StoreButton(client);
		placeholderRelease = new PlaceholderRelease(client, savedOrder, config::placeholders, this::relayout);
		visibility = new PotionVisibility(this::readHidden, this::writeHidden);
		templatePanel = new TemplatePanel(new PanelActions());
		sidebar = new VisibilityPanel(itemManager::getImage, visibility, () -> clientThread.invokeLater(this::sendPotions),
			templatePanel);
		doseLabels = new DoseLabelOverlay(() -> frame);
		overlayManager.add(doseLabels);
		sidebarButton = NavigationButton.builder()
			.tooltip("Enhanced Potion Storage")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(SIDEBAR_PRIORITY)
			.panel(sidebar)
			.build();
		if (config.showSidebar())
		{
			clientToolbar.addNavigation(sidebarButton);
		}
		bankTags.refresh();
		beforeBankTags = eventBus.register(ScriptPreFired.class, this::onScriptPreFiredBeforeBankTags, BEFORE_BANK_TAGS);
		relayout();
		clientThread.invokeLater(() ->
		{
			accountSettings.accountChanged();
			refreshPlan();
			panel.refresh();
			// Started with the bank open: count it now, so the first deposit is compared with something.
			ItemContainer bank = client.getItemContainer(InventoryID.BANK);
			if (bankShown() && bank != null)
			{
				bankPotions = potionCounts(bank);
				placeholderRelease.bankChanged(bank);
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(sidebarButton);
		overlayManager.remove(doseLabels);
		eventBus.unregister(beforeBankTags);
		clientThread.invokeLater(() ->
		{
			panel.remove();
			storeButton.restore();
			resetSession();
			savedOrder.reset();
			layoutIfBankView();
		});
	}

	/** Everything that lasts one bank session: what is drawn, the store as read, the drag, the copies, the learning. */
	private void resetSession()
	{
		frame = new Frame(Frame.Mode.NONE);
		store.reset();
		drags.reset();
		panel.reset();
		learnPending = false;
		bankSignature = 0;
		learnedSignature = 0;
		bankPotions = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		// The plugin's per-account records - the saved order, templates, settings' copies - are not settings.
		if (!EnhancedPotionStorageConfig.GROUP.equals(e.getGroup()) || e.getProfile() != null)
		{
			return;
		}
		boolean fromAccount = accountSettings.restoring();
		accountSettings.changed(e.getKey(), e.getNewValue());
		if (!fromAccount && (EnhancedPotionStorageConfig.PLACE_KEY.equals(e.getKey())
			|| EnhancedPotionStorageConfig.TAG_KEY.equals(e.getKey())))
		{
			String oldTag = EnhancedPotionStorageConfig.TAG_KEY.equals(e.getKey()) ? e.getOldValue() : config.bankPlaceTag();
			boolean wasTag = EnhancedPotionStorageConfig.PLACE_KEY.equals(e.getKey())
				? BankPlace.TAG.name().equals(e.getOldValue()) : config.bankPlace() == BankPlace.TAG;
			clientThread.invokeLater(() -> applyBankPlace(wasTag ? oldTag : null));
		}
		if (!SavedOrder.isOwnKey(e.getKey()))
		{
			visibility.reload();
			if ("showSidebar".equals(e.getKey()))
			{
				if (config.showSidebar())
				{
					clientToolbar.addNavigation(sidebarButton);
				}
				else
				{
					clientToolbar.removeNavigation(sidebarButton);
				}
			}
			SwingUtilities.invokeLater(sidebar::settingsChanged);
			relayout();
			// The panel's own clicks need no new potions sent: it has drawn them already.
			boolean switchChanged = !PotionVisibility.isOwnKey(e.getKey());
			clientThread.invokeLater(() ->
			{
				panel.refresh();
				if (switchChanged && sidebarShown())
				{
					sendPotions();
				}
			});
		}
	}

	/** The Bank Tags plugin, the Potion Storage Customizer or the base Potion Storage plugin may have come or gone. */
	@Subscribe
	public void onPluginChanged(PluginChanged e)
	{
		if (e.isLoaded() && BASE_PLUGIN.equals(e.getPlugin().getClass().getName()))
		{
			stepAsideForBasePlugin();
		}
		clientThread.invokeLater(() ->
		{
			bankTags.refresh();
			panel.refresh();
		});
		relayout();
	}

	/**
	 * With the base Potion Storage plugin on, turn this one off - saved as off, so it stays off until the user
	 * turns it on again, and turns itself straight back off if the base plugin is still on then.
	 */
	private void stepAsideForBasePlugin()
	{
		if (BankTagsBridge.findActive(pluginManager, p -> BASE_PLUGIN.equals(p.getClass().getName())) == null)
		{
			return;
		}
		log.info("the Potion Storage plugin is on: turning Enhanced Potion Storage off");
		SwingUtilities.invokeLater(() ->
		{
			pluginManager.setPluginEnabled(this, false);
			try
			{
				pluginManager.stopPlugin(this);
			}
			catch (PluginInstantiationException ex)
			{
				log.warn("turning Enhanced Potion Storage off failed", ex);
			}
		});
	}

	/** Draw the bank again, at the end of this frame. */
	private void relayout()
	{
		clientThread.invokeLater(this::layoutIfBankView);
	}

	/** Never while the store's own panel is up: redrawing it rebuilds the buttons a click may be aimed at. */
	private void layoutIfBankView()
	{
		if (bankShown() && client.getVarbitValue(VarbitID.BANK_CURRENTTAB) != BANKTAB_POTIONSTORE)
		{
			bankSearch.layoutBank();
		}
	}

	private boolean bankShown()
	{
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return items != null && !items.isHidden();
	}

	// ---- building the bank ----

	private void onScriptPreFiredBeforeBankTags(ScriptPreFired e)
	{
		if (e.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}
		// Before the Bank Tags plugin sizes its tab column, which it stops above a shown pop-out tab.
		storeButton.apply(config.hideStoreButton());
		if (!config.addToLayouts())
		{
			return;
		}
		try
		{
			placeTaggedPotions();
		}
		catch (RuntimeException ex)
		{
			log.warn("adding stored potions to a tag layout failed", ex);
		}
	}

	@Subscribe(priority = AFTER_BANK_TAGS)
	public void onScriptPreFired(ScriptPreFired e)
	{
		if (e.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}
		frame = new Frame(Frame.Mode.NONE);
		try
		{
			frame = build();
			learnPending |= frame.orderChanged;
		}
		catch (RuntimeException ex)
		{
			log.warn("drawing the potion store into the bank failed", ex);
		}
	}

	private View currentView(int tab)
	{
		if (tab == BANKTAB_POTIONSTORE)
		{
			return View.NONE;
		}
		if (bankTags.tagGridShown())
		{
			return View.TAG_GRID;
		}
		if (bankTags.activeBankTag() != null)
		{
			return bankTags.activeLayout() != null ? View.TAG_LAYOUT : View.TAG_PLAIN;
		}
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		return title != null && title.getText() != null && title.getText().startsWith(SEARCH_TITLE) ? View.SEARCH : View.BANK;
	}

	private Frame build()
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		Frame built = new Frame(Frame.Mode.NONE);
		if (bank == null || items == null)
		{
			return built;
		}
		int tab = client.getVarbitValue(VarbitID.BANK_CURRENTTAB);
		View shown = currentView(tab);
		if (shown == View.TAG_LAYOUT)
		{
			tidyLayout(items, bank);
		}
		refreshPlan();
		List<Stack> stacks = new ArrayList<>(store.stacks());
		stacks.addAll(placeholders(stacks));
		List<Stack> shownStacks = storeShownIn(shown, stacks);
		if (shownStacks.isEmpty())
		{
			return built;
		}
		// A potion the bank also holds is shown on the bank's own stack (BankView.combine), not as a stack of its own;
		// its placeholder, likewise, on the bank's own one.
		Set<Integer> held = config.combineWithBank() ? view.itemsHeld(bank) : Collections.emptySet();
		List<Stack> loose = shownStacks.stream().filter(s -> !held.contains(s.itemId)).collect(Collectors.toList());
		switch (shown)
		{
			case TAG_PLAIN:
				BankTag tag = bankTags.activeBankTag();
				built = view.append(items, bank, loose.stream().filter(s -> s.ids.stream().anyMatch(tag::contains)).collect(Collectors.toList()));
				break;
			case SEARCH:
				built = view.arrange(items, bank, 0, loose, searchFilter(client.getVarcStrValue(VarClientID.MESLAYERINPUT)));
				break;
			case BANK:
				built = view.arrange(items, bank, tab, loose, null);
				break;
			default:
				// A laid-out tag tab: the Bank Tags plugin draws stored potions there itself.
				break;
		}
		if (config.combineWithBank())
		{
			List<Stack> holding = shownStacks.stream().filter(s -> !s.placeholder).collect(Collectors.toList());
			view.combine(built, items, bank, holding, config.combinedWithdraw() == CombinedWithdraw.POTION_STORAGE_FIRST);
		}
		return built;
	}

	/**
	 * A laid-out tag tab as the Bank Tags plugin drew it, stored potions and all: take out the unfinished ones
	 * unless they are wanted and the ones the sidebar panel hides in tag tabs, and the Doses submenu where
	 * there is only one dose to pick.
	 */
	private void tidyLayout(Widget items, ItemContainer bank)
	{
		boolean unfinished = config.showUnfinished();
		Set<Integer> hidden = store.idsOf(k -> (k.unfinished && !unfinished) || !visibility.shown(Where.TAGS, k.id()));
		if (!hidden.isEmpty())
		{
			view.blankLayoutCells(items, bank, hidden);
		}
		view.dropDosesMenu(items, store.idsOf(k -> k.doses.isEmpty()));
	}

	/**
	 * The stored potions a view shows - as stacks of their own, or combined into the bank's: none where its
	 * setting is off, which leaves the bank's own stacks as the game draws them, no unfinished ones unless they
	 * are wanted, and none the sidebar panel hides there (left out, they keep their places for when they are
	 * wanted again). A laid-out tag tab shows stored potions whatever "Show in tag tabs" says: they are the
	 * Bank Tags plugin's, drawn by its layout, and {@link #tidyLayout} takes out the ones not wanted.
	 */
	private List<Stack> storeShownIn(View shown, List<Stack> stacks)
	{
		boolean on;
		switch (shown)
		{
			case TAG_LAYOUT:
				on = true;
				break;
			case TAG_PLAIN:
				on = config.inTagTabs();
				break;
			case SEARCH:
				on = config.inSearch();
				break;
			case BANK:
				// Sent to a bank tag, the slots are that tag's: the tag tab shows them, the bank does not.
				on = config.inBank() && config.bankPlace() != BankPlace.TAG;
				break;
			default:
				on = false;
				break;
		}
		if (!on)
		{
			return Collections.emptyList();
		}
		Where where = shown == View.BANK ? Where.BANK : shown == View.SEARCH ? Where.SEARCH : Where.TAGS;
		return stacks.stream()
			.filter(s -> (config.showUnfinished() || !s.kind.unfinished) && visibility.shown(where, s.kind.id()))
			.collect(Collectors.toList());
	}

	/**
	 * The game's bank search, for a stored stack: the name holds the text - or, after {@code tag:}, one of the
	 * stack's bank tags starts with it, the way the Bank Tags plugin reads a tag search.
	 */
	private Predicate<Stack> searchFilter(String raw)
	{
		String q = raw == null ? "" : raw.toLowerCase();
		if (q.startsWith(BankTagsPlugin.TAG_SEARCH))
		{
			String tag = Text.standardize(q.substring(BankTagsPlugin.TAG_SEARCH.length()));
			return s -> !tag.isEmpty() && s.ids.stream().anyMatch(id -> bankTags.tags(id).stream().anyMatch(t -> t.startsWith(tag)));
		}
		return s -> q.isEmpty() || s.name.toLowerCase().contains(q);
	}

	/**
	 * A laid-out tag tab draws what its layout holds, plus any bank item tagged since - so a potion tagged
	 * while it sits in the store never gets a place in it. Give it one, where the Bank Tags plugin puts a newly
	 * tagged bank item, before that plugin lays the tab out; from then on that plugin draws it from the store
	 * itself.
	 */
	private void placeTaggedPotions()
	{
		BankTag tag = bankTags.activeBankTag();
		Layout layout = bankTags.activeLayout();
		if (tag == null || layout == null || !bankTags.layoutChangeable())
		{
			return;
		}
		Set<Integer> placed = Arrays.stream(layout.getLayout()).boxed().collect(Collectors.toSet());
		// A potion the bank holds too is the bank's to place, and BankView.combine shows the store's count on it.
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		Set<Integer> inBank = config.combineWithBank() && bank != null ? view.itemsHeld(bank) : Collections.emptySet();
		// The Bank Tags plugin draws a stored potion in a layout as the store's withdraw dose, whichever dose its
		// cell holds: one cell per potion, as the active slot.
		Set<Integer> kinds = new HashSet<>();
		bankTags.addToOpenLayout(store.stacks().stream()
			.filter(s -> s.active && kinds.add(s.kind.index))
			.filter(s -> !inBank.contains(s.itemId) && (config.showUnfinished() || !s.kind.unfinished)
				&& visibility.shown(Where.TAGS, s.kind.id()))
			.filter(s -> s.ids.stream().noneMatch(placed::contains) && s.ids.stream().anyMatch(tag::contains))
			.map(s -> s.itemId)
			.collect(Collectors.toList()));
	}

	// ---- the store ----

	/**
	 * Read the store if it changed, and keep a placeholder for each dose slot that ran out - while the bank keeps
	 * them for its own items. One that leaves none loses its place, as a bank item does: back in stock, it goes
	 * where the template puts it. A slot back in stock is drawn as itself again, where its placeholder was. Not
	 * inside a script. True when the store's numbers changed.
	 */
	private boolean readStore()
	{
		refreshPlan();
		boolean wasRead = store.hasRead();
		Set<Integer> before = store.stockedKeys();
		if (!store.readIfDirty())
		{
			return false;
		}
		if (wasRead)
		{
			Set<Integer> now = store.stockedKeys();
			Set<Integer> refilled = savedOrder.placeholders();
			refilled.retainAll(now);
			savedOrder.fillPlaceholders(refilled);
			before.removeAll(now);
			if (!before.isEmpty() && config.placeholders() && client.getVarbitValue(VarbitID.BANK_LEAVEPLACEHOLDERS) == 1)
			{
				savedOrder.addPlaceholders(before);
			}
			else if (!before.isEmpty())
			{
				savedOrder.release(before);
			}
		}
		return true;
	}

	/**
	 * Where a dose slot new to the order goes when none of its neighbours in the template is placed: the bank tab
	 * "Bank place" names, or the untabbed part.
	 */
	private int newSection(int key)
	{
		int s = config.bankPlace().section;
		return s >= 1 ? s : BankArrangement.MAIN;
	}

	// ---- templates ----

	/** The open template's slots, made again when it or the store's list of potions changed. Client thread. */
	private void refreshPlan()
	{
		int v = templates.version();
		boolean in = client.getGameState() == GameState.LOGGED_IN;
		int count = in ? store.kinds().size() : 0;
		if (v == planVersion && count == planPotions)
		{
			return;
		}
		planVersion = v;
		planPotions = count;
		store.setPlan(SlotPlan.of(templates.working(), in ? store.potions() : Collections.emptyList()));
		visibility.reload();
	}

	/** The template changed: draw the bank and the panel from it again. Client thread. */
	private void templateChanged()
	{
		refreshPlan();
		visibility.reload();
		relayout();
		sendPotions();
	}

	/** A drag moved a dose slot: an edit to the open template's layout. */
	private void placementEdited()
	{
		templates.placementEdited();
		templateChanged();
	}

	private TemplateStore.Storage templateStorage()
	{
		return new TemplateStore.Storage()
		{
			@Override
			public String account()
			{
				return configManager.getRSProfileKey();
			}

			@Override
			public String get(String key)
			{
				return configManager.getRSProfileConfiguration(EnhancedPotionStorageConfig.GROUP, key);
			}

			@Override
			public void set(String key, String value)
			{
				configManager.setRSProfileConfiguration(EnhancedPotionStorageConfig.GROUP, key, value);
			}

			@Override
			public void unset(String key)
			{
				configManager.unsetRSProfileConfiguration(EnhancedPotionStorageConfig.GROUP, key);
			}
		};
	}

	private static Where whereOf(String key)
	{
		for (Where w : Where.values())
		{
			if (w.key.equals(key))
			{
				return w;
			}
		}
		return Where.BANK;
	}

	/** The potions the open template hides in a place, for {@link PotionVisibility}. */
	private String readHidden(String key)
	{
		return PotionVisibility.format(templates.working().hidden(whereOf(key)));
	}

	/** The panel hid or showed a potion: an edit to the open template. */
	private void writeHidden(String key, String value)
	{
		Where w = whereOf(key);
		Set<Integer> ids = PotionVisibility.parse(value);
		clientThread.invokeLater(() ->
		{
			templates.edit(t -> t.setHidden(w, ids));
			templateChanged();
		});
	}

	private SlotPlan.Potion potion(int potionId)
	{
		for (SlotPlan.Potion p : store.potions())
		{
			if (p.id == potionId)
			{
				return p;
			}
		}
		return null;
	}

	/**
	 * Give potion {@code p} the dose slots {@code next}, each slot of it that stays keeping its place in the bank:
	 * {@code from[i]} is the index in its slots now of {@code next}'s {@code i}th, or -1 for a new one. A dose
	 * taken out goes to "Recently removed".
	 */
	private void setDoses(SlotPlan.Potion p, List<Integer> next, int[] from)
	{
		List<Integer> now = SlotPlan.effectiveDoses(templates.working(), p);
		List<Integer> oldKeys = SlotPlan.keysOf(p, now);
		List<Integer> newKeys = SlotPlan.keysOf(p, next);
		Map<Integer, Integer> renames = new HashMap<>();
		boolean[] kept = new boolean[now.size()];
		for (int i = 0; i < next.size(); i++)
		{
			if (from[i] >= 0)
			{
				renames.put(oldKeys.get(from[i]), newKeys.get(i));
				kept[from[i]] = true;
			}
		}
		// Before the edit, so a template it makes keeps the slots where they are.
		savedOrder.renameStored(renames);
		templates.edit(t ->
		{
			if (t.showAllDoses)
			{
				t.doses.clear();
				t.showAllDoses = false;
			}
			t.doses.put(p.id, new ArrayList<>(next));
		});
		Set<Integer> stillThere = new HashSet<>(next);
		for (int i = 0; i < now.size(); i++)
		{
			if (!kept[i] && !stillThere.contains(now.get(i)))
			{
				templates.recordRemoved(new Removed(p.id, now.get(i), oldKeys.get(i), Removed.How.DOSE_REMOVED));
			}
		}
		refreshPlan();
		sortStored();
		templateChanged();
	}

	/** Each new slot matched to the first unmatched slot of the same dose now: keeps what can be kept. */
	private static int[] matchByDose(List<Integer> now, List<Integer> next)
	{
		int[] from = new int[next.size()];
		boolean[] used = new boolean[now.size()];
		for (int i = 0; i < next.size(); i++)
		{
			from[i] = -1;
			for (int j = 0; j < now.size(); j++)
			{
				if (!used[j] && now.get(j).equals(next.get(i)))
				{
					used[j] = true;
					from[i] = j;
					break;
				}
			}
		}
		return from;
	}

	/** The Doses submenu on a dose slot: that slot shows {@code dose} from now on, in the same place. */
	private void changeSlotDose(Stack s, int dose)
	{
		SlotPlan.Potion p = potion(s.kind.id());
		if (p == null || p.itemOf(dose) <= 0 || dose == s.dose)
		{
			return;
		}
		List<Integer> now = SlotPlan.effectiveDoses(templates.working(), p);
		int at = SlotPlan.keysOf(p, now).indexOf(s.key);
		if (at < 0)
		{
			return;
		}
		List<Integer> next = new ArrayList<>(now);
		next.set(at, dose);
		int[] from = new int[next.size()];
		for (int i = 0; i < from.length; i++)
		{
			from[i] = i;
		}
		setDoses(p, next, from);
	}

	/** The template's order changed: the dose slots in each tab sorted into it, where they are. */
	private void sortStored()
	{
		savedOrder.sortStored();
	}

	/**
	 * "Bank place" changed: every dose slot, together, to the bank tab it names - or, for a bank tag, every dose
	 * of every potion in the plan tagged with it, and taken out of {@code oldTag}'s.
	 */
	private void applyBankPlace(String oldTag)
	{
		refreshPlan();
		BankPlace place = config.bankPlace();
		String tag = config.bankPlaceTag() == null ? "" : config.bankPlaceTag().trim();
		Set<Integer> ids = new HashSet<>();
		for (SlotPlan.Slot s : store.plan().slots())
		{
			PotionStore.Kind k = store.kindOf(s.itemId);
			if (k != null)
			{
				ids.addAll(k.doseIds);
			}
		}
		if (oldTag != null && !oldTag.trim().isEmpty() && !(place == BankPlace.TAG && oldTag.trim().equalsIgnoreCase(tag)))
		{
			bankTags.removeTag(ids, oldTag.trim());
		}
		if (place == BankPlace.TAG)
		{
			if (!tag.isEmpty())
			{
				ids.forEach(id -> bankTags.addTag(id, tag, false));
			}
		}
		else if (place.section >= 1 && client.getVarbitValue(BankWidgets.TAB_VARBITS[place.section - 1]) == 0)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Bank tab " + place.section
				+ " has no items, and a potion from potion storage can't start a tab - put a bank item in it first.", null);
			return;
		}
		else
		{
			savedOrder.moveAllStored(Math.max(place.section, BankArrangement.MAIN));
		}
		relayout();
		sendPotions();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		accountSettings.accountChanged();
		clientThread.invokeLater(this::templateChanged);
	}

	/** What the template part of the sidebar panel shows. Client thread. */
	private TemplatePanel.State panelState()
	{
		if (client.getGameState() != GameState.LOGGED_IN || configManager.getRSProfileKey() == null)
		{
			missing = Collections.emptyList();
			return TemplatePanel.State.none();
		}
		Template w = templates.working();
		List<SlotPlan.Potion> potions = store.potions();
		Map<Integer, SlotPlan.Potion> byId = new HashMap<>();
		potions.forEach(p -> byId.put(p.id, p));
		List<Template.Category> categories = SlotPlan.categories(w, potions);
		List<TemplatePanel.CategoryRow> rows = new ArrayList<>();
		for (Template.Category c : categories)
		{
			List<TemplatePanel.PotionRow> ps = new ArrayList<>();
			for (int id : c.potions)
			{
				SlotPlan.Potion p = byId.get(id);
				PotionStore.Kind k = p == null ? null : store.kindOf(p.itemOf(p.naturalDoses().get(0)));
				if (p != null && k != null)
				{
					ps.add(new TemplatePanel.PotionRow(p.id, p.name, p.naturalDoses(), SlotPlan.effectiveDoses(w, p),
						store.doses(k) > 0));
				}
			}
			rows.add(new TemplatePanel.CategoryRow(c.name, c.enabled, ps));
		}
		missing = missingSlots(w, categories, byId);
		List<TemplatePanel.RemovedRow> removed = new ArrayList<>();
		for (Removed r : templates.removed())
		{
			SlotPlan.Potion p = byId.get(r.potionId);
			if (p == null || !stillMissing(r, w, p))
			{
				templates.forgetRemoved(r);
				continue;
			}
			removed.add(new TemplatePanel.RemovedRow(r, p.name + " (" + r.dose + ")"
				+ (r.how == Removed.How.DOSE_REMOVED ? " slot" : " placeholder")));
		}
		return new TemplatePanel.State(true, templates.names(), templates.activeName(), templates.dirty(), w.showAllDoses,
			config.bankPlace(), config.bankPlaceTag() == null ? "" : config.bankPlaceTag(), rows, missing, removed);
	}

	private boolean stillMissing(Removed r, Template w, SlotPlan.Potion p)
	{
		if (r.how == Removed.How.DOSE_REMOVED)
		{
			return !SlotPlan.effectiveDoses(w, p).contains(r.dose);
		}
		return store.plan().slot(r.key) != null && !store.stockedKeys().contains(r.key)
			&& !savedOrder.placeholders().contains(r.key);
	}

	/**
	 * Every slot the layout asks for that the bank does not show: a run-out slot with no placeholder, and - when
	 * not every dose shows - a dose taken out of a potion's slots. Only for potions in storage or with a
	 * placeholder left, in categories that are on, shown in the bank.
	 */
	private List<TemplatePanel.Missing> missingSlots(Template w, List<Template.Category> categories,
		Map<Integer, SlotPlan.Potion> byId)
	{
		List<TemplatePanel.Missing> out = new ArrayList<>();
		if (!store.hasRead())
		{
			return out;
		}
		Set<Integer> stocked = store.stockedKeys();
		Set<Integer> placeholders = savedOrder.placeholders();
		for (Template.Category c : categories)
		{
			if (!c.enabled)
			{
				continue;
			}
			for (int id : c.potions)
			{
				SlotPlan.Potion p = byId.get(id);
				PotionStore.Kind k = p == null ? null : store.kindOf(p.itemOf(p.naturalDoses().get(0)));
				if (k == null || !visibility.shown(Where.BANK, id) || (k.unfinished && !config.showUnfinished()))
				{
					continue;
				}
				List<SlotPlan.Slot> slots = store.plan().slotsOf(id);
				boolean has = store.doses(k) > 0 || slots.stream().anyMatch(s -> placeholders.contains(s.key));
				if (!has)
				{
					continue;
				}
				List<TemplatePanel.Missing.Chip> chips = new ArrayList<>();
				if (config.placeholders())
				{
					for (SlotPlan.Slot s : slots)
					{
						if (!stocked.contains(s.key) && !placeholders.contains(s.key))
						{
							chips.add(new TemplatePanel.Missing.Chip(id, s.dose, s.key, Removed.How.RELEASED));
						}
					}
				}
				if (!w.showAllDoses && p.hasDoseChoice())
				{
					List<Integer> doses = SlotPlan.effectiveDoses(w, p);
					for (int d : p.naturalDoses())
					{
						if (!doses.contains(d))
						{
							chips.add(new TemplatePanel.Missing.Chip(id, d, -1, Removed.How.DOSE_REMOVED));
						}
					}
				}
				if (!chips.isEmpty())
				{
					out.add(new TemplatePanel.Missing(c.name, p.name, chips));
				}
			}
		}
		return out;
	}

	/** Put a missing slot back: a placeholder for a run-out one, the dose back into the slots for a removed one. */
	private void restore(TemplatePanel.Missing.Chip chip)
	{
		if (chip.how == Removed.How.RELEASED)
		{
			savedOrder.addPlaceholders(List.of(chip.key));
			return;
		}
		SlotPlan.Potion p = potion(chip.potionId);
		if (p == null)
		{
			return;
		}
		List<Integer> now = SlotPlan.effectiveDoses(templates.working(), p);
		if (now.contains(chip.dose))
		{
			return;
		}
		List<Integer> next = new ArrayList<>(now);
		int at = 0;
		while (at < next.size() && next.get(at) > chip.dose)
		{
			at++;
		}
		next.add(at, chip.dose);
		setDoses(p, next, matchByDose(now, next));
	}

	/** The panel's actions, each carried out on the client thread. */
	private final class PanelActions implements TemplatePanel.Actions
	{
		private void run(Runnable r)
		{
			clientThread.invokeLater(() ->
			{
				r.run();
				templateChanged();
			});
		}

		/** A change to the open template's categories, the bank's slots then sorted into the new order. */
		private void edit(Consumer<Template> change)
		{
			run(() ->
			{
				templates.edit(change);
				refreshPlan();
				sortStored();
			});
		}

		@Override
		public void open(String name, boolean saveFirst)
		{
			run(() ->
			{
				if (saveFirst)
				{
					templates.save();
				}
				templates.open(name);
			});
		}

		@Override
		public void save()
		{
			run(templates::save);
		}

		@Override
		public void saveAs(String name)
		{
			run(() -> templates.saveAs(name));
		}

		@Override
		public void rename(String from, String to)
		{
			run(() -> templates.rename(from, to));
		}

		@Override
		public void delete(String name)
		{
			run(() -> templates.delete(name));
		}

		@Override
		public void export(String name, Consumer<String> then)
		{
			clientThread.invokeLater(() ->
			{
				String json = templates.export(name);
				SwingUtilities.invokeLater(() -> then.accept(json));
			});
		}

		@Override
		public void importJson(String json)
		{
			run(() ->
			{
				String name = templates.importJson(json);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", name == null
					? "Enhanced Potion Storage: that file isn't a template."
					: "Enhanced Potion Storage: imported the template \"" + name + "\". Pick it in the panel to open it.", null);
			});
		}

		@Override
		public void setShowAllDoses(boolean on)
		{
			edit(t -> t.showAllDoses = on);
		}

		@Override
		public void setBankPlace(BankPlace place, String tag)
		{
			if (place == BankPlace.TAG)
			{
				configManager.setConfiguration(EnhancedPotionStorageConfig.GROUP, EnhancedPotionStorageConfig.TAG_KEY, tag);
			}
			configManager.setConfiguration(EnhancedPotionStorageConfig.GROUP, EnhancedPotionStorageConfig.PLACE_KEY, place);
		}

		@Override
		public void restore(TemplatePanel.Missing.Chip chip)
		{
			run(() -> EnhancedPotionStoragePlugin.this.restore(chip));
		}

		@Override
		public void restoreAll()
		{
			run(() ->
			{
				for (TemplatePanel.Missing m : missing)
				{
					m.chips.forEach(EnhancedPotionStoragePlugin.this::restore);
				}
			});
		}

		@Override
		public void undo(Removed r)
		{
			run(() ->
			{
				templates.forgetRemoved(r);
				EnhancedPotionStoragePlugin.this.restore(new TemplatePanel.Missing.Chip(r.potionId, r.dose, r.key, r.how));
			});
		}

		@Override
		public void setCategoryEnabled(int category, boolean on)
		{
			edit(t ->
			{
				if (category < t.categories.size())
				{
					t.categories.get(category).enabled = on;
				}
			});
		}

		@Override
		public void moveCategory(int category, int by)
		{
			edit(t ->
			{
				int to = category + by;
				if (category < t.categories.size() && to >= 0 && to < t.categories.size())
				{
					Collections.swap(t.categories, category, to);
				}
			});
		}

		@Override
		public void renameCategory(int category, String name)
		{
			edit(t ->
			{
				if (category < t.categories.size())
				{
					t.categories.get(category).name = name;
				}
			});
		}

		@Override
		public void addCategory(String name)
		{
			edit(t -> t.categories.add(new Template.Category(name, Collections.emptyList())));
		}

		@Override
		public void deleteCategory(int category)
		{
			edit(t ->
			{
				if (category >= t.categories.size() || t.categories.size() <= 1)
				{
					return;
				}
				Template.Category gone = t.categories.remove(category);
				t.categories.get(Math.max(category - 1, 0)).potions.addAll(gone.potions);
			});
		}

		@Override
		public void movePotion(int potionId, int by)
		{
			edit(t ->
			{
				Template.Category c = t.categoryOf(potionId);
				if (c == null)
				{
					return;
				}
				int at = c.potions.indexOf(potionId);
				int to = at + by;
				if (to >= 0 && to < c.potions.size())
				{
					Collections.swap(c.potions, at, to);
				}
			});
		}

		@Override
		public void movePotionTo(int potionId, int category)
		{
			edit(t ->
			{
				Template.Category from = t.categoryOf(potionId);
				if (from == null || category >= t.categories.size())
				{
					return;
				}
				from.potions.remove(Integer.valueOf(potionId));
				t.categories.get(category).potions.add(potionId);
			});
		}

		@Override
		public void setDoses(int potionId, List<Integer> doses)
		{
			run(() ->
			{
				SlotPlan.Potion p = potion(potionId);
				if (p == null)
				{
					return;
				}
				List<Integer> now = SlotPlan.effectiveDoses(templates.working(), p);
				EnhancedPotionStoragePlugin.this.setDoses(p, doses, matchByDose(now, doses));
			});
		}
	}

	/** The placeholders to draw beside {@code stocked}: one for each potion run out that left one. */
	private List<Stack> placeholders(List<Stack> stocked)
	{
		if (!config.placeholders())
		{
			return Collections.emptyList();
		}
		Set<Integer> keys = savedOrder.placeholders();
		stocked.forEach(s -> keys.remove(s.key));
		return keys.stream().map(store::placeholder).filter(Objects::nonNull).collect(Collectors.toList());
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		store.varpChanged(e.getVarpId());
		if (e.getVarbitId() == VarbitID.BANK_DEPOSITPOTION)
		{
			// Turned back on: a later turn off is worth another warning.
			depositWarned &= e.getValue() == 0;
			if (sidebarShown())
			{
				sendPotions();
			}
		}
	}

	/**
	 * The bank's "Always deposit to potion storage" off sends deposited potions into the bank, where they take
	 * slots and show twice over with potion storage's own. Said once a login, on opening the bank.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() != InterfaceID.BANKMAIN || depositWarned || !config.warnDeposits()
			|| client.getVarbitValue(VarbitID.BANK_DEPOSITPOTION) != 0)
		{
			return;
		}
		depositWarned = true;
		warnInChat("\"Always deposit to potion storage\" is off, so potions you deposit go into your bank, not potion storage. "
			+ "Turn it on in the bank's menu.");
	}

	/**
	 * A potion that potion storage keeps, landing in the bank because "Always deposit to potion storage" is off:
	 * say what went where, every deposit. Seen as the bank's stack of it growing - nothing else adds to the bank
	 * while it is open - and a whole inventory deposited is one change, so one message.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		placeholderRelease.bankChanged(e.getItemContainer());
		Map<Integer, Integer> before = bankPotions;
		bankPotions = potionCounts(e.getItemContainer());
		if (before == null || !config.warnDeposits()
			|| client.getVarbitValue(VarbitID.BANK_DEPOSITPOTION) != 0)
		{
			return;
		}
		List<String> added = new ArrayList<>();
		bankPotions.forEach((id, n) ->
		{
			int more = n - before.getOrDefault(id, 0);
			if (more > 0)
			{
				added.add(more + " x " + itemManager.getItemComposition(id).getName());
			}
		});
		if (added.isEmpty())
		{
			return;
		}
		String what = added.size() <= DEPOSITS_NAMED ? String.join(", ", added)
			: String.join(", ", added.subList(0, DEPOSITS_NAMED)) + " and " + (added.size() - DEPOSITS_NAMED) + " more";
		warnInChat(what + " went into your bank, not potion storage: \"Always deposit to potion storage\" is off. "
			+ "Turn it on in the bank's menu.");
	}

	/** How many of each potion potion storage keeps the bank holds. */
	private Map<Integer, Integer> potionCounts(ItemContainer bank)
	{
		store.kinds();
		Set<Integer> potions = store.idsOf(k -> true);
		Map<Integer, Integer> out = new HashMap<>();
		for (Item item : bank.getItems())
		{
			if (potions.contains(item.getId()))
			{
				out.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return out;
	}

	private void warnInChat(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("Enhanced Potion Storage: ")
				.append(ChatColorType.NORMAL)
				.append(text)
				.build())
			.build());
	}

	/**
	 * A withdrawal from the store changes no bank slot, so nothing else would redraw the bank for it. And the
	 * store panel's draggable copies follow its icons as the list scrolls.
	 */
	@Subscribe
	public void onClientTick(ClientTick e)
	{
		// The bank's menu hides its items, so not bankShown(): any time the bank is open.
		if (client.getWidget(InterfaceID.Bankmain.RELEASE_PLACEHOLDERS) != null)
		{
			placeholderRelease.syncButton();
		}
		if (bankShown() && readStore())
		{
			relayout();
			if (sidebarShown())
			{
				sendPotions();
			}
		}
		panel.sync();
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		// Away from the bank the store is read only for the sidebar panel's dose counts.
		if (!bankShown() && sidebarShown() && readStore())
		{
			sendPotions();
		}
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (!bankShown() || bank == null)
		{
			return;
		}
		if (client.getTickCount() % STORE_REREAD_TICKS == 0)
		{
			store.markDirty();
		}
		if (store.expire())
		{
			relayout();
		}
		// Learn the order only from a bank that read the same a whole tick ago - never from one mid-update.
		int sig = view.signature(bank);
		boolean settled = sig == bankSignature;
		bankSignature = sig;
		if (settled && (learnPending || sig != learnedSignature))
		{
			learnPending = false;
			learnedSignature = sig;
			BankView.ServerBank server = view.readServer(bank);
			savedOrder.learn(server.reals, store.stacks().stream().map(s -> s.key).collect(Collectors.toList()), server.existing,
				this::newSection);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			depositWarned = false;
		}
		if (sidebarShown() && (e.getGameState() == GameState.LOGGED_IN || e.getGameState() == GameState.LOGIN_SCREEN))
		{
			clientThread.invokeLater(this::sendPotions);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e)
	{
		if (e.getGroupId() == InterfaceID.BANKMAIN && e.isUnload())
		{
			resetSession();
			storeButton.forget();
		}
	}

	// ---- clicks ----

	@Subscribe(priority = EARLY)
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		placeholderRelease.clicked(e);
		if (e.isConsumed())
		{
			return;
		}
		if (e.getParam1() == InterfaceID.Bankmain.POTIONSTORE_CONTAINER)
		{
			panel.click(e);
		}
		else if (e.getParam1() != InterfaceID.Bankmain.ITEMS)
		{
			return;
		}
		else if (e.getMenuAction() == MenuAction.RUNELITE && e.getMenuOption() != null
			&& Text.removeTags(e.getMenuOption()).startsWith(REMOVE_TAG))
		{
			removeStoredFromTag(e.getItemId());
		}
		else if (isWidgetOp(e.getMenuAction()) && e.getId() > 0xffff && frame.stackOn(e.getParam0()) != null
			&& !frame.keepsOwnOptions(e.getParam0()))
		{
			// The Doses submenu on a dose slot: which dose the slot shows - this client's layout, nothing to the server.
			e.consume();
			changeSlotDose(frame.stackOn(e.getParam0()), (e.getId() >> 16) - 1 - PotionStore.DOSE_OP_BASE);
		}
		else if (isWidgetOp(e.getMenuAction()) && frame.isStored(e.getParam0()) && frame.stackOn(e.getParam0()).placeholder)
		{
			// A placeholder is this plugin's alone: nothing of it goes to the server.
			e.consume();
			if (BankWidgets.RELEASE.equals(Text.removeTags(e.getMenuOption() == null ? "" : e.getMenuOption())))
			{
				Stack s = frame.stackOn(e.getParam0());
				savedOrder.release(List.of(s.key));
				templates.recordRemoved(new Removed(s.kind.id(), s.dose, s.key, Removed.How.RELEASED));
				relayout();
				if (sidebarShown())
				{
					sendPotions();
				}
			}
		}
		else if (isWidgetOp(e.getMenuAction()) && frame.withdrawsFromStore(e.getParam0()))
		{
			retargetToStore(e);
		}
	}

	/**
	 * A stored stack's withdraw is the store's own button. The stack is the one the menu was built for, by the
	 * item it carries - not whatever is drawn on the slot now: stored stacks take spare slots in turn, and a
	 * bank redrawn while the menu was open can have put another potion there. A click that cannot be pointed
	 * at the store goes nowhere, unless it is a real bank stack's, which is the bank's own click again.
	 */
	private void retargetToStore(MenuOptionClicked e)
	{
		int child = e.getParam0();
		boolean ownOptions = frame.keepsOwnOptions(child);
		if (ownOptions && (e.getId() < 1 || e.getId() > LAST_WITHDRAW_OP))
		{
			// A combined bank stack's Placeholder and Examine, and the Bank Tags plugin's Doses, stay its own.
			return;
		}
		MenuEntry m = e.getMenuEntry();
		Widget widget = m.getWidget();
		int itemId = e.getItemId() > 0 ? e.getItemId() : widget == null ? -1 : widget.getItemId();
		Stack stack = store.stackOf(itemId);
		if (stack == null || !store.prepareWidgets())
		{
			if (!ownOptions)
			{
				e.consume();
			}
			return;
		}
		String option = Text.removeTags(e.getMenuOption() == null ? "" : e.getMenuOption());
		if (!stack.active)
		{
			// Not the withdraw dose: its one option sets it - the store icon's own dose option, one click, one action.
			if (!SET_DOSE.equals(option) || ownOptions)
			{
				e.consume();
				return;
			}
			m.setParam1(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
			m.setParam0(PotionStore.iconChild(stack.kind.index));
			m.setIdentifier(PotionStore.DOSE_OP_BASE + stack.dose);
			return;
		}
		m.setParam1(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
		if (e.getId() > 0xffff)
		{
			// The Bank Tags plugin's Doses submenu, on a combined stack it drew: the sub-option's dose, on the icon.
			m.setParam0(PotionStore.iconChild(stack.kind.index));
			m.setIdentifier((e.getId() >> 16) - 1);
			return;
		}
		m.setParam0(PotionStore.rowChild(stack.kind.index));
		if (config.instantCounts() && widget != null && widget.getItemId() == stack.itemId)
		{
			ItemContainer bank = client.getItemContainer(InventoryID.BANK);
			int alsoShown = ownOptions && bank != null ? bank.count(stack.itemId) : 0;
			if (store.predictWithdraw(stack, option, widget, alsoShown))
			{
				// Every other slot of the potion draws on the same doses: count them down too.
				relayout();
			}
		}
	}

	/**
	 * The Bank Tags plugin's Remove-tag, finished for a stored potion. A stored potion is drawn as the dose the
	 * store pays out, so that plugin removes the tag - and the layout cells - of that dose alone; a cell or a
	 * tag holding another dose of it keeps drawing it. A real bank stack is left to that plugin.
	 */
	private void removeStoredFromTag(int itemId)
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		PotionStore.Kind kind = store.kindOf(itemId);
		if (kind != null && (bank == null || !bank.contains(itemId)) && bankTags.removeFromOpenTag(kind.doseIds))
		{
			relayout();
		}
	}

	// ---- the potion store's panel ----

	/** Before the Bank Tags plugin (0), which sizes its tab column after the pop-out tab is drawn. */
	@Subscribe(priority = BEFORE_BANK_TAGS)
	public void onScriptPostFired(ScriptPostFired e)
	{
		if (e.getScriptId() == ScriptID.POTIONSTORE_BUILD || e.getScriptId() == ScriptID.POTIONSTORE_DOSE_CHANGE)
		{
			panel.refresh();
		}
		else if (e.getScriptId() == ScriptID.BANKMAIN_POPUP_TAB_DRAW)
		{
			storeButton.apply(config.hideStoreButton());
		}
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort e)
	{
		panel.dedupeMenu();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		int param1 = e.getActionParam1();
		if (param1 == InterfaceID.Bankmain.POTIONSTORE_ITEMS || param1 == InterfaceID.Bankmain.POTIONSTORE_CONTAINER)
		{
			panel.addEditTags(e);
		}
	}

	// ---- the sidebar panel ----

	private boolean sidebarShown()
	{
		return sidebar != null && sidebar.isShowing();
	}

	/**
	 * Every dose slot the store can pay any of, in the plan's order, with the potions of its dose it holds, and
	 * the template part's state, to the sidebar panel. Client thread.
	 */
	private void sendPotions()
	{
		List<VisibilityPanel.Potion> potions = new ArrayList<>();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		if (loggedIn)
		{
			if (readStore() && bankShown())
			{
				relayout();
			}
			for (SlotPlan.Slot s : store.plan().slots())
			{
				PotionStore.Kind k = store.kindOf(s.itemId);
				int quantity = k == null ? 0 : store.doses(k) / s.dose;
				if (quantity > 0)
				{
					String name = client.getItemDefinition(s.itemId).getName();
					potions.add(new VisibilityPanel.Potion(k.id(), s.itemId, name, name.replaceAll(DOSE_SUFFIX, ""), s.dose,
						quantity, store.isActive(k, s.dose), !k.doses.isEmpty(), k.unfinished));
				}
			}
		}
		boolean depositsToStore = !loggedIn || client.getVarbitValue(VarbitID.BANK_DEPOSITPOTION) != 0;
		VisibilityPanel.Switches switches = new VisibilityPanel.Switches(config.inBank(), config.inTagTabs(),
			config.inSearch(), config.showUnfinished(), depositsToStore);
		TemplatePanel.State state = panelState();
		SwingUtilities.invokeLater(() ->
		{
			templatePanel.update(state);
			sidebar.update(loggedIn, potions, switches);
		});
	}

	// ---- drags ----

	@Subscribe(priority = AFTER_BANK_TAGS)
	public void onWidgetDrag(WidgetDrag e)
	{
		if (!drags.released(frame))
		{
			return;
		}
		Widget src = client.getDraggedWidget();
		if (panel.isCopy(src))
		{
			panel.drop(src);
		}
		else if (src != null)
		{
			drags.drop(frame, src);
		}
	}
}
