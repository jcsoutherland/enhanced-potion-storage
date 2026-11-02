package com.enhancedpotionstorage;

import com.enhancedpotionstorage.BankArrangement.Entry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The saved order's part in dose slots: new ones by template rank, renamed ones, moved and re-placed ones. */
public class DoseSlotArrangementTest
{
	private static String show(Map<Integer, List<Entry>> drawn, int s)
	{
		return drawn.getOrDefault(s, Collections.emptyList()).stream().map(Entry::toString).collect(Collectors.joining(" "));
	}

	private static Map<Integer, List<Integer>> bank(int section, Integer... ids)
	{
		Map<Integer, List<Integer>> m = new LinkedHashMap<>();
		m.put(section, Arrays.asList(ids));
		return m;
	}

	/** Rank by position in {@code order}; -1 for a key not in it. */
	private static IntUnaryOperator rank(Integer... order)
	{
		List<Integer> l = Arrays.asList(order);
		return key -> l.indexOf(key);
	}

	private static BankArrangement.Resolved resolve(BankArrangement a, Map<Integer, List<Integer>> reals, List<Integer> stored,
		IntUnaryOperator rank)
	{
		Set<Integer> existing = new HashSet<>(reals.keySet());
		existing.add(BankArrangement.MAIN);
		return a.resolve(reals, null, stored, existing, key -> BankArrangement.MAIN, rank);
	}

	@Test
	public void aNewSlotGoesRightAfterTheSlotRankedJustBeforeIt()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, 1, 2);
		IntUnaryOperator rank = rank(10, 11, 12, 13);
		resolve(a, reals, List.of(10, 13), rank);
		// 13 was dragged in among the real items; 11 and 12 arrive later.
		assertEquals("1 2 p10 p13", show(resolve(a, reals, List.of(10, 13), rank).drawn, 0));
		assertEquals("1 2 p10 p11 p12 p13", show(resolve(a, reals, List.of(10, 11, 12, 13), rank).drawn, 0));
	}

	@Test
	public void aNewSlotRankedFirstGoesJustBeforeTheNextOne()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, 1, 2);
		IntUnaryOperator rank = rank(10, 11, 12);
		resolve(a, reals, List.of(11, 12), rank);
		assertEquals("1 2 p10 p11 p12", show(resolve(a, reals, List.of(10, 11, 12), rank).drawn, 0));
	}

	@Test
	public void aNewSlotFollowsItsNeighbourIntoAnotherTab()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = new LinkedHashMap<>();
		reals.put(0, Arrays.asList(1, 2));
		reals.put(3, Arrays.asList(5, 6));
		IntUnaryOperator rank = rank(10, 11);
		resolve(a, reals, List.of(10), rank);
		a.moveAllStored(3, rank);
		Map<Integer, List<Entry>> d = resolve(a, reals, List.of(10, 11), rank).drawn;
		assertEquals("5 6 p10 p11", show(d, 3));
		assertEquals("1 2", show(d, 0));
	}

	@Test
	public void withNoRankedNeighbourANewSlotGoesToTheNewSection()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = new LinkedHashMap<>();
		reals.put(0, Arrays.asList(1));
		reals.put(2, Arrays.asList(7));
		Set<Integer> existing = new HashSet<>(Arrays.asList(0, 2));
		Map<Integer, List<Entry>> d = a.resolve(reals, null, List.of(10), existing, key -> 2, rank(10)).drawn;
		assertEquals("7 p10", show(d, 2));
	}

	@Test
	public void renamedSlotsKeepTheirPlaces()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, 1, 2);
		IntUnaryOperator before = rank(10, 11, 12);
		resolve(a, reals, List.of(10, 11, 12), before);
		Map<Integer, Integer> renames = new HashMap<>();
		renames.put(11, 20);
		a.renameStored(renames);
		assertEquals("1 2 p10 p20 p12", show(resolve(a, reals, List.of(10, 20, 12), rank(10, 20, 12)).drawn, 0));
	}

	@Test
	public void moveAllStoredPutsEverySlotTogetherInRankOrder()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = new LinkedHashMap<>();
		reals.put(0, Arrays.asList(1, 2));
		reals.put(4, Arrays.asList(8));
		IntUnaryOperator rank = rank(10, 11, 12);
		resolve(a, reals, List.of(12, 10, 11), rank(12, 10, 11));
		a.moveAllStored(4, rank);
		Map<Integer, List<Entry>> d = resolve(a, reals, List.of(10, 11, 12), rank).drawn;
		assertEquals("1 2", show(d, 0));
		assertEquals("8 p10 p11 p12", show(d, 4));
	}

	@Test
	public void placeStoredAsPutsSlotsAfterTheRealItemsTheyFollowed()
	{
		BankArrangement saved = new BankArrangement();
		Map<Integer, List<Integer>> was = bank(0, 1, 2, 3);
		resolve(saved, was, List.of(10, 11), rank(10, 11));
		saved.move(resolve(saved, was, List.of(10, 11), rank(10, 11)).drawn,
			new BankArrangement.Pos(0, 3), new BankArrangement.Pos(0, 1), BankArrangement.Drop.INSERT);
		assertEquals("1 p10 2 3 p11", show(resolve(saved, was, List.of(10, 11), rank(10, 11)).drawn, 0));

		// Another bank: item 1 is gone, 4 is new. The slot that followed 1 shifts to the front.
		BankArrangement live = new BankArrangement();
		Map<Integer, List<Integer>> now = bank(0, 2, 3, 4);
		resolve(live, now, List.of(10, 11), rank(10, 11));
		live.placeStoredAs(BankArrangement.decode(saved.encode()));
		assertEquals("p10 2 3 p11 4", show(resolve(live, now, List.of(10, 11), rank(10, 11)).drawn, 0));
	}

	@Test
	public void placeStoredAsSendsSlotsOfAMissingTabToTheMainView()
	{
		BankArrangement saved = BankArrangement.decode("0=1;5=50,p10,p11");
		BankArrangement live = new BankArrangement();
		Map<Integer, List<Integer>> now = bank(0, 1, 2);
		resolve(live, now, List.of(), rank());
		live.placeStoredAs(saved);
		assertEquals("1 2 p10 p11", show(resolve(live, now, List.of(10, 11), rank(10, 11)).drawn, 0));
	}

	@Test
	public void sortStoredReordersSlotsInThePlacesTheyTake()
	{
		BankArrangement a = BankArrangement.decode("0=1,p12,2,p10,p11");
		a.sortStored(rank(10, 11, 12));
		assertEquals("0=1,p10,2,p11,p12", a.encode());
	}

	@Test
	public void clearStoredForgetsEverySlot()
	{
		BankArrangement a = BankArrangement.decode("0=1,p12,2;3=p10");
		a.clearStored();
		assertEquals("0=1,2", a.encode());
	}
}
