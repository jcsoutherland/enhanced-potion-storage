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

import com.potionstorage.BankArrangement.Entry;
import com.potionstorage.PotionStore.Stack;
import static com.potionstorage.BankWidgets.CELL_W;
import static com.potionstorage.BankWidgets.DOSES;
import static com.potionstorage.BankWidgets.DOSES_OP;
import static com.potionstorage.BankWidgets.EMPTY_OPACITY;
import static com.potionstorage.BankWidgets.ITEM_CHILDREN;
import static com.potionstorage.BankWidgets.ITEM_H;
import static com.potionstorage.BankWidgets.ITEM_START_X;
import static com.potionstorage.BankWidgets.ITEM_W;
import static com.potionstorage.BankWidgets.LABEL_BASE;
import static com.potionstorage.BankWidgets.LINE_ABOVE;
import static com.potionstorage.BankWidgets.PER_ROW;
import static com.potionstorage.BankWidgets.RELEASE;
import static com.potionstorage.BankWidgets.RELEASE_OP;
import static com.potionstorage.BankWidgets.ROW;
import static com.potionstorage.BankWidgets.SECTIONS;
import static com.potionstorage.BankWidgets.SECTION_GAP;
import static com.potionstorage.BankWidgets.SEPARATOR_BASE;
import static com.potionstorage.BankWidgets.STRIP_BASE;
import static com.potionstorage.BankWidgets.WITHDRAW;
import static com.potionstorage.BankWidgets.WITHDRAW_ALL_BUT_ONE;
import static com.potionstorage.BankWidgets.cellX;
import static com.potionstorage.BankWidgets.cellY;
import static com.potionstorage.BankWidgets.place;
import static com.potionstorage.BankWidgets.rows;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;

/**
 * Draws stored stacks into the bank the game has just built, and answers what the bank holds.
 * <p>
 * Every slot past the bank's last item is a spare widget the game already takes clicks and drags on, and a
 * stored stack is drawn on one of those ({@link #draw}). In the bank itself and in a search, a section the
 * game drew is re-flowed with its stored stacks in place and grown by the rows they need, and everything
 * below slides down ({@link #arrange}); in a tag tab without a layout, the matches go after what the game drew
 * ({@link #append}). Each returns a {@link Frame} of what went where.
 */
final class BankView
{
	/** The same item twice (one per set of charges) needs two entries: the copies are keyed past every id. */
	private static final int DUPLICATE_STRIDE = 1_000_000;

	/** The bank as the server has it: each tab's real items in slot order, and which slot is which entry. */
	static final class ServerBank
	{
		final Map<Integer, List<Integer>> reals = new TreeMap<>();
		final Set<Integer> existing = new HashSet<>(Collections.singleton(BankArrangement.MAIN));
		final Map<Entry, Integer> slotOf = new HashMap<>();
		final int[] tabSizes = new int[SECTIONS];
		int[] slotSection;
		Entry[] slotEntry;
	}

	/** What the game drew of the bank's own items: which slots, and where each section it drew begins. */
	private static final class Shown
	{
		final Set<Integer> slots = new HashSet<>();
		final Map<Integer, Integer> top = new HashMap<>();
		final Map<Integer, Integer> reals = new HashMap<>();
	}

	/** Where each laid-out section went, and how far everything below each one moves. */
	private static final class Plan
	{
		final Map<Integer, Integer> end = new HashMap<>();   // where a section's block ended as the game drew it
		final Map<Integer, Integer> delta = new HashMap<>(); // how far everything after it moves
		final Map<Integer, Integer> newTop = new HashMap<>();
		Integer lineAbove;
		int shift;
	}

	private final Client client;
	private final PotionStorageConfig config;
	private final SavedOrder savedOrder;
	/** Where a stored stack new to the order goes, by key: the tab it was deposited in. */
	private final IntUnaryOperator newSection;

	BankView(Client client, PotionStorageConfig config, SavedOrder savedOrder, IntUnaryOperator newSection)
	{
		this.client = client;
		this.config = config;
		this.savedOrder = savedOrder;
		this.newSection = newSection;
	}

	// ---- what the bank holds ----

