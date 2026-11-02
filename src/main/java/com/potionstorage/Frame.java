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
import com.potionstorage.PotionStore.Stack;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one build of the bank drew, and where: which item-list child carries which stored stack, which bank
 * stack the store withdraws for, and each section's order. A new one is made for every build and replaces
 * the last whole, so nothing in it outlives the drawing it describes.
 */
final class Frame
{
	enum Mode
	{
		/** Nothing drawn: no stored stack, no drag to follow. */
		NONE,
		/** The bank itself: every stack in the saved order, and drags rearrange it. */
		ARRANGE,
		/** A search over the saved order: part of it on show, and drags rearrange it as in a game search. */
		SEARCH,
		/** A tag tab, or a search with the bank view off: stored stacks shown, in no order a drag could change. */
		FILTER
	}

	final Mode mode;
	private final Map<Integer, Stack> stored = new HashMap<>();
	private final Map<Integer, Stack> combined = new HashMap<>();
	private final Set<Integer> keepsOwnOptions = new HashSet<>();

	/** Each section's order, every stack of it whether on show or not; empty when there is no order to change. */
	Map<Integer, List<Entry>> drawn = Collections.emptyMap();
	/** For each bank slot: its section, and the entry it is in the order. */
	int[] slotSection = new int[0];
	Entry[] slotEntry = new Entry[0];
	int[] tabSizes = new int[BankWidgets.SECTIONS];
	/** The saved order, resolved for this build, says more than what is kept: worth learning once the bank settles. */
	boolean orderChanged;

	Frame(Mode mode)
	{
		this.mode = mode;
	}

	/** A stored stack drawn on {@code child}. */
	void putStored(int child, Stack stack)
	{
		stored.put(child, stack);
	}

	/**
	 * A bank widget the store withdraws for: a placeholder drawn as the store's stack, or - with
	 * {@code keepOwnOptions} - a stack whose withdraw options are the store's and whose other options (Placeholder,
	 * Examine, the Bank Tags plugin's own) stay as they are.
	 */
	void putCombined(int child, Stack stack, boolean keepOwnOptions)
	{
		combined.put(child, stack);
		if (keepOwnOptions)
		{
			this.keepsOwnOptions.add(child);
		}
	}

	boolean isStored(int child)
	{
		return stored.containsKey(child);
	}

	/** A child whose click this plugin points at the store: a stored stack, or a combined bank widget. */
	boolean withdrawsFromStore(int child)
	{
		return stored.containsKey(child) || combined.containsKey(child);
	}

	boolean keepsOwnOptions(int child)
	{
		return keepsOwnOptions.contains(child);
	}

	/** The stack the store pays out on {@code child}, stored or combined; null for anything else. */
	Stack stackOn(int child)
	{
		Stack s = stored.get(child);
		return s != null ? s : combined.get(child);
	}

	/** The order entry drawn on {@code child}, stored or real; null for a child with none. */
	Entry entryOn(int child)
	{
		Stack s = stored.get(child);
		if (s != null)
		{
			return Entry.stored(s.key);
		}
		return child < slotEntry.length ? slotEntry[child] : null;
	}

	/** Where {@code entry} is drawn in the order: its section and its index there. Null when it is not drawn. */
	Pos posOf(Entry entry)
	{
		if (entry == null)
		{
			return null;
		}
		for (Map.Entry<Integer, List<Entry>> section : drawn.entrySet())
		{
			int i = section.getValue().indexOf(entry);
			if (i >= 0)
			{
				return new Pos(section.getKey(), i);
			}
		}
		return null;
	}
}
