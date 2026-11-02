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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.EqualsAndHashCode;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The sidebar panel's template part: which template is open and what to do with it, every dose or a choice,
 * where the slots go, what is missing from the bank and what was recently removed, and the layout itself -
 * categories and the potions in them, their order and their dose slots.
 * <p>
 * Swing's thread only. What it shows comes in whole through {@link #update}; what it does goes out through
 * {@link Actions}, which the plugin carries out on the client thread.
 */
final class TemplatePanel extends JPanel
{
	private static final Color SUBTLE = new Color(138, 138, 138);
	private static final Color CHIP = new Color(220, 138, 0);
	private static final Color DOSE_ON = new Color(58, 58, 58);
	private static final int TEXT_WIDTH = 165;

	/** What the panel asks the plugin to do. */
	interface Actions
	{
		/** Open {@code name}; first save the open one's changes into it when {@code saveFirst}. */
		void open(String name, boolean saveFirst);

		void save();

		void saveAs(String name);

		void rename(String from, String to);

		void delete(String name);

		/** {@code name} as JSON, to {@code then} - on Swing's thread. */
		void export(String name, Consumer<String> then);

		void importJson(String json);

		void setShowAllDoses(boolean on);

		void setBankPlace(BankPlace place, String tag);

		void restore(Missing.Chip chip);

		void restoreAll();

		void undo(TemplateStore.Removed removed);

		void setCategoryEnabled(int category, boolean on);

		void moveCategory(int category, int by);

		void renameCategory(int category, String name);

		void addCategory(String name);

		void deleteCategory(int category);

		void movePotion(int potionId, int by);

		void movePotionTo(int potionId, int category);

		void setDoses(int potionId, List<Integer> doses);
	}

	/** One potion as the layout editor shows it. */
	@EqualsAndHashCode
	static final class PotionRow
	{
		final int id;
		final String name;
		/** The doses it comes in, largest first. */
		final List<Integer> natural;
		/** Its slots, left to right. */
		final List<Integer> doses;
		final boolean stored;

		PotionRow(int id, String name, List<Integer> natural, List<Integer> doses, boolean stored)
		{
			this.id = id;
			this.name = name;
			this.natural = natural;
			this.doses = doses;
			this.stored = stored;
		}
	}

	@EqualsAndHashCode
	static final class CategoryRow
	{
		final String name;
		final boolean enabled;
		final List<PotionRow> potions;

		CategoryRow(String name, boolean enabled, List<PotionRow> potions)
		{
			this.name = name;
			this.enabled = enabled;
			this.potions = potions;
		}
	}

	/** A potion with slots missing from the bank, and how each comes back. */
	@EqualsAndHashCode
	static final class Missing
	{
		@EqualsAndHashCode
		static final class Chip
		{
			final int potionId;
			final int dose;
			/** The slot's key, for one that comes back as a placeholder. */
			final int key;
			final TemplateStore.Removed.How how;

			Chip(int potionId, int dose, int key, TemplateStore.Removed.How how)
			{
				this.potionId = potionId;
				this.dose = dose;
				this.key = key;
				this.how = how;
			}
		}

		final String category;
		final String name;
		final List<Chip> chips;

		Missing(String category, String name, List<Chip> chips)
		{
			this.category = category;
			this.name = name;
			this.chips = chips;
		}
	}

	@EqualsAndHashCode
	static final class RemovedRow
	{
		final TemplateStore.Removed removed;
		final String label;

		RemovedRow(TemplateStore.Removed removed, String label)
		{
			this.removed = removed;
			this.label = label;
		}
	}

	/** Everything the panel shows. */
	@EqualsAndHashCode
	static final class State
	{
		final boolean loggedIn;
		final List<String> names;
		final String active;
		final boolean dirty;
		final boolean showAllDoses;
		final BankPlace place;
		final String tag;
		final List<CategoryRow> categories;
		final List<Missing> missing;
		final List<RemovedRow> removed;

		State(boolean loggedIn, List<String> names, String active, boolean dirty, boolean showAllDoses, BankPlace place,
			String tag, List<CategoryRow> categories, List<Missing> missing, List<RemovedRow> removed)
		{
			this.loggedIn = loggedIn;
			this.names = names;
			this.active = active;
			this.dirty = dirty;
			this.showAllDoses = showAllDoses;
			this.place = place;
			this.tag = tag;
			this.categories = categories;
			this.missing = missing;
			this.removed = removed;
		}

