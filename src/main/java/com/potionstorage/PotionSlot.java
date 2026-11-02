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
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * One potion in the sidebar panel's bank-like grid: drawn as the bank draws an item, with its count in the
 * game's stack colours. Faded, with a crossed-out eye, where the open tab hides it. A click shows or hides it
 * in that tab's place; a right-click offers every place by name.
 */
final class PotionSlot extends JComponent
{
	static final int HEIGHT = 38;

	private static final float HIDDEN_ALPHA = 0.3f;
	private static final Color HOVER = new Color(255, 255, 255, 20);
	private static final Color STACK_YELLOW = new Color(255, 255, 0);
	private static final Color STACK_WHITE = Color.WHITE;
	private static final Color STACK_GREEN = new Color(0, 255, 128);
	private static final Color BADGE = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color BADGE_EYE = new Color(224, 224, 224);

	/** What the slot asks of the panel that keeps the settings. */
	interface Owner
	{
		/** The place the open tab is about. */
		Where tab();

		Set<Where> placesShown(int potionId);

		void set(int potionId, Set<Where> places, boolean show);

		/** Count in doses; otherwise in full potions. */
		boolean countDoses();
	}

	final VisibilityPanel.Potion potion;
	private final Owner owner;
	private final BufferedImage icon;
	private boolean hover;

	PotionSlot(VisibilityPanel.Potion potion, AsyncBufferedImage icon, Owner owner)
	{
		this.potion = potion;
		this.owner = owner;
		this.icon = icon;
		icon.onLoaded(this::repaint);
		setPreferredSize(new Dimension(36, HEIGHT));
		setToolTipText("");
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showMenu(e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showMenu(e);
				}
				else if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint()))
				{
					Where tab = owner.tab();
					owner.set(potion.id, EnumSet.of(tab), !shown());
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private boolean shown()
	{
		return owner.placesShown(potion.id).contains(owner.tab());
	}

	private void showMenu(MouseEvent e)
	{
		Set<Where> shown = owner.placesShown(potion.id);
		JPopupMenu menu = new JPopupMenu();
		for (Where w : Where.values())
		{
			JCheckBoxMenuItem item = new JCheckBoxMenuItem("Show in " + w.phrase, shown.contains(w));
			item.addActionListener(ev -> owner.set(potion.id, EnumSet.of(w), item.isSelected()));
			menu.add(item);
		}
		menu.addSeparator();
		JMenuItem all = new JMenuItem("Show everywhere");
		all.addActionListener(ev -> owner.set(potion.id, EnumSet.allOf(Where.class), true));
		JMenuItem none = new JMenuItem("Hide everywhere");
		none.addActionListener(ev -> owner.set(potion.id, EnumSet.allOf(Where.class), false));
		menu.add(all);
		menu.add(none);
		menu.show(this, e.getX(), e.getY());
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		boolean doses = owner.countDoses();
		String phrase = owner.tab().phrase;
		return "<html><b>" + potion.name + "</b><br>"
			+ (doses ? potion.dosesText() : potion.potionsText())
			+ " <font color='#a5a5a5'>(" + (doses ? potion.potionsText() : potion.dosesText()) + ")</font><br>"
			+ (shown() ? "Click to hide in " + phrase : "Hidden in " + phrase + ": click to show")
			+ "<br><font color='#a5a5a5'>Right-click for every place</font></html>";
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (hover)
			{
				g.setColor(HOVER);
				g.fillRoundRect(0, 0, getWidth(), getHeight(), 5, 5);
			}
			boolean shown = shown();
			Composite was = g.getComposite();
			if (!shown)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, HIDDEN_ALPHA));
			}
			g.drawImage(icon, (getWidth() - icon.getWidth()) / 2, getHeight() - icon.getHeight() - 2, null);
			paintCount(g);
			g.setComposite(was);
			if (!shown)
			{
				int d = 14;
				int x = getWidth() - d - 1;
				int y = getHeight() - d - 1;
				g.setColor(BADGE);
				g.fillOval(x, y, d, d);
				Glyphs.hiddenEye(g, x + d / 2.0, y + d / 2.0, BADGE_EYE);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	/** The count as the game writes a stack's: yellow, white in thousands from 100K, green in millions from 10M. */
	private void paintCount(Graphics2D g)
	{
		int n = owner.countDoses() ? potion.doses : potion.fullPotions();
		String text;
		Color color;
		if (n >= 10_000_000)
		{
			text = n / 1_000_000 + "M";
			color = STACK_GREEN;
		}
		else if (n >= 100_000)
		{
			text = n / 1000 + "K";
			color = STACK_WHITE;
		}
		else
		{
			// Doses short of one whole potion: there is some, so never "0".
			text = n == 0 ? "<1" : String.valueOf(n);
			color = STACK_YELLOW;
		}
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setFont(FontManager.getRunescapeSmallFont());
		int y = g.getFontMetrics().getAscent() - 1;
		g.setColor(Color.BLACK);
		g.drawString(text, 2, y + 1);
		g.setColor(color);
		g.drawString(text, 1, y);
	}
}
