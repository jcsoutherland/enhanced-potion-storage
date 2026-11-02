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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * The bank's potion storage: what it holds, read through the game's own scripts the moment its varps change,
 * and the withdrawals clicked but not yet answered - as dose slots, one for each dose the template asks for
 * ({@link SlotPlan}).
 * <p>
 * The store lists every potion kind it knows, held or not, in a fixed order, and builds five widgets per kind
 * in that order: the withdraw row, the icon (which carries the doses), the name, the dose count and the
 * favourite star. A click is pointed at a kind's row or icon by that order alone.
 * <p>
 * The store pays a potion out at one dose at a time: its withdraw dose, set with the icon's dose options. Every
 * slot counts what it could pay at its own dose, but only a slot at the withdraw dose withdraws.
 */
final class PotionStore
{
	static final int COMPONENTS_PER_POTION = 5;
	/** The store icon's dose options are its ops 6 to 9: this plus the dose. */
	static final int DOSE_OP_BASE = 5;

	/** The varps the store's doses live in; the store widget's own transmit list adds to these once seen. */
	private static final Set<Integer> BASE_VARS = Set.of(
		VarPlayerID.POTIONSTORE_BASE_VAR_1, VarPlayerID.POTIONSTORE_BASE_VAR_2, VarPlayerID.POTIONSTORE_BASE_VAR_3,
		VarPlayerID.POTIONSTORE_BASE_VAR_4, VarPlayerID.POTIONSTORE_BASE_VAR_5, VarPlayerID.POTIONSTORE_BASE_VAR_6);
	/** A withdrawal the server has not answered for this many ticks since it was last added to is dropped. */
	private static final int PENDING_TICKS = 3;
	private static final int INVENTORY_SIZE = 28;

	/** The store's withdraw row for potion kind {@code kind}. */
	static int rowChild(int kind)
	{
		return kind * COMPONENTS_PER_POTION;
	}

	/** The store's icon - the component with the doses - for potion kind {@code kind}. */
	static int iconChild(int kind)
	{
		return rowChild(kind) + 1;
	}

	/** One potion kind in the store's own list. */
	static final class Kind
	{
		final int index;
		final int enumId;
		final EnumComposition potionEnum;
		/** From the store's list of unfinished potions rather than its potions. */
		final boolean unfinished;
		/** Every dose's item id, 1-dose first; an unfinished potion has one. */
		final List<Integer> doseIds;
		/** The dose numbers it comes in, for a Doses submenu: empty when there is only one, as for an unfinished potion. */
		final List<Integer> doses;
		/** The doses in one whole potion: its largest dose, 1 for an unfinished potion. */
		final int fullDose;
		/** Item id by dose. */
		final Map<Integer, Integer> itemsByDose;

		Kind(int index, int enumId, EnumComposition potionEnum, boolean unfinished)
		{
			this.index = index;
			this.enumId = enumId;
			this.potionEnum = potionEnum;
			this.unfinished = unfinished;
			List<Integer> ids = new ArrayList<>();
			List<Integer> numbers = new ArrayList<>();
			Map<Integer, Integer> byDose = new LinkedHashMap<>();
			for (int d = 1; d <= 4; d++)
			{
				int id = potionEnum.getIntValue(d);
				if (id > 0)
				{
					ids.add(id);
					numbers.add(d);
					byDose.put(d, id);
				}
			}
			this.doseIds = Collections.unmodifiableList(ids);
			this.doses = numbers.size() > 1 && !unfinished ? Collections.unmodifiableList(numbers) : Collections.emptyList();
			this.fullDose = numbers.isEmpty() ? 1 : numbers.get(numbers.size() - 1);
			this.itemsByDose = Collections.unmodifiableMap(byDose);
		}

		/** What the visibility settings and templates know it by: its smallest dose's item id, which no other kind shares. */
		int id()
		{
			return doseIds.isEmpty() ? -1 : doseIds.get(0);
		}

		/** The dose it is pictured as: its largest, the potion as it is made. */
		int iconId()
		{
			return doseIds.isEmpty() ? -1 : doseIds.get(doseIds.size() - 1);
		}
	}

	/** One dose slot the store can pay out, as the plugin draws it. */
	static final class Stack
	{
		/** What the saved order knows it by: the slot's key ({@link SlotPlan#key}). */
		final int key;
		/** The potion at the slot's dose. */
		final int itemId;
		/** Potions of the slot's dose the store could pay, less any withdrawal still in flight. */
		final int quantity;
		final String name;
		final Kind kind;
		final int dose;
		/** The store's withdraw dose is this slot's, so it withdraws; otherwise a click sets the withdraw dose to it. */
		final boolean active;
		/** Every item id it might be tagged as: its dose, then each other dose. */
		final List<Integer> ids;
		/** Run out, and kept in its place as a placeholder: holds nothing, withdraws nothing. */
		final boolean placeholder;

