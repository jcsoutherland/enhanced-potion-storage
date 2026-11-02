package com.enhancedpotionstorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlotPlanTest
{
	/** A potion whose dose d is item id {@code base + d}; its id is its smallest dose's. */
	static SlotPlan.Potion potion(String name, int base, int... doses)
	{
		Map<Integer, Integer> items = new HashMap<>();
		for (int d : doses)
		{
			items.put(d, base + d);
		}
		int smallest = Arrays.stream(doses).min().orElse(1);
		return new SlotPlan.Potion(base + smallest, name, false, base, items);
	}

	static SlotPlan.Potion unfinished(String name, int id)
	{
		Map<Integer, Integer> items = new HashMap<>();
		items.put(1, id);
		return new SlotPlan.Potion(id, name, true, id, items);
	}

	private static String doses(SlotPlan plan)
	{
		return plan.slots().stream().map(s -> s.potionId + ":" + s.dose).collect(Collectors.joining(" "));
	}

	private static final SlotPlan.Potion PRAYER = potion("Prayer potion", 100, 1, 2, 3, 4);
	private static final SlotPlan.Potion ATTACK_MIX = potion("Attack mix", 200, 1, 2);
	private static final SlotPlan.Potion SUPER_COMBAT = potion("Super combat potion", 300, 1, 2, 3, 4);
	private static final SlotPlan.Potion GUAM = unfinished("Guam potion (unf)", 400);

	@Test
	public void defaultShowsEveryDoseLargestFirstInDefaultCategoryOrder()
	{
		SlotPlan plan = SlotPlan.of(Template.builtIn(), List.of(PRAYER, ATTACK_MIX, SUPER_COMBAT, GUAM));
		assertEquals("301:4 301:3 301:2 301:1 101:4 101:3 101:2 101:1 201:2 201:1 400:1", doses(plan));
		for (int i = 0; i < plan.slots().size(); i++)
		{
			assertEquals(i, plan.rank(plan.slots().get(i).key));
		}
	}

	@Test
	public void keysAreTheDosesItemIdPlusAStrideForEachRepeat()
	{
		Template t = Template.builtIn();
		t.showAllDoses = false;
		t.doses.put(PRAYER.id, List.of(4, 3, 3, 1));
		List<Integer> keys = SlotPlan.keysOf(PRAYER, SlotPlan.effectiveDoses(t, PRAYER));
		assertEquals(List.of(104, 103, 103 + SlotPlan.COPY_STRIDE, 101), keys);
		assertEquals(103, SlotPlan.itemOfKey(103 + SlotPlan.COPY_STRIDE));
	}

	@Test
	public void showAllDosesOverridesAPotionsOwnList()
	{
		Template t = Template.builtIn();
		t.doses.put(PRAYER.id, List.of(2));
		assertEquals(List.of(4, 3, 2, 1), SlotPlan.effectiveDoses(t, PRAYER));
		t.showAllDoses = false;
		assertEquals(List.of(2), SlotPlan.effectiveDoses(t, PRAYER));
	}

	@Test
	public void aDoseThePotionDoesNotComeInIsDropped()
	{
		Template t = Template.builtIn();
		t.showAllDoses = false;
		t.doses.put(ATTACK_MIX.id, List.of(4, 2, 1));
		assertEquals(List.of(2, 1), SlotPlan.effectiveDoses(t, ATTACK_MIX));
	}

	@Test
	public void anUnfinishedPotionHasOneSlotWhateverTheListSays()
	{
		Template t = Template.builtIn();
		t.showAllDoses = false;
		t.doses.put(GUAM.id, List.of(1, 1, 1));
		assertEquals(List.of(1), SlotPlan.effectiveDoses(t, GUAM));
	}

	@Test
	public void aDisabledCategoryShowsNoSlots()
	{
		List<SlotPlan.Potion> all = List.of(PRAYER, SUPER_COMBAT);
		Template t = Template.builtIn();
		t.categories = SlotPlan.categories(t, all);
		t.category(DefaultCategories.COMBAT).enabled = false;
		assertEquals("101:4 101:3 101:2 101:1", doses(SlotPlan.of(t, all)));
	}

	@Test
	public void aPotionATemplateDoesNotListJoinsItsDefaultCategory()
	{
		Template t = Template.builtIn();
		t.categories = new ArrayList<>();
		t.categories.add(new Template.Category("Mine", List.of(SUPER_COMBAT.id)));
		t.categories.add(new Template.Category(DefaultCategories.RESTORE, List.of()));
		List<Template.Category> c = SlotPlan.categories(t, List.of(PRAYER, SUPER_COMBAT, GUAM));
		assertEquals(List.of(SUPER_COMBAT.id), c.get(0).potions);
		assertEquals(List.of(PRAYER.id, GUAM.id), c.get(1).potions);
		assertTrue("the template itself is left alone", t.categories.get(1).potions.isEmpty());
	}

	@Test
	public void customOrderIsTheBankOrder()
	{
		Template t = Template.builtIn();
		t.categories = new ArrayList<>();
		t.categories.add(new Template.Category("A", List.of(PRAYER.id, SUPER_COMBAT.id)));
		t.showAllDoses = false;
		t.doses.put(PRAYER.id, List.of(1, 4));
		t.doses.put(SUPER_COMBAT.id, List.of(4));
		assertEquals("101:1 101:4 301:4", doses(SlotPlan.of(t, List.of(PRAYER, SUPER_COMBAT))));
	}
}
