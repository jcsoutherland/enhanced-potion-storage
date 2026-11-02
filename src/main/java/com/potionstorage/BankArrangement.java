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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

/**
 * Where everything sits in the bank as the plugin draws it: the real stacks and the potion store's stacks,
 * in one order per section, so a stored potion can stand between two real items like any other.
 * <p>
 * A section is what the game calls a tab: {@code 0} is the untabbed part (the top of "View all items"),
 * {@code 1..9} the numbered tabs. Membership is the server's - a real item is in the tab its slot is in, and
 * only a server move changes that - but the order inside a section is the plugin's, because the server's
 * order has no room for a stack that has no slot. When the server reorders a section by itself - a drag the
 * plugin did not see, made in a search, with the plugin off, or on another device - the moved items follow.
 * <p>
 * Every saved list keeps its ghosts. An entry whose item is not there right now (a potion the store ran out
 * of, a stack withdrawn without a placeholder) stays where it was and is skipped when drawn, so it comes
 * back to the same place. The one ghost that is dropped is a real item seen in a different section: it was
 * moved, not used up.
 * <p>
 * Pure data - no client, no widgets.
 */
final class BankArrangement
{
	/** The untabbed part: where a stored stack starts out, and where one from a vanished tab goes. */
	static final int MAIN = 0;

	/** One cell: a real stack by its canonical item id, or a stored stack by its potion key. */
	static final class Entry
	{
		final boolean stored;
		final int id;

		private Entry(boolean stored, int id)
		{
			this.stored = stored;
			this.id = id;
		}

		static Entry real(int id)
		{
			return new Entry(false, id);
		}

		static Entry stored(int key)
		{
			return new Entry(true, key);
		}

		static Entry parse(String s)
		{
			try
			{
				return s.startsWith("p") ? stored(Integer.parseInt(s.substring(1))) : real(Integer.parseInt(s));
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Entry && ((Entry) o).stored == stored && ((Entry) o).id == id;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(stored, id);
		}

		@Override
		public String toString()
		{
			return stored ? "p" + id : Integer.toString(id);
		}
	}

	/** A drawn cell: its section and its index in that section's drawn order. */
	static final class Pos
	{
		final int section;
		final int index;

		Pos(int section, int index)
		{
			this.section = section;
			this.index = index;
		}
	}

	/** What a drop of one stack on another does. */
	enum Drop
	{
		/** The two trade places. */
		SWAP,
		/** Into the target's place, the stacks between shifting toward where it came from: the bank's insert mode. */
		INSERT,
		/** Just in front of the target: insert mode in a search. */
		IN_FRONT
	}

	static final class Resolved
	{
		final Map<Integer, List<Entry>> drawn;
		final boolean changed;

		private Resolved(Map<Integer, List<Entry>> drawn, boolean changed)
		{
			this.drawn = drawn;
			this.changed = changed;
		}
	}

	private final TreeMap<Integer, List<Entry>> sections = new TreeMap<>();

	/** An independent copy, to resolve against a bank that may be mid-update without learning from it. */
	BankArrangement copy()
	{
		BankArrangement c = new BankArrangement();
		sections.forEach((s, l) -> c.sections.put(s, new ArrayList<>(l)));
		return c;
	}

	private List<Entry> list(int section)
	{
		return sections.computeIfAbsent(section, k -> new ArrayList<>());
	}

	/**
	 * Fold what the bank holds right now into the saved order, and return what to draw.
	 * <p>
	 * {@code reals} is each section's real items in the server's slot order, {@code previous} the same as last
	 * seen (null when there is none), {@code stored} the potion keys the store holds, and {@code existing} the
	 * sections the server has. A real item new to its section goes in after the item the server has before it
	 * - so a deposit, which the server puts at the end of a tab, lands at the end here too, past any stored
	 * stacks. A stored stack seen for the first time goes at the end of the untabbed part, and so does one
	 * saved in a tab that no longer exists.
	 */
	Resolved resolve(Map<Integer, List<Integer>> reals, Map<Integer, List<Integer>> previous, Collection<Integer> stored,
		Set<Integer> existing)
	{
		return resolve(reals, previous, stored, existing, key -> MAIN);
	}

