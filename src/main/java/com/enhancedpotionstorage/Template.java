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

import com.enhancedpotionstorage.PotionVisibility.Where;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A layout of the dose slots: which potions show, in what order, in which categories, as which doses, where
 * each place hides them, and - once saved - where each slot sat in the bank. Plain data, saved as JSON.
 * <p>
 * A template with no categories is the built-in Default: its categories come from {@link DefaultCategories}
 * each time, so a potion added to the game finds its place without the template being saved again.
 */
final class Template
{
	/** The built-in template's name. It is never saved and never changes. */
	static final String DEFAULT = "Default";

	static final class Category
	{
		String name;
		boolean enabled = true;
		/** Potion ids ({@link PotionStore.Kind#id()}), in order. */
		List<Integer> potions = new ArrayList<>();

		Category()
		{
		}

		Category(String name, List<Integer> potions)
		{
			this.name = name;
			this.potions = new ArrayList<>(potions);
		}

		Category copy()
		{
			Category c = new Category(name, potions);
			c.enabled = enabled;
			return c;
		}
	}

	String name = DEFAULT;
	/** Every potion shows a slot for every dose it comes in, largest first, whatever {@link #doses} says. */
	boolean showAllDoses = true;
	List<Category> categories = new ArrayList<>();
	/** A potion's dose slots, left to right, when not every dose shows; a dose may repeat. Absent: every dose. */
	Map<Integer, List<Integer>> doses = new LinkedHashMap<>();
	/** The potions hidden in each place, by {@link Where#name()}. */
	Map<String, Set<Integer>> hidden = new LinkedHashMap<>();
	/** Where the slots sat in the bank when it was saved: a {@link BankArrangement} encoding. */
	String arrangement = "";

	static Template builtIn()
	{
		return new Template();
	}

	boolean isDefault()
	{
		return categories.isEmpty();
	}

	Template copy()
	{
		Template t = new Template();
		t.name = name;
		t.showAllDoses = showAllDoses;
		categories.forEach(c -> t.categories.add(c.copy()));
		doses.forEach((k, v) -> t.doses.put(k, new ArrayList<>(v)));
		hidden.forEach((k, v) -> t.hidden.put(k, new TreeSet<>(v)));
		t.arrangement = arrangement;
		return t;
	}

	/** Gson leaves out what a hand-written or older file has no field for: fill those in. */
	Template repaired()
	{
		if (name == null || name.trim().isEmpty())
		{
			name = "Imported";
		}
		if (categories == null)
		{
			categories = new ArrayList<>();
		}
		categories.removeIf(c -> c == null);
		for (Category c : categories)
		{
			if (c.name == null)
			{
				c.name = "Category";
			}
			if (c.potions == null)
			{
				c.potions = new ArrayList<>();
			}
		}
		if (doses == null)
		{
			doses = new LinkedHashMap<>();
		}
		doses.values().removeIf(l -> l == null);
		if (hidden == null)
		{
			hidden = new LinkedHashMap<>();
		}
		if (arrangement == null)
		{
			arrangement = "";
		}
		return this;
	}

	Set<Integer> hidden(Where where)
	{
		Set<Integer> s = hidden.get(where.name());
		return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
	}

	void setHidden(Where where, Collection<Integer> ids)
	{
		if (ids.isEmpty())
		{
			hidden.remove(where.name());
		}
		else
		{
			hidden.put(where.name(), new TreeSet<>(ids));
		}
	}

	/** The category {@code potionId} is listed in, or null. */
	Category categoryOf(int potionId)
	{
		for (Category c : categories)
		{
			if (c.potions.contains(potionId))
			{
				return c;
			}
		}
		return null;
	}

	Category category(String name)
	{
		for (Category c : categories)
		{
			if (c.name.equalsIgnoreCase(name))
			{
				return c;
			}
		}
		return null;
	}
}
