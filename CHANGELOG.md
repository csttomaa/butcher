# Changelog

One entry per jar that was actually handed out for testing. The version number bumps on
every build, so the gaps between entries are builds nobody ran - not missing notes.

## Unreleased - The Butcher's Ledger

### Added
- **The Ledger tab**: The Butcher's Ledger, a leather-bound book of ink stamps. Shut, its cover shows how many
  stamps are in it; opened, each animation is a chapter behind its own coloured divider tab, with a title page
  (its numbers and the stamp you are closest to) and a page of stamps. Click a stamp for its own page: what it is
  for, its grades, how far along you are, when you earned it.
- **59 stamps**, tiered ones graded Select, Choice, Prime and A5 Wagyu: kill counts, every rare drop, and luck -
  Beginner's Luck, Back to Back, Dry Aged, Night Shift, Hot Streak, Jackpot 777, Tycoon. Hidden ones show as "???"
  until earned.
- **Your old stats count.** The first launch stamps everything they already prove, marked "pre-ledger" with no
  date. The stamps that need history the stats never kept start counting now.
- **A stamp pop-up**: earned in game, a stamp thumps down on screen with its own sound - after the cutscene, never
  over it. Switch it off under General, set its volume under Sound.
- Stamps outlive every stats reset.

- **33 more stamps**, so every chapter fills a page: drop counts, every Demonlord and lava drop, Caged Wisp, Frozen
  Scute and Skeleton Key from corpses, Inquisitor kills, Dwarven O's busts, a drop worth 10M+ in one go, and the Ghosts'
  and sharks' runs of luck (two drops in ten minutes, three in an hour, Sorrow and Plasma together, one at midnight).

### Safety nets
- **`/butcher stop`**, for everyone: ends any cutscene or pop-up at once, gives back the HUD, chat and sound, and
  keeps the pre-kill hide off for 15 seconds.
- **An animation that hits an error stops** instead of taking the game with it: the screen is handed back, the error
  goes to the log and `/butcher log`, and chat says so once. Three errors and that animation is off until restart.
- **A watchdog**: a cutscene still up after 90 seconds is ended; a pre-kill hide stuck on for 45 seconds with nothing
  playing lets the HUD back for a minute.
- **Hypixel only**: nothing is detected on other servers or in singleplayer. The menu and "Try it" work anywhere.
- **No floods**: more than 3 cutscenes of one kind in 10 seconds, the same drop counted twice within 2 seconds, or
  more than 20 kills in a second is a detection bug, and is logged and ignored instead of played or counted.
- **Slow frames**: a cutscene running under ~20 FPS switches to calm effects and drops the gameplay window.
- The hooks that hide dropped items and tell other mods the HUD is hidden can no longer stop the game from starting
  if a game update moves them; `/butcher log` says whether each is active.
- **Stats backups**: a copy of `stats.json` per day (the newest three), and one from before the Ledger, kept for good.
- The Ledger trusts nothing time-based while the computer's clock is behind the last drop, cleans up a hand-edited
  file on load, shows at most three stamp pop-ups in a row (then "+N more"), and `/butcher ledger recheck` stamps
  anything your stats prove that the book is missing.

### Changed
- **The Ledger opens on its own**: the menu steps aside and the book drops in, larger, a little crooked on the desk,
  and swings open. Turn its pages by clicking or dragging a folded corner, with the scroll wheel, or with the chapter
  tabs - a page lifts and goes over the spine. Pencil notes, doodles, tally marks and the odd coffee ring on the
  pages. Esc, the back button or the ribbon put it away.
- The menu is roomier: a wider, taller panel with more space between rows.
- "Try it" plays busts the way your Busts setting does: short when it is short, and not at all (with a note in chat)
  when it is off.
- On a narrow window the menu's tabs show their icons only.

## 0.3.3-alpha - the first public release (2026-10-15)

### The menu, redesigned
- A new look: the mod's icon and a gold title, embers drifting over a darkened game, a framed panel with a gold trim,
  and tabs with icons and a sliding underline.
- Every animation is a card in its own colour with an item icon, a line saying what it plays on, and **its on/off
  switch right on the card** - no need to open it to turn it off. Switched-off cards dim.
- Animated switches, sliders with a real track, `< value >` pickers, "Play" buttons, a two-click red reset.
- The Stats tab shows each dry streak on its card, and large inside it.
- The panel fits its content, scrolling is smooth, and the menu reopens on the tab and cards you left open.

### The Seraph's Glyph is under maintenance
- The Voidgloom Seraph's animation is **switched off for this release** while it is reworked. Its card in the menu is
  taped over ("UNDER MAINTENANCE") and cannot be opened or switched on; its volume slider and stats card are hidden.
  Nothing of it runs: no detection, no pre-kill hide, no hidden floor loot, no cutscene. A saved setting is kept for
  when it comes back.

### Added
- **A new icon**: a gold pixel-art B with its corner chopped off. The menu shows it pixel-sharp beside the title.
- **A Speed setting for every animation** (50-200%): the Chopping Block, Hellion, Eruption, Fin and Wisp join the
  Thaw's Speed and the Scatha's Full run. The whole animation - picture, sound cues, skip target - runs on one clock
  at that pace; what it shows never changes.
- **Mod Menu support** (optional): its Configure button opens the Butcher menu, and Esc goes back to the mod list.
  Opened from the title screen, "Try it" says to join a world first instead of playing into nothing.

### Changed
- **`/butcher test` and `/butcher debug` are developer-only.** They exist only for the accounts in `Dev.java` (and
  in a development run). For everyone else they are gone, tab completion included. Players use the menu instead.
- **Every animation has "Try it" in the menu**, the Scatha and the Thaw included (they had none). Outcomes show
  their real names ("High Class Archfiend Dice", not "High class dice"). Right-click steps back through outcomes,
  busts and the gameplay window's corner.