		static State none()
		{
			return new State(false, List.of(Template.DEFAULT), Template.DEFAULT, false, true, BankPlace.MAIN, "",
				Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
		}

		boolean isDefault()
		{
			return Template.DEFAULT.equals(active);
		}
	}

	private final Actions actions;
	private State state = State.none();
	private boolean layoutOpen;
	private boolean onlyStored = true;
	/** Set while the combo box is filled in, so it does not open what it shows. */
	private boolean filling;

	TemplatePanel(Actions actions)
	{
		this.actions = actions;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		rebuild();
	}

	void update(State s)
	{
		if (s.equals(state))
		{
			return;
		}
		state = s;
		rebuild();
	}

	private void rebuild()
	{
		removeAll();
		if (!state.loggedIn)
		{
			add(note("Log in to choose a template.", SUBTLE));
			revalidate();
			repaint();
			return;
		}
		add(templateRow());
		add(templateLinks());
		add(gap());
		add(showAllRow());
		add(placeRow());
		add(gap());
		addMissing();
		add(gap());
		addLayout();
		add(gap());
		revalidate();
		repaint();
	}

	// ---- the template ----

	private JPanel templateRow()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel label = small("Template", Color.WHITE);
		row.add(label, BorderLayout.WEST);
		JComboBox<String> box = new JComboBox<>();
		filling = true;
		for (String n : state.names)
		{
			box.addItem(n);
		}
		box.setSelectedItem(state.active);
		filling = false;
		box.setToolTipText(state.dirty ? "Unsaved changes" : null);
		box.addActionListener(e ->
		{
			String to = (String) box.getSelectedItem();
			if (filling || to == null || to.equals(state.active))
			{
				return;
			}
			Boolean saveFirst = askSaveFirst();
			if (saveFirst == null)
			{
				filling = true;
				box.setSelectedItem(state.active);
				filling = false;
				return;
			}
			actions.open(to, saveFirst);
		});
		row.add(box, BorderLayout.CENTER);
		if (state.dirty)
		{
			row.add(small("unsaved", CHIP), BorderLayout.EAST);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		return row;
	}

