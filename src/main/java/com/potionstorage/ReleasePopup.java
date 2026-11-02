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

import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

/**
 * The "Release all placeholders?" confirmation, for when only this plugin has placeholders to release. The
 * game's own is opened by the server, which opens it only for a bank with placeholders of its own; this one is
 * drawn in the same place - the bank's pop-up layer - from the same sprites, fonts and measurements, so it reads
 * the same. Its buttons are this plugin's: nothing of it goes to the server. Client thread only.
 */
final class ReleasePopup
{
	private static final String TITLE = "Release all placeholders?";
	private static final String MESSAGE = "This option will release <col=ffb83f>all</col> the placeholders throughout your "
		+ "<col=ffb83f>whole bank</col>.<br><br>Are you sure?";
	private static final int TEXT_COLOR = 0xff981f;
	/** The game's message spaces its lines wider than the font does. */
	private static final int MESSAGE_LINE_HEIGHT = 15;
	/** A button's frame, and the one it takes under the mouse: corners, then edges. */
	private static final int FRAME = 913;
	private static final int FRAME_HOVER = 921;
	/** The game's bold and plain 12-point fonts, as its pop-up uses them. */
	private static final int TITLE_FONT = FontID.BOLD_12;
	private static final int BODY_FONT = FontID.PLAIN_12;

	private final Client client;
	private Widget cancel;
	private Widget confirm;

	ReleasePopup(Client client)
	{
		this.client = client;
	}

	boolean isOpen()
	{
		return confirm != null && client.getWidget(InterfaceID.Bankmain.POPUP) != null;
	}

	void open()
	{
		Widget layer = client.getWidget(InterfaceID.Bankmain.POPUP);
		if (layer == null)
		{
			return;
		}
		close();
		// Positions are the game's, within the same layer: its window at (6, 35), the box at (21, 338).
		Widget shade = rect(layer, 6, 35, 476, 751, 0x000000, 150, true);
		shade.setNoClickThrough(true);

		sprite(layer, 297, true, 22, 339, 444, 143);
		text(layer, TITLE, TITLE_FONT, 27, 344, 434, 24);
		sprite(layer, 310, true, 21, 338, 25, 30);
		sprite(layer, 311, true, 442, 338, 25, 30);
		sprite(layer, 312, true, 21, 453, 25, 30);
		sprite(layer, 313, true, 442, 453, 25, 30);
		sprite(layer, 172, true, 6, 368, 36, 85);
		sprite(layer, 315, true, 446, 368, 36, 85);
		sprite(layer, 314, true, 46, 323, 396, 36);
		sprite(layer, 173, true, 46, 462, 396, 36);
		sprite(layer, 2546, true, 26, 352, 436, 26);

		// The list's border: two outlines over the box's own background.
		rect(layer, 31, 378, 426, 95, 0x4d4d4d, 0, false);
		rect(layer, 32, 379, 424, 93, 0x171717, 0, false);
		Widget message = text(layer, MESSAGE, BODY_FONT, 34, 380, 403, 46);
		message.setXTextAlignment(WidgetTextAlignment.LEFT);
		message.setYTextAlignment(WidgetTextAlignment.TOP);
		message.setLineHeight(MESSAGE_LINE_HEIGHT);
		message.revalidate();
		cancel = button(layer, 93, 436, "<col=ff0000>Cancel</col>", "Cancel");
		confirm = button(layer, 267, 436, "<col=0dc10d>Release them all</col>", "Release them all");

		// The scroll bar the game's list shows, with nothing to scroll.
		sprite(layer, 792, false, 439, 396, 16, 59);
		sprite(layer, 790, false, 439, 396, 16, 59);
		sprite(layer, 789, false, 439, 396, 16, 5);
		sprite(layer, 791, false, 439, 450, 16, 5);
		sprite(layer, 773, false, 439, 380, 16, 16);
		sprite(layer, 788, false, 439, 455, 16, 16);
		layer.revalidate();
	}

	void close()
	{
		Widget layer = client.getWidget(InterfaceID.Bankmain.POPUP);
		if (layer != null && confirm != null)
		{
			layer.deleteAllChildren();
			layer.revalidate();
		}
		cancel = null;
		confirm = null;
	}

	/** True for a click on "Release them all", false for one on "Cancel"; null for any other click. */
	Boolean answer(MenuOptionClicked e)
	{
		if (!isOpen() || e.getParam1() != InterfaceID.Bankmain.POPUP)
		{
			return null;
		}
		if (e.getParam0() == confirm.getIndex())
		{
			return true;
		}
		return e.getParam0() == cancel.getIndex() ? false : null;
	}

	/**
	 * A framed button as the game draws one: eight frame parts round a clear middle, and a centred label. Under
	 * the mouse its frame darkens, as the game's does.
	 */
	private Widget button(Widget layer, int x, int y, String label, String option)
	{
		// Sprite FRAME + n, at x, y, w, h: four corners, then the left, top, right and bottom edges.
		int[][] parts = {
			{0, 0, 0, 9, 9}, {1, 103, 0, 9, 9}, {2, 0, 21, 9, 9}, {3, 103, 21, 9, 9},
			{4, 0, 9, 9, 12}, {5, 9, 0, 94, 9}, {6, 103, 9, 9, 12}, {7, 9, 21, 94, 9}};
		Widget[] frame = new Widget[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			int[] f = parts[i];
			frame[i] = sprite(layer, FRAME + f[0], true, x + f[1], y + f[2], f[3], f[4]);
		}
		Widget w = text(layer, label, BODY_FONT, x, y, 112, 30);
		w.setAction(0, option);
		w.setHasListener(true);
		w.setOnOpListener((JavaScriptCallback) ev ->
		{
		});
		w.setOnMouseOverListener((JavaScriptCallback) ev -> frame(frame, FRAME_HOVER));
		w.setOnMouseLeaveListener((JavaScriptCallback) ev -> frame(frame, FRAME));
		return w;
	}

	private static void frame(Widget[] frame, int base)
	{
		for (int i = 0; i < frame.length; i++)
		{
			frame[i].setSpriteId(base + i);
		}
	}

	private static Widget sprite(Widget layer, int spriteId, boolean tiling, int x, int y, int w, int h)
	{
		Widget c = layer.createChild(-1, WidgetType.GRAPHIC);
		c.setSpriteId(spriteId);
		c.setSpriteTiling(tiling);
		return place(c, x, y, w, h);
	}

	private static Widget rect(Widget layer, int x, int y, int w, int h, int color, int opacity, boolean filled)
	{
		Widget c = layer.createChild(-1, WidgetType.RECTANGLE);
		c.setFilled(filled);
		c.setTextColor(color);
		c.setOpacity(opacity);
		return place(c, x, y, w, h);
	}

	private static Widget text(Widget layer, String text, int font, int x, int y, int w, int h)
	{
		Widget c = layer.createChild(-1, WidgetType.TEXT);
		c.setText(text);
		c.setFontId(font);
		c.setTextColor(TEXT_COLOR);
		c.setTextShadowed(true);
		c.setXTextAlignment(WidgetTextAlignment.CENTER);
		c.setYTextAlignment(WidgetTextAlignment.CENTER);
		return place(c, x, y, w, h);
	}

	private static Widget place(Widget c, int x, int y, int w, int h)
	{
		c.setOriginalX(x);
		c.setOriginalY(y);
		c.setOriginalWidth(w);
		c.setOriginalHeight(h);
		c.revalidate();
		return c;
	}
}