	/** A placeholder's item; any other item is itself. */
	int realId(int itemId)
	{
		ItemComposition def = client.getItemDefinition(itemId);
		return def.getPlaceholderTemplateId() != -1 && def.getPlaceholderId() != -1 ? def.getPlaceholderId() : itemId;
	}

	/** Where the game puts a deposited item: the numbered tab open, or the untabbed part. */
	int depositSection()
	{
		int tab = client.getVarbitValue(VarbitID.BANK_CURRENTTAB);
		return tab >= 1 && tab < SECTIONS ? tab : BankArrangement.MAIN;
	}

	/** Every item the bank holds, a placeholder counted as its item. */
	Set<Integer> itemsHeld(ItemContainer bank)
	{
		Set<Integer> out = new HashSet<>();
		for (Item item : bank.getItems())
		{
			if (item.getId() >= 0)
			{
				out.add(realId(item.getId()));
			}
		}
		return out;
	}

	/** What the bank holds and how its tabs are cut, cheaply - to tell a settled bank from one mid-update. */
	int signature(ItemContainer bank)
	{
		int h = 1;
		for (int varbit : BankWidgets.TAB_VARBITS)
		{
			h = 31 * h + client.getVarbitValue(varbit);
		}
		for (Item item : bank.getItems())
		{
			h = 31 * h + item.getId();
		}
		return h;
	}

	ServerBank readServer(ItemContainer bank)
	{
		ServerBank b = new ServerBank();
		int[] tabEnd = new int[SECTIONS];
		int end = 0;
		for (int k = 1; k < SECTIONS; k++)
		{
			b.tabSizes[k] = client.getVarbitValue(BankWidgets.TAB_VARBITS[k - 1]);
			end += b.tabSizes[k];
			tabEnd[k] = end;
			if (b.tabSizes[k] > 0)
			{
				b.existing.add(k);
			}
		}
		Item[] items = bank.getItems();
		int n = Math.min(items.length, ITEM_CHILDREN);
		Set<Integer> held = new HashSet<>();
		for (int slot = 0; slot < n; slot++)
		{
			held.add(items[slot].getId());
		}
		b.slotSection = new int[n];
		b.slotEntry = new Entry[n];
		int tab = 1;
		for (int slot = 0; slot < n; slot++)
		{
			while (tab < SECTIONS && slot >= tabEnd[tab])
			{
				tab++;
			}
			int section = tab < SECTIONS ? tab : BankArrangement.MAIN;
			b.slotSection[slot] = section;
			int id = items[slot].getId();
			if (id >= 0)
			{
				Entry entry = uniqueEntry(b, canonical(id, held));
				b.slotEntry[slot] = entry;
				b.slotOf.put(entry, slot);
				b.reals.computeIfAbsent(section, s -> new ArrayList<>()).add(entry.id);
			}
		}
		return b;
	}

	/**
	 * A placeholder counts as the item it holds the place of - so the order survives withdrawing the last of a
	 * stack - unless that item is in the bank too, which one with charges or a dose can be.
	 */
	private int canonical(int id, Set<Integer> held)
	{
		int real = realId(id);
		return real != id && held.contains(real) ? id : real;
	}

	private static Entry uniqueEntry(ServerBank b, int id)
	{
		Entry entry = Entry.real(id);
		for (int copy = 1; b.slotOf.containsKey(entry); copy++)
		{
			entry = Entry.real(id + DUPLICATE_STRIDE * copy);
		}
		return entry;
	}

	// ---- the bank itself, and a search ----