		Stack(int key, int itemId, int quantity, String name, Kind kind, int dose, boolean active, boolean placeholder)
		{
			this.placeholder = placeholder;
			this.key = key;
			this.itemId = itemId;
			this.quantity = quantity;
			this.name = name;
			this.kind = kind;
			this.dose = dose;
			this.active = active;
			List<Integer> all = new ArrayList<>();
			all.add(itemId);
			kind.doseIds.stream().filter(id -> id != itemId).forEach(all::add);
			this.ids = Collections.unmodifiableList(all);
		}

		/** One with a choice of doses to show and to set. */
		boolean hasDoseChoice()
		{
			return !kind.doses.isEmpty();
		}
	}

	/** Doses taken by clicks the server has not answered yet, counted from {@code base}, the doses when clicked. */
	private static final class Pending
	{
		int takenDoses;
		int takenPotions;
		int base;
		int tick;

		Pending(int base)
		{
			this.base = base;
		}
	}

	private final Client client;

	private List<Kind> kinds;
	private int[] doses = new int[0];
	private int[] withdraw = new int[0];
	private boolean dirty = true;
	private Set<Integer> vars = BASE_VARS;
	private final Map<Integer, Pending> pending = new HashMap<>(); // kind index -> withdrawals in flight
	private SlotPlan plan = SlotPlan.empty();

	PotionStore(Client client)
	{
		this.client = client;
	}

	/** Forget the store, for a new bank session: everything is read again. */
	void reset()
	{
		kinds = null;
		doses = new int[0];
		withdraw = new int[0];
		dirty = true;
		vars = BASE_VARS;
		pending.clear();
	}

	/** The slots to draw from now on. Client thread. */
	void setPlan(SlotPlan plan)
	{
		this.plan = plan;
	}

	SlotPlan plan()
	{
		return plan;
	}

	void varpChanged(int varpId)
	{
		if (vars.contains(varpId))
		{
			dirty = true;
		}
	}

	/** Read again at the next chance, whatever varp changed or did not. */
	void markDirty()
	{
		dirty = true;
	}

	/** Read the store if a varp says it changed. Not inside a script. True when its numbers changed. */
	boolean readIfDirty()
	{
		if (!dirty)
		{
			return false;
		}
		dirty = false;
		if (kinds == null)
		{
			kinds = readKinds();
		}
		int[] d = new int[kinds.size()];
		int[] w = new int[kinds.size()];
		for (Kind k : kinds)
		{
			client.runScript(ScriptID.POTIONSTORE_DOSES, k.enumId);
			d[k.index] = client.getIntStack()[0];
			client.runScript(ScriptID.POTIONSTORE_WITHDRAW_DOSES, k.enumId);
			w[k.index] = client.getIntStack()[0];
		}
		learnVars();
		if (Arrays.equals(d, doses) && Arrays.equals(w, withdraw))
		{
			return false;
		}
		doses = d;
		withdraw = w;
		settle();
		return true;
	}

	/** The store's potion kinds in its own order, the order its widget list is built in. */
	private List<Kind> readKinds()
	{
		List<Kind> out = new ArrayList<>();
		for (int listId : new int[]{EnumID.POTIONSTORE_POTIONS, EnumID.POTIONSTORE_UNFINISHED_POTIONS})
		{
			EnumComposition list = client.getEnum(listId);
			if (list == null)
			{
				continue;
			}
			for (int potionEnumId : list.getIntVals())
			{
				EnumComposition potionEnum = client.getEnum(potionEnumId);
				if (potionEnum != null)
				{
					out.add(new Kind(out.size(), potionEnumId, potionEnum, listId == EnumID.POTIONSTORE_UNFINISHED_POTIONS));
				}
			}
		}
		return out;
	}

	/** The varps the store's widget redraws on, once it has been drawn: a change in any is a change in stock. */
	private void learnVars()
	{
		Widget list = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
		int[] trigger = list == null ? null : list.getVarTransmitTrigger();
		if (trigger != null && trigger.length > 0)
		{
			Set<Integer> v = new HashSet<>(BASE_VARS);
			Arrays.stream(trigger).forEach(v::add);
			vars = v;
		}
	}

	/** The store's withdraw dose for {@code kind}: 0 before a read. */
	int withdrawDose(Kind kind)
	{
		return kind.index < withdraw.length ? withdraw[kind.index] : 0;
	}

