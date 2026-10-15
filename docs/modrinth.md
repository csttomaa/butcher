# Publishing on Modrinth

Everything the Modrinth project page and the first version upload ask for, ready to paste.

## Project

| Field | Value |
| --- | --- |
| Name | Butcher |
| Slug | `butcher` (or `butcher-skyblock` if taken) |
| Summary | Cutscenes for Hypixel SkyBlock's rare drops. Your loot stays hidden until the reveal, so every kill is a little gamble. |
| Icon | `src/main/resources/assets/butcher/icon.png` (256 x 256) |
| Categories | Utility, Decoration |
| Client side | Required |
| Server side | Unsupported |
| License | MIT |
| Source code | https://github.com/csttomaa/butcher |
| Issue tracker | https://github.com/csttomaa/butcher/issues |

**Description:** paste `README.md`, minus the centred icon block at the top (Modrinth shows the project icon
already, and the image's path only works on GitHub). The "Building from source" section can go too.

**Gallery** (optional, but it is what sells a cutscene mod): a screenshot or GIF per animation, taken with "Try it" in
the menu. The Thaw's Dye, Diana's Relic and Hellion's High Class dice are the showiest.

## Version 0.3.3-alpha

| Field | Value |
| --- | --- |
| File | `build/libs/butcher-0.3.3-alpha.jar` (not the `-sources` jar) |
| Version number | `0.3.3-alpha` |
| Version name | Butcher 0.3.3 (alpha) |
| Release channel | Alpha |
| Loader | Fabric |
| Game version | 26.2 |
| Dependencies | Fabric API - required; Mod Menu - optional |

`.\gradlew.bat build` builds `0.3.3-alpha` and then bumps `gradle.properties` to `0.3.4-alpha` for the next build,
so the jar to upload is the one the build just named, not the number now in the file.

### Changelog

```markdown
The first public release.

- Animated cutscenes for SkyBlock's rare drops: the Scatha, Vanguard corpses, Diana's Mythological Ritual,
  the Inferno Demonlord IV, lava fishing, plus quick pop-ups for the Fishing Festival's Great White Shark and the
  Dwarven Mines' Ghosts.
- The drop stays hidden (chat, HUD, loot sound, floor loot) until the reveal, and nothing before the reveal
  depends on what dropped.
- You keep control while it plays. Chat that arrives in the meantime is held and put back afterwards.
- Settings with `/butcher` or Mod Menu: per-animation switches, speed, volume, size, how busts play, and a
  "Try it" button for every outcome.
- Stats: dry streaks and drops per source.
- Hold Backspace to skip.

The Voidgloom Seraph's animation is under maintenance and switched off in this release.
```