	/** With unsaved changes: save them first? Null to stay. Without: no. */
	private Boolean askSaveFirst()
	{
		if (!state.dirty)
		{
			return false;
		}
		int answer = JOptionPane.showConfirmDialog(this,
			"Save your unsaved changes to \"" + state.active + "\" first?",
			"Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION)
		{
			return null;
		}
		return answer == JOptionPane.YES_OPTION;
	}

	private JPanel templateLinks()
	{
		JPanel links = flow();
		if (!state.isDefault())
		{
			links.add(link("Save", state.dirty ? CHIP : ColorScheme.LIGHT_GRAY_COLOR, actions::save));
		}
		links.add(link("Save as", ColorScheme.LIGHT_GRAY_COLOR, () ->
		{
			String name = ask("Name the new template:", state.isDefault() ? "Custom" : state.active + " copy");
			if (name != null)
			{
				actions.saveAs(name);
			}
		}));
		if (state.dirty)
		{
			links.add(link("Revert", ColorScheme.LIGHT_GRAY_COLOR, () ->
			{
				if (confirm("Drop your unsaved changes to \"" + state.active + "\"?"))
				{
					actions.open(state.active, false);
				}
			}));
		}
		if (!state.isDefault())
		{
			links.add(link("Rename", ColorScheme.LIGHT_GRAY_COLOR, () ->
			{
				String name = ask("Rename \"" + state.active + "\" to:", state.active);
				if (name != null)
				{
					actions.rename(state.active, name);
				}
			}));
			links.add(link("Delete", ColorScheme.LIGHT_GRAY_COLOR, () ->
			{
				if (confirm("Delete the template \"" + state.active + "\"? Default opens in its place."))
				{
					actions.delete(state.active);
				}
			}));
		}
		links.add(link("Export", ColorScheme.LIGHT_GRAY_COLOR, this::exportTemplate));
		links.add(link("Import", ColorScheme.LIGHT_GRAY_COLOR, this::importTemplate));
		return links;
	}

	private void exportTemplate()
	{
		String name = state.active;
		actions.export(name, json ->
		{
			if (json == null)
			{
				return;
			}
			JFileChooser chooser = chooser();
			chooser.setSelectedFile(new File(name.replaceAll("[^A-Za-z0-9 _-]", "_") + ".json"));
			if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			{
				return;
			}
			File f = chooser.getSelectedFile();
			if (!f.getName().contains("."))
			{
				f = new File(f.getParentFile(), f.getName() + ".json");
			}
			try
			{
				Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
			}
			catch (IOException ex)
			{
				JOptionPane.showMessageDialog(this, "Couldn't save the file: " + ex.getMessage(), "Export",
					JOptionPane.ERROR_MESSAGE);
			}
		});
	}

	private void importTemplate()
	{
		JFileChooser chooser = chooser();
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		try
		{
			actions.importJson(new String(Files.readAllBytes(chooser.getSelectedFile().toPath()), StandardCharsets.UTF_8));
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(this, "Couldn't read the file: " + ex.getMessage(), "Import",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private static JFileChooser chooser()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("Templates (.json, .txt, .cfg)", "json", "txt", "cfg"));
		return chooser;
	}

	private JPanel showAllRow()
	{
		JCheckBox box = new JCheckBox("Show every dose of every potion", state.showAllDoses);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setToolTipText("Off: each potion shows the dose slots the layout below gives it");
		box.addActionListener(e -> actions.setShowAllDoses(box.isSelected()));
		JPanel row = flow();
		row.add(box);
		return row;
	}

	private JPanel placeRow()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(small("Bank place", Color.WHITE), BorderLayout.WEST);
		JComboBox<BankPlace> box = new JComboBox<>(BankPlace.values());
		box.setSelectedItem(state.place);
		box.setToolTipText("Move every dose slot here, together");
		box.addActionListener(e ->
		{
			BankPlace p = (BankPlace) box.getSelectedItem();
			if (p == null || p == state.place)
			{
				return;
			}
			String tag = state.tag;
			if (p == BankPlace.TAG)
			{
				tag = ask("Which bank tag?", state.tag);
				if (tag == null || tag.trim().isEmpty())
				{
					box.setSelectedItem(state.place);
					return;
				}
			}
			actions.setBankPlace(p, tag.trim());
		});
		row.add(box, BorderLayout.CENTER);
		if (state.place == BankPlace.TAG)
		{
			row.add(small(state.tag, CHIP), BorderLayout.SOUTH);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, state.place == BankPlace.TAG ? 42 : 26));
		return row;
	}

	// ---- the missing tray ----

