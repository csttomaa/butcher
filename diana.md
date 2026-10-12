# Diana — Mythological Ritual drop animation

Add a drop cutscene for Diana's Mythological Ritual. One cutscene, **"The Thread"**,
fired on Legendary-and-above mythological creatures plus Minos Champion. No per-burrow
animation — a chain is 4–10 burrows back to back and anything that plays on every dig
makes the event unplayable.

Read `CLAUDE.md` first, then `lavafishing.md` if it is in the repo. This is the third
creature family, so the shared structure is now worth factoring — see §11.

**Three things matter more than shipping fast:**

1. **The camera never moves.** One locked-off shot, start to finish. See §4.
2. It has to *look* the way it is described here, not approximately like it. See §9.
3. It has to say the *right thing*. A previous version of this mod played the wrong
   outcome on a real Scatha pet drop. See §10. **Build §10 first.**

---

## 1. The event

Verify all of this in game and against the wiki before writing code against it.

Diana is elected mayor (or Jerry with Perkpocalypse). With a Griffin pet equipped and a
spade in hand, you dig **Griffin Burrows** in the Hub. Burrows come in chains:

| spade | chain length |
| --- | --- |
| Ancestral | 4 |
| Archaic | 6 |
| Deific | 8 |
| +Erudite reforge | +2 (max 10) |

Each burrow is **75% a mythological creature, 25% treasure**. The Griffin pet's rarity
gates which creatures can appear.

**Only these creatures are in scope.** The Common through Rare tiers and the Minotaur
are deliberately excluded — they are not worth a cutscene and they spawn constantly.
Do not add them, do not write a table entry for them, do not fire on them.

| creature | tier | drop | rate |
| --- | --- | --- | --- |
| Minos Champion | Epic | Minos Relic | 0.02–0.04% |
| Sphinx | Legendary | Brain Food | 0.25–0.5% |
| Minos Inquisitor | Legendary | Enchanted Book (Chimera I) | 1–1.25% |
| Minos Inquisitor | Legendary | Mythological Dye | 1 in 50,000 |
| Manticore | Mythic | Fateful Stinger | 0.5% |
| Manticore | Mythic | Manti-core | 0.2% |
| King Minos | Mythic | Crown of Greed | 2% |
| King Minos | Mythic | Shimmering Wool | 0.2% |

Every one of them also drops Ancient Claws, Enchanted Gold Ingots and coins,
guaranteed. Confirm whether Mythological Dye drops from creatures other than the
Inquisitor — the sources are not clear and it changes one row.

A Minos Inquisitor is ~1.30% of mobs (about 1 in 77) on a Legendary+ Griffin, with
50,000,000 HP (80M on Mythic) and 50% magic damage resistance.

**So the bust rate is 97%+ on every creature here.** Same conclusion as Jawbus: the
losing path is the common path, and it is the one that has to feel good.

---

## 2. When the cutscene fires

The eight rows above, and nothing else. Per-creature toggles in the menu, shipped on by
default for all five creatures.

Minos Champion is in despite being Epic, purely because the Minos Relic is worth it.
That is the only sub-Legendary exception — do not generalise it into "any Epic".

---

## 3. Detection

Diana is the easiest of the three. The game narrates almost everything:

| message | meaning |
| --- | --- |
| `You dug out a Griffin Burrow! (3/4)` | burrow dug, and the chain position |
| `You finished the Griffin burrow chain! (4/4)` | chain over |
| `Danger! You dug out a [mob]!` / `Yikes! ...` | which creature spawned |
| `RARE DROP! You dug out a <treasure>!` | treasure burrow |

Confirm every string in game before matching it — the mob line has several random
prefixes, so match on the creature name, not the prefix. Capture real strings before
writing patterns against them (§10).

That gives the creature identity up front, which is what decides whether the cutscene
fires at all. For the kill and the drop, reuse what exists: these mobs carry HP
nametags, so `ScathaWormTracker`'s armour-stand scan works with a different name
filter, and `HotbarDropWatcher` handles the inventory diff. **Reuse both, do not fork
them.**