	/** Potions of {@code kind} at {@code dose} the store could pay, by the server's last word. */
	private int stock(Kind kind, int dose)
	{
		return dose > 0 ? doses(kind) / dose : 0;
	}

	/** Potions of {@code kind} at {@code dose} the store could pay, less what is in flight. */
	private int shown(Kind kind, int dose)
	{
		Pending p = pending.get(kind.index);
		return dose > 0 ? Math.max(doses(kind) - (p == null ? 0 : p.takenDoses), 0) / dose : 0;
	}

	/** The slot's dose is the one the store pays out - always, for a potion with one dose. */
	boolean isActive(Kind kind, int dose)
	{
		return kind.doses.isEmpty() || withdrawDose(kind) == dose;
	}

	private Map<Integer, Kind> kindsById()
	{
		Map<Integer, Kind> out = new HashMap<>();
		if (kinds != null)
		{
			kinds.forEach(k -> out.put(k.id(), k));
		}
		return out;
	}

	/**
	 * Every dose slot the plan asks for that the store can pay at least one potion of, in the plan's order, less
	 * what is in flight. Empty until the store has been read. The store's empty vials are left out on purpose:
	 * they are not something anyone banks for.
	 */
	List<Stack> stacks()
	{
		if (kinds == null)
		{
			return Collections.emptyList();
		}
		Map<Integer, Kind> byId = kindsById();
		List<Stack> out = new ArrayList<>();
		for (SlotPlan.Slot s : plan.slots())
		{
			Kind k = byId.get(s.potionId);
			if (k != null && stock(k, s.dose) > 0)
			{
				out.add(new Stack(s.key, s.itemId, shown(k, s.dose), client.getItemDefinition(s.itemId).getName(), k,
					s.dose, isActive(k, s.dose), false));
			}
		}
		return out;
	}

	/**
	 * Every potion kind the store knows, held or not, in its own order, read from the game's cache if it has not
	 * been yet. Client thread.
	 */
	List<Kind> kinds()
	{
		if (kinds == null || kinds.isEmpty())
		{
			kinds = readKinds();
		}
		return Collections.unmodifiableList(kinds);
	}

	/** Every potion kind as a template knows it, named without its dose. Client thread. */
	List<SlotPlan.Potion> potions()
	{
		List<SlotPlan.Potion> out = new ArrayList<>();
		for (Kind k : kinds())
		{
			if (k.id() > 0)
			{
				out.add(new SlotPlan.Potion(k.id(), nameOf(k), k.unfinished, k.index, k.itemsByDose));
			}
		}
		return out;
	}

	/** A potion's name without its dose: "Saradomin brew". */
	String nameOf(Kind k)
	{
		return client.getItemDefinition(k.iconId()).getName().replaceAll("\\s*\\(\\d\\)$", "");
	}

	/** The doses of {@code kind} the store holds, by its last read: 0 before one. */
	int doses(Kind kind)
	{
		return kind.index < doses.length ? doses[kind.index] : 0;
	}

	/** Whether the store has been read this bank session, so its numbers mean something. */
	boolean hasRead()
	{
		return doses.length > 0;
	}

	/** The slots, by key, the store can pay any of by the server's numbers. Empty until it has been read. */
	Set<Integer> stockedKeys()
	{
		Set<Integer> out = new HashSet<>();
		if (kinds != null)
		{
			Map<Integer, Kind> byId = kindsById();
			for (SlotPlan.Slot s : plan.slots())
			{
				Kind k = byId.get(s.potionId);
				if (k != null && stock(k, s.dose) > 0)
				{
					out.add(s.key);
				}
			}
		}
		return out;
	}

	/** A placeholder for the slot known as {@code key}, drawn as its dose; null when the plan has no such slot. */
	Stack placeholder(int key)
	{
		SlotPlan.Slot s = plan.slot(key);
		Kind k = s == null || kinds == null ? null : kindsById().get(s.potionId);
		if (k == null)
		{
			return null;
		}
		return new Stack(key, s.itemId, 0, client.getItemDefinition(s.itemId).getName(), k, s.dose,
			isActive(k, s.dose), true);
	}

	/** Every dose of every potion kind {@code which} picks. Empty until the store has been read. */
	Set<Integer> idsOf(Predicate<Kind> which)
	{
		Set<Integer> out = new HashSet<>();
		if (kinds != null)
		{
			kinds.stream().filter(which).forEach(k -> out.addAll(k.doseIds));
		}
		return out;
	}

