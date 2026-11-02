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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.EqualsAndHashCode;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The sidebar panel: a tab for each place a stored potion can be shown - the bank, tag tabs, searches - and,
 * under it, every stored potion laid out the way the bank lays out items. Click a potion to hide it in the
 * open tab's place, or to bring it back; right-click it for every place at once. The plugin's own on/off
 * setting for each place still applies on top, and the panel says so when one is off.
 * <p>
 * Swing's thread only. What the game knows - the potions and their doses - comes in through {@link #update}.
 */
final class VisibilityPanel extends PluginPanel
{
	private static final int COLUMNS = 5;
	/** Swing sizes an HTML label's width in its own units, a little wider than the pixels it fills. */
	private static final int TEXT_WIDTH = 165;

	private static final Color BANK_FILL = new Color(62, 53, 41);
	private static final Color BANK_EDGE = new Color(43, 36, 25);
	private static final Color BANK_LINE = new Color(90, 77, 58);
	private static final Color TAB_ON = new Color(58, 58, 58);
	private static final Color TAB_HOVER = new Color(51, 51, 51);
	private static final Color SUBTLE = new Color(138, 138, 138);
	private static final Color FAINT = new Color(111, 111, 111);

	/** One potion kind, as the panel draws it. */
	@EqualsAndHashCode
	static final class Potion
	{
		final int id;
		final int iconId;
		final String name;
		final int doses;
		/** The doses in one whole potion. */
		final int fullDose;
		final boolean unfinished;

		Potion(int id, int iconId, String name, int doses, int fullDose, boolean unfinished)
		{
			this.id = id;
			this.iconId = iconId;
			this.name = name;
			this.doses = doses;
			this.fullDose = Math.max(fullDose, 1);
			this.unfinished = unfinished;
		}

		int fullPotions()
		{
			return doses / fullDose;
		}

		/** "262 potions + 3 doses", or "262 potions" when there are no doses over. */
		String potionsText()
		{
			int left = doses - fullPotions() * fullDose;
			return plural(fullPotions(), "potion") + (left > 0 ? " + " + plural(left, "dose") : "");
		}

		String dosesText()
		{
			return plural(doses, "dose");
		}

		private static String plural(int n, String one)
		{
			return String.format("%,d %s%s", n, one, n == 1 ? "" : "s");
		}
	}

	/** The plugin's settings that turn a place off whatever the panel says, and how it counts. */
	@EqualsAndHashCode
	static final class Switches
	{
		final boolean inBank;
		final boolean inTagTabs;
		final boolean inSearch;
		final boolean unfinished;
		/** Count in doses rather than full potions. */
		final boolean countDoses;
		/** The bank's "Always deposit to potion storage" is on. */
		final boolean depositsToStore;

		Switches(boolean inBank, boolean inTagTabs, boolean inSearch, boolean unfinished, boolean countDoses,
			boolean depositsToStore)
		{
			this.inBank = inBank;
			this.inTagTabs = inTagTabs;
			this.inSearch = inSearch;
			this.unfinished = unfinished;
			this.countDoses = countDoses;
			this.depositsToStore = depositsToStore;
		}
	}

	/** An item's icon, by id. */
	private final IntFunction<AsyncBufferedImage> icons;
	private final PotionVisibility visibility;
	/** Asked when the panel is opened: send the potions again, as they are now. */
	private final Runnable onOpen;
	/** Save the count setting: true for doses. */
	private final Consumer<Boolean> setCountDoses;

	private final PotionSlot.Owner slotOwner;
	private final CountSwitch countSwitch = new CountSwitch();
	private final TabBar tabBar = new TabBar();
	private final JLabel depositWarning = new JLabel();
	private final JLabel hint = new JLabel();
	private final JLabel notice = new JLabel();
	private final BankArea bank = new BankArea();
	private final JPanel finishedGrid = grid();
	private final JComponent divider = new Divider();
	private final JPanel unfinishedGrid = grid();
	private final JLabel unfinishedNote = new JLabel();
	private final JLabel empty = new JLabel();
	private final JLabel summary = new JLabel();
	private final JPanel footer = new JPanel(new BorderLayout());
	private final List<PotionSlot> slots = new ArrayList<>();

	private Where tab = Where.BANK;
	private List<Potion> potions = new ArrayList<>();
	private Switches switches = new Switches(true, true, true, true, false, true);