- **Drop records are off by default** for new installs. They keep chat lines (other players' too) in a file that is
  never pruned, which is for bug reports, not for everyone. An existing setting is left as it is.
- The slayers' banner setting is called "Drop banner": it shows the drop's own tier (RARE DROP!, VERY RARE DROP!...),
  not always "PRAY TO RNGESUS DROP!".
- Menu: group names are consistent, the Seraph's hide slider has its explanation, the panel fits narrow windows,
  and the Stats tab has Wisp's Sorrow and Plasma.

### Fixed
- Fin and Wisp sat over the action bar (skill XP, mana...) above the hotbar. They are 18 pixels higher.
- **Switching the Scatha cutscene off silenced every other animation.** The synth only ever started inside the
  Scatha's tick.
- A skin download that failed (offline, or a slow textures.minecraft.net) was retried every tick, with a new
  thread and a log warning each time. It waits two minutes now.
- The Vanguard profit window no longer opens over a chest or your inventory (that closed it on the client only). It
  waits until no screen is open, and gives up after 20 seconds.
- Stats and settings are written atomically, so a crash mid-save can't leave a half-written file. An unreadable
  `butcher.json` or `stats.json` is kept as `.broken` instead of being overwritten with defaults.
- Hand-edited settings out of range (a negative volume, a misspelt mode) are put back in range instead of crashing.
- On a brand-new install, setting the Chopping Block to 100% in the first session moved it to 130% on the next launch.
- Testing the Scatha with its cutscene switched off played nothing; testing it during a real kill could hand the
  test's drop to the real cutscene. Testing the Thaw with it switched off played nothing either.
- The pre-kill hide test ran only with the Scatha cutscene on.
- Held chat, and cutscenes waiting their turn, are dropped when you leave the server instead of following you
  into the next one.
- The finished Paragon Shard head capture no longer prints "thanks!" to players opening its bazaar page.
- fabric.mod.json linked to Scatha-Pro's repository and described only the Scatha. The README described only two
  of the eight animations and still said chat is dropped during a cutscene.
- `LICENSE` now carries SkyMaxx's MIT notice for the code ported from it.
- **The Wisp group was missing from the menu** unless the Fishing Festival group was open. Groups no longer hand
  on to each other, so a closed group can't hide the next one again.
- **Stats now count every kill**, not only those that got a cutscene. With "Busts: none", a creature switched off,
  or another cutscene already playing, a bust used to leave the dry streak and the kill count untouched. Diana's
  kills with no screen are counted as busts once their drop has had time to come.
- **Skipping the Scatha before its drop was known opened a second cutscene** when the drop came in. The drop now
  goes to the skipped run: counted, summarised in chat, not played.
- **A Scatha pet skipped before its reveal was never counted** (nor summarised). It is now.
- A drop line arriving in the last quarter-second before Hellion's, the Seraph's or Eruption's reveal was
  swallowed: the kill played as a bust and the win never showed. It now gets a cutscene of its own; the Chopping
  Block does the same past its deadline.
- Lava fishing: a win from a creature you switched off could play under the previous creature's name; a known
  kill's drop skipped the "Only play drops worth at least" floor.
- **Sound crackled with the volume turned up.** The synth's soft clip jumped from 0.83 to 1.0 at full scale, a
  click on every loud moment past 100%. It is a smooth curve now.
- Hellion, the Seraph and Eruption no longer fire a backlog of sounds all at once when frames resume after the
  window was minimised or the game hitched.
- The Chopping Block's price line ran off the bottom on short screens (1080p at GUI scale 4, 720p at 3); its
  showcase text now stays on screen. Hellion's pillar timers ran off the side on 4:3 screens.

## 0.3.2-alpha — 2026-10-12

### Fixed
- **Hellion**: a boss with the Shuriken's ✯ mark is no longer taken for yours. 0.3.1 did that, and anyone can put
  the mark on any boss.

## 0.3.1-alpha — 2026-10-12

### Fixed
- **Hellion now plays for every Demonlord you kill.** Checked against a friend's 131 tier IV kills:
  - A boss that spawns together with the quest (no "SLAYER BOSS!" message, e.g. auto-slayer) was never picked
    up, so 22 of those kills played nothing - an Archfiend Dice among them.
  - A boss that first appears a little further away is picked up too.
  - Another player's boss next to yours could be mistaken for yours coming back from a demon phase, and play a
    bust when it died. Your own boss walking out of range and back is still yours.
  - If your own drop comes and your boss was never picked up, the drop still plays.
- **Diana's showcases stay on screen**: the Mythological Dye was so big its name and price ran off the bottom.
  Every drop is now at most a quarter of the screen high and lifted so its words fit.
- **Diana's rising specks** go behind the drop and its words instead of across them.
- **Diana's win rays** are finer and no longer draw as big see-through squares.

### Removed
- **Eruption**: the lava drips down the vent.

## 0.3.0-alpha — 2026-10-12

The polish pass: the last tier of the animation review.

### Changed
- **Eruption**
  - The sea creature is bigger, and its name floats over it for the first second.
  - Where it sinks, rings and bubbles run out over the lava. The crust then spreads out from that spot behind
    a glowing front, instead of the whole floor going dark at once.
  - The vent glows as lava in the heat's own colour; white heat is pale yellow with an orange rim, not sand.
  - At yellow and white heat the whole vent glows, not only its cracks. Its foot is rounder, and two drips of
    lava run down from the crater.
  - After an eruption a ring of broken stumps is left, and the pool darkens towards its edge.
  - The basalt pillars are lighter, so their stripes show. Lone blocks sit awash instead of floating. Every
    cluster stays clear of the screen's edges.
  - The smoke drifts as round puffs, not dotted lines.
  - A bust ends on a beat (the crater block slumps in with a puff of steam) and is a second shorter.
  - Flash I: a thicker lightning bolt, the vent flashing white as it strikes, and sparks crawling round the book.
  - The End Times' glowing cracks are straight, branching cracks, not worms.
- **The Seraph**: smaller End Crystals that stay on screen, and their beams are solid two-cell beams instead of
  dotted noise.
- **Hellion**
  - The four sigils are one set of runes.
  - Grey and white pillar fire keeps a warm base, so it reads as fire, not smoke.
  - The countdown ends in tenths (0.9 to 0.0, red), landing on 0.0 as the shield lands.
  - The hit counter only flashes red as a hit comes off.
  - On a bust the pillar fires go out one after the other, each with a puff of smoke.
- **Diana**
  - The board pops in solid with a puff of dust, instead of fading in see-through.
  - Each blow smears on the way down and kicks the camera down; the first blow bites deeper.
  - Blows throw spinning wood splinters, few on the first and more on each one after.
  - The cut face is meat with a pale rim, not plum cake.
  - The win's light comes out as eight wedge rays, not a grid of thin lines.
  - The odds and your count share one line ("1 in ~80 Minos Inquisitors · your 1st").
- **Fin**: the cleaver is gone. A fishing hook drops in, the shark goes for it, and the hook is yanked up with the
  drop under it. On a bust it comes up empty and swings.

## 0.2.90-beta — 2026-10-12

### Added
- **Wisp - Dwarven Mines Ghosts**: when a Ghost drops **Sorrow** or **Plasma**, a quick pop-up like Fin's.
  A Ghost (a pale charged creeper, its charge crawling round it) rises out of a strip of mist, comes
  apart into wisps with a crackle, and the drop pops out with its name and bazaar price. Nothing is
  hidden. Menu: "Dwarven Mines Ghosts - Wisp" (on/off, price, volume, Try it); `/butcher test ghost`.
  Drop records keep every Ghost line, so the real drop message can be checked.

## 0.2.89-beta — 2026-10-12

### Changed
- **The Byzantium Dye's dragon**: its loop round the beacon stays on screen, and at the end it rears up
  further back and higher - whole in the frame, its wings spread above the banner instead of across it,
  the dye and its words clear underneath.

## 0.2.88-beta — 2026-10-12

### Changed
- **Hellion, High Class Dice**: the pillars stay on screen while the camera is close in, the camera leans
  over the die less (two faces show, not one flat square), and the lit pips keep a gap between them.
- **The Seraph**: a faint end-sky glow along the horizon, so the Seraph, its beam and the dragon show
  against the night; the Eye ending no longer zooms in so far that the Seraph fills a third of the screen.
- **Eruption, Radioactive Vial**: a solid mushroom cloud and a solid green shockwave with dust, not
  scattered dots; Jawbus throws his arms up and out as it goes critical, and the vial shows in his chest.
- **Eruption, Kuudra**: the camera stays further back so more of Kuudra is in frame; the dye is a little
  smaller; the banners sit clear of the drop.

### Fixed
- **The mod's own chat lines** (the chat summary) are no longer recorded or read as drops.

## 0.2.87-beta — 2026-10-11

### Changed
- **Diana's win rays come out of the drop itself** and stay close round it, instead of starting at the
  block and running to the screen's edges behind the words.

## 0.2.86-beta — 2026-10-11

### Added
- **Busts: whole / short / none**, for Diana, Hellion, the Seraph and lava fishing. Short jumps a bust
  straight to its own ending once it is known; none plays only wins.
- **Only play drops worth at least...** (General, Prices): a cheaper win plays as a bust.
- **Chat summary**: one line in chat as each cutscene ends - what dropped, its price, which one of yours.
- **Calmer effects**: no screen shake and softer flashes, in every animation.
- **Try it**: every animation's group can play any outcome from the menu.
- **Quick Thaw**: a corpse with nothing above common skips to its last strike.
- **Reset all settings** (General). Your stats stay.

### Changed
- **The menu**: the first tab is "Animations"; Live prices moved to General; every animation's volume is
  on the Sound tab too; the skip key is explained; out-of-date notes fixed.
- **The Chopping Block is 30% bigger by default** (an untouched size setting follows).
- **Eruption**: the End Times' heated ground and Kuudra's carmine sea stay round the vent instead of
  flooding the whole view; the Carmine Dye rests below Kuudra's eyes; the vent's glow no longer hangs as a
  pale disc after the reveal.
- **Chimera I's purple vignette** is faint and only in the corners.
- **The Seraph's frozen world keeps its textures** instead of turning into plain grey blocks.
- **The Judgement Core's red rays are behind it**, and Hellion's coins no longer cover its banner.
- **A Scatha bust says what it gave** ("Just gemstones..." / "Dwarven O's...") instead of "It ran off...".

## 0.2.85-beta — 2026-10-11

### Added
- **Stats for Hellion, the Seraph, Eruption and Fin**: each has its own group in the Stats tab (dry streak,
  every drop, kills) with its own reset, and their reveals now say "your 3rd" after the odds.
- **A Diana bust says whose kill it was and your dry streak**: "Minos Inquisitor - 14 kills without a drop".
- **The Scatha pet's reveal**: rays in its rarity's colour turn behind the head, the name is stamped out a
  letter at a time, and the price counts up with the coins' sound.
- **Under every price, where it came from** ("bazaar instant sell", "lowest BIN on the auction house").

### Changed
- **Slayer banners use Hypixel's own word for the drop** (RARE, VERY RARE, CRAZY RARE, PRAY TO RNGESUS) from
  the drop line, in its colour.
- **Big drops are shown bigger**: Diana's expensive drops 1.25x and the Dye 1.45x; Eruption's up to 1.5x for
  the Carmine Dye.
- **Diana's showcase item floats gently** instead of standing still.
- **Glows are pixel art now**, in steps, in every animation - no more smooth blurs next to the blocky models.
- **"Hide the HUD below" is a percentage of the boss's health** for Diana, the Demonlord and the Seraph too
  (your old settings are converted).
- **"Enchanted Book (Flash I)" is just "Flash I"**, as Diana's "Chimera I".
- **Sound**: the Mythological Dye's ending is louder (+3 dB), the Seraph's bust louder (+5 dB), the Vial's
  detonation no longer clips, and skipping or a win cutting in plays a short tape stop.

## 0.2.84-beta — 2026-10-11

### Added
- **Hold Backspace to skip a cutscene** (rebind it under Controls, "Butcher"). A win jumps straight to its
  showcase with the price already counted; a bust ends.
- **Keep health and hotbar** (menu, on by default): while the HUD is hidden your hotbar, hearts, hunger,
  armour and XP stay on screen, drawn over the cutscene.
- **The Scatha pet gets a proper reveal card**: "PET DROP!", its rarity and name in the rarity's colour, its
  price at that rarity, and "Your Nth Scatha pet, after N kills". The head holds in the middle for it.

### Changed
- **A rare drop cuts in over a bust.** If a win lands while another cutscene is showing a bust, the bust ends
  and the win plays. Anything else waits its turn instead of being thrown away (a bust waits up to 5 s).
- **Slayer drops on the floor are hidden** from the moment the boss is nearly dead until its cutscene ends,
  so they can't give the reveal away behind it or in the gameplay window.
- **The fifth swing no longer means a pet**: now and then a Scatha bust gets a fifth swing that it ducks.
- **Eruption's drops glow with their own pattern** as they rise out of the lava, instead of a blank pale block.
- **The Seraph's black beam has a purple edge**, so a bust's tell can be seen against the night.
- **Every name, odds and price line has a dark plate behind it**, like a nametag, so it reads over bright lava.
- **Eruption fades out evenly**; the basalt pillars no longer linger over the game.
- **Eruption's bust shows the guaranteed drop's price as it is**, no counting up and no coin sounds, with
  Hypixel's own icon for it when its resource pack is on.

## 0.2.83-beta — 2026-10-11

### Fixed
- **Lord Jawbus floating up immune no longer opens Eruption.** While he floats, his nametag stops showing his
  health; the mod took that for his death. It now keeps following him until he really dies. The Inferno
  Demonlord is followed the same way.

### Changed
- **Drop records follow a boss's nametag through every change** (its immune phases too), four times a second
  instead of once, with its height above you.

## 0.2.82-beta — 2026-10-06

### Fixed
- **Eruption no longer plays for other players' sea creatures.** In a party, every Jawbus, Thunder and
  Scuttler someone else killed near you was opening a bust. Now only your own catches play, plus anyone
  else's whose rare drop actually comes to you by loot share.
- **The HUD hide only fires for your own creatures**, not for every party member's that gets low.
- **Charming someone else's Thunder** no longer counts as your kill.
- **Your own creature killed by a party member's last hit** is still caught, even when its health bar never
  read 0.

## 0.2.80-beta — 2026-10-05

### Added
- **The gameplay window in every new animation**: Hellion, the Seraph's Glyph and Eruption now show the game
  live in a corner, as the Scatha, Vanguard and Diana cutscenes do (menu: Gameplay window, same setting).

## 0.2.79-beta — 2026-10-05

### Changed
- **Eruption's vent:** the lava streams down its sides are gone, and the crater's opening is smaller.
- **The sides of the lava field are filled**: clusters of basalt columns stand out of the lava either side
  of the vent, with smoke rising off them and ash drifting down. The smoke thickens through the build-up,
  clears on a win and hangs on a bust.

## 0.2.78-beta — 2026-10-05

### Added
- **The Seraph's Glyph plays on real kills.** Every kill of your own tier IV Voidgloom Seraph (the one whose
  "Spawned by" is you) opens it the moment the boss dies; the drop line decides the ending. Written from a
  friend's recorded session. The four small endings (Chalice, Espresso, Sinful Dice, the Void Conqueror skin)
  play only while that drop is worth 500k or more.