	/** A slot the store pays out as {@code itemId}; null when it pays out no such potion. */
	Stack stackOf(int itemId)
	{
		for (Stack s : stacks())
		{
			if (s.itemId == itemId)
			{
				return s;
			}
		}
		return null;
	}

	/** The kind {@code itemId} is a dose of; null when it is no potion the store keeps. */
	Kind kindOf(int itemId)
	{
		if (kinds != null)
		{
			for (Kind k : kinds)
			{
				if (k.doseIds.contains(itemId))
				{
					return k;
				}
			}
		}
		return null;
	}

	/** New numbers from the server: count off what they show of each withdrawal in flight. */
	private void settle()
	{
		pending.entrySet().removeIf(entry ->
		{
			int i = entry.getKey();
			if (kinds == null || i >= kinds.size())
			{
				return true;
			}
			Pending p = entry.getValue();
			int now = doses(kinds.get(i));
			int landed = p.base - now;
			if (now == p.base)
			{
				return false;
			}
			if (landed > 0 && landed < p.takenDoses)
			{
				p.takenDoses -= landed;
				p.takenPotions = Math.max(p.takenPotions - landed / Math.max(withdrawDose(kinds.get(i)), 1), 0);
				p.base = now;
				p.tick = client.getTickCount();
				return false;
			}
			return true;
		});
	}

	/** Drop the withdrawals the server has stopped answering. True when any was dropped. */
	boolean expire()
	{
		int tick = client.getTickCount();
		return pending.values().removeIf(p -> tick - p.tick > PENDING_TICKS);
	}

	/**
	 * Count a withdrawal off its stack now rather than a round trip later - off every slot of the potion, as all
	 * of them draw on the same doses. The amount is what the option says, no more than the stack holds or -
	 * unless withdrawing as notes - the inventory has room for. X and the Doses submenu are left to the server:
	 * neither says how many it takes. The server's numbers replace the guess when they come ({@link #settle()}),
	 * or it is dropped after {@link #PENDING_TICKS} ticks without another click on it. {@code alsoShown} is what
	 * the widget shows besides the store's count - a bank stack's own, on a combined stack. True when it counted.
	 */
	boolean predictWithdraw(Stack s, String option, Widget w, int alsoShown)
	{
		if (!s.active || !option.startsWith(BankWidgets.WITHDRAW))
		{
			return false;
		}
		String what = option.substring(BankWidgets.WITHDRAW.length());
		int shown = shown(s.kind, s.dose);
		int n;
		if (what.equals("All"))
		{
			n = shown;
		}
		else if (what.equals("All-but-1"))
		{
			n = shown - 1;
		}
		else
		{
			try
			{
				n = Integer.parseInt(what);
			}
			catch (NumberFormatException ex)
			{
				return false;
			}
		}
		if (client.getVarbitValue(VarbitID.BANK_WITHDRAWNOTES) == 0)
		{
			n = Math.min(n, freeSlots() - pending.values().stream().mapToInt(p -> p.takenPotions).sum());
		}
		n = Math.min(n, shown);
		if (n <= 0)
		{
			return false;
		}
		Pending p = pending.computeIfAbsent(s.kind.index, i -> new Pending(doses(s.kind)));
		p.takenDoses += n * s.dose;
		p.takenPotions += n;
		p.tick = client.getTickCount();
		w.setItemQuantity(alsoShown + shown - n);
		if (alsoShown + shown - n == 0)
		{
			w.setOpacity(BankWidgets.EMPTY_OPACITY);
		}
		return true;
	}

	private int freeSlots()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			return INVENTORY_SIZE;
		}
		int used = 0;
		for (Item item : inv.getItems())
		{
			if (item.getId() >= 0)
			{
				used++;
			}
		}
		return INVENTORY_SIZE - used;
	}

	/**
	 * The store's list only has components once its panel has been opened this bank session, and a click needs
	 * one to land on - so build blank ones in the shape the store builds, as the Bank Tags plugin does.
	 */
	boolean prepareWidgets()
	{
		Widget list = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
		if (list == null || kinds == null || kinds.isEmpty())
		{
			return false;
		}
		if (list.getChildren() != null)
		{
			return true;
		}
		int i = 0;
		for (int k = 0; k < kinds.size() * COMPONENTS_PER_POTION; k++)
		{
			list.createChild(i++, WidgetType.GRAPHIC);
		}
		// The vials' components, after every potion's.
		for (int t : new int[]{WidgetType.GRAPHIC, WidgetType.RECTANGLE, WidgetType.TEXT, WidgetType.RECTANGLE, WidgetType.TEXT})
		{
			list.createChild(i++, t);
		}
		return true;
	}
}
