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

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.runelite.client.util.Text;

/**
 * Which stored potions each part of the bank shows, potion by potion: the sidebar panel's settings. Every
 * potion is shown everywhere until it is hidden somewhere, so only the hidden ones are kept - one list per
 * place, of {@link PotionStore.Kind#id() potion ids}, in this plugin's config.
 * <p>
 * Read on the client thread while the bank is drawn and written from the panel's, so the lists are swapped
 * whole rather than changed in place.
 */
final class PotionVisibility
{
	/** The parts of the bank a stored potion can be shown in or hidden from. */
	enum Where
	{
		BANK("hiddenInBank", "the bank"),
		TAGS("hiddenInTags", "tag tabs"),
		SEARCH("hiddenInSearch", "searches");

		/** The config key of the potions hidden here. */
		final String key;
		/** How a sentence names it: "shown in the bank". */
		final String phrase;

		Where(String key, String phrase)
		{
			this.key = key;
			this.phrase = phrase;
		}
	}

	private final Function<String, String> read;
	private final BiConsumer<String, String> write;
	private volatile Map<Where, Set<Integer>> hidden;

	/** Whether a config key is one of these lists. */
	static boolean isOwnKey(String key)
	{
		for (Where w : Where.values())
		{
			if (w.key.equals(key))
			{
				return true;
			}
		}
		return false;
	}

	/** {@code read} and {@code write} are this plugin's config values by key. */
	PotionVisibility(Function<String, String> read, BiConsumer<String, String> write)
	{
		this.read = read;
		this.write = write;
		reload();
	}

	/** Read the lists again: the config changed, by a profile switch or a reset. */
	synchronized void reload()
	{
		Map<Where, Set<Integer>> m = new EnumMap<>(Where.class);
		for (Where w : Where.values())
		{
			m.put(w, parse(read.apply(w.key)));
		}
		hidden = m;
	}

	boolean shown(Where where, int potionId)
	{
		return !hidden.get(where).contains(potionId);
	}

	/** The places {@code potionId} is shown in. */
	Set<Where> placesShown(int potionId)
	{
		Set<Where> out = Collections.newSetFromMap(new EnumMap<>(Where.class));
		for (Where w : Where.values())
		{
			if (shown(w, potionId))
			{
				out.add(w);
			}
		}
		return out;
	}

	/** Show or hide every one of {@code potionIds} in every one of {@code places}, and save what changed. */
	synchronized void set(Collection<Integer> potionIds, Collection<Where> places, boolean show)
	{
		Map<Where, Set<Integer>> m = new EnumMap<>(hidden);
		for (Where w : places)
		{
			Set<Integer> s = new TreeSet<>(m.get(w));
			boolean changed = show ? s.removeAll(potionIds) : s.addAll(potionIds);
			if (changed)
			{
				m.put(w, Collections.unmodifiableSet(s));
				write.accept(w.key, format(s));
			}
		}
		hidden = m;
	}

	static Set<Integer> parse(String csv)
	{
		if (csv == null || csv.trim().isEmpty())
		{
			return Collections.emptySet();
		}
		Set<Integer> out = new TreeSet<>();
		for (String part : Text.fromCSV(csv))
		{
			try
			{
				out.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException e)
			{
				// Not one of ours: dropped the next time the list is saved.
			}
		}
		return Collections.unmodifiableSet(out);
	}

	static String format(Set<Integer> ids)
	{
		return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
	}
}