	/**
	 * As {@link #resolve(Map, Map, Collection, Set)}, with a stored stack new to the order going to the end of
	 * the section {@code newSection} gives for its key - the tab open when it was deposited, as the game puts a
	 * deposited item there - if that tab exists, and to the untabbed part if not.
	 */
	Resolved resolve(Map<Integer, List<Integer>> reals, Map<Integer, List<Integer>> previous, Collection<Integer> stored,
		Set<Integer> existing, IntUnaryOperator newSection)
	{
		boolean changed = followRenumbering(reals);
		List<Entry> homeless = new ArrayList<>();
		changed |= dropVanishedTabs(existing, homeless);
		Set<Integer> placed = new HashSet<>();
		changed |= dropStrays(reals, placed);
		changed |= insertNewReals(reals);
		changed |= followServerMoves(reals, previous);
		changed |= placeNewStored(homeless, stored, placed, key ->
		{
			int s = newSection.applyAsInt(key);
			return existing.contains(s) ? s : MAIN;
		});
		return new Resolved(drawn(reals, new HashSet<>(stored)), changed);
	}

	/**
	 * Emptying a tab deletes it on the server and renumbers every tab after it, and dragging a tab's button
	 * reorders the tabs, so saved section 5 can now describe tab 4. A saved section none of whose items are in
	 * its own tab any more, but some of which are together in another, is that tab now - unless that tab still
	 * holds items of its own, which makes it a tab that one was emptied into rather than a renumbered one. A
	 * number left without its old contents gives its stored stacks to the untabbed part.
	 */
	private boolean followRenumbering(Map<Integer, List<Integer>> reals)
	{
		Map<Integer, Integer> serverOf = sectionOf(reals);
		Map<Integer, Map<Integer, Integer>> votes = new HashMap<>();
		Set<Integer> home = new HashSet<>(Collections.singleton(MAIN)); // sections that kept items of their own
		for (Map.Entry<Integer, List<Entry>> section : sections.entrySet())
		{
			Map<Integer, Integer> v = new LinkedHashMap<>();
			for (Entry e : section.getValue())
			{
				Integer t = e.stored ? null : serverOf.get(e.id);
				if (t != null)
				{
					v.merge(t, 1, Integer::sum);
				}
			}
			votes.put(section.getKey(), v);
			if (v.containsKey(section.getKey()))
			{
				home.add(section.getKey());
			}
		}
		Map<Integer, Integer> target = new HashMap<>();
		votes.forEach((s, v) ->
		{
			if (home.contains(s))
			{
				return;
			}
			int best = -1;
			int bestCount = 0;
			for (Map.Entry<Integer, Integer> vote : v.entrySet())
			{
				if (!home.contains(vote.getKey()) && vote.getValue() > bestCount)
				{
					best = vote.getKey();
					bestCount = vote.getValue();
				}
			}
			if (best >= 0)
			{
				target.put(s, best);
			}
		});
		if (target.isEmpty())
		{
			return false;
		}
		Map<Integer, List<Entry>> old = new LinkedHashMap<>(sections);
		sections.clear();
		Set<Integer> claimed = new HashSet<>(target.values());
		List<Entry> displaced = new ArrayList<>();
		old.forEach((s, l) ->
		{
			Integer t = target.get(s);
			if (t != null)
			{
				list(t).addAll(l);
			}
			else if (claimed.contains(s))
			{
				l.stream().filter(e -> e.stored).forEach(displaced::add);
			}
			else
			{
				list(s).addAll(l);
			}
		});
		list(MAIN).addAll(displaced);
		return true;
	}

	/** Sections the server no longer has go, their stored stacks into {@code homeless}. */
	private boolean dropVanishedTabs(Set<Integer> existing, List<Entry> homeless)
	{
		boolean changed = false;
		for (Integer s : new ArrayList<>(sections.keySet()))
		{
			if (!existing.contains(s))
			{
				sections.remove(s).stream().filter(e -> e.stored).forEach(homeless::add);
				changed = true;
			}
		}
		return changed;
	}

