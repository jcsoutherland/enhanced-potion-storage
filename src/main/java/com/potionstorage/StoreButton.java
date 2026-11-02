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

import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * The potion storage button at the foot of the bank's tab column, hidden when the player would rather not
 * have it. It sits in a small pop-out tab that, for a group ironman, also holds the group storage button: with
 * that one absent the whole tab goes, so no empty frame is left - and the Bank Tags plugin, which stops its
 * tag tabs above a shown pop-out tab, gets the room. Only what this hid is shown again. Client thread only.
 */
final class StoreButton
{
	private final Client client;
	/** The widgets this hid, to show again: no others. */
	private final Set<Integer> hidden = new LinkedHashSet<>();

	StoreButton(Client client)
	{
		this.client = client;
	}

	/** Hide the button, or show again what was hidden. Run before the Bank Tags plugin lays out its tabs. */
	void apply(boolean hide)
	{
		if (!hide)
		{
			restore();
			return;
		}
		hide(InterfaceID.Bankmain.POTIONSTORE_BUTTON);
		Widget group = client.getWidget(InterfaceID.Bankmain.GIM_STORAGE);
		if (group == null || group.isSelfHidden())
		{
			hide(InterfaceID.Bankmain.STORAGE_POPUP_TAB);
		}
	}

	void restore()
	{
		for (int id : hidden)
		{
			Widget w = client.getWidget(id);
			if (w != null)
			{
				w.setHidden(false);
			}
		}
		hidden.clear();
	}

	/** The bank closed: its widgets went with it. */
	void forget()
	{
		hidden.clear();
	}

	private void hide(int id)
	{
		Widget w = client.getWidget(id);
		if (w != null && !w.isSelfHidden())
		{
			w.setHidden(true);
			hidden.add(id);
		}
	}
}
