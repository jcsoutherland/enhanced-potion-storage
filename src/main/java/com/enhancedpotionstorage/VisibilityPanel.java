/*
 * Copyright (c) 2026, ziggle-dev
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

import com.enhancedpotionstorage.PotionVisibility.Where;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
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
 * The sidebar panel, all in one window: the open template and what to do with it ({@link TemplatePanel}), then
 * every dose slot in storage laid out the way the bank lays it out, each with how many potions of its dose
 * there are. Click a slot, left or right, to choose where its potion shows - the bank, tag tabs, searches. The
 * plugin's own on/off setting for each place still applies on top, and the panel says so when one is off.
 * <p>
 * Swing's thread only. What the game knows - the slots and their counts - comes in through {@link #update}.
 */
final class VisibilityPanel extends PluginPanel
{
	private static final int COLUMNS = 5;
	/** Swing sizes an HTML label's width in its own units, a little wider than the pixels it fills. */
	private static final int TEXT_WIDTH = 165;

	private static final Color BANK_FILL = new Color(62, 53, 41);
	private static final Color BANK_EDGE = new Color(43, 36, 25);
	private static final Color BANK_LINE = new Color(90, 77, 58);
	private static final Color SUBTLE = new Color(138, 138, 138);

	/** One dose slot, as the panel draws it. */
	@EqualsAndHashCode
	static final class Potion
	{
		/** The potion's id ({@link PotionStore.Kind#id()}): what its visibility is kept by. */
		final int id;
		/** The slot's dose's item. */
		final int iconId;
		/** The dose's name: "Prayer potion(3)". */
		final String name;
		/** The potion's name: "Prayer potion". */
		final String potionName;
		final int dose;
		/** Potions of this dose potion storage could pay. */
		final int quantity;
		/** The store's withdraw dose is this one. */
		final boolean active;
		final boolean hasDoseChoice;
		final boolean unfinished;

		Potion(int id, int iconId, String name, String potionName, int dose, int quantity, boolean active,
			boolean hasDoseChoice, boolean unfinished)
		{
			this.id = id;
			this.iconId = iconId;
			this.name = name;
			this.potionName = potionName;
			this.dose = dose;
			this.quantity = quantity;
			this.active = active;
			this.hasDoseChoice = hasDoseChoice;
			this.unfinished = unfinished;
		}
	}

	/** The plugin's settings that turn a place off whatever the panel says. */
	@EqualsAndHashCode
	static final class Switches
	{
		final boolean inBank;
		final boolean inTagTabs;
		final boolean inSearch;
		final boolean unfinished;
		/** The bank's "Always deposit to potion storage" is on. */
		final boolean depositsToStore;

		Switches(boolean inBank, boolean inTagTabs, boolean inSearch, boolean unfinished, boolean depositsToStore)
		{
			this.inBank = inBank;
			this.inTagTabs = inTagTabs;
			this.inSearch = inSearch;
			this.unfinished = unfinished;
			this.depositsToStore = depositsToStore;
		}
	}

	/** An item's icon, by id. */
	private final IntFunction<AsyncBufferedImage> icons;
	private final PotionVisibility visibility;
	/** Asked when the panel is opened: send the slots again, as they are now. */
	private final Runnable onOpen;

	private final PotionSlot.Owner slotOwner;
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

	private List<Potion> potions = new ArrayList<>();
	private boolean loggedIn;
	private Switches switches = new Switches(true, true, true, true, true);

