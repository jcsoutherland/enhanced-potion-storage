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

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * The bank interface as the game builds it, measured from the live client: the item list's children, the
 * grid its stacks sit on, and the tab bar.
 * <p>
 * The item list ({@code Bankmain.ITEMS}) has one child per bank slot, then 9 separator lines (one above each
 * tab), 9 "Tab N" headings (shown in a search), then 10 transparent strips - one per section - that take a
 * drop "at the end of this tab".
 */
final class BankWidgets
{
	static final int ITEM_CHILDREN = 1410;
	static final int SEPARATOR_BASE = 1410;
	static final int LABEL_BASE = 1419;
	static final int STRIP_BASE = 1428;
	static final int SECTIONS = 10;
	static final int SEPARATOR_W = 374;
	/** On the tab bar: "View all items" is child 10, tab k is 10 + k. */
	static final int TAB_BUTTON_BASE = 10;
	/** The bank's current tab while the potion store's panel is the one on show. */
	static final int BANKTAB_POTIONSTORE = 15;

	static final int ITEM_W = 36;
	static final int ITEM_H = 32;
	static final int ITEM_START_X = 51;
	static final int CELL_W = 48;
	static final int ROW = 36;
	static final int PER_ROW = 8;
	/** From a section's next row to the next section's first: the separator line sits 5 in. */
	static final int SECTION_GAP = 12;
	/** A separator line sits this far above the first row of the section under it. */
	static final int LINE_ABOVE = 7;

	static final String WITHDRAW = "Withdraw-";
	static final String WITHDRAW_ALL_BUT_ONE = WITHDRAW + "All-but-1";
	/** How faded a stack with none left is drawn, like the game's placeholders. */
	static final int EMPTY_OPACITY = 120;
	/** A bank placeholder's option, and where the game puts it. */
	static final String RELEASE = "Release";
	static final int RELEASE_OP = 7;

	/** A bank stack's withdraw options are its ops 1 to 8; 9 and 10 are Placeholder and Examine. */
	static final int LAST_WITHDRAW_OP = 8;
	/** A stored potion's Doses submenu, in place of Examine; its sub-ops are 5 + the dose. */
	static final int DOSES_OP = 10;
	static final String DOSES = "Doses";

	static final int[] TAB_VARBITS = {
		VarbitID.BANK_TAB_1, VarbitID.BANK_TAB_2, VarbitID.BANK_TAB_3, VarbitID.BANK_TAB_4, VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6, VarbitID.BANK_TAB_7, VarbitID.BANK_TAB_8, VarbitID.BANK_TAB_9,
	};

	private BankWidgets()
	{
	}

	/** Rows {@code count} stacks take. */
	static int rows(int count)
	{
		return (count + PER_ROW - 1) / PER_ROW;
	}

	/** The x of the {@code i}th cell of a section. */
	static int cellX(int i)
	{
		return ITEM_START_X + (i % PER_ROW) * CELL_W;
	}

	/** The y of the {@code i}th cell of a section whose first row is at {@code top}. */
	static int cellY(int top, int i)
	{
		return top + (i / PER_ROW) * ROW;
	}

	/** A click on a widget's own option, as opposed to an option a plugin added. */
	static boolean isWidgetOp(MenuAction action)
	{
		return action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY;
	}

	static void place(Widget c, int x, int y)
	{
		c.setOriginalX(x);
		c.setOriginalY(y);
		c.setHidden(false);
		c.revalidate();
	}
}
