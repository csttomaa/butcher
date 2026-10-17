# Butcher

Fabric client mod for **Minecraft 26.2** (Hypixel SkyBlock, Crystal Hollows). Plays a
cutscene when a Scatha gives up its loot.

Build: `.\gradlew.bat build` — Java 25 toolchain, Fabric Loom.

---

## Design direction (the user, 2026-10-02)

**The cleaver is the Scatha's, not the mod's signature.** It fits Diana's Chopping Block by
coincidence. New animations do not need a cleaver, a kill or a "butcher" theme - each source gets
whatever suits it. (Fin used the cleaver until 0.3.0, when it got a fishing hook.)

## Verify code before handing it over

The mapped Minecraft jar Loom downloads can be compiled against directly, which
catches every API mistake without a Gradle build:

```bash
# Its class files are Java 25 (major 69). If your javac is older, rewrite the
# version bytes to 65 and it reads fine - compilation only needs the constant
# pool and member descriptors.
python3 - <<'PY'
import zipfile, struct
src = "<gradle-cache>/fabric-loom/minecraftMaven/.../minecraft-merged-deobf-26.2.jar"
zin, zout = zipfile.ZipFile(src), zipfile.ZipFile("/tmp/mc21.jar", "w", zipfile.ZIP_STORED)
for it in zin.infolist():
    d = zin.read(it.filename)
    if it.filename.endswith(".class") and d[:4] == b'\xca\xfe\xba\xbe':
        d = d[:6] + struct.pack(">H", 65) + d[8:]
    zout.writestr(it, d)
zout.close()
PY

javac -proc:none -d /tmp/out -cp /tmp/mc21.jar -sourcepath "src/main/java:/tmp/stubs" \
      $(find src/main/java -name '*.java' ! -path '*mixin*')
```

Fabric, brigadier, authlib, fastutil and gson are not in that jar. Tiny hand-written
stubs for the handful of types used (`ClientModInitializer`, `Event<T>`,
`HudElementRegistry`, `LiteralArgumentBuilder`, `GameProfile`, `IntList`,
`JsonObject`…) are enough; they only need the members this mod calls.

`javap -c` on the same jar is also how to settle any "does this method exist / what
does this argument mean" question. Do that instead of guessing.

## 26.2 is not 26.1

Scatha-Pro (the reference mod this borrows detection from) targets 26.1, so its
signatures cannot be copied verbatim:

| 26.1 | 26.2 |
| --- | --- |
| `ItemInHandRenderer.renderHandsWithItems` | `submitHandsWithItems` |
| `Minecraft.getMainRenderTarget()` | `gameRenderer.mainRenderTarget()` |
| `renderAllFeatures()` | `renderAllFeatures(SubmitNodeStorage)` |
| `renderBuffers().bufferSource().endBatch()` | `renderBuffers().endFrame()` |
| `Minecraft.setScreen` | `Gui.setScreen` (`Minecraft` keeps only `setScreenAndShow`) |

`butcher.mixins.json` used to carry `"defaultRequire": 0`, which makes a mixin
whose target name does not resolve **fail silently** rather than crash — it just never
runs. That hid a broken `ItemInHandRendererMixin` for a long time. It is now **1**, so
the same mistake crashes on load instead; check every target with `javap` before writing
a mixin, and see **Screen ownership** below.

`useLegacyMixinAp = false` in build.gradle is required: this project produces no
refmap, so without it mixin targets are looked up under Mojang names at runtime and
crash with "No refMap loaded".

## GUI drawing

Everything is drawn through `GuiGraphicsExtractor`. The important one:

```java
// 10-arg: no tint.
blit(pipeline, id, x, y, u, v, w, h, texW, texH)
// 11-arg: SAME, plus an ARGB tint multiplied into the texture. The shorter
// overloads pass -1 (opaque white) for it - confirmed in 26.2's bytecode.
blit(pipeline, id, x, y, u, v, w, h, texW, texH, argb)
```

That tint carries **alpha**, so sprites can fade and be recoloured at draw time. Much
of the older code predates finding this and fakes brightness by varying sprite *size*
(torch glow, embers) or bakes fades into frames (cleaver trail, spark sequence).
Those still work, but new code should use the tint.

`enableScissor/disableScissor`, `centeredText` and `textWithWordWrap` also exist.
`pose()` returns a **2D** `Matrix3x2fStack`. The older code took that to mean no 3D is
possible from the HUD — which is why the cleaver's rotation is pre-rendered into 14
frames and the pet head "spins" by pinching its width. It is not true; see
**3D in the GUI layer** below.

## Drop detection

There is **no chat message** for a Scatha kill or its drop. Detection is:

1. `ScathaWormTracker` — scans nearby `ArmorStand` nametags for `❤` plus `" Scatha"`.
   The nametag also carries current HP, which drives the pre-kill hide.

   **A vanishing armor stand is not a kill.** It used to be treated as one, which fired
   the cutscene on chunk unloads, walking away, teleports and relogs. `whyNotDeath()`
   (ported from SkyMaxx's `SeaCreatureDetector`) refuses a vanish that came with a
   player teleport, a bulk entity unload, an unsettled world, too few tracked ticks, a
   stale nametag, too much distance, or no damage ever observed. An entity that is
   still loaded but no longer matches is **not** a candidate at all — only one the
   client has actually dropped is. Every refusal is narrated with its reason.

   The checks are deliberately one-sided: missing a real kill costs the player the
   reveal on a drop they did get, so each only fires on strong evidence. The
   no-damage rule in particular is skipped inside `POINT_BLANK_RADIUS`, because the
   nametag is sampled at 20Hz and a one-shot kill never produces a damaged frame.
2. `HotbarDropWatcher` — diffs inventory NBT (`CUSTOM_DATA` → `"id"`, `"petInfo"`)
   against the previous tick, every tick.

**The diff must not be gated on the kill window — this was a real bug.** A kill is
only ever noticed the tick *after* the armor stand actually goes (the tracker
compares this scan against last tick's), while Hypixel hands over the loot as part
of the same death handling. So the pet routinely lands in the inventory *before*
the kill is detected. Diffing only inside the window loses it completely: the
baseline refreshes every tick, so the pet is absorbed into the baseline on the tick
it arrives, and by the time the window opens there is no increase left to see — the
window then closes empty and a **pet gets reported as a gemstone**.

Scatha-Pro's `ScathaDropsDetector` avoids this by keeping the two apart, and so does
this: every tick the inventory is diffed and anything new goes into `cachedDrops`
stamped with the time. A cached drop stays claimable for `KILL_WINDOW_MS`; a kill
then claims whatever is in the cache. That covers a drop landing up to a window
early as well as late. `ButcherClient` therefore calls `dropWatcher.tick(...)`
on **every** tick without exception — only the `mayResolve` flag is conditional.
Skipping a tick hides whatever changed across the gap.

**Gemstones can only be inferred.** With a gemstone sack they never enter the
inventory at all, so the only signal is the window closing with nothing claimed.
Gemstones are the common case, so this used to delay the cutscene by the full window.

**The crystal veins (0.2.49)** - the user's "variant A" for the Scatha's empty sides, after its cave
was removed. `render/CrystalVeins`: pure, like ThawFrost. ~45 crystals (corner clusters, singles up
the sides and along the top) in the Hollows' six gem colours, each a prism **rasterised** onto a grid
(every cell whose centre is inside, then an outline of the cells round it - walking the shape cell by
cell left holes the outline showed through), lit face / ridge / dark face / brighter tip. Each has an
arrival 0..1; `ScopeMinigameScreen.drawVeins` grows them a batch per cut (`partsRemoved / chops`),
and at the reveal: SHATTER for a gemstone, FLARE (rarity colour, turning towards the head at the
middle) for a pet, SINK on a missed last swing.

The fix — do not undo it: the cleaver cutscene **opens the instant the kill is
detected**, outcome unknown, and `ScopeMinigameScreen.resolve()` fills it in during
the fade-in. `openScope` assigns `currentScope` synchronously (only `setScreen` is
ever deferred, and only for `/butcher test`): a drop already sitting in the cache
resolves later in the *same* tick, and `trigger()` has to find the screen waiting
rather than open a second one over it.

Elusive mobs are always *charmed* rather than killed on current Hypixel; the tracker
handles that the same way, since the armor stand vanishes either way.

## The decision log

Neither half of detection can be summoned on demand — a pet is roughly a 1-in-2000
drop, and a kill means going and mining one. So both detectors narrate themselves
into `DecisionLog`: every kill accepted, every kill refused *and why*, every item
matched or ignored, and every gemstone inferred. Read it back with `/butcher log`.

When changing anything in `detect/`, add the narration at the same time. A refusal
that says nothing is indistinguishable from a bug, and that is the whole reason this
exists.

## 3D in the GUI layer

The old note here said `pose()` is a 2D `Matrix3x2fStack` so there is no 3D from the HUD,
and that this is why the cleaver's rotation is baked into 14 frames. **That was wrong.**

A 2D *affine* transform maps a rectangle onto any parallelogram, and the orthographic
projection of a flat quad IS a parallelogram. So a 3D quad draws with the ordinary blit:

```java
pose.pushMatrix();
pose.mul(new Matrix3x2f(ux/uw, uy/uw, vx/vh, vy/vh, ox, oy));
blit(pipeline, id, 0, 0, u0, v0, uw, vh, texW, texH, tint);
pose.popMatrix();
```

`render/model/` is that pipeline: `Mesh` (quads as corner + two edge vectors + normal),
`ModelProjection` (yaw/pitch, drop Z, cel-shade into 6 bands, keep depth), `BoxMesh`,
`PlayerHeadMesh`. `render/MeshRenderer` queues models, sorts **every** face from every
model together — there is no depth buffer in the GUI layer, so one global back-to-front
sort is the only thing keeping the model from turning inside out — then draws.

Two conventions that are easy to get wrong and fail silently:

- **The camera sits at +Z.** Yaw 0 shows a model's +Z faces, so a model's "front" belongs
  on +Z, *not* on -Z as a vanilla entity model would have it.
- **A mirrored face looks fine.** Get a u-edge direction backwards and the quad still
  renders, just flipped. `PlayerHeadMesh`'s UVs were settled empirically with a labelled
  skin (white pip top-left, black pip bottom-right); redo that test if you touch them.
- **That test missed the sides (fixed 0.2.53).** The unwrap is `right | front | left | back`
  wrapped round the head, so the head's own right side (the slot left of the front) is on the
  viewer's left, -X, when the face looks at the camera. `PlayerHeadMesh` had the two side slots
  swapped - invisible on symmetric skins, found because the Megalodon's mouth wraps round the front
  corners and broke there. `ChopScene`'s own head boxes always had it right (+X = slot 16, -X = 0).
  Check a head change by rendering an asymmetric skin offline and looking at the corners:
  the scratchpad's `shark/HeadShot.java` draws `PlayerHeadMesh` with any skin at several yaws.

Outlines are in `MeshRenderer`, not in the art: the silhouette is stamped eight times in
black behind the model, offset in **screen** space. Scaling the model up instead would
expand about the origin, giving a hairline outline at one end of a worm and a fat one at
the other.

## The Scatha is player heads

A Crystal Hollows worm is not a custom entity. Each segment is an armour stand wearing a
**player head**, and that head's skin is the worm's texture — an ordinary Mojang skin
owned by Hypixel, not in the game's assets or ours.

Two consequences, both of which cost a rewrite to learn:

- The stepped silhouette along the worm's back is **not geometry**. It is the hat layer:
  a second cube inflated half a unit, transparent except where it pokes out. Do not model
  it. `PlayerHeadMesh.head()` gives base + hat, which is the whole segment.
- **Do not hand-paint the texture.** `render/ScathaSkins` captures the real one off a live
  worm: armour stands near a tracked Scatha are checked for a head item with a `PROFILE`
  component, and the skin the game already resolved for it is kept. Head and body are told
  apart by frequency — the skin seen many times per worm is the body, the one seen once is
  the head. Cached to `config/butcher/skins.json`; a PNG in
  `config/butcher/skins/` overrides it. `ScathaArt` is a placeholder for before the
  first capture and nothing more — resist making it "better".

## Art

**Players can replace any of it.** Every generated texture is written to
`config/butcher/textures/` on first use, and `render/ArtOverrides` prefers an
edited file over the generated one. The trick is a SHA-256 manifest
(`generated.properties`): a file whose hash still matches is ours and gets replaced when
the art changes, a file whose hash does not is the player's and is never touched. To go
back to the built-in art **delete the PNG**, not the manifest line - deleting the line
does the opposite and marks the file as theirs forever.

There are two generations of art here, and it matters which one you are looking at.

**Generated at runtime — the live path.** `render/art/` draws with `Graphics2D` into a
`BufferedImage` when the texture is first asked for, and `TextureBank` uploads it.
Nothing is shipped in the jar, so `/butcher reload` is the whole edit loop: change the
Java, reload, look.

| class | produces |
| --- | --- |
| `CaveArt` | sparkle and vignette (still drawn); the backdrop and gem outcrops are still generated but nothing draws them since 0.2.46 - the Scatha only dims now |
| `PropArt` | the cleaver's skin; also crystal, gem-glow and stone, which nothing draws yet |
| `ScathaArt` | placeholders shown until `ScathaSkins` has captured the real worm skin |

**Baked PNGs — the older path.** The scripts in `tools/` write into
`src/main/resources/`. **Edit the script and re-run it** rather than touching the PNG,
or the next regeneration wipes the change:

| script | produces | still drawn? |
| --- | --- | --- |
| `gen_sparks.py` | `butcher/spark_0..5.png` | yes, the impact sparks |
| `gen_veil.py` | `prekill_veil.png` | no - the pre-kill veil was removed (0.2.31) |
| `gen_misc.py` | `spark.png` (+ dormant placeholder art) | yes |
| `gen_icon.py` | `icon.png` | yes - the mod icon (since 2026-10-15) |
| `IconGen.java` | `icon.png` | no - the old rendered cleaver; running it overwrites the current icon |
| `gen_cave.py` | `cave/cave_wide.png` | no — `CaveArt.backdrop()` replaced it |
| `gen_gems.py` | `cave/gem_*`, `rock_*`, `rock_glow_*`, `sparkle.png` | no — `CaveArt` replaced them |
| `gen_cleaver.py` | `butcher/cleaver_00..13.png` | no — the cleaver is a real mesh now |

The three superseded scripts are kept because they are the only copy of that art's
source; their outputs have been removed from `src/main/resources/`, so re-running one
puts files back that nothing loads.

**The icon (2026-10-15)** is `gen_icon.py`: 32 x 32 pixel art written 8x up - a gold B on a dark badge, its
top-left corner chopped off along x + y = const (the cut keeps clear of the counters, so the B always reads), the
piece pushed a pixel up-left, both cut faces white-hot, sparks along the line. The user picked it from a sheet; a cut
through the middle split both counters and read as "F3". The menu draws it at its native 32 GUI pixels
(`MenuView.ICON_SIZE`; `MenuIcon` halves 256 -> 32 exactly), beside "BUTCHER" with the subtitle under the name.

`IconGen.java` is the odd one out: it is Java, not Python, because it renders the icon
from the **real** `CleaverMesh` through the **real** `ModelProjection` with the **real**
`PropArt.cleaver()` sheet. The icon therefore cannot drift from what the mod actually
draws, and changing the blade and re-running it updates the icon to match. Its header
has the two commands. `gen_misc.py` still contains the old hand-drawn `icon()` but no
longer calls it - running it would overwrite the rendered one.

`guillotine_frame.png`, `blade.png`, `cave/cave_bg.png` and the `walk/frame_*` set have
**no generator** — they are hand-made or ported art, and every one of them is still
drawn by the guillotine mode (`AnimationMode.GUILLOTINE`), which the menu no longer
offers but a config file can still select. Do not delete them casually; there is no
script to bring them back.

`scatha_head*.png`, the `butcher/seg_*` set and everything under `scope/` were the same
kind of art but nothing loaded them any more, so the rename to `butcher` took them out.
They are in git history at the `0.1.0-beta` tag if one is ever wanted back:
`git show 0.1.0-beta:src/main/resources/assets/scathabutcher/textures/gui/scope/scope_mask.png > scope_mask.png`.

Values that are duplicated between a script and Java, and must move together:

- **Torch positions and sparkle anchors** (`CaveArt.TORCHES`, `CaveArt.SPARKLES`) only matter if
  the cave ever comes back: the Scatha's cavern, torches, bats, embers and gem outcrops were removed
  in 0.2.46 (the user: keep only the dim), along with the "Plain backdrop" option.
- **Cleaver edge anchor** — `CLEAVER_EDGE_FX/FY` still positions the blade by its
  cutting edge, and `CLEAVER_FRAMES` (14) is still the swing's timeline even though the
  frames themselves are gone: each phase works out an index 0..13 and that index maps
  to a blade angle.

Two techniques the PNG scripts rely on, which `render/art/` follows as well:

- **Blur alpha only.** Blurring an RGBA image blurs its colour channels too, and
  transparent pixels carry RGB 0, so a plain `GaussianBlur` drags black into every
  soft edge and turns a white glow into grey smear. Blur the mask, re-pair it with a
  flat colour.
- **A light map.** In `gen_cave.py` every atmospheric effect is multiplied by a
  blurred field that is bright only near the torches, with a steep (`**2.4`) falloff.
  Rim light and haze applied everywhere read as a wash, not as light — an early pass
  that skipped this turned the cave into beige soup.

Greyscale + tint is why the gem crystals are a separate sprite from the rock: the
tint multiplies into the whole texture, so stone sharing a sprite with the gems would
change colour along with them.

## Sound

`audio/` synthesises every cue at runtime - there are no sound files in the jar. A daemon
thread owns a `SourceDataLine` and fills it in 512-frame blocks; the game thread only ever
leaves a message on a queue, because the audio thread has ~11ms per block and must never
wait on a slow tick. Broken audio is silent audio: no device, an exclusive-mode driver or
a write failure all set `broken` and stop, and the cutscene plays on regardless.

Two things that were measured rather than guessed, and will be wrong again if changed
carelessly:

- **A bandpass throws away most of white noise's energy**, the more so at low Q. The
  impacts came out around -30dB before `BANDPASS_MAKEUP` put it back.
- **Normalise harmonics by root-sum-of-squares, not by the plain sum.** Partials are at
  different frequencies and only all peak together for an instant; dividing by the sum
  assumes that happens constantly and makes every multi-harmonic voice far too quiet.
- **`Voices.after` is sample-accurate on purpose.** Rounding a delay up to a whole block
  is 11.6ms, which turns the impact's 4ms metallic ring into a separate event and makes
  the chime's arpeggio uneven.

`SynthEngine.renderOffline` renders a voice to a float array with no audio device, which
is how the cues are checked without being able to hear them - envelope, peak, DC offset,
whether the voice ever ends. A voice that never returns false accumulates in the engine
forever.

Separately, `mixin/SoundEngineMixin` refuses the GAME's sounds while a cutscene owns the
screen, because Hypixel's drop chime can land before the cleaver gets there and give the
reveal away. That is a different path from the synth's own line, which is why silencing
one does not silence the other. It hooks **both** `play` and `playDelayed` - a chime
queued on the kill tick is a delayed sound, so cancelling only `play` misses exactly the
case that matters.

## Chat during a cutscene is held, not dropped

While chat is covered - a cutscene, the Scatha pre-kill hide, or the profit window
(`ButcherClient.chatHeld()`) - new chat lines are **held** by `ChatHold` and put back, oldest
first, on the first tick nothing covers the chat. Three versions were needed, each fixing
what the user saw in game:

1. Dropped them. A Thaw with its ending is 10-20 seconds; whole conversations vanished.
2. Held them at Fabric's `ALLOW_GAME`/`ALLOW_CHAT` and put them back when the animation ended.
   That was the moment the profit window opened, and a chat line only shows for ten seconds,
   so they faded out behind the window - the player still saw nothing. It also replayed lines
   other mods hide (a `/locraw` JSON appeared): Fabric ANDs every listener's vote, so at that
   hook nobody knows what the others decided.
3. Now: held at `ChatComponent.addMessage` (`mixin/ChatComponentMixin`, HEAD, cancellable),
   after every other mod has filtered, and put back through the same private method
   (`mixin/ChatComponentInvoker`) with source and tag untouched, only once the profit window
   has closed too. The Thaw claims the window *before* sending its haul summary, so the summary
   is held with everything else and comes out fresh.

The corpse loot message itself is still hidden for good at `ALLOW_GAME`; the summary replaces
it. The action bar never reaches `addMessage`, so it is simply suppressed during a cutscene.
When debugging this, the game log's `[CHAT]` lines are written by `addMessage` - a line in the
log reached the chat box; the user's live profile is `SkyBlock Enhanced Modern Edition (1)`.

## Drop records

`detect/DropRecorder` appends every real event to `config/butcher-drops.jsonl`, one JSON
object per line, append-only and never pruned: `scatha_kill` (verdict, the last 15 s of chat,
the last 25 decisions), `vanguard_corpse` (the parsed haul, 10 s of chat), and `diana_line` -
any chat line matching a broad Diana keyword pattern, captured **before** Diana is detected at
all, because her detection must be written against real strings. Every incoming line passes
through `DropRecorder.chatLine` in `allowChatLine` before anything hides it. Also `fishing_line` (0.2.42, Fin) and `slayer_line` (0.2.43: boss names, quest lines, RNG meter,
the drop tiers) - capture only, for animations not built yet. Tests are never
recorded. Written on its own thread. `recordDrops` (menu: Drop records), `/butcher records`.

## Diana - decisions made against docs/diana.md (2026-09-27)

`docs/diana.md` is the spec. Where it conflicts with this project, the user decided:

- **The tell is honest**, like the Thaw's flare - it does not lie on a share of losses (§5).
  Tension comes from the fixed, outcome-identical sway and the verdict blackout.
- **Art is Java, painted at runtime** in `render/art/`, with a Java previewer rendering the
  real meshes (the ThawPreview way), not `tools/gen_*.py` and a Python previewer (§6, §9).
- **Factor across Scatha, Vanguard and Diana** (§11). (An old note here said lava fishing
  does not exist - wrong: it is on the Crimson Isle, Thunder and Lord Jawbus among its creatures, and
  the user proposed it as a source on 2026-10-02.)
- **"Never infer by elimination" is Diana-only** (§10.1). The Scatha keeps inferring a
  gemstone from an empty kill window - with a gemstone sack there is no other signal.
- **These creatures never start the cutscene, whatever they drop** (overrides §2's "any rare
  drop from any creature"): Minos Hunter (Hilt of Revelations), Siamese Lynxes (Crochet
  Tiger Plushie), Stranded Nymph (Washed-up Souvenir), Cretan Bull (Cretan Urn), Harpy
  (Antique Remedies), Gaia Construct (Dwarf Turtle Shelmet), Minotaur (Daedalus Stick). Only
  the rarer, more expensive creatures and drops play it.
- **What plays the cutscene**: every kill of a Sphinx, Minos Inquisitor, Manticore or King
  Minos (opened on the kill, outcome pending - nearly always a bust). A **Minos Champion only
  when the Relic actually drops** - not on its kills. The **Mythological Dye from any
  creature**, the seven excluded ones included. Both drop-triggered cases open on the drop,
  so the outcome is known when they start; they still play the same sway.
- The tell colours still need the user's sign-off before they are built (§5). Proposed
  2026-09-27: sway ember #7A3410 -> amber #F2A12A -> Diana red #E0301E -> black at the
  verdict, identical on every outcome. After it, one colour per creature, in niche shades the
  user asked for ("so they don't look like every other mod"): bust cold ash #3C4250, Minos
  Relic moonstone #BFC8DD (the user asked for silver), Brain Food wisteria #9F7BEA (purple),
  Chimera I thulian pink #DE6FA1 (pink), Manticore's Fateful Stinger / Manti-core viridian
  #3A8C6E (dark green), King Minos's Crown of Greed / Shimmering Wool carmine #C8003C (red;
  climbs into the judge), Mythological Dye its real in-game olive #6F6F0C (a turning hue
  until 2026-09-28, when the user chose the real colour). No gold any more - every drop that
  plays has its own. (The swatch renders were a throwaway script; the real previewer is to
  be Java.) Accepted 2026-09-27 - the user asked for no changes after seeing them.