	/**
	 * Lay the stored stacks out among the real items, a section at a time. For the bank itself ({@code search}
	 * null) every stack in its order; for a search, the real items the game matched and the stored stacks
	 * {@code search} passes, each in the section it is kept in. {@code tab} is the tab on show, 0 for "View all
	 * items" and for a search. A section the game drew grows by the rows it needs and everything below it
	 * slides down; a section it did not draw is opened where it belongs, with the line a tab has above it.
	 * <p>
	 * A search over the saved order can be rearranged like the bank; one over the server's order - the bank
	 * view off - has no order of stored stacks to change.
	 */
	Frame arrange(Widget items, ItemContainer bank, int tab, List<Stack> stacks, Predicate<Stack> search)
	{
		ServerBank server = readServer(bank);
		Frame frame = new Frame(search == null ? Frame.Mode.ARRANGE : config.inBank() ? Frame.Mode.SEARCH : Frame.Mode.FILTER);
		frame.slotSection = server.slotSection;
		frame.slotEntry = server.slotEntry;
		System.arraycopy(server.tabSizes, 0, frame.tabSizes, 0, SECTIONS);
		Map<Integer, Stack> byKey = new HashMap<>();
		stacks.forEach(s -> byKey.put(s.key, s));
		Map<Integer, List<Entry>> order = sectionOrder(server, stacks, frame);
		if (frame.mode != Frame.Mode.FILTER)
		{
			frame.drawn = order;
		}

		Shown shown = measureShown(items, server, search != null);
		Map<Integer, List<Entry>> display = displayed(order, byKey, server, shown.slots, search);
		List<Integer> sections = sectionsToLayOut(display.keySet(), shown.top.keySet(), tab, search != null);
		if (sections.isEmpty())
		{
			return frame;
		}
		Map<Widget, Integer> others = otherChildren(items, sections);
		Plan plan = layOut(frame, items, bank, server, sections, display, shown, byKey);
		slideOthers(others, plan, sections);
		for (int s : sections)
		{
			strip(items, s, display.get(s).size(), plan.newTop.get(s));
		}
		if (plan.lineAbove != null)
		{
			line(items, plan.lineAbove, plan.newTop.get(plan.lineAbove));
		}
		growScroll(plan.shift);
		return frame;
	}

	/**
	 * Every section's drawing order: the saved one when the bank view is on (see {@link SavedOrder#draw});
	 * otherwise the server's, with the stored stacks after the untabbed part's items.
	 */
	private Map<Integer, List<Entry>> sectionOrder(ServerBank server, List<Stack> stacks, Frame frame)
	{
		if (config.inBank())
		{
			List<Integer> keys = stacks.stream().map(s -> s.key).collect(Collectors.toList());
			BankArrangement.Resolved resolved = savedOrder.draw(server.reals, keys, server.existing, newSection);
			frame.orderChanged = resolved.changed;
			return resolved.drawn;
		}
		Map<Integer, List<Entry>> order = new TreeMap<>();
		server.reals.forEach((s, ids) -> order.put(s, ids.stream().map(Entry::real).collect(Collectors.toCollection(ArrayList::new))));
		List<Entry> main = order.computeIfAbsent(BankArrangement.MAIN, k -> new ArrayList<>());
		stacks.forEach(s -> main.add(Entry.stored(s.key)));
		return order;
	}

	/**
	 * Where the game drew each section it is showing, and which real items it drew. A search draws every tab's
	 * heading, matched or not, with its items starting under it; the untabbed part has no heading and is the
	 * very top.
	 */
	private Shown measureShown(Widget items, ServerBank server, boolean search)
	{
		Shown shown = new Shown();
		for (int slot = 0; slot < server.slotEntry.length; slot++)
		{
			Widget c = items.getChild(slot);
			if (c == null || c.isSelfHidden() || server.slotEntry[slot] == null)
			{
				continue;
			}
			int s = server.slotSection[slot];
			shown.slots.add(slot);
			shown.top.merge(s, c.getOriginalY(), Math::min);
			shown.reals.merge(s, 1, Integer::sum);
		}
		if (search)
		{
			shown.top.putIfAbsent(BankArrangement.MAIN, 0);
			shown.reals.putIfAbsent(BankArrangement.MAIN, 0);
			for (int k = 1; k < SECTIONS; k++)
			{
				Widget label = items.getChild(LABEL_BASE + k - 1);
				if (!shown.top.containsKey(k) && label != null && !label.isSelfHidden())
				{
					shown.top.put(k, label.getOriginalY() + label.getOriginalHeight());
					shown.reals.put(k, 0);
				}
			}
		}
		return shown;
	}

