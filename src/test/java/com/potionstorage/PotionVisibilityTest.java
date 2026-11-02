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

import com.potionstorage.PotionVisibility.Where;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PotionVisibilityTest
{
	private final Map<String, String> config = new HashMap<>();

	private PotionVisibility visibility()
	{
		return new PotionVisibility(config::get, (k, v) ->
		{
			if (v.isEmpty())
			{
				config.remove(k);
			}
			else
			{
				config.put(k, v);
			}
		});
	}

	@Test
	public void everythingIsShownUntilHidden()
	{
		PotionVisibility v = visibility();
		assertEquals(EnumSet.allOf(Where.class), v.placesShown(3024));
		assertTrue(config.isEmpty());
	}

	@Test
	public void hidingInOnePlaceLeavesTheOthers()
	{
		PotionVisibility v = visibility();
		v.set(List.of(3024), EnumSet.of(Where.TAGS), false);
		assertFalse(v.shown(Where.TAGS, 3024));
		assertTrue(v.shown(Where.BANK, 3024));
		assertTrue(v.shown(Where.SEARCH, 3024));
		assertEquals("3024", config.get("hiddenInTags"));
		assertFalse(config.containsKey("hiddenInBank"));
	}

	@Test
	public void showingAgainRemovesTheSetting()
	{
		PotionVisibility v = visibility();
		v.set(List.of(3024, 6685), EnumSet.allOf(Where.class), false);
		assertEquals("3024,6685", config.get("hiddenInSearch"));
		v.set(List.of(3024), EnumSet.allOf(Where.class), true);
		assertEquals("6685", config.get("hiddenInSearch"));
		v.set(List.of(6685), EnumSet.allOf(Where.class), true);
		assertTrue(config.isEmpty());
	}

	@Test
	public void savedListsAreReadBack()
	{
		config.put("hiddenInBank", "6685, 3024");
		config.put("hiddenInSearch", "junk,3024");
		PotionVisibility v = visibility();
		assertEquals(EnumSet.of(Where.TAGS), v.placesShown(3024));
		assertEquals(EnumSet.of(Where.TAGS, Where.SEARCH), v.placesShown(6685));
	}

	@Test
	public void reloadPicksUpOutsideChanges()
	{
		PotionVisibility v = visibility();
		config.put("hiddenInBank", "3024");
		assertTrue(v.shown(Where.BANK, 3024));
		v.reload();
		assertFalse(v.shown(Where.BANK, 3024));
	}
}