	VisibilityPanel(IntFunction<AsyncBufferedImage> icons, PotionVisibility visibility, Runnable onOpen,
		Consumer<Boolean> setCountDoses)
	{
		this.icons = icons;
		this.visibility = visibility;
		this.onOpen = onOpen;
		this.setCountDoses = setCountDoses;
		// RuneLite's own panel layout keeps room for a hidden note; this gives it none.
		setLayout(new Column(3));
		this.slotOwner = new PotionSlot.Owner()
		{
			@Override
			public Where tab()
			{
				return tab;
			}

			@Override
			public Set<Where> placesShown(int potionId)
			{
				return visibility.placesShown(potionId);
			}

			@Override
			public void set(int potionId, Set<Where> places, boolean show)
			{
				visibility.set(List.of(potionId), places, show);
				refresh();
			}

			@Override
			public boolean countDoses()
			{
				return switches.countDoses;
			}
		};

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(0, 0, 2, 0));
		JLabel title = new JLabel("Potion Storage");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.WEST);
		header.add(countSwitch, BorderLayout.EAST);
		add(header);

		depositWarning.setFont(FontManager.getRunescapeSmallFont());
		depositWarning.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		depositWarning.setText(html("\"Always deposit to potion storage\" is off, so potions you deposit go into your bank, "
			+ "not potion storage. Turn it on in the bank's menu."));
		add(depositWarning);

		add(tabBar);

		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(hint);

		notice.setFont(FontManager.getRunescapeSmallFont());
		notice.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		add(notice);

		bank.setLayout(new Column(0));
		bank.add(finishedGrid);
		bank.add(divider);
		unfinishedNote.setFont(FontManager.getRunescapeSmallFont());
		unfinishedNote.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		unfinishedNote.setBorder(new EmptyBorder(0, 2, 4, 2));
		unfinishedNote.setAlignmentX(LEFT_ALIGNMENT);
		bank.add(unfinishedNote);
		bank.add(unfinishedGrid);
		add(bank);

		empty.setFont(FontManager.getRunescapeSmallFont());
		empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		empty.setBorder(new EmptyBorder(8, 0, 8, 0));
		add(empty);

		footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		summary.setFont(FontManager.getRunescapeSmallFont());
		summary.setForeground(SUBTLE);
		JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		links.setBackground(ColorScheme.DARK_GRAY_COLOR);
		links.add(link("Show all", ColorScheme.BRAND_ORANGE, () -> setAll(true)));
		links.add(link("Hide all", ColorScheme.LIGHT_GRAY_COLOR, () -> setAll(false)));
		footer.add(summary, BorderLayout.WEST);
		footer.add(links, BorderLayout.EAST);
		add(footer);

		refresh();
	}

	@Override
	public void onActivate()
	{
		onOpen.run();
	}

	/** The potions and the plugin's settings, as they stand. */
	void update(List<Potion> potions, Switches switches)
	{
		if (potions.equals(this.potions) && switches.equals(this.switches))
		{
			return;
		}
		boolean relayout = !potions.equals(this.potions);
		this.potions = potions;
		this.switches = switches;
		if (relayout)
		{
			rebuild();
		}
		refresh();
	}

	/** Which potions are shown where changed somewhere else: a profile switch, a reset. */
	void settingsChanged()
	{
		refresh();
	}

	private List<Potion> stored()
	{
		return potions.stream().filter(p -> p.doses > 0).collect(Collectors.toList());
	}

	private void setAll(boolean show)
	{
		List<Integer> ids = stored().stream().map(p -> p.id).collect(Collectors.toList());
		if (!ids.isEmpty())
		{
			visibility.set(ids, EnumSet.of(tab), show);
			refresh();
		}
	}

	/** New potions or new doses: lay the slots out again, in potion storage's own order, the ones there are some of. */
	private void rebuild()
	{
		slots.clear();
		finishedGrid.removeAll();
		unfinishedGrid.removeAll();
		for (Potion p : stored())
		{
			PotionSlot slot = new PotionSlot(p, icons.apply(p.iconId), slotOwner);
			slots.add(slot);
			(p.unfinished ? unfinishedGrid : finishedGrid).add(slot);
		}
	}

	/** Everything that follows the open tab and the settings: the words, the counts, the faded slots. */
	private void refresh()
	{
		List<Potion> stored = stored();
		boolean any = !stored.isEmpty();
		boolean anyFinished = stored.stream().anyMatch(p -> !p.unfinished);
		boolean anyUnfinished = stored.stream().anyMatch(p -> p.unfinished);

		depositWarning.setVisible(!switches.depositsToStore);
		hint.setText(html("Click a potion to hide it in " + tab.phrase + ". Click it again to bring it back. "
			+ "Right-click for every place at once."));
		String off = placeOff();
		notice.setText(off == null ? "" : html(off));
		notice.setVisible(off != null);

		finishedGrid.setVisible(anyFinished);
		divider.setVisible(anyFinished && anyUnfinished);
		unfinishedGrid.setVisible(anyUnfinished);
		unfinishedNote.setText(html("\"Unfinished potions\" is off in the plugin's settings, so these are not shown anywhere."));
		unfinishedNote.setVisible(anyUnfinished && !switches.unfinished);
		bank.setVisible(any);

		empty.setText(html(potions.isEmpty() ? "Log in to see your potions." : "Potion storage is empty."));
		empty.setVisible(!any);

		long hidden = stored.stream().filter(p -> !visibility.shown(tab, p.id)).count();
		summary.setText((stored.size() - hidden) + " shown · " + hidden + " hidden");
		footer.setVisible(any);

		revalidate();
		repaint();
	}

	/** The plugin setting that keeps every stored potion out of the open tab's place, when it is off. */
	private String placeOff()
	{
		switch (tab)
		{
			case BANK:
				return switches.inBank ? null : "\"Show in the bank\" is off in the plugin's settings, so the bank shows no stored potions.";
			case TAGS:
				return switches.inTagTabs ? null : "\"Show in tag tabs\" is off in the plugin's settings, so only tag tabs with a layout show stored potions.";
			default:
				return switches.inSearch ? null : "\"Show in searches\" is off in the plugin's settings, so searches show no stored potions.";
		}
	}

	private static JPanel grid()
	{
		JPanel g = new JPanel(new GridLayout(0, COLUMNS, 0, 4));
		g.setOpaque(false);
		g.setAlignmentX(LEFT_ALIGNMENT);
		return g;
	}

	private static String html(String text)
	{
		return "<html><body style='width:" + TEXT_WIDTH + "px'>" + text + "</body></html>";
	}

	/** A word that does something when clicked, lighting up under the mouse. */
	private static JLabel link(String text, Color color, Runnable onClick)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				l.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				l.setForeground(color);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (l.contains(e.getPoint()))
				{
					onClick.run();
				}
			}
		});
		return l;
	}

	/** Top to bottom at full width, each at its height, with a gap between those shown: a hidden one takes no room. */
	private static final class Column implements LayoutManager
	{
		private final int gap;

		Column(int gap)
		{
			this.gap = gap;
		}

		@Override
		public void addLayoutComponent(String name, Component c)
		{
		}

		@Override
		public void removeLayoutComponent(Component c)
		{
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			Insets in = parent.getInsets();
			int w = 0;
			int h = 0;
			int shown = 0;
			for (Component c : parent.getComponents())
			{
				if (c.isVisible())
				{
					Dimension d = c.getPreferredSize();
					w = Math.max(w, d.width);
					h += d.height;
					shown++;
				}
			}
			return new Dimension(w + in.left + in.right, h + Math.max(shown - 1, 0) * gap + in.top + in.bottom);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent)
		{
			Insets in = parent.getInsets();
			int w = parent.getWidth() - in.left - in.right;
			int y = in.top;
			for (Component c : parent.getComponents())
			{
				if (c.isVisible())
				{
					int h = c.getPreferredSize().height;
					c.setBounds(in.left, y, w, h);
					y += h + gap;
				}
			}
		}
	}

	/** The bank's own brown, behind the potions. */
	private static final class BankArea extends JPanel
	{
		BankArea()
		{
			setOpaque(false);
			setBorder(new EmptyBorder(6, 4, 6, 4));
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				int w = getWidth();
				int h = getHeight();
				g.setColor(BANK_EDGE);
				g.fillRoundRect(0, 0, w, h, 5, 5);
				g.setColor(BANK_FILL);
				g.fillRect(2, 2, w - 4, h - 4);
				g.setColor(BANK_LINE);
				g.drawRect(2, 2, w - 5, h - 5);
			}
			finally
			{
				g.dispose();
			}
		}
	}

	/** The line the bank draws between its tabs, here between potions and unfinished potions. */
	private static final class Divider extends JComponent
	{
		Divider()
		{
			setPreferredSize(new Dimension(10, 11));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 11));
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(BANK_LINE);
			g.fillRect(6, getHeight() / 2, getWidth() - 12, 1);
		}
	}

	/** The three places, as tabs: a picture, a name, and how many of the stored potions it shows. */
	private final class TabBar extends JComponent
	{
		private static final int H = 40;
		private static final int GAP = 3;
		private Where hot;

		TabBar()
		{
			setPreferredSize(new Dimension(PANEL_WIDTH - 2 * BORDER_OFFSET, H));
			setToolTipText("");
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					hot = at(e.getX());
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hot = null;
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					Where w = at(e.getX());
					if (w != null && w != tab && contains(e.getPoint()))
					{
						tab = w;
						refresh();
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		private int tabWidth()
		{
			int n = Where.values().length;
			return (getWidth() - GAP * (n - 1)) / n;
		}

		private Where at(int x)
		{
			int i = x / (tabWidth() + GAP);
			return i >= 0 && i < Where.values().length ? Where.values()[i] : null;
		}

		@Override
		public String getToolTipText(MouseEvent e)
		{
			Where w = at(e.getX());
			return w == null ? null : "Which stored potions " + w.phrase + " show";
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				List<Potion> stored = stored();
				int tw = tabWidth();
				for (Where w : Where.values())
				{
					int x = w.ordinal() * (tw + GAP);
					boolean on = w == tab;
					g.setColor(on ? TAB_ON : w == hot ? TAB_HOVER : ColorScheme.DARKER_GRAY_COLOR);
					g.fillRoundRect(x, 0, tw, H, 6, 6);
					if (on)
					{
						g.setColor(ColorScheme.BRAND_ORANGE);
						g.fillRect(x + 4, H - 2, tw - 8, 2);
					}

					g.setFont(FontManager.getRunescapeBoldFont());
					FontMetrics fm = g.getFontMetrics();
					String label = label(w);
					int left = x + (tw - (Glyphs.SIZE + 4 + fm.stringWidth(label))) / 2;
					Color fg = on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR;
					Glyphs.paint(g, w, left + Glyphs.SIZE / 2.0, 13, fg);
					g.setColor(fg);
					g.drawString(label, left + Glyphs.SIZE + 4, 13 + fm.getAscent() / 2 - 1);

					long shown = stored.stream().filter(p -> visibility.shown(w, p.id)).count();
					String sub = shown + " of " + stored.size();
					g.setFont(FontManager.getRunescapeSmallFont());
					g.setColor(on ? ColorScheme.BRAND_ORANGE : FAINT);
					g.drawString(sub, x + (tw - g.getFontMetrics().stringWidth(sub)) / 2, 32);
				}
			}
			finally
			{
				g.dispose();
			}
		}

		private String label(Where w)
		{
			switch (w)
			{
				case BANK:
					return "Bank";
				case TAGS:
					return "Tags";
				default:
					return "Search";
			}
		}
	}

	/** Potions | Doses: what the slots count. */
	private final class CountSwitch extends JComponent
	{
		private final String[] parts = {"Potions", "Doses"};
		private int hot = -1;

		CountSwitch()
		{
			setPreferredSize(new Dimension(84, 18));
			setToolTipText("Count in full potions or in doses. Hovering a potion shows the other");
			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					hot = at(e.getX());
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hot = -1;
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					boolean doses = at(e.getX()) == 1;
					if (contains(e.getPoint()) && doses != switches.countDoses)
					{
						setCountDoses.accept(doses);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		private int at(int x)
		{
			return x < getWidth() / 2 ? 0 : 1;
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int w = getWidth();
				int h = getHeight();
				g.setColor(ColorScheme.DARKER_GRAY_COLOR);
				g.fillRoundRect(0, 0, w, h, 6, 6);
				g.setFont(FontManager.getRunescapeSmallFont());
				FontMetrics fm = g.getFontMetrics();
				for (int i = 0; i < parts.length; i++)
				{
					boolean on = (i == 1) == switches.countDoses;
					int x0 = i * w / 2;
					int x1 = (i + 1) * w / 2;
					if (on || i == hot)
					{
						g.setColor(on ? TAB_ON : TAB_HOVER);
						g.fillRoundRect(x0 + 2, 2, x1 - x0 - 4, h - 4, 4, 4);
					}
					g.setColor(on ? Color.WHITE : SUBTLE);
					g.drawString(parts[i], x0 + (x1 - x0 - fm.stringWidth(parts[i])) / 2, (h + fm.getAscent()) / 2 - 2);
				}
			}
			finally
			{
				g.dispose();
			}
		}
	}
}
