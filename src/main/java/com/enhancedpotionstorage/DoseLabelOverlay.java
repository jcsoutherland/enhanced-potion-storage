/*
 * Copyright (c) 2026, jcsoutherland
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
package com.enhancedpotionstorage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.Supplier;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Each dose slot's dose, in the bottom-left corner of its cell: green on the slot at potion storage's withdraw
 * dose - the one a click withdraws from - white on the others, where a click sets the withdraw dose instead.
 */
final class DoseLabelOverlay extends WidgetItemOverlay
{
	static final Color ACTIVE = new Color(0, 255, 0);
	private static final Color OTHER = Color.WHITE;

	/** What the last build of the bank drew. */
	private final Supplier<Frame> frame;

	DoseLabelOverlay(Supplier<Frame> frame)
	{
		this.frame = frame;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		Widget w = item.getWidget();
		if (w == null || w.getId() != InterfaceID.Bankmain.ITEMS)
		{
			return;
		}
		PotionStore.Stack s = frame.get().stackOn(w.getIndex());
		if (s == null || !s.hasDoseChoice())
		{
			return;
		}
		Rectangle b = item.getCanvasBounds();
		String text = String.valueOf(s.dose);
		g.setFont(FontManager.getRunescapeSmallFont());
		int x = b.x;
		int y = b.y + b.height;
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(s.active && !s.placeholder ? ACTIVE : OTHER);
		g.drawString(text, x, y);
	}
}