	private void addMissing()
	{
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.setAlignmentX(LEFT_ALIGNMENT);
		head.add(bold("Missing slots"), BorderLayout.WEST);
		if (!state.missing.isEmpty())
		{
			head.add(link("Restore all", CHIP, actions::restoreAll), BorderLayout.EAST);
		}
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		add(head);
		if (state.missing.isEmpty())
		{
			add(note("Nothing is missing: every slot your layout asks for is in the bank.", SUBTLE));
		}
		String category = null;
		for (Missing m : state.missing)
		{
			if (!m.category.equals(category))
			{
				category = m.category;
				add(small(category, CHIP));
			}
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(new EmptyBorder(2, 4, 2, 4));
			row.setAlignmentX(LEFT_ALIGNMENT);
			JLabel name = small(m.name, ColorScheme.LIGHT_GRAY_COLOR);
			name.setToolTipText("Click to restore every missing slot of " + m.name);
			name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			name.addMouseListener(clicked(() -> m.chips.forEach(actions::restore)));
			row.add(name, BorderLayout.WEST);
			JPanel chips = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
			chips.setOpaque(false);
			for (Missing.Chip c : m.chips)
			{
				JLabel chip = link("+" + c.dose, CHIP, () -> actions.restore(c));
				chip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createDashedBorder(SUBTLE),
					new EmptyBorder(0, 3, 0, 3)));
				chip.setToolTipText(c.how == TemplateStore.Removed.How.RELEASED
					? "Bring back the " + c.dose + "-dose slot as a placeholder"
					: "Add a " + c.dose + "-dose slot back to the layout");
				chips.add(chip);
			}
			row.add(chips, BorderLayout.EAST);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
			add(row);
		}
		if (!state.removed.isEmpty())
		{
			add(small("Recently removed", CHIP));
			for (RemovedRow r : state.removed)
			{
				JPanel row = new JPanel(new BorderLayout());
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				row.setBorder(new EmptyBorder(2, 4, 2, 4));
				row.setAlignmentX(LEFT_ALIGNMENT);
				row.add(small(r.label, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
				row.add(link("Undo", CHIP, () -> actions.undo(r.removed)), BorderLayout.EAST);
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
				add(row);
			}
		}
	}

	// ---- the layout ----

	private void addLayout()
	{
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.setAlignmentX(LEFT_ALIGNMENT);
		JLabel title = link((layoutOpen ? "▾ " : "▸ ") + "Layout", Color.WHITE, () ->
		{
			layoutOpen = !layoutOpen;
			rebuild();
		});
		title.setFont(FontManager.getRunescapeBoldFont());
		head.add(title, BorderLayout.WEST);
		if (layoutOpen)
		{
			head.add(link("Add category", CHIP, () ->
			{
				String name = ask("Name the new category:", "New category");
				if (name != null && !name.trim().isEmpty())
				{
					actions.addCategory(name.trim());
				}
			}), BorderLayout.EAST);
		}
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		add(head);
		if (!layoutOpen)
		{
			return;
		}
		JCheckBox only = new JCheckBox("Only potions in storage", onlyStored);
		only.setFont(FontManager.getRunescapeSmallFont());
		only.setBackground(ColorScheme.DARK_GRAY_COLOR);
		only.addActionListener(e ->
		{
			onlyStored = only.isSelected();
			rebuild();
		});
		JPanel o = flow();
		o.add(only);
		add(o);
		add(note("Click a dose to add or remove its slot. Right-click a potion or a category for more.", SUBTLE));
		for (int i = 0; i < state.categories.size(); i++)
		{
			addCategory(i, state.categories.get(i));
		}
	}

	private void addCategory(int index, CategoryRow c)
	{
		JPanel row = new JPanel(new BorderLayout(2, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 2, 2, 4));
		row.setAlignmentX(LEFT_ALIGNMENT);
		JCheckBox on = new JCheckBox(c.name, c.enabled);
		on.setFont(FontManager.getRunescapeSmallFont());
		on.setForeground(c.enabled ? CHIP : SUBTLE);
		on.setOpaque(false);
		on.setToolTipText(c.enabled ? "Untick to keep this category's potions out of the bank" : "Tick to show this category");
		on.addActionListener(e -> actions.setCategoryEnabled(index, on.isSelected()));
		row.add(on, BorderLayout.CENTER);
		JPanel arrows = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		arrows.setOpaque(false);
		arrows.add(link("▲", ColorScheme.LIGHT_GRAY_COLOR, () -> actions.moveCategory(index, -1)));
		arrows.add(link("▼", ColorScheme.LIGHT_GRAY_COLOR, () -> actions.moveCategory(index, 1)));
		row.add(arrows, BorderLayout.EAST);
		JPopupMenu menu = new JPopupMenu();
		menu.add(item("Rename", () ->
		{
			String name = ask("Rename \"" + c.name + "\" to:", c.name);
			if (name != null && !name.trim().isEmpty())
			{
				actions.renameCategory(index, name.trim());
			}
		}));
		menu.add(item("Delete", () ->
		{
			if (state.categories.size() <= 1)
			{
				JOptionPane.showMessageDialog(this, "A template needs at least one category.");
			}
			else if (confirm("Delete \"" + c.name + "\"? Its potions move to the category above it."))
			{
				actions.deleteCategory(index);
			}
		}));
		row.setComponentPopupMenu(menu);
		on.setComponentPopupMenu(menu);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		add(row);
		for (PotionRow p : c.potions)
		{
			if (!onlyStored || p.stored)
			{
				add(potionRow(index, p));
			}
		}
	}

	private JPanel potionRow(int category, PotionRow p)
	{
		JPanel row = new JPanel(new BorderLayout(2, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 14, 1, 4));
		row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel name = small(p.name, p.stored ? ColorScheme.LIGHT_GRAY_COLOR : SUBTLE);
		name.setToolTipText(p.name + ": right-click to move it or edit its slots");
		row.add(name, BorderLayout.CENTER);
		JPanel doses = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		doses.setOpaque(false);
		if (p.natural.size() > 1)
		{
			for (int d : p.natural)
			{
				long n = p.doses.stream().filter(x -> x == d).count();
				JLabel chip = link(n > 1 ? d + "×" + n : String.valueOf(d), n > 0 ? Color.WHITE : SUBTLE,
					() -> actions.setDoses(p.id, toggled(p, d)));
				chip.setOpaque(n > 0);
				chip.setBackground(DOSE_ON);
				chip.setBorder(new EmptyBorder(0, 3, 0, 3));
				chip.setToolTipText(n > 0 ? "Remove the " + d + "-dose slot" : "Add a " + d + "-dose slot");
				doses.add(chip);
			}
		}
		row.add(doses, BorderLayout.EAST);
		JPopupMenu menu = new JPopupMenu();
		menu.add(item("Move up", () -> actions.movePotion(p.id, -1)));
		menu.add(item("Move down", () -> actions.movePotion(p.id, 1)));
		JMenu to = new JMenu("Move to");
		for (int i = 0; i < state.categories.size(); i++)
		{
			if (i != category)
			{
				int target = i;
				to.add(item(state.categories.get(i).name, () -> actions.movePotionTo(p.id, target)));
			}
		}
		menu.add(to);
		if (p.natural.size() > 1)
		{
			menu.add(item("Edit dose slots...", () -> editDoses(p)));
			menu.add(item("Every dose", () -> actions.setDoses(p.id, p.natural)));
		}
		row.setComponentPopupMenu(menu);
		name.setComponentPopupMenu(menu);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		return row;
	}

	/** {@code p}'s slots with dose {@code d} taken out (every slot of it) or, with none, put back in dose order. */
	private static List<Integer> toggled(PotionRow p, int d)
	{
		List<Integer> out = new ArrayList<>(p.doses);
		if (out.contains(d))
		{
			out.removeIf(x -> x == d);
			return out;
		}
		int at = 0;
		while (at < out.size() && out.get(at) > d)
		{
			at++;
		}
		out.add(at, d);
		return out;
	}

	private void editDoses(PotionRow p)
	{
		String now = p.doses.toString().replaceAll("[\\[\\] ]", "");
		String text = ask("Dose slots for " + p.name + ", left to right (a dose may repeat):", now);
		if (text == null)
		{
			return;
		}
		List<Integer> out = new ArrayList<>();
		for (String part : text.split("[,\\s]+"))
		{
			try
			{
				int d = Integer.parseInt(part.trim());
				if (p.natural.contains(d))
				{
					out.add(d);
				}
			}
			catch (NumberFormatException e)
			{
				// skipped
			}
		}
		actions.setDoses(p.id, out);
	}

	// ---- bits ----

	private String ask(String question, String start)
	{
		Object answer = JOptionPane.showInputDialog(this, question, "Enhanced Potion Storage", JOptionPane.PLAIN_MESSAGE,
			null, null, start);
		return answer == null ? null : answer.toString().trim();
	}

	private boolean confirm(String question)
	{
		return JOptionPane.showConfirmDialog(this, question, "Enhanced Potion Storage", JOptionPane.OK_CANCEL_OPTION)
			== JOptionPane.OK_OPTION;
	}

	private static JMenuItem item(String text, Runnable run)
	{
		JMenuItem i = new JMenuItem(text);
		i.addActionListener(e -> run.run());
		return i;
	}

	private static JPanel flow()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		return p;
	}

	private static JPanel gap()
	{
		JPanel g = new JPanel();
		g.setOpaque(false);
		g.setPreferredSize(new Dimension(1, 6));
		g.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		g.setAlignmentX(LEFT_ALIGNMENT);
		return g;
	}

	private static JLabel small(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel bold(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		return l;
	}

	private static JLabel note(String text, Color color)
	{
		JLabel l = small("<html><body style='width:" + TEXT_WIDTH + "px'>" + text + "</body></html>", color);
		l.setBorder(new EmptyBorder(2, 0, 2, 0));
		return l;
	}

	private static MouseAdapter clicked(Runnable run)
	{
		return new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && e.getComponent().contains(e.getPoint()))
				{
					run.run();
				}
			}
		};
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
				if (SwingUtilities.isLeftMouseButton(e) && l.contains(e.getPoint()))
				{
					onClick.run();
				}
			}
		});
		return l;
	}
}