- **Hide the HUD below** for the Seraph (default 15M HP).

### Changed
- **Eruption's lake runs to every edge** and fades into darkness with distance. The cliff is gone.
- **The vent is a real cone**: cooled blackstone round its foot, magma above, lava running down its faces.
- **The Radioactive Vial ending is now a meltdown:**
  - the eruption goes green;
  - Jawbus rises bigger with the vial in his chest, and it goes critical over six beats, faster and
    faster, with arcs crackling over him and the screen's edges throbbing green;
  - it detonates and blows him apart;
  - a green mushroom cloud rises behind, and the vial comes down out of it.

## 0.2.77-beta — 2026-10-05

### Changed
- **The cliff behind Eruption's lake is a broken rock face**: columns at different heights and depths, in
  shadow, its foot lit by the lake in whatever colour it is (orange, green, carmine).
- **The Radioactive Vial ending is grander:**
  - the lake turns into moving green lava (it was a flat green fill), with thin glowing seams through the
    crust;
  - the vial throbs in Jawbus's chest, faster and faster, as he raises his arms;
  - it bursts out of him in a flash and a ring of radiation racing across the lake; he reels back and sinks;
  - it stands in a column of green light for the rest of the ending.
- The lava's glow no longer washes creatures out.
- Kuudra's carmine sea is moving lava too.

### Fixed
- **A loot-shared lava creature's drop now opens Eruption even when its nametag was out of range.** From
  your records: a Thunder's Flash I came 36 ms after its loot share line, and would not have played.

## 0.2.76-beta — 2026-10-05