Open the cutscene the instant the kill is detected with the outcome pending, as
`openCutsceneOnKill` does now. Do not wait for the drop.

Suppress the drop chat lines while the cutscene runs via the existing `ALLOW_CHAT` /
`ALLOW_GAME` hooks. Seeing the drop in chat before the thread goes taut ruins it.

---

## 4. The animation — "The Thread"

Ariadne's thread. You hold one end; something out in the dark has the other.

**One fixed shot for the entire cutscene.** The camera never moves, never pans, never
falls. Nothing in the projection animates. This is a hard constraint, not a preference —
it is what makes the cutscene achievable and hard to get visibly wrong.

### The shot

- Bottom third: the ground at the burrow's edge, lit warm. A loose pile of thread.
- Everything above: darkness, with the faintest suggestion of walls.
- The thread runs from bottom-centre away to a vanishing point near the middle of frame.

### The whole animation is two numbers

- **`payout`** — how far along the thread the bead of light has travelled, 0→1
- **`tension`** — 0 is a deep sag, 1 is dead straight

A polyline whose sag is driven by `tension`, and a glow whose position along it is
driven by `payout`. That is the entire cutscene. Do not add a third mechanism.

### Beats

| ms | beat |
| --- | --- |
| 0–1000 | **Pay out.** Coils unspool from the pile. The bead of light runs away down the thread, shrinking, and disappears into the dark. `payout` 0→1, `tension` low. A dry whirring. |
| 1000–2600 | **The wait.** The thread hangs slack, barely moving. **Two or three faint tugs** — the sag jumps and settles. Colour appears in the sag: this is the tell (§5). Room tone, a distant scrape. |
| 2600–2900 | **It goes taut.** The sag pulls out fast, the thread snaps dead straight. `tension` → 1. One hard screen shake, white flash, then dark. |
| 2900+ | **Branch** — below. |

**Everything before 2900ms is byte-identical on every outcome.** Same tug count, same
tug timings, same sag depth, win or lose. The flash at the snap conceals the branch,
exactly as the bite does in `lavafishing.md`. If a win ever tugs harder or waits longer
than a loss, the animation is dead the first time a player notices.

The screen shake is a 2D offset applied at draw time. It is not a camera move and must
not become one.

### Crash (~97%)

- 2900–3200: light returns. The thread is already lying slack on the ground — it went
  loose while the screen was dark.
- 3200–3800: the end comes into view, cut clean and frayed. Nothing on it.
- 3800–4100: fade. The room tone dies.

**Under 1.2 seconds from the flash.** No consolation flourish, no sad sting. It ends.

### Explode (~3%)

- 2900–3200: light returns on a thread pulled tight and humming.
- 3200–4600: something is sliding down the thread toward you out of the dark, growing as
  it comes, gathering light. This is a sprite scaling up along the fixed thread path —
  **not** a camera push.
- 4600–6000: it arrives centre-frame, lit, name beneath it. Let it breathe.

Fast losses, slow wins. The asymmetry is the point.

---

## 5. The tell — **agree this with me before building it**

During the wait, colour appears in the sagging thread. Diana already has a proximity
colour language in the burrow particles — **yellow → red → black** — and borrowing it
makes the cutscene feel native to the event rather than bolted on.

**Do not pick these yourself and ship them.** Come back to me with concrete proposals —
hex values, and how each behaves (steady, pulsing, climbing the thread, bleeding off
it) — for the baseline and for each of the eight drops above, and get my sign-off. Show
swatches if you can. This is what the player reads fastest and the part I most want a
say in.

Two rules hold whatever we pick:

**The "something is coming" colour must sometimes lie.** A percentage of losses have to
show it and still bust, tuned so it is roughly a coin flip rather than a promise. Put
the rate in config so I can move it.

**The rarest colours never fake.** Whatever we assign to Minos Relic, Crown of Greed and
Mythological Dye appears only when that thing actually dropped.

