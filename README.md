# Enhanced Potion Storage

A RuneLite plugin that shows the bank's potion storage as bank slots, with **a slot for every dose**:
Prayer potion(4), (3), (2) and (1) side by side, each showing how many potions of that dose you could
withdraw. The slots are sorted by a template you choose, keep placeholders like real bank items, and take
up no bank space.

Built on [Potion Storage](https://github.com/ziggle-dev/potion-storage) by ziggle-dev (BSD 2-Clause),
which it is a separate plugin from. The two draw onto the same bank slots, so this one turns itself off
while that one is on.

## How the dose slots work

Potion storage keeps one **withdraw dose** per potion, and a withdrawal always pays out that dose.
Jagex's rules allow one game action per click, so:

- The slot at the potion's withdraw dose is the one that withdraws. Its dose number, bottom left, is
  **green**. Its left-click follows the bank's quantity setting (1, 5, 10, X, All).
- Every other dose slot's left-click is **Set-dose**: it sets the withdraw dose to that slot's dose, the
  same thing as the dose option in potion storage itself. Click again to withdraw. Its Withdraw options are
  hidden so they can't pay out a different dose than the slot shows.
- **Doses** in a slot's right-click menu changes which dose that slot shows. This is a change to your
  layout only; nothing is sent to the game. Two slots may show the same dose.
- A slot's count is the doses stored divided by its dose, rounded down. 172 prayer doses show as 43 / 57 /
  86 / 172 on the 4 / 3 / 2 / 1-dose slots.
- A dose slot with none left becomes a placeholder when the bank's **Always set placeholders** is on, and
  frees its place when it is off, as a bank item does.
- A real bank stack of the same potion and dose is combined into the slot, with both counts, as in the base
  plugin.

## Templates

The sidebar panel's **Template** picker chooses the layout. The built-in **Default** sorts potions into:

1. Combat-Boosting Potions
2. Divine Potions
3. Restore & Recovery Potions
4. Stamina & Mobility Potions
5. Defensive & Anti-Poison/Venom Potions
6. Bossing & Special Brews
7. Utility
8. Raid Potions (empty: no raid potion goes in potion storage, so it is there to move potions into)
9. Mixes & Unfinished Potions

Default never changes. Edit anything while it is open (drag a slot, change a dose, reorder, hide a potion)
and the change is saved straight into a new custom template, which opens.

A custom template can change category order, potion order, which category a potion is in, the categories
themselves, which doses each potion shows (with **Show every dose of every potion** off), which categories
are on, and where each potion shows (bank, tag tabs, searches). Saving it also saves where its slots sit in
the bank.

- Edits are kept, across restarts too, but the template only changes when you **Save**. **Revert** drops them.
- Switching templates with unsaved changes asks whether to save them first.
- **Export** and **Import** write and read a template as a `.json` file, placement included. On another
  account, slots whose tab or neighbouring items aren't there fall back to the main bank view.
- Templates, unsaved changes and the plugin's settings are kept **per account**.

## The sidebar panel

All in one window:

- **Template** picker, with Save, Save as, Revert, Rename, Delete, Export and Import.
- **Show every dose of every potion**.
- **Bank place**: send every slot, together, to the main bank view, a numbered bank tab, or a bank tag.
- **Missing slots**: every slot your layout asks for that the bank isn't showing, as `+dose` chips. Click
  a chip for one slot, a potion's name for all of its slots, or **Restore all**. **Recently removed** keeps
  your last 5 removals, with Undo, until a template is opened.
- **Layout**: categories (tick to turn on or off, arrows to reorder, right-click to rename or delete) and
  their potions (click a dose to add or remove its slot; right-click to move it or edit its slots).
- **Dose slots**: every slot in storage, in bank order, with its count. Click one to choose where its
  potion shows: the bank, tag tabs or searches.

## Development

Requires a JDK (11 or newer; the build compiles for Java 11).

```
./gradlew test       # automated tests
./gradlew run        # RuneLite in developer mode with this plugin loaded
```

On Windows, `run-dev.bat` does the same as `./gradlew run`.

`./gradlew run` starts RuneLite with its own configuration profile, `enhanced-potion-storage-dev`. That
profile has none of your normal profile's Plugin Hub plugins or settings, and nothing you do in it changes
them. So you can test here without turning off the base Potion Storage plugin in your everyday setup.

### Logging in with a Jagex account

The developer client can't be started from the Jagex Launcher, so it needs your launcher session once:

1. Open the RuneLite launcher's configuration: on Windows, the start menu entry **RuneLite (configure)**.
2. In **Client arguments**, add `--insecure-write-credentials`, then save.
3. Start RuneLite once from the **Jagex Launcher**. It writes your session to
   `%USERPROFILE%\.runelite\credentials.properties`.
4. Take the argument back out (so later launches don't rewrite the file).
5. `./gradlew run` now logs straight in with that session.

`credentials.properties` lets anyone who has it log in as you: never share or commit it. Delete it when
you're done testing. To cut the session off entirely, end your sessions from your Jagex account settings.

### Staying within the rules

The developer client connects to the real game, so test with the same care as normal play. Everything this
plugin does is a bank click you could make yourself, one action per click: withdrawals and Set-dose are
redirected to potion storage's own buttons (the same redirection RuneLite's Bank Tags plugin uses), and
everything else (layout, placeholders, Doses, templates) stays in your client and sends nothing to the
game.

## Licence

BSD 2-Clause: see [LICENSE](LICENSE). Copyright ziggle-dev (the base plugin) and jcsoutherland (the
enhancements).