### Changed
- **Eruption's lake has a cliff behind it.** The low wall at the back left the game showing above it; now a
  blackstone cliff rises off the top of the screen, fading into the dark. The rim is blackstone too, instead
  of basalt.
- **The vent looks like magma.** Its blocks are darker with the cracks glowing in the heat's own colour (they
  were washed towards peach), and the crater on top is the game's moving lava in the heat's colour instead of
  a flat pale square.

## 0.2.75-beta — 2026-10-05

### Changed
- **The Mythological Dye has a new ending: "The Whole Hunt".** Everything up to the split is the same as
  before. Then:
  - the split goes wrong: the cut glows olive, the board cracks, and the cleaver is wrenched out and
    spun away;
  - half a second of silence, then a geyser of olive paint blasts out, throws the head open and rains on
    the board;
  - the whole bestiary comes out of it one head at a time, real skins, and lands in a ring round the
    block, each painted olive by its splash: the seven lesser creatures, the Sphinx, the Champion, the
    Manticore, the Inquisitor, and King Minos last, bigger, falling from the top of the screen;
  - the ring spins, Diana's moon rises behind it, and a silver arrow from the moon goes through every
    head in turn, bursting each into paint;
  - everything is pulled into the split, a shockwave blows the board away, and the dye rises in a column
    of light under WOW!, with paint running down from the top of the screen.
  - Its own sounds and score. About 19 seconds, the longest Diana ending.

### Fixed
- **Eruption's vent and Kuudra's tentacles were missing faces in game** (the vent's base looked like an
  empty grid, the tentacles like flat spikes).
- **Eruption's bust**: the guaranteed drop's name no longer sits on top of the vent.

## 0.2.74-beta — 2026-10-05

### Added
- **Eruption, the lava fishing animation.** It plays when your own Lord Jawbus, Thunder, Ragnarok or Fiery
  Scuttler dies, and for loot-shared kills too.
  - The creature sinks into a lava lake, a crust closes over it and a vent of magma blocks pushes up.
  - The vent's heat breathes black, red, orange, yellow, white, faster and faster - the same on every run -
    and lands on the drop's heat. Black is a bust: the vent cools and shows what that creature always drops
    (Magma Lord Fragment, Scorched Crab Stick) with its price.
  - Every drop has its own ending:
    - Chain of the End Times: the vent erupts and the chain rises out of the lava.
    - Severed Pincer: it snaps up out of the lava and clacks twice in a spray of sparks.
    - Flash I: lightning strikes the vent and the book rises crackling with charge.
    - Brimstone Handle: it is drawn up blade-first and hammered three times.
    - Radioactive Vial: the lava turns green, Lord Jawbus rises with the vial glowing in his chest, and it
      breaks free.
    - Scuttler Shell: the shell rises and the crab inside peeks out and scuttles away.
    - Burnt Texts: the sky darkens, three bolts strike, the ground heats like Ragnarok's arena, the book rises
      burning and the ground cracks open.
    - Carmine Dye: time stops, the whole lake turns carmine, the sea heaves, tentacles burst up all round,
      Kuudra rises behind and offers you the dye. "WOW!"
  - Plhlegblast only plays for the Carmine Dye.
  - Its own soundtrack.
- **A Lava Fishing group in the menu**: Eruption on or off, each creature on or off, loot-shared kills, hide
  the HUD when your creature gets low (default 4% of its health), the banner, the price and the volume.
- `/butcher test lava` plays any ending (`/butcher test lava bust thunder`, `/butcher test lava carmine jawbus`).

### Note
- Detection is written from your chat logs but no nametag has been seen yet. Fish with drop records on and
  send the file - `/butcher log` says why each kill did or did not play.

## 0.2.72-beta — 2026-10-05

### Fixed
- **Butcher now reads every chat line before other mods can hide it.** Feesh replaces Hypixel's sea
  creature catch lines with its own, and could hide the original before Butcher saw it, depending on which
  mod loaded first. Nothing changes for what you see in chat.

## 0.2.71-beta — 2026-10-05

### Added
- **Drop records now capture lava fishing**, for the coming lava animation:
  - every chat line about the rare lava creatures, their drops, the Carmine Dye, charms, loot share and
    double hooks;
  - each rare creature's nametag as it fights and dies;
  - what each creature is built from in game.

## 0.2.70-beta — 2026-10-05

### Changed
- **The menu's groups are separate.** The Fishing Festival and the slayer settings used to appear only
  while Diana's group was open. Now every group opens on its own.