	VisibilityPanel(IntFunction<AsyncBufferedImage> icons, PotionVisibility visibility, Runnable onOpen, JComponent templates)
	{
		this.icons = icons;
		this.visibility = visibility;
		this.onOpen = onOpen;
		// RuneLite's own panel layout keeps room for a hidden note; this gives it none.
		setLayout(new Column(3));
		this.slotOwner = new PotionSlot.Owner()
		{
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
		};

		JLabel title = new JLabel("Enhanced Potion Storage");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setBorder(new EmptyBorder(0, 0, 2, 0));
		add(title);

		depositWarning.setFont(FontManager.getRunescapeSmallFont());
		depositWarning.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		depositWarning.setText(html("\"Always deposit to potion storage\" is off, so potions you deposit go into your bank, "
			+ "not potion storage. Turn it on in the bank's menu."));
		add(depositWarning);

		// Templates first: which slots the bank shows. Below, slot by slot, where each potion is shown.
		add(templates);

		JLabel slotsTitle = new JLabel("Dose slots");
		slotsTitle.setFont(FontManager.getRunescapeBoldFont());
		slotsTitle.setForeground(Color.WHITE);
		add(slotsTitle);

		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setText(html("Every slot in storage, in bank order. Click one to choose where its potion shows: "
			+ "the bank, tag tabs or searches."));
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
		links.add(link("Show all everywhere", ColorScheme.BRAND_ORANGE, this::showAll));
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

	/** The slots and the plugin's settings, as they stand. */
	void update(boolean loggedIn, List<Potion> potions, Switches switches)
	{
		if (loggedIn == this.loggedIn && potions.equals(this.potions) && switches.equals(this.switches))
		{
			return;
		}
		boolean relayout = !potions.equals(this.potions);
		this.loggedIn = loggedIn;
		this.potions = potions;
		this.switches = switches;
		if (relayout)
		{
			rebuild();
		}
		refresh();
	}

	/** Which potions are shown where changed somewhere else: a template opened, a profile switch, a reset. */
	void settingsChanged()
	{
		refresh();
	}

	/** Every potion with a slot here, by id. */
	private Set<Integer> potionIds()
	{
		Set<Integer> ids = new LinkedHashSet<>();
		potions.forEach(p -> ids.add(p.id));
		return ids;
	}

	private void showAll()
	{
		Set<Integer> ids = potionIds();
		if (!ids.isEmpty())
		{
			visibility.set(ids, EnumSet.allOf(Where.class), true);
			refresh();
		}
	}

	/** New slots or new counts: lay the slots out again, in bank order. */
	private void rebuild()
	{
		slots.clear();
		finishedGrid.removeAll();
		unfinishedGrid.removeAll();
		for (Potion p : potions)
		{
			PotionSlot slot = new PotionSlot(p, icons.apply(p.iconId), slotOwner);
			slots.add(slot);
			(p.unfinished ? unfinishedGrid : finishedGrid).add(slot);
		}
	}

	/** Everything that follows the settings: the words, the counts, the faded slots. */
	private void refresh()
	{
		boolean any = !potions.isEmpty();
		boolean anyFinished = potions.stream().anyMatch(p -> !p.unfinished);
		boolean anyUnfinished = potions.stream().anyMatch(p -> p.unfinished);

		depositWarning.setVisible(!switches.depositsToStore);
		String off = placesOff();
		notice.setText(off == null ? "" : html(off));
		notice.setVisible(off != null);

		finishedGrid.setVisible(anyFinished);
		divider.setVisible(anyFinished && anyUnfinished);
		unfinishedGrid.setVisible(anyUnfinished);
		unfinishedNote.setText(html("\"Unfinished potions\" is off in the plugin's settings, so these are not shown anywhere."));
		unfinishedNote.setVisible(anyUnfinished && !switches.unfinished);
		bank.setVisible(any);

		empty.setText(html(!loggedIn ? "Log in to see your potions." : "Potion storage has no potions your layout shows."));
		empty.setVisible(!any);

		Set<Integer> ids = potionIds();
		long hidden = ids.stream().filter(id -> visibility.placesShown(id).size() < Where.values().length).count();
		summary.setText(slots.size() + " slots · " + hidden + " hidden somewhere");
		footer.setVisible(any);

		revalidate();
		repaint();
	}

	/** The plugin settings that keep every stored potion out of a place, when they are off. */
	private String placesOff()
	{
		List<String> off = new ArrayList<>();
		if (!switches.inBank)
		{
			off.add("\"Show in the bank\"");
		}
		if (!switches.inTagTabs)
		{
			off.add("\"Show in tag tabs\"");
		}
		if (!switches.inSearch)
		{
			off.add("\"Show in searches\"");
		}
		if (off.isEmpty())
		{
			return null;
		}
		return String.join(" and ", off) + (off.size() == 1 ? " is" : " are")
			+ " off in the plugin's settings, which goes for every potion whatever its menu here says.";
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

	/** The bank's own brown, behind the slots. */
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
}
