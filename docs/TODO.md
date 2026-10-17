# TODO

## Ledger: the last vanilla stand-ins (2026-10-17)

Stamps whose drop has a bundled SkyBlock skin now draw that item (`StampArt.ITEMS`, `head:set:name`, built by
`GuiDraw.head` as a skinned PLAYER_HEAD). These still use a vanilla item because no skin for them is in the jar:

| Stamp | SkyBlock item | What is needed |
| --- | --- | --- |
| `scatha_worm_food` | Worm Membrane | NEU skin (`WORM_MEMBRANE`), if it is a head; otherwise Hypixel's pack icon |
| `vg_master_key` | Skeleton Key | NEU `SKELETON_KEY` - check whether it is a head |
| `hellion_cold_feet` | Subzero Inverter | NEU `SUBZERO_INVERTER` |
| `hellion_blueprints` | Wilson's Engineering Plans | NEU `WILSON_ENGINEERING_PLANS` (a paper/map item in game?) |
| `lava_forged` | Brimstone Handle | a stick in vanilla, a custom model in Hypixel's pack (`hypixel_skyblock:textures/item/...`) |
| `fin_tooth_fairy`, `fin_dentist` | Great White Shark Tooth | NEU `GREAT_WHITE_SHARK_TOOTH` |
| `wisp_sorrowful`, `wisp_plasma`, `wisp_high_voltage` | Sorrow, Plasma | Hypixel pack icons, as GhostPopup draws them |
| `diana_wool` | Shimmering Wool | check NEU - yellow wool is the stand-in |

For heads: add the NEU blob to the matching `assets/butcher/*_heads.json` and point the stamp at `head:<set>:<name>`.
For pack icons: `Draw.item` would need a third kind ("pack:<path>", drawn only when the resource exists, else the
vanilla fallback) - GhostPopup already has that lookup.

Also to check in game: the head icons in the book and the pop-up show their skins (Steve for a moment on the very
first open is expected while the game downloads them).