The colour is a tint on the thread and glow sprites — `blit`'s 11-arg ARGB overload does
all of it. No per-colour textures.

---

## 6. Art

Warm stone and lamplight at the bottom of frame, cold dark above. Far less to draw than
the other two cutscenes, which is the point.

- **The thread** is the hero object and almost the only one. A catenary sag between two
  fixed points, tessellated into a polyline, drawn with the tint. Give it slight
  irregularity — a real thread is not a perfect curve — and a faint fuzz of fibres so it
  does not read as a vector stroke.
- **The coil pile** shrinks as `payout` climbs. A handful of overlapping loops; it does
  not need to be simulated, only to look disturbed.
- **The dark** is most of the frame. Use the light-map technique from `gen_cave.py`,
  re-palettised: bright at the ground lamps, steep falloff, nothing lit above. Do not
  light it uniformly — that produced beige soup in the cave and it will produce grey
  soup here. Blur alpha only, as documented.
- **Dust** drifting in the lit band at the bottom. Cheap, and it stops the dark reading
  as a flat fill.

The mesh pipeline is **optional** here. If the thread is easier as a 2D path with the
blit tint, do it that way — this cutscene was chosen specifically because it does not
need rigged geometry.

All art goes through a `tools/gen_*.py` script. Do not hand-paint a PNG. Route
replaceable pieces through `ArtOverrides` like everything else.

---

## 7. Audio

Through the synthesiser in `audio/`:

- Pay out: a dry whirring, slowing as it runs out.
- The wait: room tone, and a distant scrape for each tug. Identical on every outcome.
- The snap: one hard crack, then silence. The silence is the effect — do not fill it.
- Crash: the slack thread settling. Almost nothing.
- Win: the hum of a tight line, then the flood as the item arrives — bright, loud, and
  clearly a different instrument from anything in the build-up.

---

## 8. Integration

- **Menu.** Add a **Diana** section to `/butcher` alongside Scatha Gambling and Lava
  Fishing: on/off, duration slider, per-creature toggles. Hand-drawn rows rebuilt from
  config every frame, as the menu already works. No vanilla widgets.
- **Config.** New fields on `PatternConfig`. Do not rename existing ones —
  `animationMode` is still `SCOPE` for the cleaver and renaming it breaks every config
  file on disk.
- **Pre-kill hide.** Reuse `PreKillHider` against the creature's nametag HP. Do not
  write a second hider.
- **Test commands.** `/butcher test diana <outcome>` for the animation, and
  `/butcher simulate diana <outcome>` for the whole detection chain. See §10.

---

## 9. Making it look right

`diana_storyboard.png` sits beside this file — six panels, one per beat. Match the
staging: one locked-off frame throughout, the thread running to a vanishing point near
centre, the lit band only at the bottom.

§4 is a specification, not a mood board. Where a number is given, hit the number. Where
one is not, propose one and show me before it is load-bearing.

**Do not build this blind and hand it to me to look at.** Build a way to see it first:

1. **A Python previewer.** The art is generated in Python already. Write
   `tools/preview_diana.py` that plots the thread from the same `payout` / `tension`
   curves the Java will use and writes a contact sheet and an animated GIF. Because the
   whole cutscene is two numbers, the previewer is nearly exact — **iterate there until
   it looks right**, then port the curves. Do not write the Java first.
2. **An in-game frame dump.** `/butcher dump diana <outcome>` writes every Nth frame to
   `screenshots/`. I will run it once per outcome and hand you the PNGs. That is the
   ground truth; the previewer is how you get close to it.

Re-read the Art section of `CLAUDE.md` first. The mistakes that have already cost this
project a rebuild each: blurring RGBA instead of alpha only, applying glow uniformly
instead of through a light map with a steep falloff, skipping the RGB bleed before a
downscale, and repainting one frame of a set that shares a pivot.

---

## 10. Making it correct — **build this first**

A real Scatha pet dropped in game and the cutscene played the wrong outcome. That is the
worst failure this mod can have: the one moment the whole thing exists for, and it lied
about it.

