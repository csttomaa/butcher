# Changelog

One entry per jar that was actually handed out for testing. The version number bumps on
every build, so the gaps between entries are builds nobody ran - not missing notes.

## 0.2.7-beta — 2026-09-24

### Fixed
- Chat after a Vanguard corpse, properly this time. The messages from during the Thaw were
  being put back while the profit window was open and had faded out by the time you closed
  it. Now everything - what arrived during the Thaw, the haul summary, and anything during
  the profit window - appears together, in order, the moment you close the window.
- Messages other mods hide (like the location lines SkyHanni and Skyblocker swallow) no
  longer reappear in chat after a cutscene.

## 0.2.6-beta — 2026-09-24

### Fixed
- Chat that arrives during a cutscene is no longer lost. It used to be thrown away for the
  whole Thaw (and the Scatha cutscene); now it is held back and appears in chat, in order,
  the moment the cutscene ends. The profit window no longer holds chat back at all.

### Changed
- `/butcher test` runs are no longer counted in the stats - no test corpse, pet, gemstone
  or cut adds to the numbers or moves a dry streak. (Numbers already counted from tests stay
  until you reset that section in the menu.)

## 0.2.4-beta — 2026-09-24

### Added
- **Profit window after the Thaw**: every drop from the corpse with what it is worth - bazaar
  instant-sell, or lowest BIN on the auction house for the Dye, the Locket and the Wisp - the
  Skeleton Key it cost (bazaar instant-buy) taken off, and the profit or loss at the bottom.
  Prices are fetched while the Thaw plays. Crystals and Glacite Powder can't be sold and
  count as 0. Any key or click closes it. **Profit window after the Thaw** in the menu turns
  it off; **Live prices** (was "Live Locket price") turns off all price fetching.
- Glacite Powder is recognised as a drop, with its own icon: a heap of light blue powder like
  the old light blue dye. Drop a 16x16 `glacite_powder.png` into `config/butcher/textures/`
  to use any texture you like for it.

## 0.2.3-beta — 2026-09-24

### Changed
- The faces at the back of the ice, the ones you see through the front, now meet cleanly
  as well - they were drawn mirrored, which put their edges in the wrong places.
- The Vanguard in the ice is solid instead of see-through.

## 0.2.2-beta — 2026-09-24

### Changed
- The T of the Vanguard's visor now runs edge to edge across his helmet and all the way
  down, as on the real one.

## 0.2.1-beta — 2026-09-24

### Changed
- The Vanguard in the ice is a dark shadow again, not a painted figure - but now it is his
  real shape, helmet and pauldrons and all, still frozen in one of the six poses. Only the
  T of his visor catches a little light.
- The ice chunk's faces now meet cleanly. There was a jagged grey fringe round the outside
  and thin gaps between faces.

## 0.2.0-beta — 2026-09-24

The Vanguard release: everything from 0.1.25 to here - the Thaw, its five endings, the
Locket's and the Dye's big endings, the colour cycle - plus:

### Changed
- **The Vanguard himself is frozen in the ice**, as a real 3D figure in his blue armour
  instead of a dark silhouette. He is caught in one of six poses - shielding his face,
  reaching up, mid-swing, huddled, falling back, bracing - picked per corpse and sometimes
  mirrored, so corpses in a row don't look the same.
- **The ice is a rough chunk instead of a box**: leaning sides, a sloping top and chipped
  corners, with finer frost. The cracks are thinner so you can see him through them.

## 0.1.37-beta — 2026-09-24

### Changed
- The last strike's colour cycle is now blue, purple, gold, three times round, with no white.
  It comes back round to blue just before it lands, so a legendary jumps from blue to gold.

## 0.1.36-beta — 2026-09-24

### Changed
- The flare glow is back to how it was before 0.1.34 - the bigger, brighter one was too much.
- The last strike's colours now cycle: white, blue, purple, three times round, fading into
  each other and speeding up, before landing on the real colour. A rising sound builds under
  the whole cycle, with a tick for each colour, and cuts out as it lands.
- The cycle is the same length for every drop, so the last hit lands at the same moment
  whatever you got. Nothing before that moment gives the result away.

## 0.1.35-beta — 2026-09-24

### Changed
- Every Vanguard corpse now strikes at the same pace, whatever it drops. An all-commons
  corpse used to play at half speed, which gave the result away from the first hit. Losses
  are now a little longer (about 8 seconds). Only the ending, after the last strike,
  is shorter for a loss than for a win.

## 0.1.34-beta — 2026-09-24

