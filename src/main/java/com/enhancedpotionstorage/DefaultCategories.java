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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The built-in Default template's categories, and which potion goes in which, by name - potion storage's list
 * comes from the game, so a potion added to it later is sorted by its name: a mix to the mixes, an unfinished
 * potion to the unfinished ones, anything else unknown to Utility.
 * <p>
 * No potion potion storage keeps is a raid potion, so Raid starts empty: it is there to move potions into.
 */
final class DefaultCategories
{
	static final String COMBAT = "Combat-Boosting Potions";
	static final String DIVINE = "Divine Potions";
	static final String RESTORE = "Restore & Recovery Potions";
	static final String STAMINA = "Stamina & Mobility Potions";
	static final String DEFENSIVE = "Defensive & Anti-Poison/Venom Potions";
	static final String BOSSING = "Bossing & Special Brews";
	static final String UTILITY = "Utility";
	static final String RAID = "Raid Potions";
	static final String MIXES = "Mixes & Unfinished Potions";

	static final List<String> ORDER = List.of(COMBAT, DIVINE, RESTORE, STAMINA, DEFENSIVE, BOSSING, UTILITY, RAID, MIXES);

	/** Potions by name, lower case and without a dose, each category's in the order it lists them. */
	private static final Map<String, String> CATEGORY = new HashMap<>();
	private static final Map<String, Integer> RANK = new HashMap<>();

	static
	{
		put(COMBAT, "super combat potion", "super attack", "super strength", "super defence", "ranging potion",
			"magic potion", "bastion potion", "battlemage potion", "combat potion", "attack potion", "strength potion",
			"defence potion", "magic essence");
		put(DIVINE, "divine super combat potion", "divine super attack potion", "divine super strength potion",
			"divine super defence potion", "divine ranging potion", "divine magic potion", "divine bastion potion",
			"divine battlemage potion");
		put(RESTORE, "saradomin brew", "super restore", "prayer potion", "prayer regeneration potion",
			"blighted super restore", "sanfew serum", "restore potion", "menaphite remedy");
		put(STAMINA, "stamina potion", "extended stamina potion", "super energy", "extreme energy potion", "energy potion");
		put(DEFENSIVE, "anti-venom+", "extended anti-venom+", "anti-venom", "antidote++", "antidote+", "superantipoison",
			"antipoison", "extended super antifire", "super antifire potion", "extended antifire", "antifire potion",
			"relicym's balm");
		put(BOSSING, "zamorak brew", "ancient brew", "forgotten brew", "armadyl brew", "surge potion", "goading potion");
		put(UTILITY, "agility potion", "fishing potion", "super fishing potion", "hunter potion", "super hunter potion",
			"compost potion", "serum 207", "guthix balance", "weapon poison", "weapon poison(+)", "weapon poison(++)");
		put(MIXES, "superattack mix", "super str. mix", "super def. mix", "combat mix", "attack mix", "strength mix",
			"defence mix", "ranging mix", "magic mix", "magic essence mix", "ancient mix", "zamorak mix",
			"super restore mix", "prayer mix", "restore mix", "stamina mix", "super energy mix", "energy mix",
			"anti-poison supermix", "antidote+ mix", "antipoison mix", "extended super antifire mix", "super antifire mix",
			"extended antifire mix", "antifire mix", "relicym's mix", "agility mix", "fishing mix", "hunting mix");
	}

	private DefaultCategories()
	{
	}

	private static void put(String category, String... names)
	{
		for (String n : names)
		{
			CATEGORY.put(n, category);
			RANK.put(n, RANK.size());
		}
	}

	private static String key(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	/** The Default category of a potion by its name. */
	static String categoryOf(String name, boolean unfinished)
	{
		String n = key(name);
		String c = CATEGORY.get(n);
		if (c != null)
		{
			return c;
		}
		return unfinished || n.endsWith(" mix") || n.endsWith("mix") || n.contains("(unf)") ? MIXES : UTILITY;
	}

	/**
	 * Every potion in its Default category, in the order this lists them, then any this does not know in the
	 * order they come; unfinished potions after the mixes.
	 */
	static List<Template.Category> build(List<SlotPlan.Potion> potions)
	{
		Map<String, List<SlotPlan.Potion>> by = new HashMap<>();
		for (SlotPlan.Potion p : potions)
		{
			by.computeIfAbsent(categoryOf(p.name, p.unfinished), k -> new ArrayList<>()).add(p);
		}
		List<Template.Category> out = new ArrayList<>();
		for (String c : ORDER)
		{
			List<SlotPlan.Potion> list = by.getOrDefault(c, new ArrayList<>());
			list.sort((a, b) -> Integer.compare(sortKey(a), sortKey(b)));
			List<Integer> ids = new ArrayList<>();
			list.forEach(p -> ids.add(p.id));
			out.add(new Template.Category(c, ids));
		}
		return out;
	}

	/** Known potions in list order, then unknown finished ones, then unfinished ones, each in the store's order. */
	private static int sortKey(SlotPlan.Potion p)
	{
		Integer r = RANK.get(key(p.name));
		if (r != null)
		{
			return r;
		}
		return (p.unfinished ? 20_000 : 10_000) + p.order;
	}
}