The cause is the shape of the detection, not a typo. The inventory diff is gated on a
time window and the common outcome is reached **by elimination**, so anything the diff
misses — a drop landing after the window, one absorbed by a sack, one in a slot the diff
does not cover — does not produce an error. It produces a confident wrong answer.

If the recorder and replay harness from `lavafishing.md` already exist, extend them to
Diana and skip to §10.5. If not, build them now:

**1. Never infer by elimination.** If the chain cannot positively identify what dropped,
it says so — an `UNKNOWN` outcome with a neutral ending. A neutral ending on a rare drop
is a disappointment; a confident wrong one is a betrayal.

**2. Record every kill, starting now.** Build this before the animation so it captures
real events while everything else is being written. One record per kill appended to
`config/butcher-drops.jsonl`: timestamp, every chat line in the window verbatim,
inventory NBT before and after, creature identity, chain position, the verdict, and
which signal reached it. Append-only, never pruned. **A 0.02% drop happens once** — when
it does, its exact inputs are on disk forever.

**3. Replay them.** `/butcher replay <n>` re-feeds a recorded event through the live
detector and prints what it decides now and why, plus a JUnit test that replays the
whole corpus against stored expectations. The next real Relic becomes a permanent
regression test.

**4. Synthesise the branches we have not seen.** Hand-write fixture records for all
eight drops above from the real message formats and run them through the same replay
path, so every branch is tested on day one.

**5. Two test commands, kept separate.** `/butcher test ...` forces the animation and
bypasses detection — that is for art. `/butcher simulate ...` injects at the
**detector's** input and runs the whole chain. The old command only ever tested the
second half, which is exactly why the bug survived to meet a real pet.

**6. Log the reasoning.** `DecisionLog` records which signal decided each verdict and
what the alternatives were, so when it is wrong again I can paste the log.

**7. A debug overlay** behind a config flag showing the detector's live state, the
current chain position and the current verdict.

**§10.5 — Diana specifics.** The creature identity comes from a chat line, so a missed
or misparsed line means the cutscene fires on the wrong creature or not at all. Treat an
unrecognised creature name as `UNKNOWN` and log the raw line — do not fall through to a
default, and in particular do not fall through to one of the five tracked creatures.
Chains overlap with kills, so a burrow dug while a previous creature is still alive must
not reassign the pending cutscene.

The honest limit on all of it: none of this proves Hypixel's messages are what we think.
Capture real strings before matching them, and treat any pattern not confirmed against a
recorded line as unverified.

---

## 11. Factoring the shared structure

`lavafishing.md` said to defer this until there was a second implementation to factor
against. Diana is that second implementation, so do it now — but only for what has
actually repeated twice:

- **The cutscene shape.** Build-up → concealing beat → branch → fast loss / slow win,
  with a byte-identical pre-branch segment and a colour tell during the build-up. That is
  the house style and it has now earned an abstraction.
- **Detection and recording.** Chat-line matching, inventory diff, the `.jsonl` recorder,
  keyed by creature. Creatures become entries in a table, not hard-coded branches.
- **Menu and config.** Each event gets a section of the same shape.

What stays local: the thread, the lava lake, the jaw, the cave, and every timing number.
Do not abstract the art or the beats — they are not the same, and pretending they are
will flatten all three into one cutscene with different textures.

---

## 12. Do not break

- **The camera never moves.** No pans, no pushes, no falls, no projection animation. If
  a change adds one, the change is wrong.
- The cutscene opens on kill with the outcome pending, and the watcher keeps ticking
  while it waits. Load-bearing.
- `"defaultRequire": 1` in the mixins json. A mixin whose target does not resolve must
  crash loudly, not vanish.
- Verify against the real mapped Minecraft jar before handing anything over, per
  `CLAUDE.md`. 26.2 is not 26.1.
- The pre-branch segment is identical on every outcome. If a change makes a win
  detectable before the snap, the change is wrong.
- No outcome is ever reached by elimination.
- Nothing plays on an ordinary burrow dig, or on any creature outside the five listed.
