# Diana — Mythological Ritual drop animation

Add a drop cutscene for Diana's Mythological Ritual. One cutscene, "The Judgement",
fired on Legendary-and-above mythological creatures. No per-burrow animation — a chain
is 4–10 burrows back to back and anything that plays on every dig makes the event
unplayable.

Read `CLAUDE.md` first, then `lavafishing.md` if it is in the repo. This is the third
creature family, so the shared structure is now worth factoring — see §11.

**Two things matter more than shipping fast:**

1. It has to *look* the way it is described here, not approximately like it. See §9.
2. It has to say the *right thing*. A previous version of this mod played the wrong
   outcome on a real Scatha pet drop. See §10. **Build §10 first.**

---

## 1. The event

Verify all of this in game and against the wiki before writing code against it. The
creature roster in particular has grown over time and the sources disagree.

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

**The creatures**, by rarity tier, with their signature drop:

| creature | tier | signature drop | rate |
| --- | --- | --- | --- |
| Minos Hunter | Common | Hilt of Revelations | 0.25–2% |
| Siamese Lynxes | Common | Crochet Tiger Plushie | 0.05–0.4% |
| Stranded Nymph | Uncommon | Washed-up Souvenir | 0.2–0.5% |
| Cretan Bull | Uncommon | Cretan Urn | 0.2–0.5% |
| Harpy | Rare | Antique Remedies | 0.35–0.6% |
| Gaia Construct | Rare | Dwarf Turtle Shelmet | 0.35–0.6% |
| Minotaur | Epic | Daedalus Stick | 0.04–0.08% |
| Minos Champion | Epic | Minos Relic | 0.02–0.04% |
| Sphinx | Legendary | Brain Food | 0.25–0.5% |
| **Minos Inquisitor** | Legendary | Enchanted Book (Chimera I) | 1–1.25% |
| Manticore | Mythic | Fateful Stinger / Manti-core | 0.5% / 0.2% |
| King Minos | Mythic | Crown of Greed / Shimmering Wool | 2% / 0.2% |

Mythological Dye is 1 in 50,000. Every creature also drops Ancient Claws, Enchanted
Gold Ingots and coins, guaranteed.

A Minos Inquisitor is ~1.30% of mobs (about 1 in 77) on a Legendary+ Griffin, with
50,000,000 HP (80M on Mythic) and 50% magic damage resistance.

**So the bust rate is 98%+ on every creature worth watching.** Same conclusion as
Jawbus: the losing path is the common path, and it is the one that has to feel good.

---

## 2. When the cutscene fires

**Legendary and above** — Sphinx, Minos Inquisitor, Manticore, King Minos — plus **any
actual rare drop from any creature**, so a Minos Relic off a Champion still gets the
full treatment.

Put per-creature toggles in the menu, shipped with that as the default. Nothing fires
on Common through Epic kills unless the drop itself is rare.

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
fires at all. For the kill itself and the drop, reuse what already exists: these mobs
carry HP nametags, so `ScathaWormTracker`'s armour-stand scan works with a different
name filter, and `HotbarDropWatcher` handles the inventory diff. **Reuse both, do not
fork them.**

Open the cutscene the instant the kill is detected with the outcome pending, as
`openCutsceneOnKill` does now. Do not wait for the drop.

Suppress the drop chat lines while the cutscene runs via the existing `ALLOW_CHAT` /
`ALLOW_GAME` hooks. Seeing the drop in chat before the scales settle ruins it.

The chain position is worth keeping even without a per-burrow animation: carve the
current position into the chamber wall (§5) so the cutscene knows where in the run it
is. Grace note, not a feature.

---

## 4. The animation — "The Judgement"

In the myth, King Minos is a judge of the dead. That is the whole idea: you are not
opening a box, you are being judged, and the verdict is your drop.

The camera falls into the burrow. Underneath is a chamber. A colossal seated figure
sits in shadow above, and a pair of bronze scales hangs in front of you. Your kill goes
on the pan. The scales sway.

### Beats

