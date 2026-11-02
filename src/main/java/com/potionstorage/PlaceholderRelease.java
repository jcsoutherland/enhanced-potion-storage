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

import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.Text;

/**
 * The bank menu's "Release all placeholders", made to count and release this plugin's placeholders with the
 * bank's own. While there are any of ours, the button is drawn as the game draws it with placeholders to
 * release - its red frame, its orange text, the count in its text and its option - learned from the game
 * whenever it shows it, and as seen in it otherwise. Confirming in the game's "Release all placeholders?"
 * pop-up releases ours too, places and all. The server opens that pop-up only for a bank with placeholders of
 * its own, so with only ours the click opens {@link ReleasePopup}, drawn the same, instead. With none of ours
 * left, the game redraws the button itself. Client thread only.
 */
final class PlaceholderRelease
{
	private static final String BUTTON_TEXT = "Release all placeholders";
	private static final String POPUP_TITLE = "Release all placeholders?";
	private static final String CONFIRM = "Release them all";
	/** The button's frame: its first nine parts, corners and edges. */
	private static final int FRAME_PARTS = 9;

	private final Client client;
	private final SavedOrder savedOrder;
	private final BooleanSupplier enabled;
	private final Runnable relayout;
	private final ReleasePopup popup;

	/** The bank's own placeholders, as last counted. */
	private int bankPlaceholders;
	/** The button is as this drew it, not as the game did. */
	private boolean counted;

	// The button as the game draws it with placeholders to release.
	private int[] enabledFrame = {1150, 1151, 1152, 1153, 1154, 1155, 1156, 1157, 1158};
	private int enabledColor = 0xff981f;

	PlaceholderRelease(Client client, SavedOrder savedOrder, BooleanSupplier enabled, Runnable relayout)
	{
		this.client = client;
		this.savedOrder = savedOrder;
		this.enabled = enabled;
		this.relayout = relayout;
		this.popup = new ReleasePopup(client);
	}

	void bankChanged(ItemContainer bank)
	{
		int n = 0;
		for (Item item : bank.getItems())
		{
			if (item.getId() >= 0)
			{
				ItemComposition def = client.getItemDefinition(item.getId());
				if (def.getPlaceholderTemplateId() != -1 && def.getPlaceholderId() != -1)
				{
					n++;
				}
			}
		}
		bankPlaceholders = n;
	}

	private int ours()
	{
		return enabled.getAsBoolean() ? savedOrder.placeholders().size() : 0;
	}

	/** The button, with ours in it. Each frame the bank is open: it does nothing until the game redraws it. */
	void syncButton()
	{
		Widget button = client.getWidget(InterfaceID.Bankmain.RELEASE_PLACEHOLDERS);
		Widget[] parts = button == null ? null : button.getDynamicChildren();
		Widget text = textOf(parts);
		if (text == null)
		{
			return;
		}
		int ours = ours();
		if (ours == 0)
		{
			if (counted)
			{
				counted = false;
				redrawByGame(button);
			}
			return;
		}
		String label = BUTTON_TEXT + " (" + (bankPlaceholders + ours) + ")";
		if (counted && label.equals(text.getText()))
		{
			return;
		}
		if (bankPlaceholders > 0)
		{
			// The game's own drawing, with placeholders to release: the look to copy.
			learn(button, parts, text);
		}
		text.setText(label);
		text.setTextColor(enabledColor);
		for (int i = 0; i < FRAME_PARTS && i < parts.length; i++)
		{
			if (parts[i] != null)
			{
				parts[i].setSpriteId(enabledFrame[i]);
			}
		}
		button.setAction(0, label);
		counted = true;
	}

	private void learn(Widget button, Widget[] parts, Widget text)
	{
		if (parts.length >= FRAME_PARTS)
		{
			int[] frame = new int[FRAME_PARTS];
			for (int i = 0; i < FRAME_PARTS; i++)
			{
				frame[i] = parts[i] == null ? enabledFrame[i] : parts[i].getSpriteId();
			}
			enabledFrame = frame;
		}
		enabledColor = text.getTextColor();
	}

	/** Have the game draw the button again, as it does when the bank changes. */
	private void redrawByGame(Widget button)
	{
		Object[] redraw = button.getOnInvTransmitListener();
		if (redraw == null || redraw.length == 0)
		{
			return;
		}
		for (Object arg : redraw)
		{
			// An argument the game fills in from an event there is none of here.
			if (arg instanceof Integer && (Integer) arg <= Integer.MIN_VALUE + 16)
			{
				return;
			}
		}
		client.runScript(redraw);
	}

	/** The button's own text, the one text component among its parts. */
	private static Widget textOf(Widget[] parts)
	{
		if (parts != null)
		{
			for (Widget w : parts)
			{
				if (w != null && w.getType() == WidgetType.TEXT)
				{
					return w;
				}
			}
		}
		return null;
	}

	/**
	 * The button, with only ours to release: our pop-up, and nothing to the server. An answer in our pop-up. And
	 * "Release them all" in the game's: ours go too.
	 */
	void clicked(MenuOptionClicked e)
	{
		Boolean answer = popup.answer(e);
		if (answer != null)
		{
			e.consume();
			client.playSoundEffect(SoundEffectID.UI_BOOP);
			popup.close();
			if (answer)
			{
				releaseAll();
			}
		}
		else if (e.getParam1() == InterfaceID.Bankmain.RELEASE_PLACEHOLDERS && bankPlaceholders == 0 && ours() > 0)
		{
			e.consume();
			client.playSoundEffect(SoundEffectID.UI_BOOP);
			popup.open();
		}
		else if (e.getParam1() == InterfaceID.Popupoverlay.BUTTON_1 && e.getMenuOption() != null
			&& CONFIRM.equals(Text.removeTags(e.getMenuOption())) && popupIsRelease())
		{
			releaseAll();
		}
	}

	private void releaseAll()
	{
		savedOrder.release(savedOrder.placeholders());
		relayout.run();
	}

	/** The game's pop-up is a general one: it is this one only under this title. */
	private boolean popupIsRelease()
	{
		Widget frame = client.getWidget(InterfaceID.Popupoverlay.FRAME);
		Widget[] parts = frame == null ? null : frame.getDynamicChildren();
		if (parts != null)
		{
			for (Widget w : parts)
			{
				if (w != null && POPUP_TITLE.equals(Text.removeTags(w.getText() == null ? "" : w.getText())))
				{
					return true;
				}
			}
		}
		return false;
	}
}
