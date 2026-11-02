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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The account's templates: the saved ones, which is open, the open one as edited since it was saved, and the
 * slots recently removed. Kept per RuneScape account in RuneLite's profile config, as JSON.
 * <p>
 * The open template's working copy is what the bank draws. Edits change it and leave the saved template as it
 * was until it is saved - except on Default, which never changes: an edit there saves the change straight into
 * a new custom template and opens that. The placement half of a template is the saved order ({@link SavedOrder}),
 * which a save copies in and a load puts back.
 * <p>
 * Client thread only.
 */
final class TemplateStore
{
	private static final String TEMPLATES_KEY = "templates";
	private static final String ACTIVE_KEY = "activeTemplate";
	private static final String WORKING_KEY = "workingTemplate";
	private static final String DIRTY_KEY = "templateDirty";
	private static final String REMOVED_KEY = "recentlyRemoved";
	/** How many removals "Recently removed" keeps. */
	static final int REMOVED_KEPT = 5;
	private static final String CUSTOM = "Custom";

	private static final Type TEMPLATE_LIST = new TypeToken<List<Template>>()
	{
	}.getType();
	private static final Type REMOVED_LIST = new TypeToken<List<Removed>>()
	{
	}.getType();

	/** A slot the user took out of the bank, to put back. */
	static final class Removed
	{
		enum How
		{
			/** A placeholder released: the slot comes back as a placeholder. */
			RELEASED,
			/** The dose taken out of the potion's slots: it is put back in them. */
			DOSE_REMOVED
		}

		int potionId;
		int dose;
		/** The slot's key ({@link SlotPlan#key}), for a placeholder to bring back. */
		int key;
		How how;

		Removed()
		{
		}

		Removed(int potionId, int dose, int key, How how)
		{
			this.potionId = potionId;
			this.dose = dose;
			this.key = key;
			this.how = how;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Removed && ((Removed) o).potionId == potionId && ((Removed) o).dose == dose
				&& ((Removed) o).key == key && ((Removed) o).how == how;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(potionId, dose, key, how);
		}
	}

	/** Where the config is kept, so tests can stand in for RuneLite's. */
	interface Storage
	{
		/** The account logged in, or null. */
		String account();

		String get(String key);

		void set(String key, String value);

		void unset(String key);
	}

	private final Storage storage;
	private final Gson gson;
	/** The bank's saved order as it stands, for a template to keep. */
	private final Supplier<String> arrangement;
	/** Put a template's placement back into the bank's saved order. */
	private final Consumer<String> placeAs;
	/** Every potion the store keeps, for making Default's categories into a custom template's. */
	private final Supplier<List<SlotPlan.Potion>> potions;

	private String account;
	private boolean loaded;
	private List<Template> saved = new ArrayList<>();
	private String active = Template.DEFAULT;
	private Template working = Template.builtIn();
	private boolean dirty;
	private List<Removed> removed = new ArrayList<>();
	/** Counts every change to what the bank should draw. */
	private int version;

	TemplateStore(Storage storage, Gson gson, Supplier<String> arrangement, Consumer<String> placeAs,
		Supplier<List<SlotPlan.Potion>> potions)
	{
		this.storage = storage;
		this.gson = gson;
		this.arrangement = arrangement;
		this.placeAs = placeAs;
		this.potions = potions;
	}

	static boolean isOwnKey(String key)
	{
		return TEMPLATES_KEY.equals(key) || ACTIVE_KEY.equals(key) || WORKING_KEY.equals(key) || DIRTY_KEY.equals(key)
			|| REMOVED_KEY.equals(key);
	}