- **A Slayers group**, with the Inferno Demonlord (Hellion) and the Voidgloom Seraph (The Seraph's Glyph)
  as their own sub-groups inside it.

### Added
- **Hellion settings:**
  - Hide the HUD when your Demonlord gets low (default 10M HP, 0 turns it off).
  - The "PRAY TO RNGESUS DROP!" banner on or off.
  - Show the price on or off.
  - Its own volume.
- **The Seraph's Glyph settings:** its switch, the banner, the price and its own volume.
- **Fin settings:** the bust (a shark of yours dying with nothing) can be turned off, and so can the
  price.

## 0.2.69-beta — 2026-10-04

### Added
- **Hellion now plays for real Inferno Demonlord kills.** Every kill of your own tier IV Demonlord
  opens the animation the moment the boss dies, and the drop decides how it ends. Only your own boss
  counts, recognised from Hypixel's "SLAYER BOSS! The Inferno Demonlord IV spawned!" line. Someone
  else's boss nearby never triggers it, and neither does a fight where the boss kills you. Chat is held
  until it's over, so the drop never shows in chat early. Built from a real tier IV session's chat lines
  and nametags. Turn it off under "Inferno Demonlord - Hellion" in the menu.
- The Paragon Shard shows its real head.

## 0.2.68-beta — 2026-10-03

### Fixed
- **The drop is shown in the middle of the screen in every Voidgloom ending.** As the drop comes in,
  the camera now centres on it, and the banner, item, name, odds and price sit in the middle,
  both across and up and down. The slow camera sway no longer pulls it off to one side.
- **The Ender Dragon is solid.** Parts that should be hidden, like the far wing and the legs behind
  the body, no longer show through it. It is now drawn the way the game draws models, with every pixel
  depth-tested, so its outline is clean too. It also uses your resource pack's dragon texture.

## 0.2.67-beta — 2026-10-03

### Fixed
- **The Byzantium Dye's Dragon ending no longer kills the frame rate.** Greying the world and
  painting it drew every texel of every pillar, crystal and the Seraph separately, up to about 80,000
  draws a frame. It now draws about 2,500 on average, close to the other endings. It looks the same,
  except the paint on the pillars and crystals is now one even shade of Byzantium.
- The same fix applies to Diana's gold and paint spreads.

## 0.2.66-beta — 2026-10-03

### Changed
- **Judgement Core: "The Eye Opens"**, replacing the old judgement ending.
  - The beam lands purple and everything freezes except the beacon's core.
  - Its glass shell cracks and bursts into shards, leaving the Judgement Core itself: the white cube
    with the red eye.
  - The eye opens, glances one way and the other, then stares at you with a heavy, deep pulse.
  - The Seraph and both crystals are pulled into the pupil in a spiral, and the Core rises with its
    name.
- **Byzantium Dye: "The Dragon's Tribute"**, its own ending.
  - The beam deepens to Byzantium, time stops, and the whole scene greys while a roar grows.
  - The real Ender Dragon, posed by the game's own model code, swoops in out of the void. It passes
    in front of the beacon, circles it and lands behind it with its wings spread. Its flight path was
    made in Blender.
  - It lowers its head and breathes Byzantium dragon's breath over the beacon. The breath pools into
    purple paint that spreads over the ground, the pillars and the crystals.
  - The beacon bursts and the dye rises out of the pool. The dragon leaps up behind the drop and
    rears with its wings wide, roaring, while purple embers rain down. It holds longer than any other
    ending.
  - Its own sounds: wingbeats in time with its wings, the landing, the breath, the paint, the roar.

## 0.2.65-beta — 2026-10-03

### Changed
- **The Seraph holds the glyph like a real enderman the whole time.** Its arms stay in the vanilla
  carrying pose: no lifting overhead. The glyph shakes in its hands as it charges, then leaves them in
  an arc.

## 0.2.64-beta — 2026-10-03

### Changed
- **The Seraph's drop is always shown in the middle.** The glyph lands in front of the Seraph's feet
  instead of off to one side, so the camera no longer drifts away.
- **The glyph is drawn in front of the Seraph.**
- **The throw can be followed.** The Seraph lifts the glyph overhead, holds it, then throws it in an
  arc the camera follows down. The build-up is about 5.4 s.
- **Void Conqueror skin:** the Seraph's head pops off with its jaw hanging open, and the jaw closes up
  as the skin spreads over it.

## 0.2.63-beta — 2026-10-03

### Added
- **The Seraph's Glyph: the Voidgloom Seraph's animation (tier IV, test only).**
  - **The build-up:** a vanilla enderman carries a beacon, the Yang Glyph, the way endermen carry
    blocks. It stands behind its hit shield ("100 Hits") between two End Crystals on obsidian
    pillars, whose beams feed the beacon. Every hit ticks the beacon's colour through black, white,
    light blue, magenta and purple, faster and faster. At 0 hits it casts the fight's 12 lasers, then
    throws the beacon down. The colour the beam lands on is the answer.
  - **Black is a bust:** the beam chokes out, the beacon turns to obsidian and the Seraph teleports
    away.
  - **Any colour is a win:** the beam erupts, the crystals blow up, the Seraph dies the vanilla way,
    and the drop rises up the beam.
  - **Its own endings:**
    - Handy Blood Chalice: the beam turns red and blood drips up into the chalice.
    - Pocket Espresso Machine: steam and a pour.
    - Sinful Dice: the die tumbles out.
    - Void Conqueror skin: the Seraph's head comes off and the skin spreads over it.
  - **The jackpot (Exceedingly Rare Ender Artifact Upgrade, End Stone Idol):** eight afterimages
    teleport in around the beacon and implode. A shockwave goes out and the End stone floats away.
  - **The judgement (Judgement Core, Byzantium Dye):** time stops and the Seraph kneels. The sky
    cracks open onto the End, a beam of judgement dissolves it, and the drop descends with a choir.
  - Its own sound throughout: the teleport "vwoop", a glass clink per hit in the beacon's colour, and
    the beacon powering up or down at the reveal.
  - Try it with `/butcher test slayer enderman <drop>`. It doesn't play on real kills yet.
- Slayer bosses' nametags are now recorded with Drop records on, so their detection can be written.

## 0.2.61-beta — 2026-10-02

### Changed
- **The High Class Archfiend Dice is a jackpot now.**
  - **Bullet time:** the die's last tip onto its 6 plays in slow motion. The music cuts out, you hear
    a heartbeat, and the camera pushes in.
  - **The pips:** once it's flat, the six pips light up gold one by one, each a note higher.
  - **The jackpot:** on the sixth, the screen shakes and a gold shockwave goes out. The camera pulls
    back as the pillars erupt in gold fire, gold rays turn behind the die and coins fountain out of
    it. Then the fanfare, and the name stamps in letter by letter.
- **The Flame Dye goes all out.**
  - **The freeze:** time stays stopped longer, for three heartbeats. Each beat an ember at the core
    swells and pulls the cold in.
  - **The supernova:** two rings of fire and a choir. The frozen rods catch fire and fly off, the
    pillars erupt, and a wall of fire rises along the whole bottom of the screen with embers going up
    over everything.
  - **The reveal:** a vortex of flame spirals into the dye as it rises. It stands bigger, in turning
    fire rays, its name burning in letter by letter, and it holds longer.

## 0.2.60-beta — 2026-10-02

### Fixed
- **Hellion's pillar fire is real Minecraft fire in game.** It showed as stretched streaks because
  the game only handed over the first frame of the fire texture. Now the whole animation is read,
  from your resource pack if you use one. It's vanilla's size, only licking up a little on each clang.

### Changed
- **The fire takes the shield's colour:** grey for Ashen, white for Spirit, gold for Auric, cyan for
  Crystal. The glow and embers match too.

## 0.2.59-beta — 2026-10-02

### Added
- **Fin has a bust.** When one of your own Great White Sharks dies and drops neither the Megalodon
  nor the tooth, the fin rises and the cleaver comes down, but the shark ducks under at the last
  moment. The cleaver splashes into empty water and a few bubbles come up. It's short, about 1.6 s.
  Try it with `/butcher test shark bust`. This is untested on real sharks: no Great White Shark had
  been recorded yet, so `/butcher log` says why each kill did or didn't count.

### Changed
- **The Megalodon's price is George's:** 2,500,000 coins for a legendary, 500,000 for an epic.

## 0.2.58-beta — 2026-10-02

### Changed
- **Hellion has its own sound.**
  - **Build-up:** fire crackling under the whole build-up, the rods whooshing past faster and
    faster, and a dagger clang on the shield at every step. Each attunement rings in its own voice:
    Ashen a dead thunk, Spirit a pale glass bell, Auric a warm gold bell, Crystal an icy chime.
  - **Bust:** the fire hisses out and the rods clatter down.
  - **Win:** the shield shatters in glass, a chord rises and a gong and brass land on the drop.
  - **Dice:** each one clacks on the floor and rolls.
  - **Flame Dye:** time winds down like a tape to a heartbeat, then a match strike and a roar.
- **The pillar fire looks like the game's own fire now.** It's built the way Minecraft builds fire on
  a block, with a soft glow round it.
- **The pillars stand further from the blaze,** level with each other, and closer in on narrow
  windows so they stay on screen.

## 0.2.57-beta — 2026-10-02

### Added
- **Hellion's rare drop endings.**
  - **Archfiend Dice:** the die is thrown out of the core and tumbles across the floor (a real
    physics simulation) until it lands on its 6, then the camera leans over it.
  - **High Class Archfiend Dice:** the same throw, then a burst of gold and red, its own music, and
    "PRAY TO RNGESUS DROP!" stamped over it.
  - **Flame Dye:** time stops just after the shield breaks and everything hangs cold. Then it all
    catches fire: a ring of flame, the pillars erupt and the dye rises out of the fire.
  - Try them with `/butcher test slayer blaze dice`, `highclass` or `flamedye`.

### Changed
- **The fire pillars are the fight's own:** four terracotta blocks with fire on top and the
  "6s 8 Hits" nametag counting down. The fire burns blue for Crystal.
- The rods' top speed is lower.
- The Paragon Shard shows its real texture.

## 0.2.55-beta — 2026-10-02

### Changed
- **Hellion takes its time.** The build-up is now 4.8 s: the shield goes round four times, slow at
  first and faster and faster. The rods stay on their rings and just spin faster and faster until
  the reveal (they no longer close in). A bust still ends a second after the reveal.

### Added
- **The Paragon Shard's texture can be captured.** It isn't in any item list. Open its bazaar page
  once and the mod saves its skin (a chat line confirms it).

## 0.2.54-beta — 2026-10-02

### Added
- **Hellion, the Inferno Demonlord's animation (first version, test only).** A 3D blaze (the vanilla
  model, rods spinning) is the Demonlord's core. Its Hellion Shield cycles through the four
  attunements with a clang each step, faster and faster, while two fire pillars at the sides burn in
  the shield's colour. Then it lands. Grey (Ashen) is a bust: the fire dies, the rods fall, the head
  crumbles to ash, all in 2 s. Any other colour shatters the shield, blows the rods out and throws
  the drop from the core, with its name, odds and price. Try it with
  `/butcher test slayer blaze <bust|duplex|crystal|subzero|wilsons|paragon|dice|highclass|flamedye>`.
  It doesn't play on real kills yet; detection comes once Demonlord chat has been recorded.