	/** A real item seen in another section was moved; a stored stack lives in one section only. */
	private boolean dropStrays(Map<Integer, List<Integer>> reals, Set<Integer> placed)
	{
		Map<Integer, Integer> home = sectionOf(reals);
		boolean changed = false;
		for (Map.Entry<Integer, List<Entry>> section : sections.entrySet())
		{
			int s = section.getKey();
			changed |= section.getValue().removeIf(e -> e.stored ? !placed.add(e.id) : home.containsKey(e.id) && home.get(e.id) != s);
		}
		return changed;
	}

	/** Forget where a stored stack goes: should it come back, it is placed as a new one. True when it had a place. */
	boolean forgetStored(int key)
	{
		boolean changed = false;
		for (List<Entry> l : sections.values())
		{
			changed |= l.removeIf(e -> e.stored && e.id == key);
		}
		return changed;
	}

	/** Each real item not yet in its section's list goes in after the item the server has before it. */
	private boolean insertNewReals(Map<Integer, List<Integer>> reals)
	{
		boolean changed = false;
		for (Map.Entry<Integer, List<Integer>> section : reals.entrySet())
		{
			List<Entry> l = list(section.getKey());
			Set<Integer> present = new HashSet<>(section.getValue());
			Set<Integer> known = l.stream().filter(e -> !e.stored).map(e -> e.id).collect(Collectors.toSet());
			Integer prev = null;
			for (int id : section.getValue())
			{
				if (known.add(id))
				{
					l.add(insertionPoint(l, prev, present), Entry.real(id));
					changed = true;
				}
				prev = id;
			}
		}
		return changed;
	}

	/**
	 * Items the server moved within their section since {@code previous} - the ones off the longest run that
	 * kept its order - go next to the item the server now has before them (or after them, when first). One
	 * already in such a place, as after a drag the plugin made itself, stays where it is: only what this
	 * order has not heard of moves.
	 */
	private boolean followServerMoves(Map<Integer, List<Integer>> reals, Map<Integer, List<Integer>> previous)
	{
		if (previous == null)
		{
			return false;
		}
		boolean changed = false;
		for (Map.Entry<Integer, List<Integer>> section : reals.entrySet())
		{
			List<Integer> before = previous.get(section.getKey());
			if (before == null)
			{
				continue;
			}
			Set<Integer> was = new HashSet<>(before);
			List<Integer> now = section.getValue().stream().filter(was::contains).collect(Collectors.toList());
			Set<Integer> stayed = new HashSet<>(now);
			List<Integer> then = before.stream().filter(stayed::contains).collect(Collectors.toList());
			Set<Integer> moved = movedBetween(then, now);
			for (int i = 0; i < now.size(); i++)
			{
				if (moved.contains(now.get(i)))
				{
					changed |= reposition(list(section.getKey()), now.get(i),
						i > 0 ? now.get(i - 1) : null, i + 1 < now.size() ? now.get(i + 1) : null);
				}
			}
		}
		return changed;
	}

	/** The items of {@code now} off the longest subsequence it shares with {@code then}: the ones that moved. */
	private static Set<Integer> movedBetween(List<Integer> then, List<Integer> now)
	{
		Map<Integer, Integer> rank = new HashMap<>();
		for (int i = 0; i < then.size(); i++)
		{
			rank.put(then.get(i), i);
		}
		int n = now.size();
		int[] length = new int[n];
		int[] back = new int[n];
		int best = -1;
		for (int i = 0; i < n; i++)
		{
			length[i] = 1;
			back[i] = -1;
			for (int j = 0; j < i; j++)
			{
				if (rank.get(now.get(j)) < rank.get(now.get(i)) && length[j] + 1 > length[i])
				{
					length[i] = length[j] + 1;
					back[i] = j;
				}
			}
			if (best < 0 || length[i] > length[best])
			{
				best = i;
			}
		}
		Set<Integer> moved = new HashSet<>(now);
		for (int i = best; i >= 0; i = back[i])
		{
			moved.remove(now.get(i));
		}
		return moved;
	}