- Diana was not mayor on 2026-09-27 but was leading the election by far; her detection waits
  on real strings from `diana_line` records.

**The Thread replaced The Judgement** (2026-09-27). The user did not like the Judgement (a
bronze scales chamber) and rewrote `docs/diana.md` as "The Thread": Ariadne's thread, one
locked-off shot, the whole animation two numbers. `diana_storyboard.png` beside it is the
staging. The same four decisions were re-confirmed against the new spec: honest tell, Minos
Champion only on a Relic, the Dye from any creature, Java art and a Java previewer.

**The Labyrinth replaced The Thread** (2026-09-28). The user found the Thread "boring and simple"
next to the Thaw and asked for ideas from Diana's ritual itself; of three pitched (the
Labyrinth, a dig cross-section, Diana's arrow at the moon) they picked the Labyrinth and, seeing
the preview, said it was what they had wanted in the first place. The Thread's files are gone.

**The Chopping Block replaced The Labyrinth** (0.2.25, 2026-09-28). The user liked the drop
endings (the Relic most, then Brain Food) but found the maze did not match Diana's vibe and needed a
whole black-screen cutscene; they asked for something simpler and exciting, on the Butcher theme -
killing the mob - over the **dimmed** game, not black. Of the options pitched they took this one, and
option (b) for Chimera. The Labyrinth's seven files were moved out of the repo, uncommitted, to
`E:\mod\labyrinth-archive\` - its `box()`, moon head, orbiting stars and screen were ported from there.

**How The Chopping Block is built** (playable only through `/butcher test diana <outcome>` until
detection exists):

- `anim/ChopTimeline` - pure, per verdict, **creature** and seed. The head drops onto the block
  (`LAND` 560), three chops (`STRIKES` 1250/1850/2400, each deeper - `bite`), the last wind-up
  trembles while the cracks cycle colours (`CYCLE` 2700 - the same fixed moonstone, wisteria,
  thulian, viridian, carmine, moonstone, wisteria, thulian), and the split at `SPLIT` 3300 - the
  first moment anything may differ. Bust 4.4 s: halves fall open and crumble. Normal win 8 s: light
  pours up, the drop rises (`ARRIVE` 4300), then the block and halves sink away (`blockFall`) so the
  drop, name and price stand alone. UNKNOWN 5.2 s, neutral.
- **The creature is not the verdict.** `Creature.of(verdict, seed)` gives the drop's own creature
  (Relic - Champion, Brain Food - Sphinx, Chimera - Inquisitor, Stinger/Manti-core - Manticore,
  Crown/Wool - King Minos); a bust or the Dye picks one of the four kill creatures by seed. In game
  the head will be the **kill's**, which the player already knows, so it tells nothing -
  `tools/ChopTest` hashes every draw call before the split for all ten verdicts, **per creature**,
  five seeds.
- **The heads are real skins** from the NEU repo's `constants/bestiary.json` (keyed by the mob's
  display name, field `texture`), bundled in `assets/butcher/creature_heads.json` and loaded by
  `HeadSkins.CREATURES`. Only the Chimera's lion, goat and serpent have no skin anywhere and are
  painted (`ChopArt.head`). **For any head texture, check the NEU repo first** - the user's rule.
- `render/ChopScene` - one drawing routine for game (`gui/ChopScreen`) and preview
  (`tools/ChopPreview`), over a Canvas whose `quad` takes a **texture name** (sheet, skins, cleaver,
  beasts). `box()` draws any rotated textured box, front faces only, with an ink pass for the black
  outline; a face rect of null is skipped. The head is `HEAD` 4 half-extent on the block top (y 0),
  base + hat as every head is; after the split two half-boxes pivot on their outer bottom edges,
  the cut face `ChopArt.FLESH` (or `BRAIN_CUT` for Brain Food). The blade goes *into* the head:
  see the cleaver below (`clipAbove`).
- The cleaver is the real `CleaverMesh` with `PropArt.cleaver()`, scale 0.42. **Its blade lies in
  the plane of the cut** (x = `strikeX`), edge running front to back along the slot, handle behind,
  swung about its pommel (`cleaverVec`); `cleaver(t)` gives {x, edge y, angle, alpha}, angle 0 level
  in the head, -1.35 raised. 0.2.25 had it facing the camera while the head split left/right, so
  the chips flew out at the viewer - the user saw it at once. While level and in, each face is
  clipped to the part above the head's top (`clipAbove`; every face of a level box has one flat
  edge, so the cut stays a parallelogram) - the old trick of redrawing the head's front over it
  only works for a face-on blade. Camera yaw 44 -> 36 through the chops so the blade reads.
- Chips burst from the slot off both faces of the blade (along X), skim the head's top, land on the
  board or fall off its edge; drawn in two passes, behind the head and after the blade.
- The board is thin (`BLOCK_H` 4) - the user's call. **No letterbox bars** on Diana's endings -
  the user asked for them gone (the Thaw keeps its own).
- `render/art/ChopArt` - the 128 sheet (oak end-grain top, bark side, plum flesh, a brain in
  section, the moon's six faces and hat, runes), glow, ring, rays, the three beasts.
- Sounds (`Voices.chop*`): the head's thud on the wood, a swish before each blow, each chop a wet
  crunch + wood thock + ringing blade a whole tone up each time and **louder each time** (-9.6,
  -9.0, -7.4 dB), the Thaw's riser and cycle ticks over the wind-up, `silence()` and the split
  (-2.2 dB). Bust: the empty breath and a crumble. Win: the rising swell, bells and fanfare.
- **The Relic** (16.8 s): the Champion's split head holds together and throbs three times
  (`RELIC_BEATS`), then comes apart into 4x4x4 chunks of its own skin, outside first
  (`chunkLaunch`, `chunk`) - each chunk the colour of the skin where it sat, not flesh - which spiral
  up a vortex to `MOON` and fuse at `RELIC_CONVERGE`; the block falls away (`blockFall`) and the
  camera climbs to the moon. Then as before: the moon head with orbiting stars spins up flickering
  moon/Relic faces (`relicFaces`), stops three-quarters on (`RELIC_REST_TURN`) as the Relic.
- **Brain Food** (12.8 s): the halves ease apart; a tree of veins (`veins()`, `{x0,y0,z0,x1,y1,z1,
  depth, parent}`, grown over the head's top, its front and out across the block top, 60+ per seed)
  lights in order with a flash per tip, brain waves pulse through, 26 sparks run home along the
  parents (`brainSpark`); it drains into the split, the head bursts, Brain Food rises inside the pi
  ring. Head-local vein points turn with their half (`ChopScene.veinWorld`).
- **Chimera I, "The Beast Fused"** (13.1 s): the Inquisitor's head falls open; lion, goat and serpent
  rise and circle facing outwards, each lunging to the camera to roar in turn (`roarTime`, a ring),
  spiral in and crash (`CHI_CRASH`); the **vanilla enchanted book icon** is what is left, not a 3D
  book (the user asked), named in #FF55FF.
- Carried over from the Labyrinth and still true: drops drawn as their real items (`DianaItems`,
  NEU skins for heads, vanilla icons for the book and helmet, a 3D yellow wool block), every drop
  outlined, every win's price counting up (bazaar instant sell, else lowest BIN, else NPC -
  `ChopScreen.fixPrice`), letter-by-letter name and two lines for the specials under a letterbox,
  text capped at 1.35x, the gameplay window (below), Diana volume on the win cues.
- **Gameplay window** (0.2.18): the game itself live in a corner of every cutscene - SkyMaxx's
  `GameplayView`, MIT, credited in the file. `mixin/GameRendererMixin` copies the main render
  target's colour texture just before `GuiRenderer.render()` (26.2: no argument; main target is
  `gameRenderer.mainRenderTarget()`; format is `GpuFormat`, taken from the source texture), only
  on frames after a cutscene asked. Config `gameplayCorner` 0 off / 1-4 corners, menu "Gameplay
  window". Freed on every cutscene close. Unverified in game. Since 0.2.80 Hellion, the Seraph and Eruption draw it
  too (each screen calls `GameplayView.draw` last and `release` in `close()`); any new cutscene must do the same.
- `tools/ChopTest` (136 checks): honesty per creature, the cycle's steps, a bust under 4.5 s, every
  ending fades out, the cleaver never jumps and is level on each blow, every Relic chunk still until
  the peel and home at the moon when it ignites with no jump, the veins joined, ordered, dense, on
  the block and dark before the burst, the Chimera's roars and crash. `tools/ChopPreview` renders
  stills (`-Dat=`) or every frame, `-Dskins=` a folder of `<ID>_skin.png` (creatures and drops),
  `-Dvanilla=` the book, helmet and wool, `-Dbg=` a screenshot to dim behind.
- The user's standard for every ending (2026-09-28): the most rendering, animation, sound and
  effects possible - and neat: nothing unnecessary on screen. Busts short, so the player keeps
  playing; a real drop exciting.
- **The quality rebuild (0.2.29)** - the user tested 0.2.27 in game and sent screenshots: the Dye
  "horrible", the Stinger's venom and Brain Food's veins "weird", the Manti-core good. Asked why these
  could not be as good as the Thaw, the honest answers, which are now **rules**:
  - **The preview must render like the game** (`tools/ChopPreview`): laid out at 640 x 360 GUI pixels
    but rasterised at 1920 x 1080 (`-Dscale`, default 3), lines as hard-edged filled rectangles (what
    `ChopScreen.line` draws), small textures nearest-neighbour, a dark stand-in of the user's world
    behind (`-Dbg`). The old preview drew smooth anti-aliased strokes at 640 x 360 on a flat sky, and
    effects that passed there looked cheap in game.
  - **Pixel art only for effects.** Smooth, shaded spheres (the old `ChopArt.drop`) read as olives or
    baubles next to pixel-art heads; lines floating in front of a face read as bars. Liquids are
    `TEX_PIXDROP`/`TEX_PIXSPLAT` and squares (the game's own particles); veins are pixels painted onto
    the faces (`ChopScene.pixelVeins`, half-texel grid, each pixel on the half its middle is on).
  - **Never a full-screen white or grey fill** - over the dimmed game it is grey fog (twice now).
    Flashes are a glow where it happens (`ChopTimeline.flashAt`).
  - **Overlays go texel by texel through the faces themselves** (`ChopScene.Gild`: a colour per
    texel of every face, its alpha byte how strongly the face's own texture shows through as relief).
    The Crown's gold, the Dye's stone and paint all use it; a screen wash with a hole did not work.
- **Cheap drops are short** (`ChopTimeline.cheap()`: Brain Food ~1M, Crown ~1.8M, Stinger ~2.9M, prices
  2026-09-29; the user: cheap drops do not need that much animation). Their lines and price come in
  sooner. Expensive: Wool ~38M, Manti-core ~35M, Relic ~29M, Chimera ~26M, Dye ~15M.
  - **Stinger** (`StingEnding`, 9.6 s): the barb (bulb, spike, two plates of tail, chitin in the Fateful
    Stinger's maroon) rises out of the split, hangs with a pixel bead, stabs the board (`IMPACT`):
    pixel splash, a crack and a stain in the wood (`stingDecal`); turns into the drop at the top. The
    first version (tail, strike at the camera, glass) was "too grand" for the user.
  - **Crown** (`MidasEnding`, 9.9 s): gold spreads (60 ms/texel), a shine, the halves burst into coins
    and the 3D golden helmet pops out of the split; the vanilla icon at the reveal.
  - **Brain Food** (9.1 s): pixel veins, one flood, burst, pi ring (the user likes it).
- **Dye, "The Whole Hunt"** (`HuntEnding`, 0.2.75; replaced `PaintEnding`'s "Wet Paint", the third dye to stop time).
  Pitched and approved 2026-10-05. The split goes wrong (halves shut on an olive cut, board cracks - `huntCracks`,
  cleaver wrenched out - `HuntEnding.cleaver`), `HUSH` (a cut cue, half a second of nothing), `GEYSER` (a column of
  pixel squares, the halves thrown open, rain staining the board and runs down the halves through the gild -
  `stained`, `dripped`), the parade (`BESTIARY`: all twelve mythological creatures' NEU bestiary heads, bundled in
  `creature_heads.json` and drawn through `Canvas.bestiarySkin`; out of the geyser tumbling into a ring, `slot` a
  permutation so they land scattered, King Minos last and bigger into the slot nearest the camera), each painted
  olive bottom-up by the gild at full relief so the skin still shows tinted, `SPIN` the ring, the moon rises behind,
  `ARROW` threads every head in `hitOrder` (King Minos last) where each head is at its hit, `SUCK`, `SHOCK` (the
  board blown away - `blockFall`), the dye in a column at `ITEM_Y`. Heads behind the block draw in `beforeBlock`.
  The camera looks down on the ring as it fills (from the side it was a heap) and up for the moon. Sounds are a cue
  list, `audio/HuntSound` (`Voices.hunt*`, G dorian; `ButcherAudio.playDiana` on the win volume);
  `tools/HuntSoundtrack` renders it. ChopFill's rays and motes now play for it, from the dye. ChopTest checks it.
- **Manti-core** (`HeartEnding`): 26 cells fly in and fuse (`cell`); each wing is three jointed panels
  (`bend`, `ChopSpecials.wingPanels`, texture split at `JOINTS`); the cocoon is the wings swung across
  in front (phi 2.25, a small curl) - curled round at phi 1.45 they went edge-on; cracks of light on the
  cocoon (`wingCracks`); the lion's roar at the burst.
- **Wool** (`FleeceEnding`): the ram constellation (`STARS`, `LINES`, drawn a line at a time, square to
  the camera) whose stars fall into a turning spindle (wood shaft, golden whorl); light shafts on the
  wool (`shafts`). A pluck per line.
- **Chimera I, "The Glint"** (`GlintEnding`, replaced the three beasts - their code is gone; the painted
  beast heads remain in ChopArt, unused). The game's glint texture is plain colour meant to be added;
  the GUI only blends, so `ChopArt.glint` turns its brightness into alpha - **read from the game's own
  resources at runtime** (`Minecraft.getResourceManager().open`), never bundled - scrolled over every
  face by a hook (`ChopScene.glintHook`). The enchanting glyphs are the game's own `particle/sga_*`,
  adopted per frame. A violet vignette for the air - tiles of glint along the edges drew a seamed frame.
- **Shared, for the expensive ones:** the banner (`ChopTimeline.banner`: "RARE DROP!" in #FFAA00, the
  Dye's "WOW!" in #FF55FF - Hypixel's own chat words) stamped between the drop and the name; a score
  (`Voices.score`, an arpeggio in each ending's own key and mode, to the reveal; the Dye's stops with
  time); lights (`ChopScene.light`, set per frame by `ChopSpecials.lights` plus the split's own) tint
  faces facing them towards their colour.
- **Settings and stats (0.2.30).** The menu's Diana section: creature switches (checked in
  `ButcherClient.openChop` for real runs only - tests always play), every drop's own ending (off:
  `ChopTimeline.options(simple, ...)` gives every drop the plain ending - ChopTest checks the
  pre-split frames are unchanged by it), the banner, the price, the size (`ChopTimeline.scale`), the
  music, the win volume. `ButcherStats.onDiana(creature, drop)` counts a real run before its screen
  is made, so the screen's second line reads "Your Nth X" from `dianaDropCount`; a test says so.
  The Stats tab has a Diana group with its own reset.
- **Detection (0.2.32)** - `detect/DianaWatcher`, written against the user's 500+ captured lines
  (2026-09-30). **No chat line marks a kill**: an Inquisitor dug and killed left only "Good Grief! You
  dug out a Minos Inquisitor!". So the kill is SBO's: the creature's nametag armour stand, HP read
  from it (`HP`, the number before "/" or the heart), dead the tick it reads 0; fallback, the nametag
  going at <= 5% HP within 32 blocks and no teleport. **Only creatures the player dug**: the dig line
  claims the nametag of that creature first seen within 20 blocks, from 3 s before the line to 15 s
  after. **Or it hid the HUD, or was charmed (0.2.40)**: a party member's Inquisitor killed 3 blocks
  away gave no loot share line at all, so the HUD went and nothing played; now a creature that armed
  the hide (`Mob.armedAt`, within `ARM_MS`) is ours when it dies, and "CHARM! You charmed the X and
  received" is read like a loot share naming X (it came 60 ms before the nametag read 0).
  **Or loot share (0.2.33)** - the user expected a loot-shared King Minos to play: a "LOOT SHARE
  You received ... for assisting" line within 2 s of a death, either side (it came 40 ms *before* the
  nametag read 0), claims it; the new wording names the creature ("2 King Minos Shards"), the older
  ("loot for assisting X") does not. Death first: kept 2 s in `unclaimedDeaths` - a RARE DROP line in
  that gap opens the chop itself before the line reaches chat, so the drop never shows early.
  Nametags read (2026-09-30): "[Lv1250] \ue07e\ue07b\ue077 Empyrean Sphinx 65M/65M❤" and at death
  "... 0/65M❤" - the HP reading is confirmed. Chat lines mostly arrive stripped, but some keep their
  `§` codes in the text (the new loot share line did), so `plain()` strips them first. Otherwise stripped ("RARE DROP! Antique Remedies (+277 \ue01a Magic Find)");
  `dropOf` is pure - "RARE DROP!" plus a drop name (Chimera by "chimera", not "You dug out"),
  or "WOW! ... found a Mythological Dye" naming the player. Only game lines, never player chat.
  A kill opens `ChopScreen` with a **pending** verdict (null): the drop line calls `resolve(v)`,
  which rebuilds the timeline with the same seed and creature - invisible, since nothing before the
  split depends on the verdict - and counts the stats; unresolved at `RESOLVE_BY` (SPLIT - 250) it
  is a bust. A drop with no kill pending (Relic, Dye, loot share, or a line that beat the nametag)
  opens a known-outcome chop. Nametags first seen and at death are recorded as `diana_mob`, so the
  real nametag format can be checked - **not yet seen by us; confirm from the first records**.
- **First real win, confirmed in game (2026-09-30, 0.2.35):** the user's own Inquisitor dropped Chimera I
  (26.7M); the RARE DROP line came 28 ms before the nametag read 0, the drop path opened a known-outcome
  chop, the kill was then skipped as already playing, and the Glint ending played - the user saw it.
- **The pre-kill hide (0.2.35)** - the user asked for the HUD, GUI and chat to go "a little before the
  mob is killed, like when he has 3m hp left ... like skymaxx does it". SkyMaxx's rule, read from its
  classes (`SeaCreatureDetector.aboutToDie`, `AnimationDirector.arm`): a creature that is ours, **or
  any while we have had loot share in the last 5 min**, seen >= 6 ticks, close (16 blocks), HP at or
  below the threshold with a max above it; armed for a rolling 2.5 s. `DianaWatcher.arm` decides once a
  tick; `armed()` only reads a volatile time, because the sound mixin and every HUD element ask it.
  It feeds `shouldHidePreKill`, so everything the Scatha's hide does follows (HUD, chat shut and
  held, game muted, hand hidden). Config `dianaHideBelowM` (default 3, 0 off), menu slider.
- **The HUD fades instead of cutting** (all hides): SkyMaxx's `HudFilter` (MIT) as `render/HudFade` +
  `mixin/GuiRenderStateFadeMixin` - while `HudFade.begin(alpha)` is on, every state added to
  `GuiRenderState` (element, blit, text) is replaced by a copy with its colours' alpha scaled; items
  and picture-in-picture states cannot be tinted and are dropped past half way; text below alpha 5
  is dropped (the font draws alpha < 4 opaque). `GuiTextRenderStateAccessor` reads the text state's
  private fields. It is begun only inside `hideVanillaHud`'s wrapper, per vanilla element, so our own
  cutscene is never touched. `hudAlpha()` eases 1 -> 0 over 0.35 s and snaps back to 1 when the
  hide ends. **Unverified in game** - the targets and constructors were checked with javap.
- **Filling the screen (0.2.47)** - the user found the sides empty, rejected info panels (nothing to
  read), and chose screen-filling motion for Diana (no walls - it plays over the real game).
  `render/ChopFill`: `debris` (each blow throws oak splinters and sparks out toward the edges, more
  each blow, 44 at the split; seeded, on the scene clock, so ChopTest's honesty still holds and the
  Dye freezes it), `rays` (a win's light: 12 hard-edged pixel rays in the drop's colour, stepped down
  over 7 bands, from the split to past the edges, behind the block), `motes` (40 squares of that
  colour rising across the width), `ash` (a bust's, short). No rays or motes for the Relic or the
  Dye. Everything on a one-head-texel grid. The rays come in at the drop's showcase (`arrive() - 150`),
  not at the split - the user's call in 0.2.51.
- Next: UNKNOWN from detection, replay/simulate, fixtures, `/butcher dump`.

**What SBO knows about Diana's messages** (SBO 0.5.2 for 26.2, a Diana mod the user supplied,
read from its class files 2026-09-28 - use as the starting point for detection, then confirm
against our own `diana_line` records; `§` codes as SBO matches them):

- A creature dug up: `^(.*?) §eYou dug (.*?)§2(.*?)§e!(.*?)$` - group 3 the creature ("Oi! You
  dug out a Minos Inquisitor!"). Treasure: `^§6§lRARE DROP! §eYou dug out a (.*?)§e!$`, coins
  `^§6§lWow! §eYou dug out §6(.*?) coins§e!$`, burrows `^§eYou (.*?) Griffin [Bb]urrow(.*?)$`.
- A drop: `^§6§lRARE DROP! (.*?)$`, told apart by `contains` on the name (Chimera arrives as an
  Enchanted Book whose text contains "Chimera"); Magic Find rides along as `(+N ✯ Magic Find)`.
- The Dye: `^§d§lWOW! (.*?) §6found a §2Mythological Dye.*$` - group 1 is a player name, so check
  it is ours.
- Loot share: `^§e§lLOOT SHARE §fYou received loot for assisting (.*?)$`; a drop soon after it is
  a lootshared one. **A lootshared drop plays the cutscene like any other** (the user, 2026-09-28).
- A kill: SBO tracks armour-stand nametags containing the mythological mob-type glyph (`§2` +
  U+E07E) and fires its death event when the HP parsed from the name (`([0-9]+(?:\.[0-9]+)?[MK]?)§f/`)
  reaches 0 - once per entity id. That is positive evidence of a kill, unlike the Scatha's
  vanish.
- Level prefixes: Empyrean, Exalted, Runic, Venerable, Stalwart, Blessed. King Minos has a hit
  shield phase (`.*?(\d+)\s+Hits.*`).

## Test runs are not counted

`/butcher test` plays the real cutscenes but touches no stats: Scatha tests go through
`triggerTest` and a `ScopeMinigameScreen` built with `test = true` (the cutscene is counted in
its constructor, so the flag has to be a constructor argument); Vanguard tests go through
`injectTest`, which sets `testingCorpse` around the watcher's `inject`.

## Cutscenes play in the HUD - the player keeps control (0.2.20)

The user asked to be able to move and look round while an animation plays, SkyMaxx's way (MIT).
The three cutscenes (`ScopeMinigameScreen`, `ThawScreen`, `ChopScreen`) are still written as
`Screen`s but are **never opened**: `render/CutsceneHud.show(screen)` hands one to a HUD element
(`cutscene_hud`, added last) that sizes it with `Screen.init(w, h)` - public on 26.2, sets width
and height and runs the screen's own init - and calls its `extractRenderState` every frame. No
screen is open, so the game keeps the mouse and keyboard. A cutscene takes itself off with
`CutsceneHud.hide(this)` in `close()`/`onClose()`; nothing calls `setScreen` for them any more.
The profit window and the menu are still real screens - they are interactive.

**The whole HUD is hidden, every mod's (0.2.37), SkyMaxx's way.** Hiding vanilla's elements one by
one (`hideVanillaHud`, 0.2.20-0.2.35) left other mods' HUD up - the user's screenshots showed
Skyblocker's status bars (registered through `HudElementRegistry`) and SkyHanni's lines, which
SkyHanni draws in `Gui.extractRenderState` just *around* the `Hud.extractRenderState` call
(`MixinGui_GameRenderer`), outside the HUD pass. Now:
- `mixin/GuiFadeMixin` turns `HudFade` on from just after the frame's `GuiGraphicsExtractor` is made
  until the first read of `Gui.overlay` - the HUD pass and everything drawn around it, never the
  overlay, screens or toasts, which come after (26.2 order read with javap).
- `mixin/HudFadeMixin` cancels `Hud.extractRenderState` whole once `hudAlpha()` reaches 0; SkyHanni's
  draws around it are dropped by the fade at 0. It also keeps the frame's extractor (`HudFade.frame`).
- **Our cutscenes are not HUD elements any more (0.2.40).** 0.2.37 drew them inside the HUD pass
  (as Fabric elements under `HudFade.untouched`, or directly when the pass was skipped) and the user
  heard every sound and saw nothing - the cause was never pinned down offline. Now `GuiFadeMixin`
  draws them where the fade window closes, fade off, after `graphics.nextStratum()`
  (`ButcherClient.drawOwnHud`): after the HUD, before screens. `HudFade.added` counts states; a
  cutscene that adds none logs "A cutscene drew nothing" to the DecisionLog once, so a repeat shows
  in `/butcher log`. Confirmed in game 2026-10-01: `/butcher test diana` shows again.
- Camera overlays (vignette, pumpkin) now go too; SkyMaxx does the same. A side effect: a cutscene
  also draws under F1 while it hides the HUD. Unverified in game.

**Other mods that skip their overlays under F1 are told the HUD is hidden (0.2.42)** -
`mixin/HudHiddenMixin` makes `Hud.isHidden()` (the method, not the field) answer true while
`shouldHideHud()`. For SBO: it draws its overlays, the Diana loot tracker among them, at the RETURN of
`Gui.extractRenderState`, after screens - outside the fade window - and skips them only on
`hud.isHidden()`; the tracker showed the drop before the split (the user, 2026-10-02). Vanilla callers,
read with javap: toasts, LivingEntityRenderer / ItemFrameRenderer name tags, the F3 overlay, and
`GameRenderer.shouldRenderBlockOutline` - so those go for the length of the hide, as under F1. The HUD
pass reads the field, so it is unaffected.

**`GuiScreenMixin` now only keeps the chat box shut** (`ButcherClient.chatShut()`: a cutscene or
the pre-kill hide) - its history is where a drop gives itself away (SkyMaxx's `chatShut`). Every
other screen opens over a cutscene that keeps playing underneath. Unlike SkyMaxx, the pause menu
does **not** skip it: the game opens that by itself when the window loses focus, and the user
alt-tabs a lot. `ChatHold` (holding new lines until the cutscene is over) is unchanged - it is
about not losing messages, not about hiding the chat.

Known gap: with the GUI hidden (F1) the HUD elements, ours included, are not drawn.

**The review's Tier 1 (0.2.84)** - the doc "Butcher animation review" (claude.ai/code/artifact/6e0782c3-c97e-430b-b957-1111c4f1af38)
holds the full priority list; Tier 1 is built and **confirmed working in game (the user, 2026-10-12)**:
- `gui/Cutscene` (Chop, Hellion, Seraph, Eruption screens): `showsWin`, `skip`, `interrupt`. `ButcherClient.makeRoom` lets
  a known win interrupt a bust or a pending kill; anything else goes to `waitForTurn` (a bust is dropped after 5 s, a win
  always waits). Each watcher listener's `resolve` falls back to `killKnown` when its pending screen is gone, so a win
  whose kill never opened still plays. Thaw and the Scatha are never interrupted.
- Skip: KeyMapping `key.butcher.skip` (Backspace, held 400 ms, `skipTick`). A win's `openedAt` moves back to
  max(arrive, countStart + COUNT_MS) and the jumped-over cues run inside `ButcherAudio.muted` so flags, counters and the
  price are set without sound. Scatha and Thaw skip by `onClose`.
- `mixin/DroppedItemHideMixin` (EntityRenderDispatcher.shouldRender) + `PickupHideMixin` (ItemPickupParticleGroup):
  no ItemEntity drawn while `hideDroppedItems()` - a slayer cutscene or its pre-kill hide. Only slayer drops land on
  the floor (the user).
- "Keep health and hotbar" (`keepVitals`, default on): `mixin/HudInvoker` calls vanilla's private
  `extractHotbarAndDecorations` again on a stratum after the cutscene (`drawVitals`); its `@Accessor isHidden` is F1
  itself, since `isHidden()` is ours while we hide.
- Text plates: every screen's `text`/`name` draws a dark plate (42% of the text's alpha) of the whole line's width.
- Scatha: `FIFTH_SWING_PCT` 10 - a bust can take four cuts and duck a fifth. The pet holds `PET_HOLD_MS` 2600 with a
  card (`petCard`): price from `LivePrice` with a filter id `PET_SCATHA?Rarity=LEGENDARY` (`binPath`; Coflnet filters
  the BIN by it), count and dry streak snapshotted at resolve.
- Eruption: `quenchTexture` maps each texel's brightness onto a heat ramp (warm yellow for white heat); the GPU ink
  fades as alpha^4; a bust's guaranteed drop shows Hypixel's pack icon (`hypixel_skyblock:textures/item/...`, only if
  the resource exists) and its price uncounted.
- The Seraph's black beam: `BLACK_RIM` #9A5CF0 edge, carried stub and landed beam alike (same for every drop before
  the reveal, so SeraphTest's honesty holds).

**Tier 2 (0.2.85)**, confirmed in game 2026-10-12:
- `ButcherStats.Source` per key ("blaze", "seraph", "lava", "shark"): kills, drops by title, dry streak;
  `onSource` from each screen's `count()` once the outcome is known (never tests), Fin from ButcherClient. Menu
  `sourceGroup`. Scenes show `nthNote` ("your 3rd") after the odds. Diana's bust: `ChopScreen.bustLine`.
- Banners: `HellionDrops.freshTier()` (the last drop line's tier word, 10 s) -> `HellionScene/SeraphScene.bannerWord`,
  coloured by `tierColour` (rare gold, very rare blue, crazy rare pink, RNGesus dark purple - only RARE DROP!'s
  §6 is seen in logs; the rest are from memory).
- `tl.price(coins, source)` in Eruption/Hellion/Seraph timelines; the source shows small under the price.
- Showcase size by worth: `ChopTimeline.showScale` (via `ChopScene.showScale` on every `itemSize`) and
  `EruptionScene.worthScale` (dropPose = dropPath grown by rise). Hellion and the Seraph already size by screen height.
- `ChopArt.glow` is 32 texels in six alpha steps and `ring` 40 texels: every `ChopScene.glow` is pixel art.
- Hides in percent: `dianaHidePct`, `blazeHidePct`, `seraphHidePct` (the Seraph's against 210M - its nametag has no
  max); `PatternConfig.migrateHides` converts the old million fields once (`hidesInPercent`).
- Sound: `Voices.gained` (pure - the offline tools reach HuntSound/SeraphSound, which must not touch ButcherAudio),
  Hunt x1.41, Seraph bust x1.78, Vial boom 0.91, `ButcherAudio.onSkip` (seraphStutter) on skip and interrupt.
- Scatha pet: `petRays` (TEX_RAYS in the rarity colour), name stamped 45 ms a letter, price counted over 900 ms.

**The menu review and half of Tier 3 (0.2.86)**, confirmed in game 2026-10-12:
- Busts per source (`dianaBusts`, `blazeBusts`, `seraphBusts`, `lavaBusts`; `ButcherClient.bustStyle`): 2 = none -
  `bustsOff` stops the kill opening, and the win still plays through each listener's resolve fallback; 1 = short -
  each screen's `shortBust()` (called wherever `count()` is, i.e. once the outcome is settled) jumps silently to
  REVEAL - 450 (Chop SPLIT - 450). Honest cost, said in the menu: a cutscene that keeps going is a win.
- `minWorth` (index into `PatternConfig.MIN_WORTH`): `underFloor(priceId)` turns a win under it into a bust in the
  listeners; an unknown price always plays. `floorPrices()` asks for every animated drop's price every 10 min.
- `chatSummary`: each screen's `summary()` on its natural end or a skip (`ButcherClient.summary`), and the Scatha's
  close. `calmEffects`: `ChopScene.calm` (set per frame by the screens) zeroes every scene shake and takes glows to
  55%; the Scatha's shake knob reads it too. `vanguardQuick`: ThawScreen jumps a CRASH corpse from the intro to
  finaleStart - 1500, events fired muted. `ButcherClient.resetSettings` (stats untouched). `playTest` for the
  menu's "Try it" rows.
- **"Try it" follows the bust setting (2026-10-16, the user)**: `playTest` sets `ButcherClient.tryingIt` while it opens
  a screen, which the screen keeps as `followsBusts`, so `shortBust` applies to it; with busts off a bust "Try it"
  plays nothing and says so in chat (`testWins`). `/butcher test` still plays everything in full.
- Menu: tab "Animations"; Live prices + the floor under General > Prices; every volume also on the Sound tab
  (`snd_*` slider ids); `bustsRow`, `tryRows`.
- Tier 3: Kuudra's carmine capped at 105 units and End Times' terracotta at 30 + 75*terra (both were floor washes);
  Kuudra's dye rest 56 (below the eyes); heatGlow 0.6 after the reveal (0.35 Kuudra); Chimera vignette 25% at 1.22x;
  the Seraph's world grey gild relief 0xD0; the Eye's rays drawn before the core; `HellionScene.flushLayer` draws
  the pixel layer before the showcase words; `dianaSize` default 130 (`dianaSizeRaised` migrates an untouched 100);
  the Scatha's card "Just gemstones..." / "Dwarven O's...". The glow is 40 texels in eight steps.
- **Diana's win rays come out of the drop (0.2.87, the user)**: every showcase item goes through
  `ChopScene.showItem`, which remembers where it was drawn (`itemX/Y/Size`); `ChopFill.rays` starts there, a frame
  behind, and reaches 0.85 of the item's size (the downward ones 0.3-0.7, short of the words). They used to start
  at the block's head point and run to the screen's edges behind the text.

**The rest of Tier 3 (0.2.88)**, confirmed in game 2026-10-12: `HellionScene.pillarPull` (0.92 / the camera's zoom while it leans
in on a die) and the lean to -40 not -48; each lit pip inset 14% (the High Class 6 is two bars in Hypixel's own skin -
not ours to change). `SeraphScene.horizon` - six stepped bands of #3A2160 up from the ground, 20% at most (a rim light
was impossible: a tint only multiplies, and the enderman's texture is black). The Eye's zoom is 1.3x. The Vial's
shockwave is four rings of touching squares plus dust, the stem 300 squares, the cap 760; Jawbus's arms go past
horizontal and then spread (`LavaRigs.golem`: zRot 0.45 and head back 0.35 per radian over 1), the vial in his chest
at 1.05. Kuudra pulls back to 0.68 and stays (it used to come back in); its dye's `worthScale` is 1.25; Eruption's
banner clears the item by `half * 1.45` (1.2 for a head). **Our own chat lines are skipped in `allowChatLine`**: the
chat summary ("[Butcher] ...") was being recorded as `slayer_line`. **The dragon (0.2.89)**: `SeraphDragon.pos` scales
x < 0 by `WEST_PULL` 0.6 - the loop round the beacon's left reached x -9 and left the screen; done in code over the
baked track (start, landing and heading checks unchanged), so a re-bake should fold it into the control points. The
rear: `REAR_BACK` 9, `REAR_LIFT` 4.6, `REAR_ZOOM` 0.24 (the camera pulls back), `REAR_RISE` 6 - wings above the banner,
the head in frame. **The review's "doubled pillars" (H8) was wrong** - two
neighbouring frames of the contact sheet; only the pillars being cut by the screen edges was real.

**Tier 4, the polish (0.3.0-alpha, 2026-10-12)** - unverified in game. The version moved to `-alpha`; the build's
auto-bump keeps whichever suffix is there (`x.y.z-alpha|beta`).
- Eruption: `creatureScale` per creature (Jawbus 1.5, Thunder 1.15, Plhlegblast 1.2, Ragnarok 1.3, Scuttler 1.4 - the
  guardian and squid are big already); the nametag sits over `creatureTop` (the highest projected corner of the
  creature's polygons), to 1.2 s. `ripples` where it sank. **The crust now spreads outward** from the vent
  (`crustFront` 0 -> 380 over `CRUST_FROM` 1450 .. `CRUST_TO` 2150, then everything) with a glowing rim at the front;
  a bust still closes it back in over the ring (`closing`). The heat ring is `RECOLOUR` gild (white heat #FFF0B0 with
  an #FF8A20 rim), its edge noise in 3-texel blocks; after an eruption it darkens to its edge. Magma blocks brighten
  with heat above 2.4 (`glowAll` in `drawGpu`); the ring's corners are half blocks; `drips` down the crater block's
  +Z face. Erupted, the rock blocks stay as stumps 3-9 high. `sideX` pulls the side pillars and smoke sources in to
  keep 6% of the screen clear (`sideLimits` is set at the top of `draw` - set inside `models` it was a frame late and
  broke EruptionTest's honesty on the very first frame). Basalt #B0A8B0, lone blocks sunk to their top. Smoke is
  round puffs. A bust's `SLUMP` (R+520): the crater block drops in, `slumpSteam`, `Voices.eruptionSlump`; bust total
  R+1800 / R+1100. Flash: a 4-cell bolt, the cone white for 50 ms at `F_BOLT`, `arcs` crawl round the book's square
  (`outline`). End Times' cracks are `crackSegments` (straight segments with kinks and two branches per ray).
- The Seraph: crystals 0.75x (base 9x3x9); `pillarX` from the screen's width with a 6% margin (26..48); crystal beams
  are two `TEX_WHITE` ribbons (2 cells purple at 40%, 1 cell pale at 80%) under the speckled texture - the vanilla
  beam texture is 80% transparent by design.
- Hellion: `RUNES` 5x7 Elder Futhark (thurisaz, algiz, othala, dagaz), the current one lit with a glow, the rest at
  half; a warm core - the real fire's bottom five rows at their own size - over the tinted fire; the last second in
  tenths, red; hits red only as one comes off (odd steps); a bust snuffs left then right (`snuffPuff`), the fire
  following the sinking pillar.
- Diana: the 3D scene is never faded in (fade-out only) - `blockPop` 0.85 -> 1 back-out over 120 ms and
  `ChopFill.dust`; `smear` (the blade 28 and 56 ms back, no ink, last 60 ms of each swing); a 2 px downward kick per
  blow in `shake`; `bite` {1.8, 2.8, 3.8, 8.4}; splinters are three grid cells turning; `DEBRIS` {6, 12, 18, 40};
  flesh is meat (#861C1E, fibres #B0302E / #5C1012, rim #E2BBA8); rays are eight wedges widening to 10 / 6 cells;
  `lines` is one merged line ("odds · your Nth", `ChopTimeline.merged`, filled by `ChopScreen.settle`).
- Fin: the cleaver is gone - `HOOK` on a line drops in at the middle (`HOOK_IN` .. `HOOK_UNDER`), the fin swims over
  from `FIN_FROM` and dives, and at `CHOP` the hook is yanked up (`YANKED`) over the drop; a bust's swings empty.
  `Voices.finYank`. Drawn only in game - checked with a Python mock of the sprites.
- Left of Tier 4: T8 is data (records at the next Fishing Festival). D10's pixel ring was done in Tier 3 (ChopArt.ring).

**0.3.1-alpha, from the user's look at 0.3.0 (2026-10-12):** the vent drips are gone (they looked weird). Diana's
showcases go through `ChopSpecials.fit` - the drop at most 0.26 of the screen high (`ChopScene.screenH`) and lifted
so the words clear the bottom; the Dye's `showScale` is 1.3 (at 1.45 with the 130% size its price was off-screen).
`ChopFill.motes` draws before the drop and its words. The wedge rays step every ~1.5 cells and end 6 / 4 cells wide.

## Publishing pass (2026-10-15)

- **Developer commands are whitelisted** (`Dev.allowed()`: the UUIDs in `Dev.WHITELIST`, or a development run).
  `/butcher test` and `/butcher debug` carry `.requires(src -> Dev.allowed())`, so for anyone else they do not exist.
  Everything a player needs is in the menu: every group ends in "Try it" (`tryRows` + `ButcherClient.playTest`,
  which covers "scatha" and "vanguard" too). Menu text must not point players at `/butcher test`.
- `ButcherAudio.tick()` runs every client tick, outside the Scatha's check - inside it, the cleaver switched off left
  the synth unstarted and every other animation silent.
- Config: `PatternConfig.sanitize()` clamps every value to the menu's range on load (keep it in step with new
  sliders); `fresh()` is the defaults with the one-off migrations marked done; `writeAtomically` is used by both
  config and stats; an unreadable file is moved to `.broken`. `recordDrops` defaults to **false**.
- The profit window waits in `pendingProfit` until no screen is open (`showPendingProfit`, 20 s at most).
- `HeadSkins.pixels` backs off two minutes after a failed fetch. `HeadCapture.WANTED` is empty (the Paragon Shard is
  bundled).
- **Stats count at detection when no screen will**: `countUnplayed` (busts off, switched off, a waited bust dropped),
  `unopenedDiana` (Diana kills with no screen; claimed by their drop line, a bust after `DIANA_DROP_WAIT_MS`;
  `ChopScreen.interrupt` hands a pending kill back). Screens still count their own runs - never both.
- `resolve(drop)` on Hellion/Seraph/Eruption/Chop returns false when it came too late to change the screen; the
  listener then gives the win a screen of its own (`killKnown` / a known chop).
- Scatha: `skippedScope` takes the drop of a run skipped before it resolved (`resolveSkipped`: counted, summarised,
  not played); `countDrop` counts once, at the reveal or on a skip.
- `SynthEngine.softClip` is linear to 0.5 then tanh to 1 - the old cubic stepped from 0.83 to 1.0 at full scale.
  The cue-list screens skip cues more than `STALE_CUE_MS` late (a `cut` still applies).
- Off-screen text: a scratch checker (a Canvas recording every text box) ran ChopScene, HellionScene and SeraphScene
  over 640x360, 480x360, 854x360, 480x270 and 427x240. `specialText` now shifts its block up to stay on screen;
  Hellion's pillar tag is clamped to the edges. Only Brain Food's pi ring leaves the screen, as it fades out.
- `tools/ChopPreview` takes `-Dw` / `-Dh` (default 640 x 360), used by the other previews too.

## Release (2026-10-15)

0.3.3-alpha is the first build published to Modrinth. `docs/modrinth.md` holds the page's fields (summary, categories,
dependencies) and the release notes; the project description is the README. The Stats rework the user asked for next
became the Ledger (below).

## The Butcher's Ledger (2026-10-16)

The user's achievement system: local only, a new "Ledger" tab beside Stats, "like an openable book ... with stamps,
maybe like a binder", a chapter per animation, old stats backfilled. They edited the stamp list as a spreadsheet.

- **`docs/ledger_stamps.xlsx` is the source of the stamp list** (ID, chapter, name, what earns it, kind, four tier
  numbers, hidden, keep). `tools/gen_ledger.py` writes `ledger/StampTable.java` from it - edit the sheet and re-run,
  never the Java. IDs are stable; what each one *counts* is code, `Ledger.value` (a switch on the ID; unknown IDs are
  events). The user's numbers: Scatha Worm Food 50/100/500/1000, Dry Aged 100 (45 Days 200, hidden), Beginner's Luck
  20; Vanguard 5/25/50/100, Ice Cold 25 (without a legendary - what the stats count), In the Black 30M; Thermal
  Runaway 50; Spirit Level 100/250/500 Ghost drops. Fin's Big Fish is any Megalodon (the stats never kept the pet's
  rarity; both are titled "Megalodon"); Bigger Fish, the legendary, is an event from the update on.
- **`com.butcher.ledger` is pure** (no Minecraft, no files): `Chapter` (colour for the tab, a darker `ink` for the
  page), `Grade` (Select, Choice, Prime, A5 Wagyu and their ring colours), `Stamp`, `Facts` (what the stats say -
  `ButcherStats.facts()` fills it), `LedgerData` (saved inside stats.json as `ledger`: entries {grade, at, value},
  event `flags` {id -> kill}, `seen`, the House's own counters, the last 60 drops), `Ledger` (value, gradeFor,
  refresh, award, run, drop, corpseWorth). `tools/LedgerTest` (219 checks) also reads the drop enums' source to
  check every name a stamp looks for is spelled as the stats write it.
- **Counted in ButcherStats**: every `on*` ends in `Ledger.run` (the House's cutscenes and bust run), `Ledger.drop`
  for a win (hot streak, night shift 3-5 AM local, the 777th kill of a source, Scatha back to back, the first pet's
  kill) and `stampCheck()` (refresh, then `ButcherStats.stamped` -> `StampPopup.queue`). `ledger()` backfills once
  (`backfilled`): the House's cutscene count from every source, `firstPetKill` as an upper bound, then a refresh with
  `pre` - undated, not announced. Resets never touch the Ledger. Events fed from outside: `onSkip` (skipTick, never a
  test), `onCoins` (the Chop/Hellion/Seraph/Eruption `summary()`, a real win's price), `onCorpseWorth`
  (`ledgerWorthTick`: the last real corpse once `CorpseValue` is complete, 60 s at most, live prices on), `award` for
  a Jawbus win (EruptionScreen.count) and a legendary Megalodon; Diana's loot share is `DianaWatcher.lastShareAt`
  within 10 s of the win.
- **The book is `gui/menu/LedgerView`**, laid out at 360 x 228 and scaled into the tab (mouse turned back the same
  way): shut, a leather cover (gold border, the icon, "THE BUTCHER'S LEDGER", stamps and new ones, a strap); open,
  the cover swings to the left (the front narrows to the spine, then the left page grows out of it), divider tabs
  down the right edge (a red dot for unseen stamps; the Seraph's taped), ruled pages with a red margin, a red ribbon
  that shuts it. Left page: the chapter's title page (stamps, grades, its numbers, the closest stamp with a bar) or a
  stamp's own page (the stamp large, grade, description, tiers, progress, "Stamped <date>" or pre-ledger, the kill).
  Right page: 4 x 3 stamps a page, "< n / m >" past twelve, "NEW" in red on fresh ink (dried when the chapter is
  left, the book shut or the stamp clicked). A page flip runs when the chapter changes. `StampArt` draws stamps (a
  grade ring, a paper ring, the ink disc, the chapter's item, pips, worn flecks fixed per id, a tilt per id) and the
  hazard tape (the Seraph's menu card uses it too). The tab's footer and `TAB_ICONS` have a book.
- **The pop-up** is `render/StampPopup` (the queue; shown from `drawOwnHud` only when no cutscene, pop-up or pre-kill
  hide is up - `tick(free)`) over `gui/menu/StampCard` (pure: a paper card, the stamp falling from 2.6x and turned,
  the thump at 170 ms with ink flecks and a jolt, "STAMPED!", the name, the grade; 2.95 s). `GuiDraw` is the menu's
  in-game `Draw`, shared. `Voices.stamp` (thump, edge click, paper, two bell notes, a third for a raised grade) on
  `ledgerVolume`; `ledgerPopups` under General. Offline: the scratchpad's `menu/src/CardPreview.java`.
- `PANEL_W` is 440 (`PANEL_H_MAX` 400, `PAD` 12, `GAP` 4 - the user found 392 crowded); a tab too narrow for its name
  draws its icon alone.
- **The book is shown on its own (2026-10-16, the user)**: the Ledger tab sets `MenuView.ledgerMode` (static, so the
  menu reopens in it); the menu fades out over 180 ms and the book drops in (`LedgerView.enter`, `backOut`), shut, and
  swings open. "< Back to the menu" top left, a hint line at the bottom, Esc (`MenuView.escape` from
  `ButcherMenuScreen.onClose`) and the ribbon (`shutByRibbon` -> `wantsLeave`) put it away (`leave`, `gone`). `tab`
  never becomes `TAB_LEDGER` any more. The book is 412 x 250, scaled to the screen in quarter steps (2.0 at a 960 x 540
  GUI), resting at `TILT` -0.02 rad with more while it lands, leaves, opens and turns; `toBook` undoes the transform for
  the mouse. Stamp names are 0.75x, wrapped at 36.
- **Pages turn** (`Spot` = chapter + page; `next`/`prev` run through the whole book): the folded corners (click, or drag -
  `drag`/`release`, over at `TURN_COMMIT` 0.3), the wheel, and the divider tabs. While a page turns the statics stay at
  `turnFrom`; `at(spot, r)` draws a page as another spot. The sheet is that page squeezed sideways
  (`Draw.scale(sx, sy)`, added for this) towards the spine, darkening as it stands up, with the next page on its back.
- **Pencil** on the pages: a quip and a 2x doodle per chapter at the foot of the title page (`note`, `DOODLES` in
  Chapter order), a double squiggle under the title, a coffee ring on two chapters in three, tally marks of the
  chapter's stamps on the right page.
- **92 stamps (2026-10-16, the user: "10 per page at least")**: 12 a chapter, 10 for Fin and Wisp (the Seraph's
  chapter stays taped, with none). The 33 added are marked in the sheet's notes column. They count from `Facts`
  fields added for them (`dwarvenOs`, `corpseRolls`, `corpseItems` by name, `dianaKillsBy` by creature,
  `allDrops()`, `dropSources()`), except the events: Big Ticket (`ButcherStats.onCoins`, one drop >= 10M), and Fin's
  Feeding Frenzy and Wisp's Double Haunting, Poltergeist, Matched Pair and Witching Hour, which `Ledger.timed` reads
  from the source's own recent drops (only when the clock is sane). LedgerTest checks every chapter has 10+.
- **The user's sheet edits (2026-10-16)**: Beginner's Luck is a pet within the **day's** first 10 Scatha kills
  (`LedgerData.scathaDay`/`scathaDayKills`, counted by `Ledger.scathaKill` from `ButcherStats.onKill`, local midnight)
  - an event, not backfilled. Dentist 25/50/200/500. The Ghosts' windows are `HAUNT_MS` 30 s (Double Haunting) and
  `GHOST_RUN_MS` 60 s (Poltergeist, Matched Pair). **An awarded event reads as its own number** (`need(grades)`):
  read as 1, Back to Back (50) and In the Black (30M) were flagged but never stamped. LedgerTest 409.
- **Every stamp looks different (2026-10-16, the user)**: `StampArt.ITEMS` gives each its own vanilla item (each must
  also be a case in `GuiDraw.stack`, or it draws as paper). **On 26.2 coloured items are collections**: there is no
  `Items.GREEN_DYE` or `YELLOW_WOOL` - wool is `Items.WOOL.pick(DyeColor.X)` (dyes are presumably the same, unverified),
  and the copper family (lightning rods, copper lanterns...) are `WeatheringCopperCollection`s: one of those in the
  `stack` switch makes the whole `new ItemStack(switch ...)` fail to compile. Every other name there was confirmed by
  the user's build (javac reports all missing names at once, and only those three were)), and `StampArt.shape` one of six `Shape`s (circle, octagon,
  rounded square, shield, diamond, scalloped seal) by its place in the chapter, so neighbours never match.
  `inside(shape, x, y, r)` is the one definition; `fill`/`outline` draw it a row of runs at a time, and empty slots
  pencil the same outline dashed. **The cover keeps its face while it swings**: `cover` draws `coverFace` whole,
  squeezed towards the spine with `Draw.scale(sx, 1)` and darkened as it turns, opening and closing alike.
- **Secrets and SkyBlock items (2026-10-17, the user: more secret, funnier stamps; SkyBlock items not vanilla)**:
  109 stamps, 27 secret, each with a `hint` (the sheet's last column, "Hint (secret stamps)") pencilled on its page in
  place of the rule; the pop-up says "SECRET STAMP!". The new ones are events: dates (Friday 13th, 29 Feb, Christmas -
  in `Ledger.timed`, so the clock check covers them), Two-Timer (two sources within 5 min), Natural Talent (a drop on
  kill 1), Round Number, Is That It? (a rare pet after 300+ dry - the gap from `recent`, or the whole count for a first
  pet, never guessed), Cereal Killer (`Ledger.scathaResult`, `LedgerData.cerealRun`), Brain Freeze (`onCorpse`, 2+
  legendaries), Nobody Expects It (`onDiana`), Number of the Beast, Snake Eyes, Hot Potato, Bigger Boat, Ain't Afraid,
  Loose Change (`onCoins`), Health Inspector (`stopAll(true)` with something playing). A desc's "N" / "Nth" is filled
  with the stamp's number by `LedgerView.describe`. **Icons**: `head:<set>:<name>` in `StampArt.ITEMS` draws the
  drop's own SkyBlock head (`HeadSkins.named(set).blob(name)`, `head:scatha:pet` is `PetHeadPopup.SKIN_BASE64`) as a
  PLAYER_HEAD with a resolved profile (`GuiDraw.head`); the ones still vanilla are listed in `docs/TODO.md`.
- Saving the sheet with openpyxl drops Excel's extension-form dropdowns (the Chapter list): re-add it as a plain
  `DataValidation` (`=Chapters!$A$2:$A$10`, B2:B140) after any scripted edit.
- **`/butcher test ledger`** (developers only - it hangs off `test`, so `Dev.allowed()`; `ButcherClient.ledgerTestNode`):
  `popup <id> [grade]` (only the pop-up, `StampPopup.queueTest`, shown even with pop-ups off; `popup burst` queues
  six to see the cap), `give <id> [grade]` / `take <id>`, `info <id>` (what `Ledger.value` counts now, the grade it
  earns, what is in the book), `fill` (every stamp at its top, the Seraph's left out), `unseen` (fresh ink) and
  `wipe confirm` (empty, then the backfill again). Every stamp id is its own literal, so tab completes them. All but
  popup and info change the real Ledger (`ButcherStats.test*`).

## Fail-safes (2026-10-16)

The user asked for fail-safes across the mod; these are the fifteen they approved. `com.butcher.Safety` holds most:
- **Errors**: every animation draw (`drawOwnHud`), watcher tick and watcher chat call runs in `Safety.run(key, ...)`
  (keys are the stats' source keys plus "ledger"). A throw goes to `Safety.failed`: logged, `stopAll(false)`, one chat
  line; `STRIKES` (3) and `Safety.on(key)` is false for the session - its watcher stops and `mayOpen` refuses it.
- **`ButcherClient.stopAll(byPlayer)`**: interrupts the newer cutscene, closes the Scope/Thaw (an unresolved Scope goes
  to `skippedScope` like a skip), stops the guillotine, Fin, Wisp and the stamp pop-up, silences the synth,
  `CutsceneHud.clear()`, nulls every `current*`, clears `waiting`, and holds the pre-kill hide off
  (`hideSuppressedUntil`, 15 s for `/butcher stop`, 5 s otherwise). Each step in its own try.
- **`watchdog()`** every tick: the same thing up for `WATCHDOG_MS` 90 s (per screen - back-to-back Thaws each start
  afresh) is stopped; the pre-kill hide on with nothing playing for `HIDE_STUCK_MS` 45 s is held off 60 s.
- **Hypixel only**: `Safety.onHypixel()` (`getCurrentServer().ip` contains hypixel.net, read with SkyMaxx's bytecode;
  a development run always passes) gates every watcher tick and chat line. Tests and the menu are unaffected.
- **Bursts**: `Safety.allowOpen` - a fourth real open of one source in 10 s is refused. Stats: `ButcherStats.refused`
  drops the same (source, drop) win within 2 s (never for corpses) and anything past 20 counted events a second.
- **Slow frames**: `CutsceneHud.draw` is timed; averaging over 50 ms for a second sets `Safety.slow()` until the next
  cutscene, which `ChopScene.calm` and `GameplayView.corner()` read.
- **Optional hooks**: `DroppedItemHideMixin`, `PickupHideMixin`, `HudHiddenMixin` are `require = 0` and call
  `Safety.hook(i)` (one array read on the hot path); `/butcher log` ends with which ran, what is off, and off-Hypixel.
- **Stats**: `load()` copies `stats.json.YYYY-MM-DD.bak` once a day (newest `BACKUPS` 3 kept) and
  `stats.json.pre-ledger` once. `/butcher ledger recheck` = `ButcherStats.recheck()` (refresh with pre, count added).
- **Ledger**: `Ledger.drop` runs the clock-based awards (`timed`: hot streak, night shift) only when now is past the
  last recent drop; `LedgerData.fix()` caps grades at the stamp's, drops empty entries, zeroes negatives, caps
  `recent`, keeps unknown ids. `StampPopup.CAP` 3 in a row, then `StampCard.paintMore` "+N more stamps".
  LedgerTest has the clock and clean-load checks (233).

## Under maintenance: the Seraph (2026-10-15)

The user is reworking the Seraph's Glyph before players see it. `Maintenance.SERAPH` (pure, no Minecraft) is the
switch. While it is true, `ButcherClient.seraphOn()` is false whatever `seraphAnimation` says, and every Seraph path
goes through it: the watcher's tick and chat lines, the pre-kill hide, `hideDroppedItems`. "Try it" for "seraph" only
plays for `Dev.allowed()`, and `/butcher test slayer enderman ...` (developer-only already) still plays it. The menu
draws a `TapedRow` in place of its card (hazard tape across, "UNDER MAINTENANCE", no switch, does not open), drops
the Sound tab's slider and the Stats card, and the Slayers subtitle says it is on its way. `Draw.rotate` exists for
the tape. To bring it back, set `SERAPH` to false; the README's table and fabric.mod.json's description name it as
coming, and need the same edit.

## Speed per animation (2026-10-15)

`anim/SceneClock` is the scene time of the Chop, Hellion, Seraph and Eruption screens: `t()` is wall time since
opening times the speed (`dianaSpeed`, `blazeSpeed`, `seraphSpeed`, `lavaSpeed`, 50-200, read once when the screen
is made), `jump(target)` is what `openedAt -= target - t` used to be (skip, short bust), `openedAt()` the wall time
for seeds, the gameplay window's fade and `dianaKillUnshown`. Cue lists and `RESOLVE_BY` are on scene time, so at
200% a pending kill must be resolved in half the real time - a drop line later than that plays as its own screen,
as any late one does. Sounds are not pitched or stretched: each cue just fires sooner. Fin and Wisp scale their own
`elapsed()` (`fishingSpeed`, `ghostSpeed`). The Thaw (`vanguardSpeed`) and the Scatha (`scopeDurationMs`, "Full run")
already had their own, through their timelines.

Fin and Wisp sit at `h - LIFT` (82): at `h - 64` they covered the action bar (vanilla draws it at h - 72 .. h - 63).

## Mod Menu

`gui/ModMenuIntegration` (the "modmenu" entrypoint, `suggests` in fabric.mod.json) returns `ButcherMenuScreen::new`
with Mod Menu's screen as `parent`; `onClose` goes back to it. Mod Menu's maven is not used:
`src/main/java/com/terraformersmc/modmenu/api/` holds compile-time copies of `ModMenuApi` and `ConfigScreenFactory`
(signatures read from SkyMaxx's compiled integration: `create(Screen)Screen` erased), and `build.gradle` excludes
`com/terraformersmc/**` from the jar and the sources jar, so the real ones are always Mod Menu's own. Without a world
(the title screen) `Host.canTry()` is false and the Try-it rows say to join one.

## The menu (redesigned 2026-10-15)

`gui/menu/MenuView` is the whole menu - rows, layout, drawing, mouse - with **no Minecraft in it**: it draws through
`gui/menu/Draw` (fill, text, width, item, modIcon, clip, push/translate/scale/pop) and asks a `Host` for the config,
"Try it", reset, Diana's drop names and the version. `gui/ButcherMenuScreen` is a thin Screen that implements `Draw`
over `GuiGraphicsExtractor` (only calls the codebase already uses in game) and forwards the mouse. That split is so the
real menu can be rendered offline: a Java2D `Draw` with the game font's character advances, plus stubs for
`FabricLoader`, Gson and `ButcherClient.LOGGER` (the session's scratchpad `menu/` held one). Keep `MenuView` free of
Minecraft, Fabric (beyond config/stats) and the market classes, or that stops working.

The look: embers rising over a vignette; the title with the mod icon (`TEX_ICON`, the 32 px pixel art at its own size,
registered in `ButcherClient`); a gradient panel with a gold trim and a glint along its top, as tall as its
content (`shownH` eases); tabs with 7x7 pixel icons and a sliding gold underline; each animation a card in its own
colour (`SCATHA`, `VANGUARD`, `DIANA`...) with a vanilla item icon, a subtitle (what it plays on; on the Stats tab the
live dry streak), and its **on/off switch on the card itself** (`HeaderRow.onSwitch`) - so the in-card master toggles
are gone. An open card's rows hang off a bar in its colour. Pill switches (`pill`, eased per id by `approach`),
sliders with a track and knob, `< value >` choices (right-click goes back), `ButtonRow` for "Play" and the red
two-click resets, `HeroRow` for the dry streak. Smooth scrolling (`scrollTarget`), faded list edges, a footer with
hints and the version. The tab and the open cards are static, so the menu reopens where it was left.

## Hellion (the blaze slayer, Inferno Demonlord tier IV) - first version 0.2.54

Built without the user's Demonlord session (they asked for a start before it); detection waits for
`slayer_line` records. The user's decisions: **tier IV only**, wins are drops worth **500k or more**,
**landing on grey (Ashen) is the bust tell**. Use Blender only for what code does badly (rigid-body
physics: the dice tumble) - their call.

- `anim/HellionTimeline` - pure. `Drop` (bust, Duplex I, Scorched Power Crystal, Subzero, Wilson's,
  Paragon Shard, Archfiend Dice, High Class Archfiend Dice, Flame Dye) each with its landing
  attunement, ending, price ID, art (a `HeadSkins.SLAYER` skin or a vanilla icon) and odds. The beats:
  `APPEAR` 350, the shield's `STEPS` (two rounds of Ashen, Spirit, Auric, Crystal, faster), `HANG` 1100
  (rods snap in, the colours flicker), `REVEAL` 1400 - nothing before it depends on the drop
  (`tools/HellionTest`, 132 checks, hashes every draw call for every drop on five seeds). Bust ends at
  2000; wins `ARRIVE` at 2100.
- `render/HellionScene` - draws through ChopScene's box renderer (its camera statics, `box`, ink
  outline), on its Canvas, so `tools/HellionPreview` renders it offline with ChopPreview's
  game-accurate canvas. The blaze is the vanilla `BlazeModel` read with javap: 8-cube head and twelve
  2x2x8 rods in rings of radius 9 / 7 / 5 at model y -2 / 2 / 11, the vanilla per-ring rates (middle
  ring reversed, `f++` per rod as vanilla does), the cosine bob - spin sped up through the cycle by
  integrating `HellionTimeline.spin`, frozen at the reveal. Parts are depth-sorted (no depth buffer),
  outline pass first. The texture is `textures/entity/blaze/blaze.png`, adopted every frame
  (HellionScreen), never bundled; the scratchpad has a copy from the 26.2 client jar for previews.
  Sigils: 5x5 pixel runes per attunement orbiting the head, the current one large. Pillars: ragged
  pixel fire (per-column flicker, holes near the tips) on a `PixelLayer` - one smooth spike read as
  a jet. Bust: rods drop and tumble, head greys and crumbles to ash squares. Win: shards of the
  landing colour, rods blown out, the head implodes into a flash, the drop rises with name / odds /
  price.
- `gui/HellionScreen` - the canvas for the game, sounds (reused Chopping Block/Thaw voices for now),
  the price (bazaar instant sell, else lowest BIN). `ButcherClient.openHellion`; tests via
  `/butcher test slayer blaze <drop>`.
- **0.2.55, from the user's first look:** "way too fast" - the build-up is 4.8 s (`REVEAL`; the user:
  the rods can take 4.5-5 s, this one is not rushed), the shield four rounds from 700 ms, 420 ms a
  step shrinking 9% each (`STEPS` computed), `HANG` at the last step's end (4335). The rods no longer
  close in (the user) - only `spin` grows, 1 + 9x^2, integrated in `HellionScene.ticks`. Clang
  pitch capped at an octave. The "fire pillars" the user meant are **the fight's own**: from their
  screenshot, four terracotta blocks (brown, red, orange, yellow bottom up) with fire on top and a
  "6s 8 Hits" nametag - now 3D boxes of the game's own terracotta and animated fire (soul fire for
  Crystal), adopted at runtime. The seconds count to 0 at the reveal, the hits fall with the clangs.
  `SPIN_TOP` 5 (the user: a smaller top speed).
- `detect/HeadCapture` - the Paragon Shard is in neither the NEU repo nor Hypixel's
  `/v2/resources/skyblock/items` (hunting shards are missing there): any open chest-style menu showing
  a wanted head (the bazaar page is enough) gives up its textures property as a `head_capture`
  record, once, with a chat line. Bundle it into `slayer_heads.json` from the user's records.
- **The rare drop endings (0.2.57).** Per ending, `HellionTimeline.arrive()` is when the drop is shown
  (name, odds, price all follow it).
  - **Dice** (`anim/HellionDice`): Blender 5.2 rigid body (a 2 m cube thrown from the core with spin,
    floor at mod y -8, 60 fps, settles at frame 124), baked to `assets/butcher/hellion_dice.json`:
    per frame x y z + the 3x3 rotation, row-major, columns = the die's own axes. One throw, two tracks:
    the rotation is turned so each die's **6** ends up: the Archfiend Dice's 6 is its back face (-Z),
    the High Class one's its right side (-X) - pips read off the NEU skins. HellionTest checks it.
    Thrown at `DICE_LAUNCH` (reveal + 220), drawn with `ChopScene.boxAxes` (base + hat, ink pass),
    shadow on the floor, sparks and a thud per `bounces()` (one hard hit, then the clack as it tips
    flat), then the camera leans over it (pitch -14 -> -48) and closes in.
  - **High Class**: a gold/red burst as it stops, a score (`Voices.score`, D phrygian dominant) to the
    stop, and the banner **"PRAY TO RNGESUS DROP!"** in #AA00AA (Hypixel's own words) stamped above.
  - **Flame Dye**: time stops at `FREEZE` (reveal + 260) - `phys(t)` is the clock rods, shards, pillar
    fire and embers run on, held until `IGNITE` (+1700); they go cold blue-dark (a tint only
    multiplies, so yellow cannot go grey). Then a ring of fire, a standing pixel flame column, the
    pillars erupt (they stand through this ending, not blown apart), the dye rises; banner and score.
  - Preview: the classpath must include `src/main/resources` for the track, and in Git Bash set
    `MSYS_NO_PATHCONV=1` or the `;`-separated classpath is mangled and the die silently sits still.
- **0.2.58, the user: its own sounds, better fire, pillars further out.**
  - Sound is `audio/HellionSound.schedule(tl)`: the whole soundtrack as timed cues (`Voices.hellion*`),
    computed once. `HellionScreen` plays what is due; a `cut` cue silences first (the reveal, the
    Dye's freeze and ignition). `tools/HellionSoundtrack` renders the same list to WAV, the only way
    to hear it offline. It needs slf4j on the classpath, because SynthEngine logs. E phrygian
    throughout, turning to E major for a win. The rods' whooshes follow the blaze's own sped-up clock,
    a pass every 250 ms of it. The die's clicks come from the baked track: `HellionDice.tumbles`,
    the moments the up face changes near the floor. HellionTest renders everything before the
    reveal for every drop and requires identical samples.
  - Fire: vanilla's `template_fire_floor` model, read from the 26.2 jar. Four planes, 0.8 px either
    side of the middle, turned 22.5 degrees with rescale, 22.4 px tall, so they lean across each
    other (`FIRE_CORNERS`, `HellionScene.fire`). The two crossed planes before it read as a thin tuft.
  - Pillars sit along the camera's resting sideways (yaw 26). Spread along world x, the far one
    climbed the screen once the gap widened. `pillarX` = min(33, 18.5 * w / h) screen units.
- **0.2.60: the fire in game was streaks.** Adopting `textures/block/fire_0.png` by its Identifier
  gives only its first 16x16 frame, not the 16x512 strip. The frame rects then sampled half a texel
  and stretched it. The preview loaded the strip from a file, so it never showed. **An animated block
  texture cannot be adopted by name.** Now `ButcherClient.readGameTexture` reads the PNG through the
  resource manager (a pack's fire too). `HellionArt.fireStrip` normalises any strip to 32 frames of
  16 px, and `greyFire` makes a grey copy. The fire is that grey copy tinted with the shield's colour
  (`HellionScene.fireTint`; Ashen lifted to #8E8E8E), the user's ask. The Dye's eruption uses the real
  orange fire. The clang only adds 10% height (the user: vanilla's size).
- **0.2.61, the jackpots (the user: High Class "a bit more hype", the best drop before the Dye; the
  Dye "extravagant, extraordinary").**
  - **High Class:**
    - **Bullet time.** `diceMs(t)` plays the track at `SLOW_RATE` 0.35 between `SLOW_FROM` 1150 and
      `SLOW_TO` 1560 track ms: from the hard edge landing to the tip flat onto the 6, which
      `HellionDice.bounces()` puts at 1117 and 1533. `diceReal` is the inverse; every dice sound and
      spark goes through it.
    - **The pips.** They light one per `PIP_STEP` 130 after `flatAt()`. `PIPS` are the skin's six 2x2
      pips on the -X face (0,8), read off the NEU skin. `pips()` redraws each pip's own texels
      tinted gold: black stays black, white turns gold.
    - **The jackpot.** At `jackpot()` (sixth pip + 90): shake, shockwave, burst, rays, a coin fountain
      on the PixelLayer (`coin`, 4-cell pixel coins that turn), and `erupt()` pushes the pillars up.
      High Class keeps its pillars standing, as the Dye does. The camera leans in through bullet
      time (x1.5), then pulls back and re-centres at the jackpot so the pillars are in the shot.
    - **Sound.** The swell and score run to bullet time and are cut (`hellionSlow`: tape down, hum,
      two heartbeats). `hellionPip` climbs E major, then `hellionJackpot` plus the fanfare.
  - **Flame Dye:**
    - **The freeze.** It runs to `IGNITE` = reveal + 2400, with `HEARTS` at freeze + 400 / 1120 /
      1840. `ember()` swells per beat, and before each beat a ring of cold squares is pulled in,
      warming (`hellionPull` + `hellionHeart`).
    - **The ignition.** Two rings, a smaller flash than first tried: over the dimmed game, a big
      glow is a wash, on both endings. The rods turn fire-coloured and glow (`lit`).
      `hellionSupernova`: a boom plus an A major choir.
    - **The showcase.** `curtain()` is a flame wall on the PixelLayer along the bottom, 22% high, with
      embers rising over the whole width. `vortex` arms spiral into the dye. The dye is drawn at 0.21
      h in fire `rays`, its name colour cycling through the fire. `arrive` = IGNITE + 1400, total +6500.
  - **Both:** the name stamps letter by letter (`letters()`; HellionScreen draws the first n where
    they sit in the whole name). `hellionGlow` lasts to the end, as the showcases used to go quiet.
- **Detection (0.2.69)** - `detect/HellionWatcher` + `detect/HellionDrops`, written against a friend's real
  tier IV session (2026-10-03: 13 kills, 4 bosses with nametags on 0.2.68; the user does not do slayers).
  What the records settled, against the guesses that were here before:
  - **No "Spawned by" line exists** under the boss (never seen in 4 bosses), and no "NICE! SLAYER BOSS
    SLAIN!". Ownership is the quest's own chat line `SLAYER BOSS! The Inferno Demonlord IV spawned!`: the first
    T4 nametag within 16 blocks from 2 s before to 5 s after it is ours (seen at 4-11 blocks). Hypixel only
    started printing that line mid-day; the afternoon's format had just `SLAYER MINI-BOSS X has spawned!`
    (evening: `SLAYER MINIBOSS! The X spawned!`). Until a spawn line has been seen, a running quest claims
    the first T4 nametag within 12 blocks instead.
  - **The nametag** is `[Lv825]  Inferno Demonlord IV 150M/150M❤`, HP read as Diana's. It **goes and comes
    back twice** - the demon phases at ~2/3 and ~1/3 - as a new entity at or a little below the HP it left at
    (99.9M -> 99.9M, 56.7M -> 48.5M); one first seen within 45 s of ours going, within 24 blocks, between that
    HP + 2% and - 15% of max, is ours again.
  - **The kill**: ours reads `0/150M❤`; ~1.2 s later the drop line, a quarter-second after it `  SLAYER QUEST
    COMPLETE!`. So Hellion opens on the 0 with the drop pending (`HellionScreen(null)`, `resolve(d)` rebuilds
    the timeline on the same seed - invisible before `REVEAL` 4800), the first win among the drop lines
    resolves it, QUEST COMPLETE with none (or 3 s) makes it a bust. A kill whose 0 was missed opens at QUEST
    COMPLETE with the outcome known. `SLAYER QUEST FAILED!` (the boss killed the player) and a cancelled
    quest forget it.
  - **Drop lines**: `VERY RARE DROP! (Scorched Power Crystal) (+236% ✯ Magic Find)` - the item in brackets
    after the tier (RARE / VERY RARE / CRAZY RARE / PRAY TO RNGESUS), a count ("5x ") or symbol ("◆ ") in
    front. Mini-boss drops and Diana's share "RARE DROP!" without the brackets and are not read. Any slayer
    drop not in `Drop` is a bust for the animation (the 500k floor for others is still not built).
  - `tools/HellionDetectTest` (35 checks, the real lines; `-Drecords=` runs a whole file through) and
    `tools/HellionReplay` (plays a records file's slayer lines and nametags back through the real watcher at
    their times - compile it against `build/classes/java/main` plus the game's classpath, because the
    detection package reaches Fabric). On the friend's file: 2 kills opened and resolved as busts, the two
    failed quests opened nothing, every phase return claimed. `DecisionLog` logs through its own slf4j
    logger now (and a `tap`), so it works offline.
  - Menu (0.2.70): a "Slayers" group with Hellion and the Seraph as indented sub-groups (`HeaderRow` takes an
    indent). Hellion: `blazeAnimation`, `blazeHideBelowM` (the pre-kill hide - `HellionWatcher.armed()`, Diana's
    rule on our own boss, feeds `shouldHidePreKill`; default 10M of 150M), `blazeBanner`
    (`HellionScene.showBanner`), `blazePrice`, `blazeVolume` (`ButcherAudio.playHellion` applies it; the Seraph
    has `playSeraph` and `seraph*` of its own). Menu groups no longer hand on to each other (2026-10-15):
    `buildRows` calls each group's builder in turn - chained, a closed group's early return hid the next (Diana hid
    Fin and the slayers; later Fin hid Wisp).
  - **A friend's 131 tier IV kills (0.3.1, 2026-10-06..12, records on 0.2.69)**, replayed through `HellionReplay`:
    - **Bosses that come with the quest** (22 of 131): no "SLAYER BOSS!" line and no mini-bosses, the nametag at full
      health a few blocks off in the same tick as "SLAYER QUEST STARTED!" (auto-slayer). Claimed within `INSTANT_MS`
      1.5 s of the quest starting, at full health, within `INSTANT_REACH` 20; QUEST STARTED re-runs `claim` over the
      bosses already present. A spawn-line claim reaches 24 blocks for a full-health boss (one stood at 21).
    - **The trailing ✯ is not ownership** - it is a Shuriken's Magic Find mark, which any player can put on any mob
      (the user). 0.3.1 briefly trusted it and was corrected. `PHASE_REACH` is 14 (was 24): neighbouring players'
      bosses at 19-26 blocks were taken as phase returns. An entity id that was ours this quest (`ownedIds`) is ours
      again whatever the distance - one boss walked to 25 blocks and back as the same entity.
    - **Quest complete with a win of ours and nothing claimed** plays the win (`killKnown`): a bracketed slayer drop
      line is only ever the player's own.
    - The demon phases are the "float up" (the nametag goes and a new entity comes back); the replay opens no
      cutscene for them. "YOU COCOONED YOUR SLAYER BOSS" (24 kills) changes nothing: the drop still comes ~1.2 s after
      the 0, QUEST COMPLETE ~6.4 s after.
    - Result: 131 kills, 131 openings, no false ones; 6 Archfiend Dice, 1 High Class, 5 Duplex, 7 Power Crystals all
      resolve. `HellionDetectTest` 42 checks, with the three rules played through the real watcher.
    - The Ghosts' line is confirmed in the same file: `RARE DROP! Sorrow (+325 ✯ Magic Find)` (and Plasma).
  - The Paragon Shard's head is the real one (captured by `HeadCapture`, in `slayer_heads.json`); the pixel
    stand-in is gone.

## The Seraph's Glyph (the Enderman slayer, Voidgloom Seraph tier IV) - 0.2.63

Pitched 2026-10-03; the user approved it with three calls: **End Crystals at the sides**, the
**Judgement Core gets the extraordinary ending and the Upgrade the jackpot**, and a **black beam is the
bust**. They do not do slayers: **a friend will play it with records on**, and detection waits on those
records. The same rules as Hellion apply: tier IV only, a 500k floor, an honest tell, the Hellion
architecture.

- **Facts, from hypixelskyblock.minecraft.wiki.** The official wiki closed in July 2026; the fandom wiki
  is behind a paywall.
  - The fight: 210M HP; the Malevolent Hitshield is 100 hits at spawn, 140M and 70M. The Yang Glyph:
    the boss "holds a beacon for 2 seconds then throws it". Broken Heart Radiation: 12 beams in 4
    groups of 3, 20 blocks long.
  - The tier IV drops and odds are in `SeraphTimeline.Drop`. Prices on 2026-10-03:
    - Byzantium Dye 775M (1 in 500,000).
    - Judgement Core 190M.
    - End Stone Idol 43.5M (1 in 7,085, rarer than the Upgrade).
    - Exceedingly Rare Ender Artifact Upgrade 9.4M.
    - Enchant Rune I 4.9M (Coflnet tag `RUNE_ENCHANT`, NEU id `ENCHANT_RUNE;1`).
    - Paragon Shard ~2.1M, Summoning Eye 1.55M, Etherwarp Merger 650k.
    - **Under the floor that day:** the four with their own small endings - Chalice 299k, Espresso
      150k, Sinful Dice 350k, Void Conqueror skin 75k (NEU `PET_SKIN_ENDERMAN_SLAYER`; plain
      `PET_SKIN_ENDERMAN` is the Spooky skin). They play only if their price rises.
  - The heads are bundled in `slayer_heads.json` from the NEU repo.
  - The original approval gave the Judgement Core the "extraordinary" ending; it is now the Eye, and the
    Dye has the Dragon (0.2.66, below).
- **Vanilla, read with javap from the 26.2 jar.**
  - **EndermanModel:** head 8-cube at pivot (0,-13,0); the "hat" is the jaw, an 8-cube shrunk 0.5 at
    tex (0,16). Body 8x12x4 at (0,-14,0), tex (32,16). Arms 2x30x2 at (±5,-12,0) and legs at
    (±2,-5,0), tex (56,0), the left ones mirrored. Texture 64x32.
  - **Creepy:** the head lifts 5 px and the jaw stays.
  - **Carrying (the user asked for exact vanilla):** both arms xRot -0.5, zRot ±0.05. They stay
    there through the throw (0.2.65, the user: vanilla endermen never lift their arms). The glyph
    shakes in the carry, the arms push 0.3 forward at the release, and it flies from the carry spot.
    **CarriedBlockLayer**: translate (0, 0.6875, -0.75), rotX 20, rotY 45, translate (0.25, 0.1875,
    0.25), scale (-0.5, -0.5, 0.5), rotY 90. That puts a half-size block in front of it at hand
    height, not over its head as first pitched.
  - **Beacon:** models/block/beacon.json, a glass shell, an obsidian base and the core.
  - **EndCrystalModel:** the outer glass, the inner at 0.875 and the cube at 0.766. Each is turned 60
    degrees about (1,0,1) and spins age*3 degrees, on a 12x4x12 base. The texture is a 128x64 sheet
    for a 64x32 layout (`scale2`).
  - **Textures:** all adopted at runtime and none animated (`SeraphScene.ADOPT`). TEX_WHITE is
    generated.
- **`render/SeraphScene`.**
  - **Model to world:** world = (x, FEET + 24 - y, -z), a half turn about X. Rotations conjugate by
    diag(1,-1,-1): keep the X angle, negate Y and Z (`toWorld`).
  - **The rig:** `rig()` builds the parts, and `drawRig` sorts every part and the carried beacon
    together, ink first.
  - **The camera** looks from the -X side of +Z (yaw 20), so a head facing it is turned
    `ry(-32)`, not +.
  - **Beams** are camera-facing ribbons tiled and scrolling (`beam()`; a beacon's tiles 16 px per 0.4
    block).
  - **The tell** is a solid stub of beam plus a stained wash. The wash alone was lost under the
    beacon's cyan core.
  - **The Void Conqueror skin** keeps its pattern in the hat layer, so the spread draws base and hat
    texels.
- **The Judgement was replaced (0.2.66).** The user found the kneel / sky-crack / dissolve ending goofy and
  said the Core and the Dye must not share one: **every animation's dye gets its own, grandest ending**.
  Both were rebuilt exactly as pitched, previewed frame by frame:
  - **Judgement Core, "The Eye Opens"** (`Ending.EYE`, `E_*`): the beam lands purple and the stopped clock
    (`phys`) freezes everything but the core. The shell cracks (lines on the layer) and bursts into shards;
    the Core's real NEU skin (white cube, red eye) grows out of the beacon's core. The lids and pupil are
    texel overlays on its front face: open in three steps, `eyeLook` -1 / +1, then the stare and the pulse
    ring. `pullInto` spirals the Seraph and both crystals into `pupil()`. The camera closes in on it.
  - **Byzantium Dye, "The Dragon's Tribute"** (`Ending.DRAGON`, `D_*`): the beam deepens to Byzantium, time
    stops and the world greys texel by texel (`worldGild`: pillars, crystals, Seraph, the beacon too; the
    beam greys with it), then the Ender Dragon flies the Blender path, lands behind the beacon, lowers its
    head and breathes. The breath pools into paint (`pool()` on its own `GROUND` PixelLayer under
    everything, `under`/`flushUnder`) and the gild paints the pillars and crystals Byzantium. The beacon
    bursts, the dye rises, the dragon hops onto the line of sight behind it with one wingbeat and rears.
  - **The dragon is the game's own** (`render/DragonRig`): `EnderDragonModel` built from its own layer, an
    `EnderDragonRenderState` filled per frame (flap clock, the 64-tick flight history its neck and tail bend
    from, sitting), its own `setupAnim`, EnderDragonRenderer's root transform, then `ModelPart.visit` for
    every posed polygon with its UVs. The same class runs in the preview, which has the client jar on its
    classpath (see below), so they cannot differ. Things the previews settled:
    - **In the rig, positive `extraPitch` is nose UP** (negative bows the head to the ground) and positive
      head `xRot` is down. The first rear used -16 and bowed instead.
    - **Sitting puts the head at 0.9 blocks**, below the beacon's top, where the beacon hid it. It stands
      (head at 2.2) and lowers the head 0.3 rad.
    - **Head-on it reads as a column** and its wings go edge-on: it lands at yaw 225 (three-quarters; 200
      would be square to the camera, whose yaw is 20) and rears at 225.
    - **The dragon is drawn whole, before the pillars while it is behind them** (`dragonBehind`), after
      them once in front - there is no depth buffer, and it was covering pillars it was behind.
    - The breath starts 12% past the jaws: the pixel layer is drawn over the scene and hid the head.
  - **0.2.67, the user: "kills frames just like the scatha did".** Counted with a counting Canvas offline
    (every draw call per frame): a normal Seraph frame is ~1,300; the Dragon reached 37,700 once the world
    greyed and 80,000 by the end. Two causes:
    - **The gild drew two quads per texel** (`ChopSpecials.gildFace`). It now works out every texel's colour
      first and draws runs of the same colour, along a row and down the rows, as one quad pair - the same
      pixels, so Diana's endings are unchanged. A wholly greyed face is 2 quads instead of 128. This only
      merges if neighbouring texels share a colour: the Byzantium paint used three random shades per texel
      and now uses one (the relief still shows the texture). **Keep gild colours flat across a face.**
    - **The dragon was ~375 polygons x 9 quads** (8 ink stamps + the face). Back faces are culled now
      (screen-space area sign, `CULL_SIGN` -1), except the wings (`q[21]`): their membranes are single
      sheets and culling took them off. The ink is 4 straight stamps; the preview showed no difference.
      ~1,000 quads now.
    Result: mean 2,500, worst 5,100. Any new ending: count it the same way before handing it over.
  - **0.2.68, the user: "you can kind of see through him".** Not alpha (the vanilla and the user's
    Defrosted pack textures are both strictly 0/255; vanilla draws it cutout) and not the GUI's ordering
    (javap: 26.2's `GuiRenderState` keeps overlapping elements in submission order -
    `navigateToAboveHighestElementWithIntersectingBounds` - and only sorts by texture within a node).
    It was the painter's sort: ~375 polygons ordered by their centres get a long, overlapping model wrong,
    so far parts drew over near ones. **The dragon is now rasterised on the CPU with a depth buffer**
    (`render/DragonRaster`, pure Java: exact row spans, cutout texels, the eyes layer on the body's own
    surface, the outline as the covered area grown by the ink width) and drawn as one image
    (`Canvas.image`; `render/RasterImage` in game - a DynamicTexture that only grows - and Java2D in the
    preview). Textures come from `ButcherClient.readGameTexture`, so a resource pack's dragon is used.
    Scale: 2 image px a GUI px (`rasterScale`, the screen caps the GUI scale at 2) and at most
    `BUDGET` 600k px; ~2-4 ms a frame offline, plus the upload in game. Back faces are still culled
    (wings excepted) only to save work. Any other big model: rasterise it the same way rather than sorting.
  - **The showcase is pinned to the centre (0.2.68)**, every ending: from `arrive() - 900` the camera eases
    its focus (`fx`, `fy`, `fz`, `sx0`, `sy0`) onto `showTarget()`, where the drop comes to rest, and puts
    the block - banner above, name, odds and price below - in the middle of the screen. The sway turns
    about the focus, so it no longer carries the drop off. Earlier fixes only stopped the sideways pan.
  - **The flight is Blender's** (`assets/butcher/seraph_dragon.json`, `anim/SeraphDragon`): a Bezier path
    with auto handles, an empty on Follow Path, offset keyed 0 / .34 @22 / .86 @70 / 1 @92, sampled per
    tick, Blender (x, -z, y) = ours. The control points are in the JSON's note; re-bake there rather than
    editing frames. Heading comes from the path's direction, settling to `LAND_YAW` over the last second.
    The circle has to stay within ~8 blocks of the beacon and ~4.5 high, or the dragon leaves the shot.
    The Blender scene is removed after each bake.
  - **Running Minecraft's own classes offline:** the merged-deobf client jar plus joml, fastutil, guava,
    gson, datafixerupper, slf4j, log4j-api, logging, brigadier, commons-lang3, authlib and **every netty
    jar** (NoClassDefFoundError otherwise), on JDK 25. `SeraphTest` keeps DragonRig out (the scene skips
    the dragon when `SeraphScene.dragon` is null) and needs only slf4j.
- **`audio/SeraphSound` + `Voices.seraph*`.** C# minor, the vwoop, a clink per hit pitched by colour
  (`SERAPH_GLASS`), cut at the reveal. The Eye: freeze, crack, shatter, the core's hum, the lids, two
  glance swishes, a heartbeat boom, the implode. The Dragon: freeze, a roar growing through the stopped
  world, a wingbeat on every downstroke of its real flap clock (`SeraphScene.flapAt`), a roar on the close
  pass, the landing, the breath, glugs as the paint pools, the burst and the rearing roar. The burst's
  shatter is 0.22 s after the splash: together they peaked at 1.2. `tools/SeraphSoundtrack` renders it to
  WAV.
- **`tools/SeraphTest` (195 checks).** Frame and audio honesty for every drop on five seeds, the cycle,
  the throw landing at the reveal, every ending's order, the eye's looks and lids, the dragon's track
  (whole flight, landing spot and heading), the paint and the grey.
- **`tools/SeraphPreview`.** Run as Hellion's preview, with `-Dvanilla` (the game's textures) and
  `-Dskins` (`<NEU id>_skin.png`).
- **`detect/SlayerRecorder` (0.2.63).** Records `slayer_mob`: every armour stand within 40 blocks naming
  a Voidgloom Seraph or an Inferno Demonlord, or "Spawned by" - when seen, on a name change (at most
  once a second) and when gone. With `slayer_line` (widened to loot share, glyph, hitshield, quest
  complete...), that is what the friend's records must contain.
- **Detection (0.2.78)** - `detect/SeraphWatcher` + `detect/SeraphDrops`, from a friend's records (2026-10-05,
  0.2.69; the recorder is andreflooo, not the user - three of their own tier IV kills among other players' bosses):
  - **Ownership is the boss's own "Spawned by: <player>" nametag**, an armour stand two entity ids after the boss's,
    seen the same tick (the Demonlord never showed one). No "SLAYER BOSS!" line exists for the Seraph. The player's
    name is `player.getGameProfile().name()`.
  - **The nametag**: `☠ Voidgloom Seraph IV 208.6M❤ ✯` (a `✯` or `ᛤ` marker either side), `... 100 Hits` through
    the hitshield (no HP - the last is kept), `0❤` dead. One entity the whole fight.
  - **The kill**: 0❤, the drop line ~1.2 s later (`VERY RARE DROP! (Null Atom) (+216% ✯ Magic Find)` - Hellion's
    format, `HellionDrops.item`), QUEST COMPLETE a third of a second after. Opens pending on the 0, resolved by the
    first win, a bust at QUEST COMPLETE or after 3 s; the nametag going at 5% or less, close, also counts (the
    records missed one 0); QUEST COMPLETE with no kill seen opens it known.
  - **The 500k floor** for the four small endings (`SeraphDrops.floored`): their prices are asked for when our boss
    appears; at the kill one worth less, or of unknown worth, plays as a bust (`ButcherClient.seraphFloor`).
  - `seraphHideBelowM` (default 15M of 210M) feeds the pre-kill hide.
  - `tools/SeraphDetectTest` (13 checks; `-Drecords= -Dself=` replays a file through the real watcher - compile
    against `build/classes/java/main`): the friend's file opens and resolves exactly their three kills.

## Eruption (lava fishing, the Crimson Isle's rare sea creatures) - 0.2.74

The user lava fishes themself. Their calls (2026-10-05): every rare creature's kill plays, **loot-shared
kills too**; the guaranteed drops (Jawbus's Magma Lord Fragment, the Scuttler's Scorched Crab Stick) are not
the reveal - a bust shows them under the cooled vent with their price; **Plhlegblast plays only for the
Carmine Dye**; the Carmine Dye gets the grandest ending ("Kuudra Rises"). Big wins must feel special.

- `anim/EruptionTimeline` - pure. **The tell is heat**: the creature sinks (`SINK`), a crust closes (`CRUST`),
  a vent of magma blocks rises (`VENT`), its heat breathes black-red-orange-yellow-white (`STEPS`,
  `stepLevel`, the same every run), flickers in the hang, and lands on the drop's `Heat` at `REVEAL` 4800.
  Black is the bust. `Drop` holds heat, ending, creature (null = any: the dye), NEU id, art (a
  `HeadSkins.LAVA` head or a vanilla icon) and odds; heat never falls as value rises (EruptionTest).
  Endings: PLAIN (Chain), PINCER, FLASH, FORGE (Brimstone), VIAL, SHELL, END_TIMES (Burnt Texts), KUUDRA.
  Kuudra stops `phys` from `K_FREEZE` to `K_RESUME`.
- **`backOut` returns exactly 0 before it starts.** The polynomial left a float crumb there and every beat is
  tested with `> 0`: the Vial was drawn in Jawbus's chest from the first frame. EruptionTest found it.
- `render/EruptionScene` - one routine for game and preview. Three kinds of drawing, chosen by cost:
  - **The lava floor is composed on the CPU into one image** (`floor`, a texel a world unit, only the part on
    screen) and drawn with one affine blit (`Canvas.imageAffine`): a flat plane under an orthographic camera
    is a parallelogram. As ~400 gilded tiles it was up to 13,000 quads a frame. Each texel is what the box +
    gild drew: lava lit 0.9415, or the gild's flat colour with the lava over it as relief. `floorGild` holds
    the crust, the heat ring, the green (Vial: green lava, veins through the crust), the terracotta and cracks
    (End Times) and the carmine (Kuudra) with the frozen grey ahead of it. The vein and crack shapes are fixed,
    so they are one precomputed mask (`mask`). `inLake` is a per-tile table.
  - **Plain cubes go to the GPU** (`Poly.gpu`, `drawGpu`): the vent's magma blocks (cracks as a second quad),
    its chunks, Kuudra's cube (the vanilla magma cube rig, inner cube and internal slice faces dropped) and
    every tentacle segment (one box of the cube's dark rock - the eight-slice model was 8,000 quads).
    Sorted by block, outline stamps first. Raster for these cost 8-19 ms a frame. **The game's GUI drops a quad
    wound the other way** (the Java2D preview draws it), so `drawGpu` turns such a quad round as `ChopScene.emit`
    does - 0.2.74 shipped without that and in game the vent's tops and the tentacles' faces were missing.
  - **Everything else is rasterised** with a depth buffer (`ModelRaster`): the creatures (vanilla rigs posed
    by vanilla code, `LavaRigs`), the drop (a head, or its icon extruded a pixel deep as the game shows a
    dropped item - `item()`), the crab, Jawbus. The raster image is drawn over the GPU cubes, so keep the
    cubes behind (tentacles stand in front of Kuudra). Faces turned away are culled - box faces by their
    normal, rig polygons by the game's own polygon normal (`LavaRigs` writes it, q[22..24]); heads are not
    culled, as vanilla draws them without. **Under the lava**: polygons wholly below y 0 in the lake are
    dropped, upright ones cut at the surface (`clipToSurface`), and `ModelRaster.cutBelow` refuses any pixel
    below it - the camera looks down, so that is exactly what an occluder did, without rasterising one.
  - Effects are squares on two PixelLayers: back (embers, bubbles, fountain, bolts, rings, rays) before the
    3D layer, front (steam, sparks, arcs, book fire, carmine rain) after it.
  - Measured offline at a 960x540 GUI, raster x2: mean 2-3 ms CPU a frame, Kuudra's worst ~9 ms; GUI calls
    mean ~700, worst ~1,900. `tools/EruptionTest` counts the calls.
- Camera: yaw 22 with a sway, pitch -24, a push in through the build-up. From the drop's rise the focus eases
  onto where it rests (`dropRest`) with the words in the middle; Kuudra pulls back and comes in again.
  `GOLEM_X` / `KUUDRA_X` / `TENTACLES` place things by screen x (screen x ~ 0.93 x + 0.37 z at yaw 22).
- **Textures** are all the game's, read through the resource manager (a pack's are used): lava and magma as
  strips normalised to 16 px frames (`LavaArt.strip` - an animated block texture cannot be adopted by name),
  the crack mask and grey lava made from them, the golem, elder guardian, squid, skeleton horse, its saddle
  and the magma cube (64 x 64). Ragnarok's and the Scuttler's skins and every head drop are SkyBlock heads
  from the NEU repo (`assets/butcher/lava_heads.json`); the raster needs pixels, so `HeadSkins.pixels`
  fetches the skin PNG from the blob's own textures.minecraft.net URL on a daemon thread and caches it in
  `config/butcher/skin_cache/`. The Brimstone Handle is a stick in vanilla (a custom model in Hypixel's pack).
- `audio/EruptionSound` + `Voices.eruption*` - D phrygian, D major for a win: the rumble, the sink, the crust
  creaking, the vent, a bubble per breath pitched by its heat, the build, the flicker; cut at the reveal.
  Then the boom (`eruptionBoom` takes an amplitude - with the End Times' bolt it peaked at 1.36), the quench,
  per-ending cues (thunder, zaps, clacks, anvil, Geiger clicks, Jawbus's groan, the crab's legs, book fire,
  ground cracking, Kuudra's freeze, choir, swell, tentacles, eyes and roar), the arrive and the glow.
  `tools/EruptionSoundtrack` renders a WAV.
- **Detection** (`detect/LavaDrops` pure, `detect/LavaWatcher`) - from the user's logs, **no nametag seen yet**:
  - Drops: `RARE DROP! Enchanted Book (Flash I) (+157 ✯ Magic Find)` - no brackets round the item. The
    Carmine Dye's line is unknown: any drop or WOW! line naming it.
  - Ours: the nametag first seen within 20 blocks, 2 s before to 6 s after its catch line - Hypixel's own or
    **Feesh's rewrite** ("A Thunder has spawned!"; Feesh hides the original, which is why our chat listeners
    run in an early phase, 0.2.72). **Nothing else makes a creature ours (0.2.82).** 0.2.74-0.2.80 also took any
    creature that armed the hide, any a loot share line named, and any CHARM: in the user's lava party
    (2026-10-05, andreflooo, NightCry, slurpee6, Antoniaaa...) nearly every bust that opened was a party member's
    Jawbus, Thunder or Scuttler, and their one charm was on someone else's Thunder.
  - Someone else's creature plays **only when its own win comes to us**: its death, a loot share line within 2 s,
    and a rare drop that creature can give (either order). A charm on ours is our kill; on anyone else's it is
    their death.
  - Dead: HP reads 0, or the nametag goes at 5% or less, close, no teleport. Also ours going short of 5% with a
    loot share for its kind within 2 s either side (a party member landed the last hit - the user's Thunder at
    7%, 23:38), or right after a win of its own. Pending until a win within 3 s, else a bust; a win up to 1.5 s
    before the death is the outcome already.
  - The pre-kill hide: ours only, at `lavaHidePct` of its health.
  - **Jawbus floats up immune (0.2.83, the user 2026-10-11)**: the same entity, but its nametag shows no health for
    ~4 s (2026-10-05 records: both stands "gone" at 18M, the same ids back at 20M). The scan only matched names with
    "/" and "❤", so it looked like a vanish and opened Eruption. A followed entity now stays followed whatever its
    name says, and health is only read off a name that shows it. HellionWatcher does the same for the Demonlord,
    reported to do it too - **its immune nametag has never been recorded; check the first records** (0.2.83's
    `SlayerRecorder` follows ids through name changes, 4/s, with `dy`, height above the player).
  - **Kills go to the last hitter.** Loot share names the player who killed it, even for the user's own catch.
  - `tools/LavaDetectTest` (32 checks). The user's whole 2026-10-05 session replayed through the watcher (records
    sample nametags once a second, so it misses some 0s the game saw) opens exactly their own 3 Ragnaroks (one
    Burnt Texts) and 7 Thunders, no one else's.
- Menu "Lava Fishing - Eruption" (`lava*` config); `/butcher test lava <drop> [creature]`.
- **0.2.75-0.2.78, from the user's screenshots:** GPU quads wound the wrong way vanish in game (fixed in
  `drawGpu`); the far shore/cliff never looked right (0.2.76-0.2.77) and is gone - **the floor image covers the whole
  view** (to +-700/900 units) and fades to near-black with distance (`fog`, from 70 units back), so nothing of the
  game shows over it. **The vent** is a cone: half-sunk blackstone boulders (`SLOT_STONE`, GPU-only), a ring of
  blackstone and magma, a magma shoulder, the crater (the game's animated lava in the heat's colour); `lavaFlows`
  run bright rivulets down its near faces. Cracks are the heat's own colour on a darkened block (mixing towards white
  read as peach). **Recolour gild** (`RECOLOUR` alpha 0xFE): the floor maps the lava texel's brightness onto the
  colour - green and carmine lava stay lava (multiplying the orange texture went olive). **The Vial is "Meltdown"**:
  `V_PULSES` (six, faster), `critical()` (green screen edges, shake), `V_FREE` the detonation (Jawbus gone, debris,
  `mushroom()` cloud, the radiation ring), the vial blown up into the cloud and down to rest (`V_DESCEND`).
- **0.2.79, the user:** the lava streams "looked ass" - removed; the crater opening is 7 units, not 12. **The sides**
  (their pick of four): `pillars()` - clusters of basalt columns (GPU, `SLOT_BASALT`/`_TOP`, tinted dark, 1-3
  blocks, placed by screen x) - and `smoke()` - grey plumes off the field by them and ash across the screen, thicker
  to the reveal (identical every run), cleared by a win. (Not picked: magma cubes hopping, side vents.)
- `tools/EruptionTest` (167 checks; needs the client classpath like the preview): frame honesty per creature
  (the raster image hashed pixel by pixel, the floor image too) on four seeds, sound honesty, heat order,
  Kuudra's clock, every ending's beats in order, the draw-call budget. `tools/EruptionPreview` as the Seraph's.
- **Next:** the user's lava session with records on - check the nametag format and the catch lines, and write
  `LavaDetectTest` / a replay against them as Hellion's were.

## Fin (Fishing Festival, 0.2.42)

The user's brief (2026-10-02): very fast, very simple, no dim, no kill animation - an announcement
when a Great White Shark drops a **Megalodon** pet or a **Great White Shark Tooth**. `detect/SharkDrops`
reads the drop line (pure; any "... DROP!" prefix; the pet's rarity is in the text, as SkyHanni's own
example "PET DROP! EPIC Slug (+1300☘)" shows; colour codes stripped). **No real line had been seen**
when it was written - `DropRecorder` keeps every fishing line as `fishing_line`, so check the first.
`render/FinPopup` is not a cutscene (HUD, chat and sounds untouched), drawn by `drawOwnHud` after the
HUD: a pixel fin rises out of a two-row water strip (`RISE`), the pixel cleaver drops straight down
on it (`CHOP` 420), the fin splits and sinks, squares splash, the drop pops out (`POP` 520) - the
Megalodon's NEU skin (`HeadSkins.FISHING`, `fishing_heads.json`, same skin both rarities) on a 3D
head, the tooth as the game's own ghast tear icon - with name (rarity colour) and price (bazaar
instant sell for the tooth, Coflnet `PET_MEGALODON` lowest BIN for the pet, any rarity), gone at
`END` 2500. Shapes are character maps (`FIN`, `CLEAVER`) drawn as 2-GUI-pixel squares with a dark
outline. Sounds: `onFinRise` (blup), `onFinChop` (chop + splash), `onFinReveal` (3-note chime, 4 for a
legendary), on `fishingVolume`. Config `fishingAnnounce`, menu "Fishing Festival - Fin",
`/butcher test shark megalodon|legendary|tooth`.

0.2.51, from the user's test: the fin waits half a second longer before the chop (`FALL` 800,
`CHOP` 920, `POP` 1020, `END` 3000). The Megalodon looked "weirdly put" because `PlayerHeadMesh`
had its sides swapped (above, 0.2.53). 0.2.51 tried the game's item renderer instead - right, but
flat and pixelated scaled up - so 0.2.53 is the fixed 3D head again, three-quarters on (yaw 35 with
a 9 degree sway, no spin: its skin wraps round the cube, eyes on the sides).

**0.2.59, the user: George's price for the pets, and a bust.**
- The pets show what George pays, fixed in `FinPopup` (`GEORGE_LEGENDARY` 2.5M, `GEORGE_EPIC` 500k),
  labelled "at George". The tooth keeps the bazaar's instant sell.
- **The bust** is `detect/SharkWatcher`, written with no real line or nametag captured. It follows
  SkyMaxx's `SeaCreatureDetector` rules (MIT, read with javap) and reuses DianaWatcher's nametag HP
  reading.
  - **Ours:** a "Great White Shark" nametag first seen within 16 blocks, from 2 s before to 4 s after
    a catch line. The catch line is matched loosely: any game line naming the shark that is not a
    drop, a charm or a loot share. Hypixel's should be "Hide no longer, a Great White Shark has
    tracked your scent and thirsts for your blood!".
  - **Dead:** the HP reads 0, or the nametag goes at half health or less, within 32 blocks, after 6+
    ticks, with no teleport.
  - **Bust:** no drop line from 1.5 s before the death to 1.5 s after it.
  - Nametags are recorded as `fishing_mob` (seen / died). **Check the first records** for the real
    nametag and catch line.
- The animation is `FinPopup.showBust()`: the fin ducks at `DUCK`, `missedCleaver` goes on into the
  water (clipped at the surface), splash and bubbles, gone at `BUST_END` 1650. It never interrupts a
  drop that is showing. Sounds are `onFinMiss`: `Voices.miss` plus `finPlunge`.
  `/butcher test shark bust`.

## Wisp (Dwarven Mines Ghosts, 0.2.90) - works in game (the user, 2026-10-12)

The user's brief (2026-10-12): like Fin, only for the drop - **Sorrow** or **Plasma** from a Ghost in the Mist.
`detect/GhostDrops` (pure): any "... DROP!" line whose item is Sorrow or Plasma (by name, alone - not Plasmaflux),
no player chat. **No real line has been seen**: the wiki gives Sorrow 0.12% "Rare Loot" and Plasma 0.1% "Legendary
Loot" but not the message; both items are RARE (§9) in NEU. DropRecorder keeps `ghost_line` - check the first.
Ghosts have no nametag or health bar, so there is no kill to detect and no bust. `render/GhostPopup`, drawn by
`drawOwnHud` with Fin: mist strip (`MIST`), the Ghost - a charged creeper, front on, pale and flickering, the charge
as diagonal blue stripes crawling in a ring two squares out (`GHOST`, `dist`) - rises (`RISE`-`RISEN`), bursts into
wisps (`BURST` 1180, every square drifting up), the drop pops (`POP`) with name and bazaar instant sell. The icon is
Hypixel's pack's (`hypixel_skyblock:textures/item/uncategorized/<id>.png`) when present, else the ghast tear for
Sorrow and a pixel orb (`ORB`) for Plasma (paper in vanilla). Sounds `Voices.ghostWail/ghostBurst`, a chime at 220 Hz,
on `ghostVolume`. Config `ghostAnnounce`, `ghostPrice`; `/butcher test ghost sorrow|plasma`; menu group after Fin.

## The Thaw (Vanguard corpses)

The second drop source, and the template for any after it. Vanguard only, permanently -
Lapis, Umber and Tungsten corpses are sampled for reference and never animated.

**Detection** is `detect/vanguard/`. Hypixel prints the whole haul in one chat message
before anything is drawn, so none of the Scatha's tracker/claim-window machinery applies.
The message format was settled from free Lapis corpses: the header regex matches any
`<TYPE> CORPSE LOOT!`, and non-Vanguard messages are copied into `DecisionLog` verbatim.
Five parser bugs were found that way and none by reasoning - sample a new source cheaply
before trusting a parser for it. The real message, and what each line needs:

```
▬▬▬ x64                      opens with a rule too - ignored, we start on the header
  VANGUARD CORPSE LOOT! (2)  a chat-dedup mod may append " (2)" to ANY line - stripped first
  +1 bonus drop!             preamble: everything above REWARDS is dropped, not collected
                             blank padding - skipped, never fatal
  REWARDS                    a heading: letters, none lower case - skipped
    Skeleton Key
     ☠ Flawless Onyx Gemstone x2   symbol prefix - VanguardLoot.key strips it
▬▬▬ x64                      closes it (as do a 20-line cap and a 40-tick timeout)
```

Skyblocker's `Corpse Profit` line never reaches our hook - it writes straight into the chat
component - so it cannot break the parse and cannot be hidden either.

**The table** (`VanguardLoot`) is SkyOcean's weights cross-checked against the wiki (sums to
3516; 160/3516 is the wiki's 4.55%), names and colours from the NEU item repo. `tier`
(drop weight, drives the animation) and `colour` (SkyBlock rarity, display only) are
deliberately independent - a Flawless Gemstone is dark purple AND the commonest drop.
`Tier` order is load-bearing: `CorpseHaul` sorts on `ordinal()`. `SPECIAL` is the
Frostbitten Dye alone, glowing pink, never gold.

**The timing** is `anim/ThawTimeline`, pure Java, computed once from the haul. The screen
only asks it what is happening now. Five endings - CRASH, REVEAL, EXPLODE from the best
tier, plus LOCKET (a legendary headline that is the Shattered Locket) and SPECIAL (the Dye)
- and **one pace for every run** until the last strike. Losses used to play at 0.45x so
they were over quickly, but that told the player the outcome from the first strike; the user
asked for every run to strike at the win pace. Even the last hit lands at the same moment
whatever the drop. Only the ending differs: loss ~10.4s, legendary ~13.3s, Locket ~18s, Dye
~20s. ThawTest fails if anything before the landing depends on the ending.

**The flare never lies.** An earlier version let ordinary strikes flare gold sometimes; the
user found that a signal which can lie kills the tension instead of building it. Every flare
is now its drop's true colour, and the tension lives in the **last** strike, which **cycles**
blue -> violet -> gold three times round from an opening blue and back to blue (`CYCLE`),
each colour fading into the next, the steps shortening from 380ms to 145ms, before landing on
the truth. No white anywhere - the user took it out. The cycle is identical for every drop,
so nothing before the landing gives it away; ending on blue makes a legendary's landing the
biggest jump, blue to gold. (It replaced a climb that stopped at the truth - a common stopped after one rung, so the
climb's length was the answer.) Under it: one rising riser (`Voices.thawBuildUp`, cut dead at
the landing), a tick per colour a semitone up each time, then the true colour's own note.
`stepStart` and `climbStep` must agree exactly - ThawTest checks it.

**The two special endings** run a second clock, `ThawScreen.vt()`: from the last impact it
runs at quarter speed for `SLOW_MOTION`, then the Locket's resumes and the Dye's **freezes**
until the paint bursts. Only physical things (chips, pick, jolt, shake) use it; sounds and the
ending's own beats stay on real time. Both endings stamp the name out letter by letter, then
"1 in ~N corpses" (from the table and the player's average rolls) and "Your Nth X" from
`ButcherStats.corpseItems`, under black letterbox bars (7% - more covers the tray).
The Dye's grey is a fill drawn *over* the tray; the pink crack is drawn after it, so it is the
only colour on screen. The Locket's price comes from `market/LivePrice` - Coflnet's
`/api/item/price/SHATTERED_PENDANT/bin`, requested when the loot message is read, read
(never waited for) when the counter starts, NPC 5M if it has not arrived. Off in the menu.

**The flare's size is settled - do not enlarge it.** 0.1.34 made it much bigger (a screen
wash, `ThawArt.aura()`, saturated colours, 42% into the ice); the user found it too big and too
colourful and it was reverted to the gem glow at 1.35/0.8 screen heights with 18% in the ice.
`ThawArt.aura()` stays: the special endings use it for their own glows.

**Art and geometry** (`render/art/ThawArt`, `render/model/ThawMeshes`) were tuned by
rendering frames offline with `tools/ThawPreview`, which draws the real meshes through the
real projection - the IconGen trick. Things the previews settled that reasoning got wrong:

- **The pick's striking prong is on +Y.** On -Y the tip still landed on the target (the
  pivot is solved from it) but the head arrived back-first.
- **A player head's front faces the camera at yaw 0**, despite `PlayerHeadMesh`'s note
  about -Z. Rest items at yaw 32 for a three-quarter view; 212 shows the back.
- **The ice is a chunk cut by planes** (`ThawMeshes.PLANES`): six faces plus five broad
  chips. Corners are where three planes meet; each surviving plane is a facet. A facet is a
  pentagon or a triangle, but only parallelograms can be drawn, so each is its bounding
  parallelogram with the real outline cut into the texture's alpha (`ThawArt.cut`). The cut
  is soft and decided per edge (`IceFacet.across`): every edge is kept a pixel beyond its
  line so neighbours meet, except a far facet's edge on the outline, trimmed a pixel inside.
  **Far facets are painted mirrored** (`ThawArt.flip`): a face seen from behind can only be
  drawn with its texture flipped (`ModelProjection`'s winding fix), invisible on the old
  rectangles but on a chipped outline it put every far facet's shape in the wrong place - a
  grey fringe outside the block and gaps between the facets seen through it. The user saw
  that as "the faces don't connect", twice; the first fix (trimming) treated the symptom.
  A plane's
  `d` is a true distance along its direction - when it was divided by the normal's length
  too, every chip cut far deeper than designed and the chunk came out as a cut gem.
- **Ice texture density is 3 px/unit** (sheet 256), about twice the old box's. Slanted facet
  edges at the old density were staircases with slivers sticking out. Cracks scale by
  `ThawArt.K` but stay one texel wide, so they do not web over the Vanguard.
- **The Vanguard is a posed 3D figure painted as a shadow** (`ThawMeshes.vanguard`,
  `ThawArt.corpse`): player-shaped boxes on the standard 64x64 skin layout, plus pauldrons.
  He was painted in his real armour for 0.2.0 and the user sent it back - a dark shape behind
  frost is the look; the shape is what makes it a Vanguard. Fully opaque - see-through read
  as a ghost. Upward faces are lifted a little, and the visor T glints faintly, edge to edge
  and down to the chin as on the real helmet. A rim on every face drew him as a wireframe.
  He needs the far facets drawn behind him: without that pale ground he is dark on dark and
  disappears. Six `POSES`,
  picked per corpse and maybe mirrored. The block is only 16 deep, so arms raised straight
  forward poke out of the front - poses reach up and sideways instead. ThawTest fails if
  any pose, either way round, leaves the ice.
- **Draw order is three passes, not one sort**: far facets, the Vanguard, near facets
  (`ThawMeshes.iceFacing`). The global sort goes by face centres, and pauldrons next to big
  facets came out in front of the frost. For a convex shape with something inside, the three
  passes are exactly right.

**Items** (`render/VanguardItems`): 21 are player heads drawn as real 3D heads with their
SkyBlock skins, from base64 blobs bundled in `assets/butcher/vanguard_heads.json` (NEU
repo). The other 5 are vanilla items through `g.item`, which captures the pose, so scaling
works. A skin lookup returns Steve until it downloads; `path.startsWith("skins/")` is how a
real skin is told apart, and an unready one draws as a tinted crystal instead.

**The font renderer treats alpha below 4 as fully opaque.** Any text that fades must stop
drawing near zero, or it flashes back on at the end of the fade.

**Sound.** The three flares sit low - G2, C3, E3, a C major triad inside the A minor
pentatonic everything else rings on - and differ by overtones, not pitch. Each strike's
steel ring is two octaves above the flare before it, so the swell resolves into the hit.
A high flare cut off by a short, unrelated hit is what made the first version sound
wrong. Strikes are deliberately long - crunch, heavy thud, a ringing steel tone with an
inharmonic partial at 2.76x, then loose ice crumbling off for a third of a second.

**The screen freezing (0.2.49)** - the user's pick for filling the Thaw's empty sides.
`render/ThawFrost`: pure (a grid, a seed, a fill call), drawn by `ThawScreen.drawFrost` under the tray
and captions. Frost ferns walk in from each corner over a textured rime, and a ragged band creeps
along the top and sides (the bottom middle stays clear for the tray); every cell has an arrival
0..1. Growth: 0.12 through the intro, then a step per strike landed (strikes land at the same moments
whatever the haul, so it tells nothing early); the newest frost catches that strike's flare. At the
landing: CRASH melts (columns drip), REVEAL cracks (only the outer frost breaks), the rest shatter
(a white flash, then pieces pop inward and fall - outward would take edge frost straight off
screen); the Locket's on `vt`, the Dye's when the paint bursts. Checked offline with a stand-in
frame (the ThawPreview tool only draws the ice).

**Draw a screen of pixel cells as ONE image (0.2.51).** The frost and the crystal veins first sent
every cell to the GUI as its own `fill` (twice, with the outline): thousands of render states a frame
at full growth, more with each cut - the user's frame rate fell with every slice of the Scatha.
`render/PixelLayer` implements both classes' `Fill`: cells are blended into an ARGB buffer one texel
per grid cell (~213 x 120), copied into a `NativeImage` (ABGR), `DynamicTexture.upload()`ed and
blitted once with the pose scaled by the cell size - nearest sampling keeps the squares crisp. One
static layer per purpose. Do not `close()` the old texture when the size changes:
`TextureManager.register` closes it already (`safeClose`), and twice is not safe. Any future effect
made of many grid squares should draw through a PixelLayer, not through `fill`.

**Profit window** (`gui/ProfitScreen`, `market/`). Opens when a Thaw ends, finished or
skipped (`ThawScreen.announce` -> `ButcherClient.onThawFinished`); the next queued corpse waits
for it (`profitShowing`), and chat is held until it closes (see above). Every drop priced, most valuable
first, minus the Skeleton Key:

- `VanguardLoot.listing` - each drop's item ID (NEU repo) and market. Checked live 2026-09-24:
  Dye, Locket, Wisp are auction-only; the crystals and Glacite Powder have no market and count
  as 0 (the user's call); everything else, the key included, is on the bazaar.
- `market/Bazaar` - Hypixel's public `/v2/skyblock/bazaar`, no key, one request for all
  products. Drops at `quick_status.sellPrice` (instant sell), the key at `buyPrice` (instant
  buy). `market/LivePrice` - Coflnet lowest BIN for the auction items.
- `market/CorpseValue` - requested when the loot message is read, recomputed every frame, so
  a late price fills in; a line still fetching shows "...". Both fetchers are async, cached,
  never waited on. Gated by `livePrices` (and `vanguardProfit` for the window).

**Glacite Powder** is in the loot table with weight 0 - not a roll; every corpse gives some -
so it is never rolled and sorts first. Drawn as a flat sprite (`VanguardItems.sprite`,
`ThawArt.glacitePowder`) in the manner of the pre-1.14 light blue dye the user wanted.
**Mojang's own sprite is deliberately not in the jar**; it lives in the user's
`config/butcher/textures/glacite_powder.png`, copied from their own 1.12.2 install, and
`ArtOverrides` prefers it. Do not bundle it.

**The menu is on the vanilla font again.** `render/ButcherFont` and `assets/butcher/font/`
are unused for now and kept on purpose: the user is choosing a replacement, and swapping
it in is `ui.json` plus the `.ttf`.

Offline suites, both runnable without the game: `tools/VgTest.java` (parser, 52 checks
including all three real messages) and `tools/ThawTest.java` (timeline, flare and climb, 15).

## Layout

```
ButcherClient       entrypoint: events, commands, texture registration
gui/ScopeMinigameScreen   the cleaver cutscene - the Scatha's
gui/ThawScreen            the Thaw - the Vanguard corpse's
gui/ButcherMenuScreen     /butcher - a thin Screen over gui/menu/MenuView (the whole menu, no Minecraft)
anim/ButcherHudRenderer   the older guillotine cutscene, drawn as a HUD overlay
anim/AnimationManager     that cutscene's phase clock
anim/PetHeadPopup         the pet reveal = vanilla's totem animation with a skinned player head
anim/PetDropFirework      the fireworks that go up behind a pet reveal
detect/                   tracker + watcher + decision log (above); DropDetector is the result type
config/PatternConfig      JSON at config/butcher.json; /butcher reload re-reads it
mixin/                    hand, sound, screen ownership, HUD hiding - see above
render/TextureBank        registry of runtime-generated textures, drawn lazily, dropped on reload
render/MeshRenderer       queues models, one global depth sort, screen-space outline
render/ScathaSkins        captures the real worm skin off a live mob
render/model/             Mesh + BoxMesh + the projection; the meshes themselves
render/scene/             where the worm and the cleaver sit each frame
render/art/               the Graphics2D painters (above)
render/ArtOverrides       writes generated art out, and never overwrites a player's edit
render/model/GltfLoader   Blockbench .gltf/.glb -> Mesh
audio/                    the synthesiser and the cutscene's cues (above)
anim/SwingCurve           Hermite spline + judder + spring, for the cleaver swing
anim/ThawTimeline         the Thaw's whole schedule, from the haul; pure, tested offline
detect/vanguard/          corpse loot parser, the loot table, the parsed haul
market/LivePrice          an item's lowest BIN from Coflnet, async, cached, never waited on
detect/DropRecorder       every real drop event, verbatim, to config/butcher-drops.jsonl
anim/ChopTimeline         Diana's cutscene, per verdict, creature and seed; pure, tested offline
anim/StingEnding ...      the later endings (Sting, Hunt, Fleece, Heart, Midas, Glint), one class each
render/ChopSpecials       draws those five, with ChopScene's machinery
render/ChopScene          draws a frame onto a Canvas - shared by the screen and the preview
gui/ChopScreen            the Chopping Block - Diana's cutscene; canvas, sounds, price
render/art/ChopArt        the block sheet, flesh, the moon's faces, the Chimera's three heads, glow, rays
render/HeadSkins          SkyBlock heads' real skins from bundled NEU blobs: VANGUARD, DIANA, CREATURES
render/VanguardItems      the 26 corpse drops as real heads and vanilla items
render/art/ThawArt        ice (8 crack stages x 3 patterns), the Vanguard's skin, pick, rays, chips
render/model/ThawMeshes   the ice chunk, the posed Vanguard inside it, the pickaxe
anim/EruptionTimeline     lava fishing's schedule; render/EruptionScene draws it, gui/EruptionScreen plays it
detect/LavaWatcher        lava creatures' kills; detect/LavaDrops reads their lines
```

`PatternConfig.animationMode` is still named `SCOPE` for the cleaver mode. Renaming
it would break every existing config file — the menu label was changed instead.

The menu rebuilds its row list from config every frame, so what is drawn and what is
clickable can never drift apart. Sliders carry a stable id so a drag survives the
rebuild.
