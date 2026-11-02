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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The sidebar panel's small pictures of the three places a potion can be shown: a chest for the bank, a tag
 * for tag tabs, a magnifying glass for searches. Drawn, not loaded, so they stay sharp at any scale; each fits
 * a box {@link #SIZE} across, centred on the point given.
 */
final class Glyphs
{
	static final int SIZE = 10;

	private Glyphs()
	{
	}

	static void paint(Graphics2D g0, Where where, double cx, double cy, Color color)
	{
		Graphics2D g = (Graphics2D) g0.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.translate(cx - SIZE / 2.0, cy - SIZE / 2.0);
			g.setColor(color);
			g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			switch (where)
			{
				case BANK:
					chest(g);
					break;
				case TAGS:
					tag(g);
					break;
				default:
					magnifier(g);
					break;
			}
		}
		finally
		{
			g.dispose();
		}
	}

	/** A chest: a domed lid over a box, and a clasp where they meet. */
	private static void chest(Graphics2D g)
	{
		Path2D lid = new Path2D.Double();
		lid.moveTo(0.8, 4.6);
		lid.lineTo(0.8, 3.2);
		lid.quadTo(0.8, 1.2, 3, 1.2);
		lid.lineTo(7, 1.2);
		lid.quadTo(9.2, 1.2, 9.2, 3.2);
		lid.lineTo(9.2, 4.6);
		g.draw(lid);
		g.draw(new RoundRectangle2D.Double(0.8, 4.6, 8.4, 4.6, 1.5, 1.5));
		g.fill(new RoundRectangle2D.Double(4, 3.6, 2, 2.6, 0.8, 0.8));
	}

	/** A luggage tag pointing left, with its hole. */
	private static void tag(Graphics2D g)
	{
		Path2D t = new Path2D.Double();
		t.moveTo(0.7, 5);
		t.lineTo(3.6, 1.4);
		t.lineTo(9.3, 1.4);
		t.lineTo(9.3, 8.6);
		t.lineTo(3.6, 8.6);
		t.closePath();
		g.draw(t);
		g.fill(new Ellipse2D.Double(3.4, 4.1, 1.8, 1.8));
	}

	/** A crossed-out eye: hidden. */
	static void hiddenEye(Graphics2D g0, double cx, double cy, Color color)
	{
		Graphics2D g = (Graphics2D) g0.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.translate(cx - SIZE / 2.0, cy - SIZE / 2.0);
			g.setColor(color);
			g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Path2D eye = new Path2D.Double();
			eye.moveTo(1, 5);
			eye.quadTo(5, -0.6, 9, 5);
			eye.quadTo(5, 10.6, 1, 5);
			eye.closePath();
			g.draw(eye);
			g.draw(new Ellipse2D.Double(3.8, 3.8, 2.4, 2.4));
			g.draw(new Line2D.Double(1.6, 1.6, 8.4, 8.4));
		}
		finally
		{
			g.dispose();
		}
	}

	/** A magnifying glass, handle down and right. */
	private static void magnifier(Graphics2D g)
	{
		g.draw(new Ellipse2D.Double(0.9, 0.9, 5.8, 5.8));
		g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Double(6.1, 6.1, 9, 9));
	}
}