	/** Put real {@code id} after {@code before} (or ahead of {@code after}) unless it already sits between them. */
	private static boolean reposition(List<Entry> l, int id, Integer before, Integer after)
	{
		int at = l.indexOf(Entry.real(id));
		int b = before == null ? -1 : l.indexOf(Entry.real(before));
		int a = after == null ? l.size() : l.indexOf(Entry.real(after));
		if (at < 0 || (b < at && at < a))
		{
			return false;
		}
		Entry e = l.remove(at);
		int to = before != null ? l.indexOf(Entry.real(before)) + 1 : Math.max(l.indexOf(Entry.real(after)), 0);
		l.add(to, e);
		return true;
	}

	/** After the real items, so a stack new to the bank's view starts out behind them. */
	private boolean placeNewStored(List<Entry> homeless, Collection<Integer> stored, Set<Integer> placed,
		IntUnaryOperator newSection)
	{
		boolean changed = false;
		for (Entry e : homeless)
		{
			if (placed.add(e.id))
			{
				list(MAIN).add(e);
			}
		}
		for (int key : stored)
		{
			if (placed.add(key))
			{
				list(newSection.applyAsInt(key)).add(Entry.stored(key));
				changed = true;
			}
		}
		return changed;
	}

	/** Each section's entries that are there to draw: its real items the server has, its stored stacks in stock. */
	private Map<Integer, List<Entry>> drawn(Map<Integer, List<Integer>> reals, Set<Integer> stored)
	{
		Map<Integer, List<Entry>> drawn = new TreeMap<>();
		sections.forEach((s, l) ->
		{
			List<Integer> ids = reals.get(s);
			Set<Integer> present = ids == null ? Collections.emptySet() : new HashSet<>(ids);
			List<Entry> d = l.stream()
				.filter(e -> e.stored ? stored.contains(e.id) : present.contains(e.id))
				.collect(Collectors.toList());
			if (!d.isEmpty())
			{
				drawn.put(s, d);
			}
		});
		return drawn;
	}

	private static Map<Integer, Integer> sectionOf(Map<Integer, List<Integer>> reals)
	{
		Map<Integer, Integer> of = new HashMap<>();
		reals.forEach((s, ids) -> ids.forEach(id -> of.putIfAbsent(id, s)));
		return of;
	}

	/** After {@code after} (null: the front) and the run that follows it, up to the next real item that is drawn. */
	private static int insertionPoint(List<Entry> l, Integer after, Set<Integer> present)
	{
		if (after == null)
		{
			return 0;
		}
		int i = l.indexOf(Entry.real(after)) + 1;
		if (i == 0)
		{
			i = l.size();
		}
		while (i < l.size() && (l.get(i).stored || !present.contains(l.get(i).id)))
		{
			i++;
		}
		return i;
	}

	/**
	 * What a drag of the cell at {@code from} onto the cell at {@code to} does ({@link Drop}). {@code drawn} is
	 * the order the cells were drawn in; a position outside it changes nothing.
	 */
	void move(Map<Integer, List<Entry>> drawn, Pos from, Pos to, Drop how)
	{
		List<Entry> was = drawn.get(from.section);
		List<Entry> wasDst = drawn.get(to.section);
		if (was == null || wasDst == null || from.index >= was.size() || to.index >= wasDst.size())
		{
			return;
		}
		List<Entry> src = new ArrayList<>(was);
		List<Entry> dst = from.section == to.section ? src : new ArrayList<>(wasDst);
		Entry e = src.get(from.index);
		Entry target = dst.get(to.index);
		if (e.equals(target))
		{
			return;
		}
		switch (how)
		{
			case SWAP:
				src.set(from.index, target);
				dst.set(to.index, e);
				break;
			case INSERT:
				src.remove(from.index);
				dst.add(to.index, e);
				break;
			default:
				src.remove(from.index);
				dst.add(dst.indexOf(target), e);
				break;
		}
		rewrite(from.section, was, src);
		if (dst != src)
		{
			rewrite(to.section, wasDst, dst);
		}
	}