	/** What each section shows: the real items the game drew there, and its stored stacks that pass. */
	private static Map<Integer, List<Entry>> displayed(Map<Integer, List<Entry>> order, Map<Integer, Stack> byKey,
		ServerBank server, Set<Integer> shownSlots, Predicate<Stack> search)
	{
		Map<Integer, List<Entry>> display = new TreeMap<>();
		order.forEach((s, list) ->
		{
			List<Entry> d = new ArrayList<>();
			for (Entry e : list)
			{
				boolean show;
				if (e.stored)
				{
					Stack st = byKey.get(e.id);
					show = st != null && (search == null || search.test(st));
				}
				else
				{
					Integer slot = server.slotOf.get(e);
					show = slot != null && server.slotSection[slot] == s && shownSlots.contains(slot);
				}
				if (show)
				{
					d.add(e);
				}
			}
			if (!d.isEmpty())
			{
				display.put(s, d);
			}
		});
		return display;
	}

	/**
	 * The sections to lay out, top to bottom: every one the game drew; and in a view of the whole bank, one it
	 * did not draw but that has stored stacks to show - any tab in a search, only the untabbed part otherwise
	 * (a tab the game left out of "View all items" is collapsed). {@code withEntries} is in section order.
	 */
	private static List<Integer> sectionsToLayOut(Set<Integer> withEntries, Set<Integer> drawnByGame, int tab, boolean search)
	{
		List<Integer> out = new ArrayList<>();
		for (int s : withEntries)
		{
			if (drawnByGame.contains(s) || (tab == 0 && (search || s == BankArrangement.MAIN)))
			{
				out.add(s);
			}
		}
		return out;
	}

	/**
	 * Every non-item child the game showed - separator lines, "Tab N" headings, drop strips, and whatever other
	 * plugins added - as it placed it, plus the drop strips of the sections being laid out.
	 */
	private static Map<Widget, Integer> otherChildren(Widget items, List<Integer> sections)
	{
		Widget[] children = items.getChildren();
		Map<Widget, Integer> others = new HashMap<>();
		for (int i = ITEM_CHILDREN; children != null && i < children.length; i++)
		{
			Widget c = children[i];
			if (c != null && (!c.isSelfHidden() || sections.contains(i - STRIP_BASE)))
			{
				others.put(c, c.getOriginalY());
			}
		}
		return others;
	}

	/** Each section in turn: where it now starts, its stacks placed, and how much it grew. */
	private Plan layOut(Frame frame, Widget items, ItemContainer bank, ServerBank server, List<Integer> sections,
		Map<Integer, List<Entry>> display, Shown shown, Map<Integer, Stack> byKey)
	{
		Plan plan = new Plan();
		Iterator<Widget> spares = spares(items, bank);
		int anchor = 0; // where the last section the game drew ends, as it drew it
		for (int i = 0; i < sections.size(); i++)
		{
			int s = sections.get(i);
			List<Entry> list = display.get(s);
			int y0;
			int d;
			Integer top = shown.top.get(s);
			if (top != null)
			{
				int oldRows = rows(shown.reals.get(s));
				y0 = top + plan.shift;
				d = (rows(list.size()) - oldRows) * ROW;
				anchor = top + oldRows * ROW;
				plan.end.put(s, anchor);
			}
			else if (i == 0)
			{
				// First on show: it opens at the very top, and the section after it - drawn with nothing above
				// it - needs the gap and the line a section below another has.
				Integer nextTop = i + 1 < sections.size() ? shown.top.get(sections.get(i + 1)) : null;
				y0 = plan.shift;
				d = rows(list.size()) * ROW + (nextTop == null ? 0 : SECTION_GAP - Math.min(nextTop, SECTION_GAP));
				plan.end.put(s, 0);
				if (nextTop != null && nextTop < SECTION_GAP)
				{
					plan.lineAbove = sections.get(i + 1);
				}
			}
			else
			{
				y0 = anchor + plan.shift + SECTION_GAP;
				d = SECTION_GAP + rows(list.size()) * ROW;
				plan.end.put(s, anchor);
				line(items, s, y0);
			}
			plan.newTop.put(s, y0);
			plan.delta.put(s, d);
			plan.shift += d;
			placeSection(frame, items, spares, server, list, y0, byKey);
		}
		return plan;
	}