	/** Read the account's templates when the account changes. */
	private void load()
	{
		String a = storage.account();
		if (loaded && Objects.equals(a, account))
		{
			return;
		}
		loaded = true;
		account = a;
		version++;
		saved = new ArrayList<>();
		active = Template.DEFAULT;
		working = Template.builtIn();
		dirty = false;
		removed = new ArrayList<>();
		if (a == null)
		{
			return;
		}
		List<Template> list = parse(storage.get(TEMPLATES_KEY), TEMPLATE_LIST);
		if (list != null)
		{
			list.stream().filter(Objects::nonNull).map(Template::repaired).forEach(saved::add);
		}
		String name = storage.get(ACTIVE_KEY);
		Template open = find(name);
		if (open != null)
		{
			active = open.name;
			Template w = parseOne(storage.get(WORKING_KEY));
			working = w != null ? w.repaired() : open.copy();
			working.name = active;
			dirty = "true".equals(storage.get(DIRTY_KEY));
		}
		List<Removed> r = parse(storage.get(REMOVED_KEY), REMOVED_LIST);
		if (r != null)
		{
			r.stream().filter(x -> x != null && x.how != null).forEach(removed::add);
		}
	}

	private <T> T parse(String json, Type type)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, type);
		}
		catch (JsonParseException e)
		{
			return null;
		}
	}

	private Template parseOne(String json)
	{
		return parse(json, Template.class);
	}

	synchronized int version()
	{
		load();
		return version;
	}

	/** The open template as edited: what the bank draws. Not to be changed: {@link #edit} does that. */
	synchronized Template working()
	{
		load();
		return working;
	}

	synchronized String activeName()
	{
		load();
		return active;
	}

	synchronized boolean dirty()
	{
		load();
		return dirty;
	}

	/** Default, then every saved template, in the order they were made. */
	synchronized List<String> names()
	{
		load();
		List<String> out = new ArrayList<>();
		out.add(Template.DEFAULT);
		saved.forEach(t -> out.add(t.name));
		return out;
	}

	private Template find(String name)
	{
		if (name == null)
		{
			return null;
		}
		for (Template t : saved)
		{
			if (t.name.equalsIgnoreCase(name))
			{
				return t;
			}
		}
		return null;
	}

	/** {@code base}, or {@code base 2}, {@code base 3}...: a name no template has. */
	synchronized String uniqueName(String base)
	{
		load();
		String b = base == null || base.trim().isEmpty() ? CUSTOM : base.trim();
		String name = b;
		for (int n = 2; Template.DEFAULT.equalsIgnoreCase(name) || find(name) != null; n++)
		{
			name = b + " " + n;
		}
		return name;
	}

	/** A copy of {@code t} with every potion in a category: Default's made into a template's own. */
	private Template materialized(Template t)
	{
		Template c = t.copy();
		c.categories = SlotPlan.categories(t, potions.get());
		return c;
	}

	/**
	 * Change the open template. On Default, the change goes into a new custom template, saved and opened at
	 * once; otherwise into the working copy, unsaved. True when a template was made.
	 */
	synchronized boolean edit(Consumer<Template> change)
	{
		load();
		if (account == null)
		{
			return false;
		}
		version++;
		if (Template.DEFAULT.equals(active))
		{
			Template t = materialized(working);
			t.name = uniqueName(CUSTOM);
			change.accept(t);
			t.arrangement = arrangement.get();
			saved.add(t);
			active = t.name;
			working = t.copy();
			dirty = false;
			saveAll();
			return true;
		}
		working.categories = SlotPlan.categories(working, potions.get());
		change.accept(working);
		dirty = true;
		saveWorking();
		return false;
	}

	/** The open template's slots were moved in the bank: an edit with no change but where they are. */
	synchronized boolean placementEdited()
	{
		return edit(t ->
		{
		});
	}

	/** Save the working copy over the open template. Default has nothing to save. */
	synchronized void save()
	{
		load();
		Template t = find(active);
		if (t == null || account == null)
		{
			return;
		}
		Template w = working.copy();
		w.name = t.name;
		w.arrangement = arrangement.get();
		saved.set(saved.indexOf(t), w);
		working = w.copy();
		dirty = false;
		saveAll();
	}

	/** Save the working copy as a new template, and open it. Its name, made unique. */
	synchronized String saveAs(String name)
	{
		load();
		if (account == null)
		{
			return null;
		}
		Template w = materialized(working);
		w.name = uniqueName(name);
		w.arrangement = arrangement.get();
		saved.add(w);
		active = w.name;
		working = w.copy();
		dirty = false;
		version++;
		saveAll();
		return w.name;
	}

	/**
	 * Open {@code name} as it was last saved, its slots back where it saved them - what the working copy had is
	 * dropped. Opening the open one again reverts it.
	 */
	synchronized void open(String name)
	{
		load();
		Template t = find(name);
		active = t == null ? Template.DEFAULT : t.name;
		working = t == null ? Template.builtIn() : t.copy();
		dirty = false;
		removed = new ArrayList<>();
		version++;
		placeAs.accept(working.arrangement);
		saveAll();
	}

	synchronized void delete(String name)
	{
		load();
		Template t = find(name);
		if (t == null)
		{
			return;
		}
		saved.remove(t);
		if (t.name.equals(active))
		{
			open(Template.DEFAULT);
		}
		else
		{
			saveAll();
		}
	}

	/** Rename {@code from}; the new name made unique. Default keeps its name. */
	synchronized void rename(String from, String to)
	{
		load();
		Template t = find(from);
		if (t == null || to == null || to.trim().isEmpty())
		{
			return;
		}
		String name = t.name.equalsIgnoreCase(to.trim()) ? to.trim() : uniqueName(to);
		boolean open = t.name.equals(active);
		t.name = name;
		if (open)
		{
			active = name;
			working.name = name;
		}
		saveAll();
	}

	/** {@code name} as JSON, everything in it - the open one as it stands, unsaved changes and all. */
	synchronized String export(String name)
	{
		load();
		Template t;
		if (name.equals(active))
		{
			t = materialized(working);
			t.arrangement = arrangement.get();
		}
		else
		{
			Template s = find(name);
			if (s == null)
			{
				return null;
			}
			t = materialized(s);
		}
		if (Template.DEFAULT.equals(t.name))
		{
			t.name = Template.DEFAULT + " copy";
		}
		return gson.toJson(t);
	}

	/** A template from JSON, saved under a name of its own. Its name; null when it is no template. */
	synchronized String importJson(String json)
	{
		load();
		if (account == null)
		{
			return null;
		}
		Template t = parseOne(json);
		if (t == null)
		{
			return null;
		}
		t.repaired();
		t.name = uniqueName(t.name);
		if (t.isDefault())
		{
			t.categories = SlotPlan.categories(t, potions.get());
		}
		saved.add(t);
		saveAll();
		return t.name;
	}

	synchronized List<Removed> removed()
	{
		load();
		return Collections.unmodifiableList(new ArrayList<>(removed));
	}

	/** A removal the user made, newest first; only the last {@link #REMOVED_KEPT} are kept. */
	synchronized void recordRemoved(Removed r)
	{
		load();
		removed.remove(r);
		removed.add(0, r);
		while (removed.size() > REMOVED_KEPT)
		{
			removed.remove(removed.size() - 1);
		}
		saveRemoved();
	}

	/** Undone, or put back some other way: nothing to undo any more. */
	synchronized void forgetRemoved(Removed r)
	{
		load();
		if (removed.remove(r))
		{
			saveRemoved();
		}
	}

	private void saveAll()
	{
		if (account == null)
		{
			return;
		}
		storage.set(TEMPLATES_KEY, gson.toJson(saved, TEMPLATE_LIST));
		if (Template.DEFAULT.equals(active))
		{
			storage.unset(ACTIVE_KEY);
		}
		else
		{
			storage.set(ACTIVE_KEY, active);
		}
		saveWorking();
		saveRemoved();
	}

	private void saveWorking()
	{
		if (account == null)
		{
			return;
		}
		if (Template.DEFAULT.equals(active))
		{
			storage.unset(WORKING_KEY);
			storage.unset(DIRTY_KEY);
			return;
		}
		storage.set(WORKING_KEY, gson.toJson(working));
		if (dirty)
		{
			storage.set(DIRTY_KEY, "true");
		}
		else
		{
			storage.unset(DIRTY_KEY);
		}
	}

	private void saveRemoved()
	{
		if (account == null)
		{
			return;
		}
		if (removed.isEmpty())
		{
			storage.unset(REMOVED_KEY);
		}
		else
		{
			storage.set(REMOVED_KEY, gson.toJson(removed, REMOVED_LIST));
		}
	}
}