	/** The cell at {@code from} to the end of {@code section} - a drop on a tab button or after a tab's last item. */
	void moveToEnd(Map<Integer, List<Entry>> drawn, Pos from, int section)
	{
		List<Entry> was = drawn.get(from.section);
		if (was == null || from.index >= was.size())
		{
			return;
		}
		List<Entry> src = new ArrayList<>(was);
		Entry e = src.remove(from.index);
		if (from.section == section)
		{
			src.add(e);
			rewrite(section, was, src);
			return;
		}
		List<Entry> wasDst = drawn.getOrDefault(section, Collections.emptyList());
		List<Entry> dst = new ArrayList<>(wasDst);
		dst.add(e);
		rewrite(from.section, was, src);
		rewrite(section, wasDst, dst);
	}

	/**
	 * Replace {@code section}'s drawn order with {@code now}, putting every ghost back behind the drawn entry
	 * it followed - or where it was, if that entry left the section.
	 */
	private void rewrite(int section, List<Entry> was, List<Entry> now)
	{
		List<Entry> l = list(section);
		Set<Entry> visible = new HashSet<>(was);
		Set<Entry> keep = new HashSet<>(now);
		List<Entry> out = new ArrayList<>(now);
		Map<Entry, Integer> behind = new HashMap<>();
		Entry anchor = null;
		for (int i = 0; i < l.size(); i++)
		{
			Entry e = l.get(i);
			if (visible.contains(e))
			{
				anchor = e;
				continue;
			}
			if (keep.contains(e))
			{
				continue;
			}
			int at = 0;
			if (anchor != null)
			{
				int idx = out.indexOf(anchor);
				if (idx < 0)
				{
					out.add(Math.min(i, out.size()), e);
					continue;
				}
				at = idx + 1;
			}
			int n = behind.getOrDefault(anchor, 0);
			out.add(Math.min(at + n, out.size()), e);
			behind.put(anchor, n + 1);
		}
		l.clear();
		l.addAll(out);
	}

	/** {@code section=entry,entry,...} per section, {@code ;} between: {@code 0=995,p2434;3=1127,p3024}. */
	String encode()
	{
		return sections.entrySet().stream()
			.filter(s -> !s.getValue().isEmpty())
			.map(s -> s.getKey() + "=" + s.getValue().stream().map(Entry::toString).collect(Collectors.joining(",")))
			.collect(Collectors.joining(";"));
	}

	static BankArrangement decode(String raw)
	{
		BankArrangement a = new BankArrangement();
		parse(raw, (s, tok) ->
		{
			Entry e = Entry.parse(tok);
			if (e != null)
			{
				a.list(s).add(e);
			}
		});
		return a;
	}

	/** The server's order, per section, in the same form as {@link #encode()}: {@code 0=995,989;3=1127}. */
	static String encodeOrder(Map<Integer, List<Integer>> order)
	{
		return order.entrySet().stream()
			.filter(s -> !s.getValue().isEmpty())
			.map(s -> s.getKey() + "=" + s.getValue().stream().map(String::valueOf).collect(Collectors.joining(",")))
			.collect(Collectors.joining(";"));
	}

	/** Null when there is nothing saved, so a first resolve follows no moves. */
	static Map<Integer, List<Integer>> decodeOrder(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		Map<Integer, List<Integer>> order = new TreeMap<>();
		parse(raw, (s, tok) ->
		{
			try
			{
				order.computeIfAbsent(s, k -> new ArrayList<>()).add(Integer.parseInt(tok));
			}
			catch (NumberFormatException e)
			{
				// a damaged token: skip it
			}
		});
		return order;
	}

	private interface TokenSink
	{
		void take(int section, String token);
	}

	private static void parse(String raw, TokenSink sink)
	{
		if (raw == null)
		{
			return;
		}
		for (String part : raw.split(";"))
		{
			int eq = part.indexOf('=');
			if (eq <= 0)
			{
				continue;
			}
			int s;
			try
			{
				s = Integer.parseInt(part.substring(0, eq));
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			for (String tok : part.substring(eq + 1).split(","))
			{
				if (!tok.trim().isEmpty())
				{
					sink.take(s, tok.trim());
				}
			}
		}
	}
}