| ms | beat |
| --- | --- |
| 0–800 | **Fall.** Camera drops through the burrow. Earth walls rush past, light growing from below. Wind, then stone. |
| 800–2000 | **The chamber.** It opens out. Braziers on the floor light everything from underneath. The seated figure is a silhouette above — never fully lit, just a knee, a hand, the edge of a jaw. The scales hang centre-frame, empty, barely moving. |
| 2000–2600 | **The offering.** What the creature left drops onto the left pan. It sinks. The beam begins to turn. |
| 2600–4200 | **The sway.** The pans swing, slow, and hover near balance. It rises toward the tipping point and falls back — more than once. **This is the tension.** Light rises from beneath the pans, colour-coded (§5). A low choral drone under stone. |
| 4200–4350 | **The verdict.** The chamber light snaps out. Silence for a beat. |
| 4350+ | **Branch** — below. |

**Everything before 4350ms is byte-identical on every outcome.** The sway is a fixed,
scripted sequence — same angles, same number of near-misses, same timings, win or lose.
The blackout at the verdict is what conceals the branch, exactly as the bite does in
`lavafishing.md`. If a win ever swings higher or hovers longer than a loss, the
animation is dead the first time a player notices, so do not let anything
outcome-dependent happen before 4350ms.

### Crash (~98%)

- 4350–4600: light returns, dim. The pan is already down — it slammed while dark.
- 4600–5200: the item slides off into shadow. Stone grinding, the beam creaking to rest.
- 5200–5700: fade. The drone dies.

**Under 1.5 seconds from the verdict.** No consolation flourish, no sad sting. It ends.

### Explode (~2%)

- 4350–4700: the pan tips **up**. Gold floods from beneath the scales and washes the
  chamber. The figure above shifts for the first time.
- 4700–6200: the item lifts off the pan, rising, and settles centre-frame, lit.
- 6200–8000: it holds, glowing, name beneath it. Let it breathe.

Fast losses, slow wins. The asymmetry is the point.

---

## 5. The tell — **agree this with me before building it**

During the sway, light rises from beneath the pans. Its colour is what the player
reads. Diana already has a proximity colour language in the burrow particles —
**yellow → red → black** — and borrowing it makes the cutscene feel native to the event
rather than bolted on.

**Do not pick these yourself and ship them.** Come back to me with concrete proposals —
hex values, and how each behaves (steady, pulsing, climbing, bleeding past the pans) —
for the baseline and for each rare drop, and get my sign-off. Show swatches if you can.
This is the part the player reads fastest and the part I most want a say in.

Two rules hold whatever we pick:

**The "something is coming" colour must sometimes lie.** A percentage of losses have to
show it and still bust, tuned so it is roughly a coin flip rather than a promise. Rate
goes in config so I can move it.

**The rarest colours never fake.** Whatever we assign to Minos Relic, Crown of Greed and
Mythological Dye only ever appears when that thing actually dropped.

The light is a tint on the glow sprite — `blit`'s 11-arg ARGB overload does all of it.
No per-colour textures.

---

## 6. Art

Marble, bronze, dust, and torchlight from below. A completely different palette from
the cave and the lava lake — cold stone lit warm from underneath, heavy shadow above.

- **The scales** are the hero object. A beam on a central pivot, two pans on chains.
  That is the same hinge maths as the Jawbus jaw — one rotation angle drives the beam,
  and the pans hang plumb from their ends. Build it in the mesh pipeline; the
  cel-shading and screen-space outline come free from `MeshRenderer`. Bronze, hammered,
  slightly asymmetric — it should look made, not machined.
- **The figure** is never resolved. A silhouette, and at most one lit edge. The moment
  you draw its face it stops being a judge and starts being a monster. Keep it dark and
  let the player's eye fill it in.
- **The chamber** reuses the light-map technique from `gen_cave.py`, re-palettised:
  bright at the braziers on the floor, steep falloff, everything above in shadow. Do not
  light it uniformly — that produced beige soup in the cave and it will produce grey
  soup here. Blur alpha only, as documented.
- **Dust** in the light shafts. Cheap, and it sells the scale of the room.

All art goes through a `tools/gen_*.py` script. Do not hand-paint a PNG. Route
replaceable pieces through `ArtOverrides` like everything else.

---

## 7. Audio

Through the synthesiser in `audio/`:

