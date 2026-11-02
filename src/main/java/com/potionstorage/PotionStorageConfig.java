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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(PotionStorageConfig.GROUP)
public interface PotionStorageConfig extends Config
{
	String GROUP = "potionstorage";

	@ConfigSection(
		name = "Bank view",
		description = "Stored potions among the bank's own items, and in bank searches",
		position = 0
	)
	String BANK_VIEW = "bankView";

	@ConfigSection(
		name = "Bank tags",
		description = "Stored potions in tag tabs, and tagging them",
		position = 1
	)
	String BANK_TAGS = "bankTags";

	@ConfigSection(
		name = "Combined stacks",
		description = "One stack for a potion both the bank and potion storage hold",
		position = 2
	)
	String COMBINED = "combined";

	@ConfigItem(
		keyName = "inBank",
		name = "Show in the bank",
		description = "Show stored potions among the bank's own items, in \"View all items\" and the numbered tabs, where they can be dragged like any other item",
		position = 0,
		section = BANK_VIEW
	)
	default boolean inBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showUnfinished",
		name = "Unfinished potions",
		description = "Show stored unfinished potions in the bank, in searches and in tag tabs",
		position = 1,
		section = BANK_VIEW
	)
	default boolean showUnfinished()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inSearch",
		name = "Show in searches",
		description = "Add the stored potions that match a bank search to its results, under the tab each is in - or in the first section, with \"Show in the bank\" off",
		position = 2,
		section = BANK_VIEW
	)
	default boolean inSearch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideStoreButton",
		name = "Hide potion storage button",
		description = "Hide the potion storage button at the foot of the bank's tag tabs. Stored potions are still shown, withdrawn and, with \"Always deposit to potion storage\" on, deposited as usual; turn this off to open potion storage itself",
		position = 3,
		section = BANK_VIEW
	)
	default boolean hideStoreButton()
	{
		return false;
	}

	@ConfigItem(
		keyName = "placeholders",
		name = "Placeholders",
		description = "When you take the last of a stored potion, leave a placeholder in its place - as the bank does for its own items while its \"Always set placeholders\" is on, and only then. Release one from its menu, or all of them with the bank's \"Release all placeholders\"",
		position = 4,
		section = BANK_VIEW
	)
	default boolean placeholders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inTagTabs",
		name = "Show in tag tabs",
		description = "List a tag's stored potions after the bank's items in its tag tab, for tag tabs without a layout",
		position = 0,
		section = BANK_TAGS
	)
	default boolean inTagTabs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "addToLayouts",
		name = "Add tagged potions to layouts",
		description = "Give a stored potion a place in a laid-out tag tab when it is tagged, as the Bank Tags plugin does for a bank item",
		position = 1,
		section = BANK_TAGS
	)
	default boolean addToLayouts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dragFromStore",
		name = "Drag from potion storage",
		description = "Drag a potion's icon in potion storage onto a tag tab to tag it. Not available while the Potion Storage Customizer plugin is on",
		position = 2,
		section = BANK_TAGS
	)
	default boolean dragFromStore()
	{
		return true;
	}

	@ConfigItem(
		keyName = "combineWithBank",
		name = "Combine with bank stacks",
		description = "Show a stored potion as one stack with the bank's own stack or placeholder of the same potion and dose, wherever stored potions are shown",
		position = 0,
		section = COMBINED
	)
	default boolean combineWithBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "combinedWithdraw",
		name = "Withdraw from",
		description = "Which a combined stack withdraws from first. Potion storage first keeps a potion you leave in the bank - as a tab's icon, say - from becoming a placeholder. Only applies with \"Combine with bank stacks\" on",
		position = 1,
		section = COMBINED
	)
	default CombinedWithdraw combinedWithdraw()
	{
		return CombinedWithdraw.POTION_STORAGE_FIRST;
	}

	@ConfigItem(
		keyName = "instantCounts",
		name = "Instant withdraw counts",
		description = "Count a withdrawal off a stored potion's stack as soon as it is clicked, instead of a moment later when the server confirms it",
		position = 3
	)
	default boolean instantCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnDeposits",
		name = "Warn when deposits skip storage",
		description = "Say so in the chat when the bank's \"Always deposit to potion storage\" is off: the first time the bank is opened each login, and when a potion you deposit goes into the bank instead of potion storage",
		position = 4
	)
	default boolean warnDeposits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSidebar",
		name = "Sidebar panel",
		description = "Add a Potion Storage button to the sidebar, which opens a panel of every potion for choosing where each is shown",
		position = 5
	)
	default boolean showSidebar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sidebarCount",
		name = "Sidebar counts",
		description = "Count each potion in the sidebar panel in full potions or in doses. Hovering a potion shows the other",
		position = 6
	)
	default SidebarCount sidebarCount()
	{
		return SidebarCount.POTIONS;
	}

	// The sidebar panel's choices: the potions hidden in each place (PotionVisibility), as lists of item ids.

	@ConfigItem(keyName = "hiddenInBank", name = "", description = "", hidden = true)
	default String hiddenInBank()
	{
		return "";
	}

	@ConfigItem(keyName = "hiddenInTags", name = "", description = "", hidden = true)
	default String hiddenInTags()
	{
		return "";
	}

	@ConfigItem(keyName = "hiddenInSearch", name = "", description = "", hidden = true)
	default String hiddenInSearch()
	{
		return "";
	}
}
