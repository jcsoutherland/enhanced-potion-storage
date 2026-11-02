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

import com.potionstorage.BankArrangement.Drop;
import com.potionstorage.BankArrangement.Entry;
import com.potionstorage.BankArrangement.Pos;
import static com.potionstorage.BankWidgets.ITEM_CHILDREN;
import static com.potionstorage.BankWidgets.SECTIONS;
import static com.potionstorage.BankWidgets.STRIP_BASE;
import static com.potionstorage.BankWidgets.TAB_BUTTON_BASE;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Drags in the bank and in a search, decided at release - after the Bank Tags plugin has had its look, so a
 * drop on a tag tab still tags the item.
 * <p>
 * A drag between two real items is the game's own and goes ahead untouched; the saved order follows it. One
 * involving a stored stack is handled here alone - there is nothing in the server's bank to move - except a
 * real item dropped on a stored stack in another tab, which has to change tabs too: that drop is handed to the
 * game as a drop on that tab's button, which is how a player moves an item into a tab.
 * <p>
 * What is dragged is taken at the press, not the release: a stored stack is drawn on whichever spare slot is
 * next, so a bank redrawn during the drag can have put another potion on the slot being held.
 */
final class BankDrags
{
	private final Client client;
	private final SavedOrder savedOrder;
	private final Runnable relayout;

	private boolean pressing;
	/** The release of the current press has been acted on: the event fires every cycle a widget is held. */
	private boolean releaseSeen;
	/** The order entry under the press, from the bank as drawn then. */
	private Entry pressed;

	BankDrags(Client client, SavedOrder savedOrder, Runnable relayout)
	{
		this.client = client;
		this.savedOrder = savedOrder;
		this.relayout = relayout;
	}

	void reset()
	{
		pressing = false;
		releaseSeen = false;
		pressed = null;
	}

	/**
	 * Follow the button: while it is down, take what the press is on, once; when it comes up, true - once per
	 * release, as a plain click is a hold too.
	 */
	boolean released(Frame frame)
	{
		if (client.getMouseCurrentButton() != 0)
		{
			releaseSeen = false;
			if (!pressing)
			{
				pressing = true;
				Widget src = client.getDraggedWidget();
				pressed = src != null && src.getId() == InterfaceID.Bankmain.ITEMS ? frame.entryOn(src.getIndex()) : null;
			}
			return false;
		}
		pressing = false;
		if (releaseSeen)
		{
			return false;
		}
		releaseSeen = true;
		return true;
	}

	/** A bank item released on {@code client.getDraggedOnWidget()}. */
	void drop(Frame frame, Widget src)
	{
		Entry entry = pressed != null ? pressed : frame.entryOn(src.getIndex());
		pressed = null;
		Widget dst = client.getDraggedOnWidget();
		if (frame.mode == Frame.Mode.NONE || entry == null || src.getId() != InterfaceID.Bankmain.ITEMS
			|| !client.isDraggingWidget() || dst == src)
		{
			return;
		}
		Pos from = frame.mode == Frame.Mode.ARRANGE || frame.mode == Frame.Mode.SEARCH ? frame.posOf(entry) : null;
		if (from != null && dst != null && dst.getId() == InterfaceID.Bankmain.ITEMS)
		{
			if (dst.getIndex() < ITEM_CHILDREN)
			{
				dropOnItem(frame, entry, from, frame.posOf(frame.entryOn(dst.getIndex())), frame.isStored(dst.getIndex()));
			}
			else if (dst.getIndex() >= STRIP_BASE && dst.getIndex() < STRIP_BASE + SECTIONS)
			{
				dropOnStrip(frame, entry, from, dst.getIndex() - STRIP_BASE);
			}
		}
		else if (from != null && dst != null && dst.getId() == InterfaceID.Bankmain.TABS && entry.stored)
		{
			dropOnTabButton(frame, from, dst.getIndex() - TAB_BUTTON_BASE);
		}
		// Whatever became of it here, a stored stack is nothing the server can move.
		if (entry.stored)
		{
			block();
		}
	}

	/**
	 * Onto another stack: a swap, or an insert, by the bank's insert-mode setting. The bank's insert puts the
	 * stack into the target's place; a search's puts it just in front of the target.
	 */
	private void dropOnItem(Frame frame, Entry entry, Pos from, Pos to, boolean ontoStored)
	{
		if (to == null)
		{
			return;
		}
		boolean search = frame.mode == Frame.Mode.SEARCH;
		Drop insert = search ? Drop.IN_FRONT : Drop.INSERT;
		Drop how = client.getVarbitValue(VarbitID.BANK_INSERTMODE) == 1 ? insert : Drop.SWAP;
		if (!entry.stored && !ontoStored)
		{
			// Real onto real: the game's own move - let it go. In the bank, follow it here at once; in a search,
			// where the server moves it among stacks not on show, the order follows the server's.
			if (!search)
			{
				savedOrder.move(frame.drawn, from, to, how);
			}
		}
		else if (!entry.stored && from.section != to.section)
		{
			// A real item into a stored stack's place in another tab: the server moves it into that tab.
			Widget button = tabButton(to.section);
			if (button == null)
			{
				block();
				return;
			}
			savedOrder.move(frame.drawn, from, to, how);
			client.setDraggedOnWidget(button);
		}
		else
		{
			// A swap would send a real item to a tab the server has not moved it to; take its place instead.
			boolean swapsReal = how == Drop.SWAP && !ontoStored && from.section != to.section;
			savedOrder.move(frame.drawn, from, to, swapsReal ? insert : how);
			block();
			relayout.run();
		}
	}

	/** Onto the space after a tab's last stack: to the end of that tab. */
	private void dropOnStrip(Frame frame, Entry entry, Pos from, int section)
	{
		if (entry.stored)
		{
			savedOrder.moveToEnd(frame.drawn, from, section);
			relayout.run();
		}
		else if (section == from.section)
		{
			// The game moves it to the end of its tab too. Into another tab, it is the server's move to follow.
			savedOrder.moveToEnd(frame.drawn, from, section);
		}
	}

	/** A stored stack onto a tab's button: to the end of that tab, if the tab exists. */
	private void dropOnTabButton(Frame frame, Pos from, int tab)
	{
		if (tab == BankArrangement.MAIN || (tab > 0 && tab < SECTIONS && frame.tabSizes[tab] > 0))
		{
			savedOrder.moveToEnd(frame.drawn, from, tab);
			relayout.run();
		}
		else if (tab > 0 && tab < SECTIONS)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"A potion from potion storage can't start a tab - put a bank item in it first.", null);
		}
	}

	private void block()
	{
		client.setDraggedOnWidget(null);
	}

	/** The tab bar button for {@code section}: "View all items" for the untabbed part. */
	private Widget tabButton(int section)
	{
		Widget tabs = client.getWidget(InterfaceID.Bankmain.TABS);
		return tabs == null ? null : tabs.getChild(TAB_BUTTON_BASE + section);
	}
}