## 0.2.53-beta — 2026-10-02

### Fixed
- **Every 3D head had its two sides swapped**: the Scatha's segments, the Thaw's head items and
  Diana's head drops. Symmetric heads hid it, but the Megalodon's mouth wraps round the corners and
  came out broken there. Heads now match the game.
- **The Megalodon in Fin** is a crisp 3D head again (0.2.51's item version was flat and pixelated),
  held three-quarters on with a gentle sway instead of spinning, and it fades with the text.

## 0.2.51-beta — 2026-10-02

### Fixed
- **Frame rate in the Scatha (and the Thaw).** The crystals, and the Thaw's frost, sent every pixel
  to the screen separately, more with every cut, so the frame rate fell with each slice. They're
  now drawn as a single image each frame. They look the same.
- **The Megalodon looked wrong in Fin.** Its skin wraps round the head (eyes on the sides, the
  mouth across the front corner), and the spinning head showed its back. It's now drawn exactly as
  your inventory shows the pet.

### Changed
- **Fin:** the fin stays up half a second longer before the cleaver comes down.
- **Diana:** the light rays come in when the drop is shown, not when the head splits.

## 0.2.49-beta — 2026-10-02

### Changed
- **The Thaw freezes the screen.** Frost grows in from the corners and along the edges with every
  pick strike, catching each strike's flare colour. When the run lands it melts away on a
  common, cracks on a reveal, and shatters like glass on a legendary, the Locket or the Dye (the
  Dye's waits for its paint). The tray at the bottom stays clear.
- **The Scatha grows crystals.** Gemstone crystals in the Crystal Hollows' colours (ruby, amber,
  sapphire, jade, amethyst, topaz) grow in from the screen's edges with every cut. At the reveal
  they shatter for a gemstone, flare in the pet's rarity colour and turn to point at the head for
  a pet, and sink back into the edges when the worm ducks the last swing.

## 0.2.47-beta — 2026-10-02

### Changed
- **Diana's Chopping Block fills the screen.** Every chop throws pixel splinters and sparks out
  toward the screen's edges, more each blow and a big burst at the split. A win's light spreads to
  the edges as pixel rays in the drop's colour, with motes of that colour drifting up across the
  whole width. A bust leaves a short puff of ash. The Relic and the Dye keep their own sky and
  freeze.

## 0.2.46-beta — 2026-10-02

### Removed
- **The Scatha's cave backdrop.** The cutscene now only dims the game, with no cavern, torches, bats
  or gem outcrops. The "Plain backdrop" option is gone, since there's nothing left to switch.

## 0.2.44-beta — 2026-10-02

### Changed
- **The Scatha's cleaver chops like a real one.** It cuts all the way through each segment instead
  of stopping at the top, comes down fast (95 ms, no slow hang), stays down a moment (300 ms), goes
  straight back up (100 ms), then waits at the top for the next chop. The whole run is about a
  second shorter.

## 0.2.43-beta — 2026-10-02

### Added
- **Slayer chat lines are recorded** in `config/butcher-drops.jsonl` (with Drop records on): boss kills,
  drops and the RNG meter. Nothing plays yet; it's so a Slayer animation can later be built on the
  real messages.

## 0.2.42-beta — 2026-10-02

### Added
- **Fin, a Fishing Festival pop-up.** When a Great White Shark drops a **Megalodon** pet or a **Great
  White Shark Tooth**: a pixel fin rises out of a strip of water, the cleaver chops it, and the drop
  pops out of the splash with its name and price. It lasts about 2.5 s. Nothing is dimmed or hidden,
  and you keep playing. Settings are in the menu under "Fishing Festival - Fin" (on/off, volume). Try
  it with `/butcher test shark megalodon|legendary|tooth`.

### Fixed
- **SBO's Diana loot tracker is hidden with the rest of the HUD.** It showed the drop before the
  Chopping Block reached its split. While the HUD is hidden, other mods are told the HUD is hidden,
  as under F1. As with F1, toasts, mob name tags and the block outline also go for that moment.

## 0.2.40-beta — 2026-10-01

### Fixed
- **The cutscenes show again.** 0.2.37 played their sounds but drew nothing. They now draw after
  the HUD on a layer of their own, so the HUD hiding can't touch them.
- **Diana kills you took part in now play**:
  - a party member's Inquisitor killed next to you gave no loot share line, so the HUD went and
    nothing followed. A creature that hid the HUD now always plays.
  - a Sphinx you charmed ("CHARM! You charmed the Empyrean Sphinx") counts as yours.

## 0.2.37-beta — 2026-09-30

### Fixed
- **Every mod's HUD goes now, not only vanilla's.** Skyblocker's health/mana bars and the sack
  lines ("+16 Ancient Claw") stayed on screen during the hide and the cutscenes. The whole HUD
  now fades out and is then skipped, SkyMaxx's way, leaving only the cutscene. Menus and
  screens are never touched.

## 0.2.35-beta — 2026-09-30

### Added
- **The HUD and chat go before a Diana kill**, SkyMaxx's way. When a Sphinx, Inquisitor, Manticore
  or King Minos is at 3M HP or less, the HUD and chat fade out, the game goes quiet and chat is shut.
  It has to be yours, or you've had loot share in the last 5 minutes, and it has to be within 16
  blocks. Set the HP (or turn it off) in the menu: Diana → The cutscene → Hide the HUD below.

### Changed
- **The HUD fades out** over a third of a second instead of vanishing (SkyMaxx's fade). This applies
  to every cutscene and to the Scatha's pre-kill hide too.

## 0.2.33-beta — 2026-09-30

### Fixed
- **Loot share plays it too.** A Sphinx, Inquisitor, Manticore or King Minos that somebody else dug
  now starts the Chopping Block when you get loot share on it. Before, only your own digs did.
- Chat lines that still carry their colour codes are read correctly.

## 0.2.32-beta — 2026-09-30

### Added
- **Diana plays for real now.** The Chopping Block starts by itself when a Sphinx, Minos
  Inquisitor, Manticore or King Minos **you dug up** dies. It opens straight away, and the drop's
  RARE DROP! line fills in the ending before the split. A Minos Relic, a Mythological Dye (yours
  only) or a loot-shared drop starts it on its own.
- Every step is in `/butcher log`, and each creature's name tag is saved to the drop records,
  so a miss can be traced.

## 0.2.31-beta — 2026-09-29

### Changed
- **The Scatha hides the HUD the way Diana does.** The dark veil that grew over the screen as a
  Scatha neared death is gone: the hotbar, chat and the rest simply disappear, and the cutscene's
  cave fades in over the game instead of opening out of black.

## 0.2.30-beta — 2026-09-29

### Added
- **Diana settings** in the menu (Gambling tab, Diana section):
  - **What plays it:** Sphinx, Minos Inquisitor, Manticore and King Minos kills, the Minos Relic,
    and the Mythological Dye, each on or off.
  - **Every drop's own ending:** switch it off for the same short ending on every drop.
  - **Looks:** the RARE DROP! banner, showing the price, and the size of the scene.
  - **Sound:** music on the big drops, and the win volume.
- **Diana stats** (Stats tab): kills since a drop and the worst dry streak, every drop counted, and
  kills per creature, with its own reset. Counted from real kills only - never from /butcher test.
- An ending's second line now says which one it is ("Your 3rd Minos Relic"), from those stats.

### Changed
- The RARE DROP! banner sits a little higher, clear of the drop and the name.

## 0.2.29-beta — 2026-09-29

### Changed
Diana's endings, rebuilt after your in-game screenshots. Everything is now drawn in the game's own
pixel-art style: no more smooth, shaded blobs, thick bars or grey washes.
- **The cheap drops are short now** (about 6-7 s before the name):
  - **Fateful Stinger:** the barb shoots up out of the split, hangs with a pixel bead of venom, and
    stabs down into the board with a thunk, venom splashing in pixels.
  - **Crown of Greed:** gold washes over the head, the halves burst into coins, and the crown pops up.
  - **Brain Food:** the veins are glowing pixels painted on the head, one flash through it, and the
    ring of pi digits stays.
- **Mythological Dye, rebuilt:** time stops and the world turns to stone texel by texel. The olive
  drop is pixel art, and its paint rolls out over the stone as a band of wet paint, the real colours
  coming back behind it. The frozen chips rain down as paint, and the paint swirls into the Dye.
- **Manti-core:** lion, bat and scorpion cells fly in and fuse into the heart. The wings have joints,
  ripple as they wait and whip on the flap, and fold over the heart as a cocoon that cracks with
  light before it bursts open, with a roar.
- **Shimmering Wool** (the most valuable drop) got the big ending: the Golden Fleece's ram draws
  itself in the sky star by star, its stars stream down onto a real spindle, and the ball of yarn
  winds on it before becoming the wool, with light falling on it.
- **Chimera I, "The Glint"**, replaces the lion, goat and serpent. The game's own enchantment
  shimmer spills over the head and board, and the enchanting table's glyphs rise and circle it.
  Everything then spirals into one point, which becomes the enchanted book.
- **The expensive drops** now each have their own musical score building to the reveal, and
  Hypixel's own "RARE DROP!" (or the Dye's pink "WOW!") stamps in over the name.
- **Light:** glowing things now light what is round them, such as the venom, the heart and the glint.
- **No more grey screen flashes:** a flash is now a burst of light where it happens.

## 0.2.28-beta — 2026-09-29

### Changed
- The Crown of Greed's ending finishes on the vanilla golden helmet icon, as Chimera I's finishes on
  the vanilla book: the 3D crown still comes down onto the statue, and bursts into the icon when it
  is presented.

## 0.2.27-beta — 2026-09-29

### Added
Every Diana drop now has its own ending. Try each with `/butcher test diana <outcome>`.
- **Fateful Stinger, "The Fatal Sting"** (`stinger`): a scorpion's tail rises out of the Manticore's
  head plate by plate, arches over, and sways like a cobra, venom beading on its barb. Then it
  turns on you and strikes: the screen cracks and venom splashes across the glass. The cracks heal,
  and the tail lowers its barb, which becomes the Fateful Stinger.
- **Mythological Dye, "Wet Paint"** (`dye`): at the split, time stops and the world drains to grey.
  A single olive drop forms on the cleaver's edge and falls in slow motion. Its splash sends a wave
  of paint across the screen that brings the colour back. Time runs again, the frozen chips rain
  down as paint, and the Dye rises out of the puddle.
- **Shimmering Wool, "The Golden Fleece"** (`wool`): golden fibres unravel out of King Minos's head
  and drift up in a breeze. They are wound round a spindle into a ball of yarn, faster and faster,
  which squares off into the wool and settles softly with sparkles.
- **Manti-core, "The Heart of the Beast"** (`manticore`): a heart of mashed-together creature cells
  beats inside the Manticore's head, faster and faster. Bat wings unfold behind it, one great flap
  blows the halves away, and the wings fold round the heart. Then they are flung open, and the
  Manti-core is there.
- **Crown of Greed, "Midas"** (`crown`): gold creeps out of the cut pixel by pixel until King Minos
  and the board are solid gold, and his halves clank shut into a statue. The Crown (the game's own
  golden helmet) comes down onto his head. Then the statue collapses into an avalanche of coins,
  and coins rain down while the price counts up.
- Every ending shows the drop's real odds (from the wiki: 1 in ~200 Manticores for the Stinger, 1 in
  ~500 for the Manti-core, 1 in ~50 King Minos for the Crown, 1 in ~500 for the Wool; the Dye's
  depends on what dropped it).