- Fall: wind, rising, cut off hard on landing.
- Chamber: a low choral drone, barely moving.
- Sway: chain creak and stone, tightening as the pans near balance. Identical on every
  outcome.
- Verdict: one beat of complete silence. The silence is the effect — do not fill it.
- Crash: grinding, the beam settling, the drone dying.
- Win: the flood. Bright, loud, clearly a different instrument from anything in the
  build-up.

---

## 8. Integration

- **Menu.** Add a **Diana** section to `/butcher` alongside Scatha Gambling and Lava
  Fishing: on/off, duration slider, per-creature trigger toggles (default Legendary+).
  Hand-drawn rows rebuilt from config every frame, as the menu already works. No vanilla
  widgets.
- **Config.** New fields on `PatternConfig`. Do not rename existing ones —
  `animationMode` is still `SCOPE` for the cleaver and renaming it breaks every config
  file on disk.
- **Pre-kill hide.** Reuse `PreKillHider` against the creature's nametag HP. Do not
  write a second hider.
- **Test commands.** `/butcher test diana <outcome>` for the animation, and
  `/butcher simulate diana <outcome>` for the whole detection chain. See §10.

---

## 9. Making it look right

§4 is a specification, not a mood board. Where a number is given, hit the number. Where
one is not, propose one and show me before it is load-bearing.

**Do not build this blind and hand it to me to look at.** Build a way to see it first:

1. **A Python previewer.** The art is generated in Python already. Write
   `tools/preview_diana.py` that composites the same sprites at the same timings and
   transforms into a contact sheet and an animated GIF. Run it, **look at the output
   yourself, and iterate there** before writing Java. Nearly every visual problem this
   mod has had would have been obvious in a contact sheet and instead cost a round trip
   through me.
2. **The projection is deterministic.** Render the scales mesh to PNG from Python with
   the same projection so the beam pivot, the plumb of the chains and the silhouette can
   be checked without launching the game.
3. **An in-game frame dump.** `/butcher dump diana <outcome>` writes every Nth frame to
   `screenshots/`. I will run it once per outcome and hand you the PNGs. That is the
   ground truth; the previewer is how you get close to it.

Re-read the Art section of `CLAUDE.md` first. The mistakes that have already cost this
project a rebuild each: blurring RGBA instead of alpha only, applying glow uniformly
instead of through a light map with a steep falloff, skipping the RGB bleed before a
downscale, and repainting one frame of a set that shares a pivot.

---

## 10. Making it correct — **build this first**

A real Scatha pet dropped in game and the cutscene played the wrong outcome. That is
the worst failure this mod can have: the one moment the whole thing exists for, and it
lied about it.

The cause is the shape of the detection, not a typo. The inventory diff is gated on a
time window and the common outcome is reached **by elimination**, so anything the diff
misses — a drop landing after the window, one absorbed by a sack, one in a slot the
diff does not cover — does not produce an error. It produces a confident wrong answer.

If the recorder and replay harness from `lavafishing.md` already exist, extend them to
Diana and skip to §10.5. If they do not, build them now:

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

**4. Synthesise the branches we have not seen.** Hand-write fixture records for every
Diana outcome from the real message formats and run them through the same replay path,
so every branch is tested on day one.

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
default. Chains overlap with kills, so a burrow dug while a previous creature is still
alive must not reassign the pending cutscene.

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

What stays local: the scales, the chamber, the lava lake, the jaw, the cave, and every
timing number. Do not abstract the art or the beats — they are not the same and
pretending they are will flatten all three into one cutscene with different textures.

---

## 12. Do not break

- The cutscene opens on kill with the outcome pending, and the watcher keeps ticking
  while it waits. Load-bearing.
- `"defaultRequire": 1` in the mixins json. A mixin whose target does not resolve must
  crash loudly, not vanish.
- Verify against the real mapped Minecraft jar before handing anything over, per
  `CLAUDE.md`. 26.2 is not 26.1.
- The pre-branch segment is identical on every outcome. If a change makes a win
  detectable before the verdict, the change is wrong.
- No outcome is ever reached by elimination.
- Nothing plays on an ordinary burrow dig. A chain is up to ten in a row.
