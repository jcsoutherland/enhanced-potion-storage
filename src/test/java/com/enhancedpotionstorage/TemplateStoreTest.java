package com.enhancedpotionstorage;

import com.enhancedpotionstorage.PotionVisibility.Where;
import com.enhancedpotionstorage.TemplateStore.Removed;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class TemplateStoreTest
{
	/** RuneLite's per-account config, kept in memory: one map per account. */
	private static final class FakeStorage implements TemplateStore.Storage
	{
		final Map<String, Map<String, String>> byAccount = new HashMap<>();
		String account = "alice";

		@Override
		public String account()
		{
			return account;
		}

		@Override
		public String get(String key)
		{
			return byAccount.computeIfAbsent(account, a -> new HashMap<>()).get(key);
		}

		@Override
		public void set(String key, String value)
		{
			byAccount.computeIfAbsent(account, a -> new HashMap<>()).put(key, value);
		}

		@Override
		public void unset(String key)
		{
			byAccount.computeIfAbsent(account, a -> new HashMap<>()).remove(key);
		}
	}

	private static final List<SlotPlan.Potion> POTIONS = List.of(
		SlotPlanTest.potion("Prayer potion", 100, 1, 2, 3, 4),
		SlotPlanTest.potion("Super combat potion", 300, 1, 2, 3, 4));

	private FakeStorage storage;
	/** The bank's saved order, as the store hands it to templates and back. */
	private String arrangement;
	private final List<String> placed = new ArrayList<>();

	private TemplateStore store()
	{
		return new TemplateStore(storage, new Gson(), () -> arrangement, placed::add, () -> POTIONS);
	}

	@Before
	public void setUp()
	{
		storage = new FakeStorage();
		arrangement = "0=1,p104";
		placed.clear();
	}

	@Test
	public void startsOnDefaultWithNothingSaved()
	{
		TemplateStore t = store();
		assertEquals(Template.DEFAULT, t.activeName());
		assertFalse(t.dirty());
		assertEquals(List.of(Template.DEFAULT), t.names());
		assertTrue(t.working().isDefault());
	}

	@Test
	public void anEditOnDefaultMakesASavedCustomTemplateAndDefaultStaysAsItWas()
	{
		TemplateStore t = store();
		assertTrue(t.edit(w -> w.showAllDoses = false));
		assertEquals("Custom", t.activeName());
		assertFalse("saved straight away", t.dirty());
		assertFalse(t.working().showAllDoses);
		assertEquals(9, t.working().categories.size());
		assertEquals("0=1,p104", t.working().arrangement);

		t.open(Template.DEFAULT);
		assertTrue(t.working().showAllDoses);
		assertTrue(t.working().isDefault());
		assertEquals(List.of(Template.DEFAULT, "Custom"), t.names());

		t.edit(w -> w.showAllDoses = false);
		assertEquals("Custom 2", t.activeName());
	}

	@Test
	public void anEditOnACustomTemplateIsUnsavedUntilSaved()
	{
		TemplateStore t = store();
		t.saveAs("Mine");
		assertFalse(t.edit(w -> w.setHidden(Where.BANK, Set.of(101))));
		assertTrue(t.dirty());
		assertEquals(Set.of(101), t.working().hidden(Where.BANK));

		// Unsaved changes last across a restart.
		TemplateStore again = store();
		assertEquals("Mine", again.activeName());
		assertTrue(again.dirty());
		assertEquals(Set.of(101), again.working().hidden(Where.BANK));

		again.save();
		assertFalse(again.dirty());
		again.open("Mine");
		assertEquals(Set.of(101), again.working().hidden(Where.BANK));
	}

	@Test
	public void openingDropsUnsavedChangesAndPutsTheSavedPlacementBack()
	{
		TemplateStore t = store();
		t.saveAs("Mine");
		arrangement = "0=p104,1";
		t.edit(w -> w.showAllDoses = false);
		t.open("Mine");
		assertFalse(t.dirty());
		assertTrue(t.working().showAllDoses);
		assertEquals("0=1,p104", placed.get(placed.size() - 1));
	}

	@Test
	public void saveKeepsThePlacementAsItIsNow()
	{
		TemplateStore t = store();
		t.saveAs("Mine");
		arrangement = "0=p104,1";
		t.placementEdited();
		assertTrue(t.dirty());
		t.save();
		t.open("Mine");
		assertEquals("0=p104,1", placed.get(placed.size() - 1));
	}

	@Test
	public void eachAccountHasItsOwnTemplates()
	{
		TemplateStore t = store();
		t.saveAs("Alice's");
		storage.account = "bob";
		assertEquals(Template.DEFAULT, t.activeName());
		assertEquals(List.of(Template.DEFAULT), t.names());
		storage.account = "alice";
		assertEquals("Alice's", t.activeName());
	}

	@Test
	public void namesAreUniqueAndDefaultIsTaken()
	{
		TemplateStore t = store();
		assertEquals("default 2", t.saveAs("default"));
		assertEquals("Default 3", t.saveAs("Default"));
		t.rename("Default 3", "Default 2");
		assertTrue(t.names().contains("Default 2 2"));
	}

	@Test
	public void deletingTheOpenTemplateOpensDefault()
	{
		TemplateStore t = store();
		t.saveAs("Mine");
		t.delete("Mine");
		assertEquals(Template.DEFAULT, t.activeName());
		assertEquals(List.of(Template.DEFAULT), t.names());
	}

	@Test
	public void exportAndImportRoundTrip()
	{
		TemplateStore t = store();
		t.saveAs("Mine");
		t.edit(w ->
		{
			w.showAllDoses = false;
			w.doses.put(101, List.of(4, 4, 1));
			w.categories.get(0).enabled = false;
		});
		String json = t.export("Mine");
		assertNotNull(json);

		storage.account = "bob";
		String name = t.importJson(json);
		assertEquals("Mine", name);
		t.open(name);
		Template w = t.working();
		assertFalse(w.showAllDoses);
		assertEquals(List.of(4, 4, 1), w.doses.get(101));
		assertFalse(w.categories.get(0).enabled);
		assertEquals("0=1,p104", placed.get(placed.size() - 1));
	}

	@Test
	public void importingJunkDoesNothing()
	{
		TemplateStore t = store();
		assertNull(t.importJson("not json {"));
		assertEquals(List.of(Template.DEFAULT), t.names());
	}

	@Test
	public void recentlyRemovedKeepsTheLastFiveNewestFirstUntilATemplateIsOpened()
	{
		TemplateStore t = store();
		for (int d = 1; d <= 7; d++)
		{
			t.recordRemoved(new Removed(101, d, 100 + d, Removed.How.RELEASED));
		}
		List<Removed> r = t.removed();
		assertEquals(TemplateStore.REMOVED_KEPT, r.size());
		assertEquals(7, r.get(0).dose);
		assertEquals(3, r.get(4).dose);
		assertEquals(5, store().removed().size());

		t.forgetRemoved(r.get(0));
		assertEquals(4, t.removed().size());
		t.open(Template.DEFAULT);
		assertTrue(t.removed().isEmpty());
	}
}