## 0.2.26-beta — 2026-09-28

### Fixed
- **Diana's cleaver chops the right way.** The blade used to face you while the head split left and
  right, so the chips flew straight out at the camera. It now lies along the cut, edge front to
  back and handle behind, and sinks into the head instead of sitting in front of it. The chips
  burst out of the slot to both sides, arc over and land on the board.

### Changed
- The chopping block is a thinner cutting board.
- No more black bars at the top and bottom during Diana's endings.

## 0.2.25-beta — 2026-09-28

### Changed
- **Diana's cutscene is now "The Chopping Block"**, replacing the Labyrinth. The head of what you
  killed (the Sphinx, Minos Inquisitor, Manticore or King Minos, and the Minos Champion for a Relic)
  thuds onto a butcher's block over your dimmed game. The Butcher's cleaver chops it three times,
  each blow harder, cracking it and throwing chips, with light leaking out. On the last wind-up the
  cracks cycle colours. Then the cleaver comes down through it and the head splits. The heads are
  the creatures' real skins.
- **A bust is short**: the halves fall open and crumble off the block, and you're back in about four
  seconds.
- **A drop** rises out of the split in its colour; then the block sinks away, leaving the drop, its
  name and its price.
- **The Minos Relic's ending**: the Champion's split head throbs three times, then comes apart into
  its own pixels. They spiral up in a vortex and fuse into Diana's moon while the block falls away.
  The moon spins up like a wheel, flickering moon and Relic, and stops as the Relic.
- **Brain Food's ending**: the Sphinx's head eases open and glowing veins spread over it and across
  the block like neurons, pulsing, with sparks racing home. The light drains in, the head bursts, and
  the Brain Food rises inside a ring of the digits of pi.
- **Chimera I's ending, "The Beast Fused"**: the Inquisitor's head falls open and the chimera's lion,
  goat and serpent heads rise out of it and circle the block. Each lunges out and roars in turn;
  then they crash together, and what is left is the real enchanted book, named in enchantment pink.
- New sounds for the chopping: the thud of the head landing, a swish before each blow, a crunch and
  a ringing blade that build with each blow, and a heavy split.
- Test with `/butcher test diana <outcome>`.

## 0.2.24-beta — 2026-09-28

### Changed
- **Chimera I's ending, restaged**: the book now rises out of the chamber and flies up high out of
  the maze, opening as it goes. The three heads burst out of its pages and circle it like an atom,
  faster and faster, then dive back in - and the book slams shut and explodes, pages and runes
  flying, leaving the real Minecraft enchanted book. Its name is in the game's enchantment pink.

## 0.2.23-beta — 2026-09-28

### Added
- **Chimera I's own ending, "Three Heads"** (`/butcher test diana chimera`). A great enchanted
  book rises over the altar, opens, pages flipping and runes drifting off it; then the chimera's
  three heads - a golden lion, a white goat and a teal serpent, each with its own voice - burst out
  and race through the maze's corridors, carving glowing runes into the walls. They braid into one
  spiral over the book and plunge into it; it slams shut and every rune in the maze flares. Then
  its name, the odds and the price.

## 0.2.21-beta — 2026-09-28

