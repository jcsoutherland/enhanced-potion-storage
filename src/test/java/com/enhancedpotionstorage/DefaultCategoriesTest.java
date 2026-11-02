package com.enhancedpotionstorage;

import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Every potion potion storage keeps, by the OSRS wiki's list, lands in the category it belongs in. */
public class DefaultCategoriesTest
{
	private static final List<String> STANDARD = List.of("Agility potion", "Ancient brew", "Anti-venom", "Anti-venom+",
		"Antidote+", "Antidote++", "Antifire potion", "Antipoison", "Armadyl brew", "Attack potion", "Bastion potion",
		"Battlemage potion", "Blighted super restore", "Combat potion", "Compost potion", "Defence potion",
		"Divine bastion potion", "Divine battlemage potion", "Divine magic potion", "Divine ranging potion",
		"Divine super attack potion", "Divine super combat potion", "Divine super defence potion",
		"Divine super strength potion", "Energy potion", "Extended anti-venom+", "Extended antifire",
		"Extended stamina potion", "Extended super antifire", "Extreme energy potion", "Fishing potion",
		"Forgotten brew", "Goading potion", "Guthix balance", "Hunter potion", "Magic essence", "Magic potion",
		"Menaphite remedy", "Prayer potion", "Prayer regeneration potion", "Ranging potion", "Relicym's balm",
		"Restore potion", "Sanfew serum", "Saradomin brew", "Serum 207", "Stamina potion", "Strength potion",
		"Super antifire potion", "Super attack", "Super combat potion", "Super defence", "Super energy",
		"Super fishing potion", "Super hunter potion", "Super restore", "Super strength", "Superantipoison",
		"Surge potion", "Weapon poison", "Weapon poison(+)", "Weapon poison(++)", "Zamorak brew");

	private static final List<String> MIXES = List.of("Agility mix", "Ancient mix", "Anti-poison supermix",
		"Antidote+ mix", "Antifire mix", "Antipoison mix", "Attack mix", "Combat mix", "Defence mix", "Energy mix",
		"Extended antifire mix", "Extended super antifire mix", "Fishing mix", "Hunting mix", "Magic essence mix",
		"Magic mix", "Prayer mix", "Ranging mix", "Relicym's mix", "Restore mix", "Stamina mix", "Strength mix",
		"Super antifire mix", "Super def. mix", "Super energy mix", "Super restore mix", "Super str. mix",
		"Superattack mix", "Zamorak mix");

	@Test
	public void spotChecks()
	{
		assertEquals(DefaultCategories.COMBAT, DefaultCategories.categoryOf("Super combat potion", false));
		assertEquals(DefaultCategories.DIVINE, DefaultCategories.categoryOf("Divine ranging potion", false));
		assertEquals(DefaultCategories.RESTORE, DefaultCategories.categoryOf("Prayer potion", false));
		assertEquals(DefaultCategories.RESTORE, DefaultCategories.categoryOf("Saradomin brew", false));
		assertEquals(DefaultCategories.STAMINA, DefaultCategories.categoryOf("Stamina potion", false));
		assertEquals(DefaultCategories.DEFENSIVE, DefaultCategories.categoryOf("Anti-venom+", false));
		assertEquals(DefaultCategories.BOSSING, DefaultCategories.categoryOf("Zamorak brew", false));
		assertEquals(DefaultCategories.UTILITY, DefaultCategories.categoryOf("Super fishing potion", false));
		assertEquals(DefaultCategories.MIXES, DefaultCategories.categoryOf("Guam potion (unf)", true));
	}

	@Test
	public void everyStandardPotionHasACategoryOfItsOwnOrUtility()
	{
		for (String name : STANDARD)
		{
			String c = DefaultCategories.categoryOf(name, false);
			assertTrue(name + " -> " + c, DefaultCategories.ORDER.contains(c) && !c.equals(DefaultCategories.MIXES)
				&& !c.equals(DefaultCategories.RAID));
		}
	}

	@Test
	public void everyMixAndUnknownMixIsAMix()
	{
		for (String name : MIXES)
		{
			assertEquals(name, DefaultCategories.MIXES, DefaultCategories.categoryOf(name, false));
		}
		assertEquals(DefaultCategories.MIXES, DefaultCategories.categoryOf("Brand new mix", false));
		assertEquals(DefaultCategories.UTILITY, DefaultCategories.categoryOf("Brand new potion", false));
	}

	@Test
	public void defaultIsTheNineCategoriesInOrderWithRaidEmpty()
	{
		List<Template.Category> c = DefaultCategories.build(List.of(SlotPlanTest.potion("Prayer potion", 100, 1, 2, 3, 4)));
		assertEquals(9, c.size());
		assertEquals(DefaultCategories.ORDER, c.stream().map(x -> x.name).collect(java.util.stream.Collectors.toList()));
		assertEquals("Utility comes after Bossing", DefaultCategories.ORDER.indexOf(DefaultCategories.BOSSING) + 1,
			DefaultCategories.ORDER.indexOf(DefaultCategories.UTILITY));
		assertTrue(c.get(DefaultCategories.ORDER.indexOf(DefaultCategories.RAID)).potions.isEmpty());
	}
}