	/** One section's stacks onto its grid: real items moved, stored stacks drawn on spare slots. */
	private void placeSection(Frame frame, Widget items, Iterator<Widget> spares, ServerBank server, List<Entry> list,
		int top, Map<Integer, Stack> byKey)
	{
		for (int j = 0; j < list.size(); j++)
		{
			Entry e = list.get(j);
			int x = cellX(j);
			int y = cellY(top, j);
			if (!e.stored)
			{
				Integer slot = server.slotOf.get(e);
				Widget c = slot == null ? null : items.getChild(slot);
				if (c != null)
				{
					place(c, x, y);
				}
				continue;
			}
			Stack stack = byKey.get(e.id);
			if (stack == null || !spares.hasNext())
			{
				continue;
			}
			Widget child = spares.next();
			draw(child, stack);
			place(child, x, y);
			frame.putStored(child.getIndex(), stack);
		}
	}

	/** The lines and other children slide down with every section above them that grew. */
	private static void slideOthers(Map<Widget, Integer> others, Plan plan, List<Integer> sections)
	{
		for (Map.Entry<Widget, Integer> o : others.entrySet())
		{
			Widget c = o.getKey();
			int y = o.getValue();
			if (sections.contains(c.getIndex() - STRIP_BASE))
			{
				continue;
			}
			int by = 0;
			for (Map.Entry<Integer, Integer> t : plan.end.entrySet())
			{
				if (t.getValue() <= y)
				{
					by += plan.delta.get(t.getKey());
				}
			}
			if (by != 0)
			{
				c.setOriginalY(y + by);
				c.revalidate();
			}
		}
	}

	// ---- a tag tab without a layout ----

	/** The matching stored stacks after the last stack the game drew. */
	Frame append(Widget items, ItemContainer bank, List<Stack> matches)
	{
		Frame frame = new Frame(Frame.Mode.FILTER);
		int lastX = -1;
		int lastY = -1;
		for (int slot = 0; slot < Math.min(bank.size(), ITEM_CHILDREN); slot++)
		{
			Widget c = items.getChild(slot);
			if (c == null || c.isSelfHidden() || c.getItemId() < 0)
			{
				continue;
			}
			if (c.getOriginalY() > lastY || (c.getOriginalY() == lastY && c.getOriginalX() > lastX))
			{
				lastY = c.getOriginalY();
				lastX = c.getOriginalX();
			}
		}
		int col = lastY < 0 ? 0 : (lastX - ITEM_START_X) / CELL_W + 1;
		int y = Math.max(lastY, 0);
		int rowsBefore = lastY < 0 ? 0 : lastY / ROW + 1;
		Iterator<Widget> spares = spares(items, bank);
		for (Stack s : matches)
		{
			if (!spares.hasNext())
			{
				break;
			}
			Widget child = spares.next();
			if (col >= PER_ROW)
			{
				col = 0;
				y += ROW;
			}
			draw(child, s);
			place(child, ITEM_START_X + col * CELL_W, y);
			frame.putStored(child.getIndex(), s);
			col++;
		}
		if (!matches.isEmpty())
		{
			growScroll((y / ROW + 1 - rowsBefore) * ROW);
		}
		return frame;
	}

	// ---- a laid-out tag tab ----

	/**
	 * Empty the cells of a laid-out tag tab that the Bank Tags plugin drew a {@code hidden} potion in - one
	 * from the store, or the faded mark of one the store has run out of - the way that plugin draws an empty
	 * cell, so a stack can still be dropped there. The layout keeps their places. A potion the bank holds is
	 * the bank's own and stays.
	 */
	void blankLayoutCells(Widget items, ItemContainer bank, Set<Integer> hidden)
	{
		for (int i = 0; i < ITEM_CHILDREN; i++)
		{
			Widget c = items.getChild(i);
			if (c == null || c.isSelfHidden() || !hidden.contains(c.getItemId()) || bank.count(c.getItemId()) > 0)
			{
				continue;
			}
			c.setOriginalWidth(CELL_W);
			c.setOriginalHeight(ROW);
			c.clearActions();
			c.setItemId(-1);
			c.setItemQuantity(0);
			c.setOnDragListener((Object[]) null);
			c.setOnDragCompleteListener((JavaScriptCallback) ev -> client.setDraggedOnWidget(null));
			c.revalidate();
		}
	}

