/*
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The dose slots a template asks for, in bank order: each potion of each enabled category, in turn, as each
 * of its slots' doses. A slot is known by a key - its dose's item id, plus {@link #COPY_STRIDE} for each
 * earlier slot of the same dose, as a template may show a dose twice - and its rank is its place in this
 * order, which is where a slot new to the bank is put ({@link BankArrangement}).
 * <p>
 * Pure data - no client.
 */
final class SlotPlan
{
	/** Keys of a second, third... slot of the same dose are past every item id. */
	static final int COPY_STRIDE = 1_000_000;
	static final int MAX_DOSE = 4;

	/** One potion potion storage keeps, as the plan needs it. */
	static final class Potion
	{
		final int id;
		final String name;
		final boolean unfinished;
		/** Its place in potion storage's own list. */
		final int order;
		/** Item id by dose, 1 to 4; -1 for a dose it does not come in. */
		private final int[] itemOfDose = {-1, -1, -1, -1, -1};

		Potion(int id, String name, boolean unfinished, int order, Map<Integer, Integer> itemsByDose)
		{
			this.id = id;
			this.name = name;
			this.unfinished = unfinished;
			this.order = order;
			itemsByDose.forEach((d, item) ->
			{
				if (d >= 1 && d <= MAX_DOSE && item != null && item > 0)
				{
					itemOfDose[d] = item;
				}
			});
		}

		int itemOf(int dose)
		{
			return dose >= 1 && dose <= MAX_DOSE ? itemOfDose[dose] : -1;
		}

		/** The doses it comes in, largest first. An unfinished potion comes in its one. */
		List<Integer> naturalDoses()
		{
			List<Integer> out = new ArrayList<>();
			for (int d = MAX_DOSE; d >= 1; d--)
			{
				if (itemOfDose[d] > 0)
				{
					out.add(d);
				}
			}
			if (unfinished && out.size() > 1)
			{
				return out.subList(out.size() - 1, out.size());
			}
			return out;
		}

		/** More than one dose to choose between. */
		boolean hasDoseChoice()
		{
			return naturalDoses().size() > 1;
		}
	}

	/** One slot in the bank: a potion at a dose. */
	static final class Slot
	{
		final int potionId;
		final int dose;
		final int itemId;
		/** How many earlier slots of the same potion have the same dose. */
		final int copy;
		final int key;
		final int rank;
		final String category;

		Slot(int potionId, int dose, int itemId, int copy, int rank, String category)
		{
			this.potionId = potionId;
			this.dose = dose;
			this.itemId = itemId;
			this.copy = copy;
			this.key = key(itemId, copy);
			this.rank = rank;
			this.category = category;
		}
	}

	private final List<Slot> slots;
	private final Map<Integer, Slot> byKey = new HashMap<>();
	private final Map<Integer, List<Slot>> byPotion = new HashMap<>();

	private SlotPlan(List<Slot> slots)
	{
		this.slots = Collections.unmodifiableList(slots);
		for (Slot s : slots)
		{
			byKey.put(s.key, s);
			byPotion.computeIfAbsent(s.potionId, k -> new ArrayList<>()).add(s);
		}
	}

	static SlotPlan empty()
	{
		return new SlotPlan(new ArrayList<>());
	}

	static int key(int itemId, int copy)
	{
		return itemId + copy * COPY_STRIDE;
	}

	static int itemOfKey(int key)
	{
		return key % COPY_STRIDE;
	}

	/** Every slot, in bank order. */
	List<Slot> slots()
	{
		return slots;
	}

	Slot slot(int key)
	{
		return byKey.get(key);
	}

	/** A slot's place in bank order; -1 for a key the plan has no slot for. */
	int rank(int key)
	{
		Slot s = byKey.get(key);
		return s == null ? -1 : s.rank;
	}

	List<Slot> slotsOf(int potionId)
	{
		return byPotion.getOrDefault(potionId, Collections.emptyList());
	}

	/** The plan {@code t} makes of {@code potions}. */
	static SlotPlan of(Template t, List<Potion> potions)
	{
		Map<Integer, Potion> byId = new HashMap<>();
		potions.forEach(p -> byId.put(p.id, p));
		List<Slot> out = new ArrayList<>();
		for (Template.Category c : categories(t, potions))
		{
			if (!c.enabled)
			{
				continue;
			}
			for (int id : c.potions)
			{
				Potion p = byId.get(id);
				if (p == null)
				{
					continue;
				}
				List<Integer> doses = effectiveDoses(t, p);
				List<Integer> keys = keysOf(p, doses);
				Map<Integer, Integer> copies = new HashMap<>();
				for (int i = 0; i < doses.size(); i++)
				{
					int d = doses.get(i);
					int copy = copies.merge(d, 1, Integer::sum) - 1;
					out.add(new Slot(p.id, d, p.itemOf(d), copy, out.size(), c.name));
				}
			}
		}
		return new SlotPlan(out);
	}

	/**
	 * A template's categories with every potion in one: Default's built afresh; a saved template's as saved,
	 * with any potion it does not list - one new to the game - added to the end of the category of Default's
	 * name it has, or of its last category when it has none of that name.
	 */
	static List<Template.Category> categories(Template t, List<Potion> potions)
	{
		if (t.isDefault())
		{
			return DefaultCategories.build(potions);
		}
		List<Template.Category> out = new ArrayList<>();
		t.categories.forEach(c -> out.add(c.copy()));
		Set<Integer> listed = new HashSet<>();
		out.forEach(c -> listed.addAll(c.potions));
		for (Potion p : potions)
		{
			if (listed.add(p.id))
			{
				Template.Category home = null;
				String name = DefaultCategories.categoryOf(p.name, p.unfinished);
				for (Template.Category c : out)
				{
					if (c.name.equalsIgnoreCase(name))
					{
						home = c;
					}
				}
				if (home == null && !out.isEmpty())
				{
					home = out.get(out.size() - 1);
				}
				if (home != null)
				{
					home.potions.add(p.id);
				}
			}
		}
		return out;
	}

	/**
	 * A potion's dose slots, left to right: every dose it comes in, largest first, when the template shows them
	 * all or names none for it; otherwise the template's list, less any dose the potion does not come in. A
	 * potion with one dose has its one slot whatever the list says.
	 */
	static List<Integer> effectiveDoses(Template t, Potion p)
	{
		List<Integer> natural = p.naturalDoses();
		if (!p.hasDoseChoice() || t.showAllDoses)
		{
			return natural;
		}
		List<Integer> chosen = t.doses.get(p.id);
		if (chosen == null)
		{
			return natural;
		}
		List<Integer> out = new ArrayList<>();
		for (int d : chosen)
		{
			if (natural.contains(d))
			{
				out.add(d);
			}
		}
		return out;
	}

	/** The key of each slot of {@code doses}, in the same order. */
	static List<Integer> keysOf(Potion p, List<Integer> doses)
	{
		Map<Integer, Integer> copies = new LinkedHashMap<>();
		List<Integer> out = new ArrayList<>();
		for (int d : doses)
		{
			int copy = copies.merge(d, 1, Integer::sum) - 1;
			out.add(key(p.itemOf(d), copy));
		}
		return out;
	}
}
