# Butcher

A Fabric client mod for **Minecraft 26.2** on Hypixel SkyBlock. When a rare drop is on its
way, Butcher keeps it hidden and plays a cutscene for it instead. The drop is revealed at
the end, and the cutscene tells you nothing about it until then.

You keep control the whole time: cutscenes play over the game, so you can still move, look
round and fight.

## What plays

| Where | Animation | Plays on |
| --- | --- | --- |
| Crystal Hollows | **The Cleaver** | every Scatha kill - a pet, gemstones or Dwarven O's |
| Glacite Mineshafts | **The Thaw** | every Vanguard corpse you open, then its profit or loss |
| Diana's Mythological Ritual | **The Chopping Block** | Sphinx, Minos Inquisitor, Manticore and King Minos kills; a Minos Relic; a Mythological Dye |
| Inferno Demonlord IV | **Hellion** | every kill of your own boss |
| Voidgloom Seraph IV | **The Seraph's Glyph** | every kill of your own boss |
| Crimson Isle lava fishing | **Eruption** | Lord Jawbus, Thunder, Ragnarok and Fiery Scuttler kills; Plhlegblast only for the Carmine Dye |
| Fishing Festival | **Fin** | a short pop-up for a Megalodon pet or a Great White Shark Tooth |
| Dwarven Mines | **Wisp** | a short pop-up when a Ghost drops Sorrow or Plasma |

The cutscenes only mention a drop once it's real. Nothing before the reveal depends on what
dropped, so a long build-up is never a promise. The big drops get their own endings, a
price counted up under them, and the odds.

While a cutscene plays, Butcher hides what would spoil it: the HUD (except, if you like,
your hotbar and health), chat, the game's own drop sounds, and a slayer boss's loot on the
floor. Chat that arrives in the meantime is held and put back afterwards, not lost.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3 or newer for Minecraft 26.2.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2 in `.minecraft/mods`.
3. Put `butcher-<version>.jar` in the same folder.

Needs Java 25, which the Minecraft 26.2 launcher already ships with.

## Using it

- **`/butcher`** opens the settings. Each animation has its own group: switch it on or off,
  choose which creatures play it, set the pre-kill hide, the size, the volume and how busts
  play. Every group has a **Try it** row that plays any outcome right now, without counting
  it in your stats. (Click to pick an outcome, right-click to go back.)
- **Hold Backspace** to skip a cutscene: a win jumps to its showcase, a bust ends. You can
  rebind it in Controls, under Butcher.
- The **Stats** tab keeps your dry streaks and drops for every source. Only real kills count.

| Command | Does |
| --- | --- |
| `/butcher` | opens the settings |
| `/butcher reload` | re-reads `config/butcher.json` and redraws the generated art |
| `/butcher log` | the last 40 detection decisions: every kill accepted or refused, and why |
| `/butcher skins` | whether the Scatha's real skin has been captured yet |
| `/butcher skins forget` | forgets the captured Scatha skins so they are captured again |
| `/butcher records` | how many drop records are on disk (when Drop records is on) |

If a cutscene plays when it shouldn't, or doesn't play when it should, `/butcher log` says
why. That output is the most useful thing to include in a bug report.

## What it sends and writes

**Network** (all optional except the skins, and only item IDs are sent - nothing about you):

- `api.hypixel.net` - bazaar prices, and `sky.coflnet.com` - lowest BIN. Both are used for
  the prices under drops and the Vanguard profit window. Turn **Live prices** off (General
  tab) and neither is contacted.
- `textures.minecraft.net` - the skins of a few SkyBlock heads, cached in
  `config/butcher/skin_cache/`.

**Files**, all under `.minecraft/config/`:

- `butcher.json` - your settings. If it ever can't be read it is moved to
  `butcher.json.broken` and the defaults are used.
- `butcher/` - your stats, the captured Scatha skin, the skin cache, and the generated art
  in `butcher/textures/` (see below).
- `butcher-drops.jsonl` - only when **Drop records** is on (it is off by default). It keeps
  each real kill and drop with the chat around it, for bug reports. It stays on your computer.

## Changing the art

Most textures are drawn by the mod when they're first needed, and written to
`config/butcher/textures/` so you can edit them. An edited file is never overwritten; to go
back to the built-in art, delete the PNG. Turn this off with **Write art to config**.

The Scatha's own texture belongs to Hypixel and is taken from a live worm the first time you
meet one. To supply it yourself, put `head.png` and/or `body.png` in `config/butcher/skins/`.
A `.gltf` or `.glb` box model in `config/butcher/models/` replaces the worm's mesh.

## Building

```
.\gradlew.bat build        (Windows)
./gradlew build            (Linux / macOS)
```

The jar lands in `build/libs/butcher-<version>.jar`. Gradle downloads a JDK 25 toolchain
the first time if it can't find one. `mod_version` in `gradle.properties` is
`x.y.z-alpha|beta` and its patch number goes up by one after every successful build.

`/butcher test …` and `/butcher debug` are developer commands. They only exist for the
accounts listed in `Dev.java` (and in a development run). For everyone else they don't
appear at all.

The technical notes are in `CLAUDE.md`.

## Credits

- The HUD fade, the gameplay window and the HUD-hiding approach are ported from **SkyMaxx**
  (MIT, see `LICENSE`).
- Scatha detection follows **Scatha-Pro**'s approach.
- Item and creature head skins and item IDs come from the **NEU item repository**; the
  Vanguard loot weights are cross-checked against **SkyOcean** and the SkyBlock wiki.
- Prices from Hypixel's public API and **Coflnet**.

Butcher is not affiliated with Hypixel or Mojang.