	/**
	 * Take the Doses submenu off the cells of a laid-out tag tab that hold a {@code singleDose} potion. The
	 * Bank Tags plugin gives every stored potion one, even one - an unfinished potion - that comes in a single
	 * dose and so has nothing to pick, and that potion storage itself gives none.
	 */
	void dropDosesMenu(Widget items, Set<Integer> singleDose)
	{
		for (int i = 0; i < ITEM_CHILDREN; i++)
		{
			Widget c = items.getChild(i);
			String[] actions = c == null || c.isSelfHidden() || !singleDose.contains(c.getItemId()) ? null : c.getActions();
			if (actions == null || actions.length <= DOSES_OP || !DOSES.equals(actions[DOSES_OP]))
			{
				continue;
			}
			String[] kept = actions.clone();
			c.clearActions();
			for (int op = 0; op < kept.length; op++)
			{
				if (op != DOSES_OP && kept[op] != null)
				{
					c.setAction(op, kept[op]);
				}
			}
		}
	}

	// ---- combined stacks ----

	/**
	 * One stack for a potion the bank and the store both hold at the same dose: wherever the bank's stack is
	 * drawn - by the game, or by the Bank Tags plugin in a laid-out tag tab - it shows both counts. A
	 * placeholder becomes the store's stack, drawn and withdrawn like any stored potion. A real stack keeps its
	 * own options; with {@code storageFirst}, its withdraw options are the store's while it has any. A stored
	 * potion the Bank Tags plugin drew itself in a laid-out tab is treated the same, and keeps that plugin's
	 * Doses submenu.
	 */
	void combine(Frame frame, Widget items, ItemContainer bank, List<Stack> stacks, boolean storageFirst)
	{
		Map<Integer, Stack> byItem = new HashMap<>();
		stacks.forEach(s -> byItem.put(s.itemId, s));
		for (int i = 0; i < ITEM_CHILDREN; i++)
		{
			Widget c = items.getChild(i);
			if (c == null || c.isSelfHidden() || c.getItemId() <= 0 || frame.isStored(i))
			{
				continue;
			}
			int real = realId(c.getItemId());
			Stack s = byItem.get(real);
			if (s == null)
			{
				continue;
			}
			if (real != c.getItemId())
			{
				draw(c, s);
				frame.putCombined(i, s, false);
			}
			else
			{
				c.setItemQuantity(bank.count(real) + s.quantity);
				if (storageFirst)
				{
					frame.putCombined(i, s, true);
				}
			}
		}
	}

	// ---- drawing ----