### Added
- **The Shattered Locket gets its own ending.** Slow motion on the last hit, black bars
  close in, the block blows apart and the Locket's pieces circle faster and faster to a music
  box until they snap together in a gold flash, with a fanfare and a choir. Coins rain, the
  name is stamped out letter by letter, and a counter ticks up to its **live lowest BIN**
  on the auction house (NPC price if the price site can't be reached).
- **The Frostbitten Dye's ending is much bigger.** Slow motion, then everything - the tray
  included - drains to grey and freezes, chips hanging in the air. Two heartbeats, silence,
  and a thin pink crack spreads through the ice until the whole thing bursts into paint that
  splatters the screen and runs down. The colour floods back, the bottle spins, and pink
  sparkles drift upwards for the rest of the ending.
- Both special endings show how rare the drop is ("1 in ~540 corpses") and which one this
  is for you ("Your first ever Frostbitten Dye!"), and keep a shimmer on their tray slot.
- `/butcher test vanguard locket`.
- **Live Locket price** switch in the Vanguard menu group.

### Changed
- **The flare never lies any more.** Every strike glows the true colour of what it drops.
  The last strike glows white and then climbs - blue, violet, gold - one step at a time,
  each with its own note, and stops on the truth. Every step takes the same time.
- The flare is much easier to see: a bigger, softer glow, stronger colours, a tint over the
  whole screen and more of the colour in the ice itself.

## 0.1.32-beta — 2026-09-24

### Changed
- The Thaw is properly centred. The ice block used to sit left of the middle to make room
  for the pick, and being turned so one side shows pushed it further left, while the item
  names and the tray were centred on the screen - two different centre lines. The block
  is now placed by the middle of what is actually drawn, lined up with everything else.
- On screens narrower than 16:9 the block and pick shrink together, so the raised pick no
  longer runs off the right edge.
- Items in the tray fit inside their slots; turned three-quarters, the heads used to come
  out a third too wide.
- The "VANGUARD CORPSE" title is gone.
- Item names and the closing "corpses since your last legendary" line sit lower, clear of
  the block even when it has zoomed in at the end of a run. The closing line has the same
  black outline as the item names.

## 0.1.30-beta — 2026-09-23

### Added
- More settings in the menu's **Vanguard Thaw** group: **Speed** (50-200%), **Flare before
  each strike** on/off, **Item name size**, and **Ending volume** for the legendary and
  Dye endings, separate from the Scatha's pet sound.

### Changed
- Pick strikes are heavier and last longer: a longer wind-up, a ringing steel hit, and
  loose ice crumbling off afterwards. Winning runs got the extra length; an all-commons
  corpse still wraps up in under five seconds.
- The flare sounds sit lower and get brighter through overtones instead of pitch, and each
  strike rings on the note of the flare before it - so the gold flare resolves into the hit
  instead of being cut off by it.
- Drop names in the Thaw are bigger, with a solid black outline all the way round so they
  read against the ice.
- The menu is back on Minecraft's own font.

## 0.1.28-beta — 2026-09-23

### Changed
- `/butcher test` is split in two: **`/butcher test scatha …`** and
  **`/butcher test vanguard …`**. Typing any level on its own - `/butcher test`,
  `/butcher test scatha`, `/butcher test scatha pet` - lists what is under it. Each option
  is clickable and puts the full command in the chat box.
- A rolled Vanguard corpse is now `/butcher test vanguard random`; the Scatha's is
  `/butcher test scatha random`. The firework and the pre-kill hide moved under `scatha`.

## 0.1.26-beta — 2026-09-23

### Changed
- `/butcher test vanguard explode` ends on a random legendary - Shattered Locket, Caged
  Wisp, Frozen Scute or Skeleton Key - instead of always the Skeleton Key.
  `/butcher test vanguard reveal` likewise picks a random uncommon instead of always an
  Umber Plate. Picked evenly, so every item turns up about as often as the others.

## 0.1.25-beta — 2026-09-23

### Added
- **The Thaw** — the Vanguard Frozen Corpse cutscene. A slab of ice with the corpse frozen
  inside, opened one pick strike per roll. Each strike flares a colour, cracks the ice and
  throws one drop into the tray along the bottom, worst drop first and best drop last.
  - Four endings, decided by the best drop: **crash** (all commons), **reveal** (an
    uncommon), **explode** (a legendary) and, for the Frostbitten Dye, the colour draining
    out of everything and flooding back pink.
  - The flare never lies about a real win — a legendary always flares gold — but ordinary
    strikes flare gold too, more often when a legendary is still to come.
  - Losses play briskly (~4.4s), wins are drawn out (~10s).
  - The 21 head drops are drawn as real 3D heads with their real SkyBlock skins; the other
    5 through the game's own item renderer.
  - New synthesised sounds for every beat: the ice crunch, the flare, landing notes, the
    crash, the detonation, and the Dye's drain and flood.
  - The haul is printed to chat when the cutscene ends (or is skipped), since the loot
    message is hidden while it plays. Two corpses opened back to back play one after the
    other.
- `/butcher test vanguard` rolls a corpse against the real loot table;
  `/butcher test vanguard crash | reveal | explode | dye` plays a chosen ending.
- **Vanguard Thaw** group in the menu's gambling tab: on/off, and hide the loot message.

## 0.1.0-beta — 2026-09-21

First release: the Scatha cleaver cutscene. The jars between this and 0.1.25 predate the
changelog - the git history has them.