### Added
- **Brain Food's own ending, "Every Path at Once"** (`/butcher test diana brainfood`). The maze
  thinks: a wisteria flood races out from the chamber through every corridor at once, dead ends
  flashing, walls jolting as it passes; brain waves pulse through the lit network while sparks race
  home; it all drains into the chamber and bursts, and the Brain Food rises, throbbing, inside a
  ring of the digits of pi. Then its name, the odds and the price.

### Changed
- Every Diana drop now has a thick black outline, like the Thaw's items; Shimmering Wool is a real
  turning block.
- The Minos Relic comes to rest turned three-quarters, looking off to the right, instead of
  staring straight out.

## 0.2.20-beta — 2026-09-28

### Changed
- **You can keep playing during every cutscene** - the Scatha's, the Thaw and the Labyrinth now
  play over the game instead of in a menu, so you can look around, move and fight while they run
  (SkyMaxx's way). The pause menu, inventory and chests open over them; only chat stays shut until
  the cutscene is over, since its history could give the drop away.
- **The HUD is hidden the SkyMaxx way**: each part of it is switched off while a cutscene plays,
  rather than the whole HUD being thrown away - the pumpkin, portal and other screen overlays stay.
- **Minos Relic**: the moon starts as all moon and turns to the Relic face by face as it spins.
  The ground under each wall that flies off goes with it, then the rest of the floor falls away -
  leaving only the spark's glowing path and the chamber floating in the dark.

## 0.2.19-beta — 2026-09-28

### Changed
- **The Minos Relic's ending, second half**: once the moon has formed, the whole maze falls away
  into the dark. The moon - a block like a Minecraft head - spins up like a wheel, flickering
  between moon and Relic faces with a click at every turn, and brakes to a stop showing the Relic:
  it was the Relic all along. Then its name, the odds and the price.
- **Every Diana drop shows its price**, counting up under its name like the Locket's: the bazaar's
  instant sell for bazaar items (Brain Food, Chimera I, Fateful Stinger), the lowest BIN for the
  rest. Needs "Live prices" on.

## 0.2.18-beta — 2026-09-28

### Added
- **Gameplay window**: the game itself, live, in a corner of the screen during every cutscene -
  the Scatha's, the Thaw and the Labyrinth - so you can see what is around you while it plays.
  Pick the corner or turn it off in the menu under "Gameplay window". (The idea is SkyMaxx's.)

### Changed
- **The Labyrinth is in the Mythos armour's colours**: plum purple walls with a teal key band
  between gold lines, a gold gate, gold braziers, and a plum-and-gold chamber floor.
- **More 3D**: the camera now turns slowly round the maze as the spark runs and orbits the chamber
  on a win; the spark is a small spinning gold gem; the Minos Relic's moon is a 3D block like a
  Minecraft head, turning, with rings of stars orbiting it.

## 0.2.17-beta — 2026-09-28

### Changed
- **The Minos Relic's ending is rebuilt, bigger and longer (16 seconds).** After the gate: three
  heartbeats send shockwaves rolling across the maze; then the walls tear loose ring by ring and
  tumble into the air as real 3D blocks, get pulled into a vortex over the chamber and fuse high
  above it - and Diana's full moon ignites where they met. A moonbeam falls to the altar, the
  Relic comes down it turning and shedding sparks, and lands with a shockwave across the bare
  slab. New sounds all the way through: heartbeats, the stone bursting ring by ring, the wind of
  the vortex, the moon igniting.
- **Every Diana drop is now drawn as its real SkyBlock item**: the Minos Relic, Brain Food,
  Fateful Stinger, Manti-core and the Mythological Dye as their actual 3D heads, turning; Chimera I
  as an enchanted book, the Crown of Greed as a golden helmet, Shimmering Wool as yellow wool.

## 0.2.16-beta — 2026-09-28

### Added
- **The Minos Relic's own ending, "The Maze Unmade"** (`/butcher test diana relic`). Everything
  up to the gate is the same as any other run. Then black bars close in and the Labyrinth takes
  itself apart: the walls lift away ring by ring from the outside in and crumble into moonstone
  dust, until only the chamber stands on the bare slab with the thread still glowing where the
  spark ran. Diana's full moon rises, a moonbeam falls to the altar, and the Relic comes down it.
  Its name is stamped out letter by letter, then the odds and which Relic this is.

### Changed
- A win's arrival flash now bursts around the drop instead of washing out the whole screen.

## 0.2.15-beta — 2026-09-28

### Changed
- **Diana's cutscene is now "The Labyrinth"**, replacing the Thread. Minos's maze rises out of
  the ground around a moonlit chamber, and a spark races it from the entrance to the chamber's
  gate - faster at every turn, lighting braziers as it passes, leaving a gold thread behind it,
  and taking a wrong turn or two into dead ends on the way. On the last stretch it cycles
  colours. Then the bronze gate slams down. A bust: the chamber is empty, the spark gutters,
  the braziers go out and the maze sinks back into the dark - under five seconds. A win: the
  chamber floods with the drop's colour, the colour runs back out through every brazier and
  along the thread, and the drop rises off the altar with its name.
- **A new maze every time** - its layout, where the spark comes in, where the gate is, the
  wrong turns and the braziers all change from run to run. Nothing before the gate depends on
  what is in the chamber.
- The Mythological Dye's colour is now its real in-game olive.
- `/butcher test diana` outcomes are unchanged: `bust`, `relic`, `brainfood`, `chimera`, `dye`,
  `stinger`, `manticore`, `crown`, `wool`, `unknown`.

## 0.2.14-beta — 2026-09-27

### Changed
- **Diana's cutscene is now "The Thread"**, replacing the Judgement. One still shot: a coil
  of thread on lamplit ground, the thread paying out into the dark with a bead of light
  running along it. It hangs slack; three faint tugs; the colour in the sag turns from
  yellow to red. Then it snaps taut - a hard crack, a white flash, darkness. A bust: the
  thread lies cut in the dirt, frayed, nothing on it, over in just over a second. A win:
  the line hums in the drop's colour and something slides down it out of the dark, arriving
  in the middle of the screen with its name.
- `/butcher test diana` outcomes: `bust`, `relic`, `brainfood`, `chimera`, `dye`,
  `stinger`, `manticore`, `crown`, `wool`, `unknown`.

## 0.2.12-beta — 2026-09-27

### Added
- **The Judgement** - Diana's cutscene, playable with `/butcher test diana <outcome>`. The
  camera falls into a burrow; below is a marble chamber lit by braziers, a colossal judge in
  the shadow above, and bronze scales. The offering drops onto a pan, the scales sway towards
  balance three times, the light snaps out - and when it comes back, the verdict. A bust is
  over in a second and a half; a win floods the chamber with its drop's colour. The whole
  build-up is identical on every outcome, so nothing gives it away early.
- Outcomes: `bust`, `relic`, `brainfood`, `chimera`, `manticore`, `kingminos`, `dye`, and
  `unknown` (the neutral ending for a drop the mod cannot identify).
- **Diana - The Judgement** group in the menu: on/off and the win's volume.
- It does not play on real kills yet: that waits on capturing Diana's chat messages once
  she is mayor. The items on the pan in tests are vanilla stand-ins.

## 0.2.10-beta — 2026-09-27

### Added
- **Drop records.** Every real Scatha kill and Vanguard corpse, and every chat line that
  looks like Diana's Mythological Ritual, is written to `config/butcher-drops.jsonl` with
  the chat and the mod's reasoning around it. It stays on your computer. The point: a rare
  drop happens once, and if the mod ever gets one wrong, the exact inputs are on disk to fix
  it against. `/butcher records` shows how many there are; **Drop records** in the menu
  turns it off.

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