	/** A stored stack on a widget, with the options a bank stack has - mostly {@code ~bankmain_drawitem}'s. */
	private void draw(Widget c, Stack s)
	{
		c.setOriginalWidth(ITEM_W);
		c.setOriginalHeight(ITEM_H);
		if (s.placeholder)
		{
			drawPlaceholder(c, s);
			return;
		}
		c.setItemId(s.itemId);
		c.setItemQuantity(s.quantity);
		c.setItemQuantityMode(ItemQuantityMode.STACKABLE);
		c.setName("<col=ff9040>" + s.name + "</col>");
		c.clearActions();
		int quantityType = client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE);
		int requested = client.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY);
		c.setAction(0, WITHDRAW + defaultWithdraw(quantityType, requested));
		if (quantityType != 0)
		{
			c.setAction(1, WITHDRAW + "1");
		}
		c.setAction(2, WITHDRAW + "5");
		c.setAction(3, WITHDRAW + "10");
		if (requested > 0)
		{
			c.setAction(4, WITHDRAW + requested);
		}
		c.setAction(5, WITHDRAW + "X");
		c.setAction(6, WITHDRAW + "All");
		c.setAction(7, WITHDRAW_ALL_BUT_ONE);
		if (!s.kind.doses.isEmpty())
		{
			c.setAction(DOSES_OP, DOSES);
			for (int d : s.kind.doses)
			{
				c.setSubOp(DOSES_OP, 5 + d, d + " Dose");
			}
		}
		// Faded like a placeholder while a withdrawal of the last of it is still on its way.
		c.setOpacity(s.quantity > 0 ? 0 : EMPTY_OPACITY);
		c.setOnDragListener(ScriptID.BANKMAIN_DRAGSCROLL, ScriptEvent.WIDGET_ID, ScriptEvent.WIDGET_INDEX,
			ScriptEvent.MOUSE_X, ScriptEvent.MOUSE_Y, InterfaceID.Bankmain.SCROLLBAR, 0);
	}

	/** A stored potion run out, drawn as the game draws a bank placeholder: its placeholder item, faded, Release. */
	private void drawPlaceholder(Widget c, Stack s)
	{
		int placeholderId = client.getItemDefinition(s.itemId).getPlaceholderId();
		c.setItemId(placeholderId > 0 ? placeholderId : s.itemId);
		c.setItemQuantity(0);
		c.setItemQuantityMode(ItemQuantityMode.STACKABLE);
		c.setName("<col=ff9040>" + s.name + "</col>");
		c.clearActions();
		c.setAction(RELEASE_OP, RELEASE);
		c.setOpacity(EMPTY_OPACITY);
		c.setOnDragListener(ScriptID.BANKMAIN_DRAGSCROLL, ScriptEvent.WIDGET_ID, ScriptEvent.WIDGET_INDEX,
			ScriptEvent.MOUSE_X, ScriptEvent.MOUSE_Y, InterfaceID.Bankmain.SCROLLBAR, 0);
	}

	/** The amount on a bank stack's left-click option, by the bank's quantity setting. */
	static String defaultWithdraw(int quantityType, int requested)
	{
		switch (quantityType)
		{
			case 1:
				return "5";
			case 2:
				return "10";
			case 3:
				return Integer.toString(Math.max(1, requested));
			case 4:
				return "All";
			default:
				return "1";
		}
	}

	/** The separator line the game draws above tab {@code section}, put above {@code top}. */
	private static void line(Widget items, int section, int top)
	{
		Widget sep = section > 0 ? items.getChild(SEPARATOR_BASE + section - 1) : null;
		if (sep == null)
		{
			return;
		}
		if (sep.getSpriteId() < 0)
		{
			sep.setSpriteId(SpriteID.TRADEBACKING_DARK);
		}
		sep.setOriginalX(ITEM_START_X);
		sep.setOriginalWidth(BankWidgets.SEPARATOR_W);
		sep.setOriginalHeight(2);
		sep.setOriginalY(top - LINE_ABOVE);
		sep.setHidden(false);
		sep.revalidate();
	}

	/** The transparent strip after a section's last stack that takes "drop at the end of this tab". */
	private static void strip(Widget items, int section, int count, int top)
	{
		Widget c = items.getChild(STRIP_BASE + section);
		if (c == null)
		{
			return;
		}
		int rem = count % PER_ROW;
		if (count == 0 || rem == 0)
		{
			c.setHidden(true);
			return;
		}
		c.setOriginalX(ITEM_START_X + rem * CELL_W);
		c.setOriginalY(top + (rows(count) - 1) * ROW + 4);
		c.setOriginalWidth((PER_ROW - rem) * CELL_W - 12);
		c.setOriginalHeight(ITEM_H);
		c.setHidden(false);
		c.revalidate();
	}

	/**
	 * The empty slots after the bank's last item, in order. Counted from that item, not from the container's
	 * size: after a deposit the server can send the bank padded out to all 1,410 slots, and a size-based count
	 * then finds no spare slot at all.
	 */
	private static Iterator<Widget> spares(Widget items, ItemContainer bank)
	{
		Item[] held = bank.getItems();
		int first = 0;
		for (int i = held.length - 1; i >= 0; i--)
		{
			if (held[i].getId() >= 0)
			{
				first = i + 1;
				break;
			}
		}
		List<Widget> out = new ArrayList<>();
		for (int i = first; i < ITEM_CHILDREN; i++)
		{
			Widget c = items.getChild(i);
			if (c != null)
			{
				out.add(c);
			}
		}
		return out.iterator();
	}

	/** The argument {@code bankmain_finishbuilding} hands to {@code if_setscrollsize} is 9 from the top of the stack. */
	private void growScroll(int by)
	{
		if (by <= 0)
		{
			return;
		}
		int[] stack = client.getIntStack();
		int i = client.getIntStackSize() - 9;
		if (i >= 0)
		{
			stack[i] += by;
		}
	}
}
