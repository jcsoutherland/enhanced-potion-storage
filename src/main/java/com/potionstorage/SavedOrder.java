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
import com.potionstorage.BankArrangement.Pos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;
import net.runelite.client.config.ConfigManager;

/**
 * The order the bank is drawn in, kept per RuneScape account in RuneLite's profile config, with the server's
 * own order as last seen - which is what lets a reorder made out of the plugin's sight be followed.
 * <p>
 * A build draws from a copy ({@link #draw}): it may be seeing the bank halfway through an update, the
 * server's new item order without its new tab sizes, and must not learn from that. What is kept changes only
 * through {@link #learn}, called once the bank has read the same for a whole tick, and through a drag.
 */
final class SavedOrder
{
	private static final String ARRANGEMENT_KEY = "arrangement";
	private static final String SERVER_ORDER_KEY = "serverOrder";
	/** The stored potions, by key, run out with a placeholder left where they were. */
	private static final String PLACEHOLDERS_KEY = "placeholders";

	private final ConfigManager configManager;

	private String account;
	private BankArrangement arrangement = new BankArrangement();
	private Map<Integer, List<Integer>> lastServer;
	private Set<Integer> placeholders = new TreeSet<>();

	SavedOrder(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** The config keys this keeps, which are not settings. */
	static boolean isOwnKey(String key)
	{
		return ARRANGEMENT_KEY.equals(key) || SERVER_ORDER_KEY.equals(key) || PLACEHOLDERS_KEY.equals(key);
	}

	void reset()
	{
		account = null;
		arrangement = new BankArrangement();
		lastServer = null;
		placeholders = new TreeSet<>();
	}

	/** The stored potions, by key, that have run out and left a placeholder. */
	Set<Integer> placeholders()
	{
		load();
		return new TreeSet<>(placeholders);
	}

	void addPlaceholders(Collection<Integer> keys)
	{
		load();
		if (placeholders.addAll(keys))
		{
			savePlaceholders();
		}
	}

	/** Potions back in stock: drawn as themselves again, where their placeholders were. */
	void fillPlaceholders(Collection<Integer> keys)
	{
		load();
		if (placeholders.removeAll(keys))
		{
			savePlaceholders();
		}
	}

	/** Release placeholders: gone, and their places with them, as the bank forgets a released one's slot. */
	void release(Collection<Integer> keys)
	{
		load();
		boolean changed = false;
		for (int key : keys)
		{
			changed |= arrangement.forgetStored(key);
		}
		if (changed)
		{
			saveArrangement();
		}
		if (placeholders.removeAll(keys))
		{
			savePlaceholders();
		}
	}

	private void savePlaceholders()
	{
		put(PLACEHOLDERS_KEY, PotionVisibility.format(placeholders));
	}

	/**
	 * What to draw for the bank as {@code reals} has it, resolved on a copy that is not kept. A stored stack new
	 * to the order goes at the end of the section {@code newSection} gives for its key.
	 */
	BankArrangement.Resolved draw(Map<Integer, List<Integer>> reals, Collection<Integer> stored, Set<Integer> existing,
		IntUnaryOperator newSection)
	{
		load();
		return arrangement.copy().resolve(reals, lastServer, stored, existing, newSection);
	}

	/** Fold a settled bank into what is kept, and remember it as the server's order last seen. */
	void learn(Map<Integer, List<Integer>> reals, Collection<Integer> stored, Set<Integer> existing,
		IntUnaryOperator newSection)
	{
		load();
		if (arrangement.resolve(reals, lastServer, stored, existing, newSection).changed)
		{
			saveArrangement();
		}
		Map<Integer, List<Integer>> now = new TreeMap<>();
		reals.forEach((s, ids) -> now.put(s, new ArrayList<>(ids)));
		if (!now.equals(lastServer))
		{
			lastServer = now;
			put(SERVER_ORDER_KEY, BankArrangement.encodeOrder(now));
		}
	}

	void move(Map<Integer, List<Entry>> drawn, Pos from, Pos to, BankArrangement.Drop how)
	{
		load();
		arrangement.move(drawn, from, to, how);
		saveArrangement();
	}

	void moveToEnd(Map<Integer, List<Entry>> drawn, Pos from, int section)
	{
		load();
		arrangement.moveToEnd(drawn, from, section);
		saveArrangement();
	}

	/** The account's order, read when the account changes. Not logged in, nothing is read or kept. */
	private void load()
	{
		String key = configManager.getRSProfileKey();
		if (!Objects.equals(key, account))
		{
			account = key;
			arrangement = BankArrangement.decode(get(ARRANGEMENT_KEY));
			lastServer = BankArrangement.decodeOrder(get(SERVER_ORDER_KEY));
			placeholders = new TreeSet<>(PotionVisibility.parse(get(PLACEHOLDERS_KEY)));
		}
	}

	private void saveArrangement()
	{
		put(ARRANGEMENT_KEY, arrangement.encode());
	}

	private String get(String key)
	{
		return account == null ? null : configManager.getRSProfileConfiguration(PotionStorageConfig.GROUP, key);
	}

	private void put(String key, String value)
	{
		if (account != null && !value.equals(get(key)))
		{
			configManager.setRSProfileConfiguration(PotionStorageConfig.GROUP, key, value);
		}
	}
}
