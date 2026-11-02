package com.potionstorage;

import com.potionstorage.BankArrangement.Drop;
import com.potionstorage.BankArrangement.Entry;
import com.potionstorage.BankArrangement.Pos;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The order the bank is drawn in once stored potions stand among real items. Real ids are small numbers,
 * stored keys are 9xx, and {@link #show} renders a section as {@code 1 2 p900 3} so a failure reads like the bank.
 */
public class BankArrangementTest
{
	private static String show(Map<Integer, List<Entry>> drawn, int s)
	{
		return drawn.getOrDefault(s, Collections.emptyList()).stream().map(Entry::toString).collect(Collectors.joining(" "));
	}

	private static Map<Integer, List<Integer>> bank(Object... sectionsAndIds)
	{
		Map<Integer, List<Integer>> m = new LinkedHashMap<>();
		for (int i = 0; i < sectionsAndIds.length; i += 2)
		{
			m.put((Integer) sectionsAndIds[i], Arrays.asList((Integer[]) sectionsAndIds[i + 1]));
		}
		return m;
	}

	private static Integer[] ids(Integer... ids)
	{
		return ids;
	}

	private static List<Integer> stored(Integer... keys)
	{
		return Arrays.asList(keys);
	}

	private static Set<Integer> sections(Integer... sections)
	{
		return new HashSet<>(Arrays.asList(sections));
	}

	/** A bank whose tabs are the ones it has items in, with no earlier server order to follow. */
	private static BankArrangement.Resolved resolve(BankArrangement a, Map<Integer, List<Integer>> reals, List<Integer> stored)
	{
		Set<Integer> existing = new HashSet<>(reals.keySet());
		existing.add(BankArrangement.MAIN);
		return a.resolve(reals, null, stored, existing);
	}

	private static Map<Integer, List<Entry>> draw(BankArrangement a, Map<Integer, List<Integer>> reals, List<Integer> stored)
	{
		return resolve(a, reals, stored).drawn;
	}

	@Test
	public void freshBankIsServerOrderWithStoredStacksAtTheEndOfTheUntabbedPart()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2, 3), 1, ids(4, 5));
		BankArrangement.Resolved r = resolve(a, reals, stored(900, 901));
		assertTrue(r.changed);
		assertEquals("1 2 3 p900 p901", show(r.drawn, 0));
		assertEquals("4 5", show(r.drawn, 1));
		assertFalse(resolve(a, reals, stored(900, 901)).changed);
	}

	@Test
	public void insertModePutsTheDraggedEntryInTheTargetsPlace()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2, 3));
		Map<Integer, List<Entry>> d = draw(a, reals, stored(900));
		a.move(d, new Pos(0, 3), new Pos(0, 1), Drop.INSERT);
		d = draw(a, reals, stored(900));
		assertEquals("1 p900 2 3", show(d, 0));
		a.move(d, new Pos(0, 0), new Pos(0, 2), Drop.INSERT);
		assertEquals("p900 2 1 3", show(draw(a, reals, stored(900)), 0));
	}

	@Test
	public void aStoredStackOutOfStockKeepsItsPlaceUntilForgotten()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2, 3));
		Map<Integer, List<Entry>> d = draw(a, reals, stored(900, 901));
		a.move(d, new Pos(0, 3), new Pos(0, 1), Drop.INSERT);
		assertEquals("1 p900 2 3 p901", show(draw(a, reals, stored(900, 901)), 0));
		// Run out: not drawn, but its place is kept for when it is back.
		assertEquals("1 2 3 p901", show(draw(a, reals, stored(901)), 0));
		assertEquals("1 p900 2 3 p901", show(draw(a, reals, stored(900, 901)), 0));
		// Released: back as a new stack, at the end of the untabbed part.
		assertTrue(a.forgetStored(900));
		assertFalse(a.forgetStored(900));
		assertEquals("1 2 3 p901 p900", show(draw(a, reals, stored(900, 901)), 0));
	}

	@Test
	public void aNewStoredStackGoesToTheOpenTab()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2), 1, ids(3, 4));
		Set<Integer> existing = sections(0, 1);
		a.resolve(reals, null, stored(900), existing, k -> 0);
		BankArrangement.Resolved r = a.resolve(reals, null, stored(900, 901), existing, k -> 1);
		assertEquals("1 2 p900", show(r.drawn, 0));
		assertEquals("3 4 p901", show(r.drawn, 1));
		// A tab that does not exist: the untabbed part.
		r = a.resolve(reals, null, stored(900, 901, 902), existing, k -> 5);
		assertEquals("1 2 p900 p902", show(r.drawn, 0));
	}

	@Test
	public void swapModeTradesTwoPlaces()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2, 3, 4));
		Map<Integer, List<Entry>> d = draw(a, reals, stored(900));
		a.move(d, new Pos(0, 4), new Pos(0, 1), Drop.SWAP);
		assertEquals("1 p900 3 4 2", show(draw(a, reals, stored(900)), 0));
	}

	@Test
	public void storedStackMovesIntoAnotherTabAndSurvivesASave()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2), 1, ids(3, 4));
		Map<Integer, List<Entry>> d = draw(a, reals, stored(900));
		a.move(d, new Pos(0, 2), new Pos(1, 1), Drop.INSERT);
		Map<Integer, List<Entry>> after = draw(a, reals, stored(900));
		assertEquals("1 2", show(after, 0));
		assertEquals("3 p900 4", show(after, 1));
		assertEquals("3 p900 4", show(draw(BankArrangement.decode(a.encode()), reals, stored(900)), 1));
	}

	@Test
	public void depositLandsAtTheEndOfItsTabAfterStoredStacks()
	{
		BankArrangement a = new BankArrangement();
		assertEquals("1 2 p900", show(draw(a, bank(0, ids(1, 2)), stored(900)), 0));
		assertEquals("1 2 p900 7", show(draw(a, bank(0, ids(1, 2, 7)), stored(900)), 0));
	}

	@Test
	public void potionTheStoreRunsOutOfKeepsItsPlace()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2, 3));
		Map<Integer, List<Entry>> d = draw(a, reals, stored(900));
		a.move(d, new Pos(0, 3), new Pos(0, 1), Drop.INSERT);
		assertEquals("1 2 3", show(draw(a, reals, stored()), 0));
		assertEquals("1 p900 2 3", show(draw(a, reals, stored(900)), 0));
	}

	@Test
	public void ghostKeepsItsPlaceThroughARearrange()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2, 3));
		Map<Integer, List<Entry>> d = draw(a, reals, stored(900, 901));
		a.move(d, new Pos(0, 4), new Pos(0, 1), Drop.INSERT); // 1 p901 2 3 p900
		d = draw(a, reals, stored(900)); // p901 out of stock: 1 2 3 p900
		a.move(d, new Pos(0, 0), new Pos(0, 2), Drop.INSERT); // drawn 2 3 1 p900
		assertEquals("2 3 1 p900", show(draw(a, reals, stored(900)), 0));
		assertEquals("2 3 1 p901 p900", show(draw(a, reals, stored(900, 901)), 0));
	}

	@Test
	public void realItemMovedToAnotherTabLeavesItsOldTabsOrder()
	{
		BankArrangement a = new BankArrangement();
		draw(a, bank(0, ids(1, 2), 1, ids(3)), stored());
		Map<Integer, List<Entry>> moved = draw(a, bank(0, ids(2), 1, ids(3, 1)), stored());
		assertEquals("2", show(moved, 0));
		assertEquals("3 1", show(moved, 1));
		assertEquals("0=2;1=3,1", a.encode());
	}

	@Test
	public void deletingATabRenumbersTheOnesAfterIt()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> before = bank(0, ids(1), 1, ids(2), 2, ids(3, 4), 3, ids(5, 6));
		Map<Integer, List<Entry>> d = draw(a, before, stored(900, 901));
		a.moveToEnd(d, new Pos(0, 1), 2);
		d = draw(a, before, stored(900, 901));
		a.moveToEnd(d, new Pos(0, 1), 3);
		d = draw(a, before, stored(900, 901));
		a.move(d, new Pos(3, 2), new Pos(3, 0), Drop.INSERT);
		BankArrangement.Resolved r = a.resolve(bank(0, ids(1, 2), 1, ids(3, 4), 2, ids(5, 6)), null, stored(900, 901),
			sections(0, 1, 2));
		assertEquals("1 2", show(r.drawn, 0));
		assertEquals("3 4 p900", show(r.drawn, 1));
		assertEquals("p901 5 6", show(r.drawn, 2));
	}

	@Test
	public void resolvingACopyAgainstAHalfUpdatedBankLeavesTheSavedOrderAlone()
	{
		// A build that saw the new item order with the old tab sizes: tab 7's only item read as untabbed and
		// tab 6's edge item as tab 7's. Learned, that merged the whole potions tab into tab 6.
		BankArrangement a = BankArrangement.decode("0=995;6=1,2,3;7=229,p900,p901");
		a.copy().resolve(bank(0, ids(995, 229), 6, ids(1, 2), 7, ids(3)), null, stored(900, 901), sections(0, 6, 7));
		assertEquals("0=995;6=1,2,3;7=229,p900,p901", a.encode());
		BankArrangement.Resolved settled = a.resolve(bank(0, ids(995), 6, ids(1, 2, 3), 7, ids(229)), null, stored(900, 901),
			sections(0, 6, 7));
		assertEquals("229 p900 p901", show(settled.drawn, 7));
	}

	@Test
	public void storedStackInATabThatNoLongerExistsGoesHome()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Entry>> d = draw(a, bank(0, ids(1), 4, ids(7)), stored(900));
		a.moveToEnd(d, new Pos(0, 1), 4);
		BankArrangement.Resolved r = a.resolve(bank(0, ids(1, 7)), null, stored(900), sections(0));
		assertEquals("1 7 p900", show(r.drawn, 0));
	}

	@Test
	public void encodingRoundTrips()
	{
		BankArrangement a = BankArrangement.decode("0=1,2,p900;3=5,p901,6");
		assertEquals("0=1,2,p900;3=5,p901,6", a.encode());
		assertEquals("5 p901 6", show(draw(a, bank(0, ids(1, 2), 3, ids(5, 6)), stored(900, 901)), 3));
		assertEquals("", BankArrangement.decode(null).encode());
		assertEquals("0=1", BankArrangement.decode("0=1,,x;bad").encode());
	}

	@Test
	public void tabEmptiedIntoAnotherKeepsThatTabsOwnStacks()
	{
		// Tab 5's last item dropped into tab 4: tab 5 is gone, and tab 4 - which kept its own items - is not
		// a renumbered tab 5. Its stored stack stays put; tab 5's goes to the untabbed part.
		BankArrangement a = BankArrangement.decode("0=1;4=10,11,p900;5=20,p901");
		BankArrangement.Resolved r = a.resolve(bank(0, ids(1), 4, ids(10, 11, 20)), null, stored(900, 901), sections(0, 1, 2, 3, 4));
		assertEquals("10 11 p900 20", show(r.drawn, 4));
		assertEquals("1 p901", show(r.drawn, 0));
	}

	@Test
	public void tabEmptiedIntoAnotherKeepsThatTabsGhosts()
	{
		BankArrangement a = BankArrangement.decode("0=1;4=11,12,10,p900;5=20");
		a.resolve(bank(0, ids(1), 4, ids(10, 11, 20)), null, stored(900), sections(0, 1, 2, 3, 4));
		assertEquals("0=1;4=11,12,20,10,p900", a.encode());
	}

	@Test
	public void reorderMadeOutOfSightIsFollowed()
	{
		// The server swapped 1 and 2 - in a search, with the plugin off, on another device.
		BankArrangement a = BankArrangement.decode("0=1,2,3,p900");
		BankArrangement.Resolved r = a.resolve(bank(0, ids(2, 1, 3)), bank(0, ids(1, 2, 3)), stored(900), sections(0));
		assertTrue(r.changed);
		assertEquals("2 1 3 p900", show(r.drawn, 0));
	}

	@Test
	public void itemMovedToTheEndOutOfSightGoesAfterItsNewNeighbour()
	{
		BankArrangement a = BankArrangement.decode("0=1,p900,2,3");
		BankArrangement.Resolved r = a.resolve(bank(0, ids(2, 3, 1)), bank(0, ids(1, 2, 3)), stored(900), sections(0));
		assertEquals("p900 2 3 1", show(r.drawn, 0));
	}

	@Test
	public void moveTheOrderAlreadyHasIsLeftAlone()
	{
		// The plugin followed the drag itself; the server's version of it changes nothing more.
		BankArrangement a = BankArrangement.decode("0=2,1,p900,3");
		BankArrangement.Resolved r = a.resolve(bank(0, ids(2, 1, 3)), bank(0, ids(1, 2, 3)), stored(900), sections(0));
		assertFalse(r.changed);
		assertEquals("2 1 p900 3", show(r.drawn, 0));
	}

	@Test
	public void sameServerOrderFollowsNothing()
	{
		BankArrangement a = BankArrangement.decode("0=3,p900,1,2");
		BankArrangement.Resolved r = a.resolve(bank(0, ids(1, 2, 3)), bank(0, ids(1, 2, 3)), stored(900), sections(0));
		assertFalse(r.changed);
		assertEquals("3 p900 1 2", show(r.drawn, 0));
	}

	@Test
	public void moveOutsideWhatIsDrawnChangesNothing()
	{
		BankArrangement a = BankArrangement.decode("0=1,2,p900");
		Map<Integer, List<Entry>> d = draw(a, bank(0, ids(1, 2)), stored(900));
		a.move(d, new Pos(0, 5), new Pos(0, 0), Drop.INSERT);
		a.move(d, new Pos(3, 0), new Pos(0, 0), Drop.INSERT);
		assertEquals("0=1,2,p900", a.encode());
	}

	@Test
	public void moveToEndOfASectionWithNothingDrawnStartsIt()
	{
		BankArrangement a = BankArrangement.decode("0=1,2,p900");
		Map<Integer, List<Entry>> d = draw(a, bank(0, ids(1, 2), 3, ids(7)), stored(900));
		a.moveToEnd(d, new Pos(0, 2), 2);
		assertEquals("0=1,2;2=p900;3=7", a.encode());
	}

	@Test
	public void serverOrderRoundTrips()
	{
		Map<Integer, List<Integer>> order = bank(0, ids(995, 989), 3, ids(1127));
		assertEquals("0=995,989;3=1127", BankArrangement.encodeOrder(order));
		assertEquals(order, BankArrangement.decodeOrder("0=995,989;3=1127"));
		assertEquals(null, BankArrangement.decodeOrder(null));
	}

	@Test
	public void inFrontPutsTheDraggedEntryBeforeTheTargetEitherWay()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2, 3));
		Map<Integer, List<Entry>> d = draw(a, reals, stored(900));
		a.move(d, new Pos(0, 3), new Pos(0, 1), Drop.IN_FRONT); // backward: the same as the bank's insert
		d = draw(a, reals, stored(900));
		assertEquals("1 p900 2 3", show(d, 0));
		a.move(d, new Pos(0, 0), new Pos(0, 3), Drop.IN_FRONT); // forward: in front of 3, where insert goes after it
		assertEquals("p900 2 1 3", show(draw(a, reals, stored(900)), 0));
	}

	@Test
	public void inFrontIntoAnotherTab()
	{
		BankArrangement a = new BankArrangement();
		Map<Integer, List<Integer>> reals = bank(0, ids(1, 2), 1, ids(3, 4));
		Map<Integer, List<Entry>> d = draw(a, reals, stored(900));
		a.move(d, new Pos(0, 2), new Pos(1, 1), Drop.IN_FRONT);
		Map<Integer, List<Entry>> after = draw(a, reals, stored(900));
		assertEquals("1 2", show(after, 0));
		assertEquals("3 p900 4", show(after, 1));
	}

	@Test
	public void droppingAStackOnItselfChangesNothing()
	{
		BankArrangement a = BankArrangement.decode("0=1,p900,2");
		Map<Integer, List<Entry>> d = draw(a, bank(0, ids(1, 2)), stored(900));
		for (Drop how : Drop.values())
		{
			a.move(d, new Pos(0, 1), new Pos(0, 1), how);
		}
		assertEquals("0=1,p900,2", a.encode());
	}
}
