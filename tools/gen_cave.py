"""
The cavern backdrop for the cleaver cutscene - a full repaint of cave_wide.png.

The old art was flat bands with hard edges, which is what made it read as clip
art. The thing that actually fixes that is not "more effects" - a first pass with
full-width haze and rim light everywhere turned the cave into beige soup, because
light that covers everything stops being light and becomes a wash.

So everything atmospheric here is multiplied by a LIGHT MAP: a blurred field that
is bright near the five torches and black elsewhere. Rim light on a crest, haze
above a layer, and warmth on the stone all exist only where a torch could
actually put them, and the rest of the cave stays properly dark. That contrast -
small pools of warm light in a lot of darkness - is what makes the crystals and
the Scatha read against it.

The five torch positions are FIXED: ScopeMinigameScreen animates flicker, embers
and glow at those exact fractions, so moving them would leave the effects hanging
in mid-air.
"""
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import math, os, os, random

# Repo-relative, so this runs from a clone anywhere.
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                     "src", "main", "resources", "assets", "scathabutcher", "textures", "gui")


OUT = os.path.join(ASSETS, "cave")
W, H = 1920, 1080
rng = random.Random(20260917)

# Must match TORCH_FRAC in ScopeMinigameScreen.
TORCHES = [(0.085, 0.569), (0.288, 0.532), (0.500, 0.584), (0.712, 0.529), (0.915, 0.574)]

VOID_TOP, VOID_MID, VOID_BOT = (7, 5, 12), (18, 13, 27), (3, 2, 5)
FAR = ((46, 35, 64), (24, 18, 34))
MID = ((30, 22, 42), (14, 10, 20))
NEAR = ((19, 14, 27), (7, 5, 10))
FLOOR = ((11, 8, 15), (3, 2, 4))
CEIL = (9, 7, 14)

WARM = (255, 184, 96)
WARM_DEEP = (255, 132, 44)


def catmull(pts, samples=22):
    ext = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(len(ext) - 3):
        p0, p1, p2, p3 = ext[i], ext[i + 1], ext[i + 2], ext[i + 3]
        for j in range(samples):
            t = j / float(samples)
            t2, t3 = t * t, t * t * t
            out.append((
                0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t
                       + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                       + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3),
                0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t
                       + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                       + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)))
    out.append(pts[-1])
    return out


def vgrad(size, top, bot, gamma=1.0):
    w, h = size
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = (y / float(max(1, h - 1))) ** gamma
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    return img


def radial(cx, cy, radius, strength, blur=None):
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=strength)
    return m.filter(ImageFilter.GaussianBlur(blur if blur else radius * 0.45))


def tinted(mask, colour):
    out = Image.new("RGBA", (W, H), colour + (0,))
    out.putalpha(mask)
    return out


def edge_y(edge, x):
    for (x0, y0), (x1, y1) in zip(edge, edge[1:]):
        if x0 <= x <= x1 and x1 != x0:
            return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    return edge[-1][1]


# ---- the light map: where a torch could plausibly put light ------------------
LIGHT = Image.new("L", (W, H), 0)
for fx, fy in TORCHES:
    LIGHT = ImageChops.lighter(LIGHT, radial(fx * W, fy * H, 150, 255, blur=72))
# Steep falloff. A linear one still left ~40% light halfway between two torches,
# which was enough to keep a rim lit right across the frame - and a rim that never
# stops reads as glowing piping, not as stone catching a flame.
LIGHT = LIGHT.point(lambda v: int(((v / 255.0) ** 2.4) * 255))


def lit(mask, amount):
    """Scale a mask by the light map, so it only exists near a flame."""
    return ImageChops.multiply(mask, LIGHT).point(lambda v: int(v * amount))


# ==================================================================== build

img = vgrad((W, H), VOID_TOP, VOID_MID, gamma=1.5).convert("RGBA")
lower = vgrad((W, H), VOID_MID, VOID_BOT, gamma=0.9).convert("RGBA")
lower.putalpha(Image.linear_gradient("L").resize((W, H)))
img.alpha_composite(lower)

# ---- ceiling and stalactites -------------------------------------------------
ceil_knots = [(-40, 34), (260, 92), (620, 56), (980, 112), (1340, 64), (1700, 104), (1960, 52)]
ceil_edge = catmull(ceil_knots)
ceiling = Image.new("RGBA", (W, H), (0, 0, 0, 0))
cd = ImageDraw.Draw(ceiling)
cd.polygon([(-40, -80)] + ceil_edge + [(1960, -80)], fill=CEIL + (255,))

x = -20
while x < W + 40:
    w = rng.uniform(14, 48)
    ln = rng.uniform(36, 200) * (0.55 + 0.45 * abs(math.sin(x * 0.0031)))
    by = edge_y(ceil_edge, min(W - 1, max(0, x)))
    bend = rng.uniform(-0.16, 0.16) * ln
    cd.polygon([(x, by - 6), (x + w, by - 6),
                (x + w * 0.62 + bend, by + ln * 0.55),
                (x + w * 0.5 + bend * 1.6, by + ln)], fill=CEIL + (255,))
    x += w + rng.uniform(12, 50)
ceiling.putalpha(ceiling.getchannel("A").filter(ImageFilter.GaussianBlur(0.7)))
img.alpha_composite(ceiling)


def wall(knots, top_col, bot_col, rim, haze):
    """One rock layer: a smooth silhouette filled with a vertical ramp, plus a
    crest rim and a haze band - both only where the light map allows."""
    edge = catmull(knots)
    top_y = int(min(y for _, y in edge))

    if haze:
        band = Image.new("L", (W, H), 0)
        ImageDraw.Draw(band).polygon(
            [(x, y - 70) for x, y in edge] + [(W, H), (0, H)], fill=255)
        band = band.filter(ImageFilter.GaussianBlur(44))
        img.alpha_composite(tinted(lit(band, haze), (188, 142, 196)))

    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon(edge + [(W, H), (0, H)], fill=255)
    body = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    body.paste(vgrad((W, H - top_y), top_col, bot_col, gamma=0.8), (0, top_y))
    body.putalpha(mask)

    # Faint angular facets. A layer painted as one smooth ramp reads as a
    # gradient swatch; a few broad planes across it read as rock.
    facets = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(facets)
    fx = -120
    while fx < W + 120:
        fw = rng.uniform(200, 460)
        fy = edge_y(edge, min(W - 1, max(0, fx + fw * 0.5))) + rng.uniform(20, 90)
        drop = rng.uniform(140, 380)
        shade = rng.choice([(0, 0, 0, 30), (0, 0, 0, 20), (0, 0, 0, 40)])
        fd.polygon([(fx, fy), (fx + fw * 0.62, fy - rng.uniform(10, 46)),
                    (fx + fw, fy + rng.uniform(20, 70)), (fx + fw * 0.45, fy + drop)], fill=shade)
        fx += fw * rng.uniform(0.55, 0.85)
    facets.putalpha(facets.getchannel("A").filter(ImageFilter.GaussianBlur(30)))
    # only where there is light to reveal them - a facet on unlit stone is just a
    # grey rectangle floating in the dark
    facets.putalpha(ImageChops.multiply(
        ImageChops.multiply(facets.getchannel("A"), mask), LIGHT))
    body.alpha_composite(facets)

    img.alpha_composite(body)

    if rim:
        r = Image.new("L", (W, H), 0)
        ImageDraw.Draw(r).polygon(edge + [(x, y + 7) for x, y in reversed(edge)], fill=255)
        r = r.filter(ImageFilter.GaussianBlur(2.5))
        img.alpha_composite(tinted(lit(r, rim), WARM))

        # warmth washing down the face just under the crest
        f = Image.new("L", (W, H), 0)
        ImageDraw.Draw(f).polygon(edge + [(x, y + 92) for x, y in reversed(edge)], fill=255)
        f = f.filter(ImageFilter.GaussianBlur(34))
        img.alpha_composite(tinted(lit(f, rim * 0.20), WARM_DEEP))
    return edge


far = wall([(-40, 486), (300, 430), (700, 472), (1080, 418), (1460, 468), (1960, 432)],
           *[FAR[0], FAR[1]], rim=0.42, haze=0.13)
mid = wall([(-40, 690), (340, 640), (760, 682), (1200, 634), (1600, 676), (1960, 642)],
           *[MID[0], MID[1]], rim=0.30, haze=0.09)
near = wall([(-40, 852), (420, 812), (900, 848), (1380, 806), (1960, 844)],
            *[NEAR[0], NEAR[1]], rim=0.18, haze=0.05)
floor = wall([(-40, 992), (560, 968), (1220, 986), (1960, 962)],
             *[FLOOR[0], FLOOR[1]], rim=0.10, haze=0.0)

# ---- gem seams in the walls --------------------------------------------------
GEM_COLS = [(226, 96, 96), (128, 214, 116), (112, 196, 232), (232, 210, 96),
            (228, 126, 196), (172, 116, 228), (232, 150, 70)]
for i in range(7):
    gx = (0.07 + 0.135 * i + rng.uniform(-0.03, 0.03)) * W
    if not (0 < gx < W):
        continue
    band = far if i % 3 else mid
    gy = edge_y(band, gx) + rng.uniform(34, 118)
    col = GEM_COLS[rng.randrange(len(GEM_COLS))]
    s = rng.uniform(0.7, 1.15)

    img.alpha_composite(tinted(radial(gx, gy - 16 * s, 58 * s, 54), col))
    gd = ImageDraw.Draw(img)
    dark = tuple(int(c * 0.45) for c in col)
    bright = tuple(min(255, int(c * 1.35)) for c in col)
    # a dark socket first, so the seam looks set into the rock rather than stuck on
    gd.polygon([(gx - 26 * s, gy + 4), (gx - 16 * s, gy - 6 * s), (gx + 18 * s, gy - 5 * s),
                (gx + 27 * s, gy + 5)], fill=(9, 7, 13, 235))
    for off, mul, wid, lean in ((-10, 0.44, 3.8, -8), (1, 1.0, 5.4, 0)):
        ox, hh, ww = off * s, rng.uniform(40, 54) * mul * s, wid * s
        tipx = gx + ox + lean * s
        gd.polygon([(gx + ox - ww, gy), (tipx, gy - hh), (gx + ox + ww, gy)], fill=dark + (255,))
        gd.polygon([(gx + ox - ww * 0.55, gy), (tipx, gy - hh * 0.94), (tipx, gy - hh * 0.05)],
                   fill=bright + (255,))
        gd.polygon([(tipx, gy - hh * 0.94), (gx + ox + ww * 0.75, gy), (tipx, gy - hh * 0.05)],
                   fill=col + (255,))

# ---- torches ----------------------------------------------------------------
for fx, fy in TORCHES:
    cx, cy = fx * W, fy * H
    img.alpha_composite(tinted(radial(cx, cy, 210, 44), WARM_DEEP))
    img.alpha_composite(tinted(radial(cx, cy, 104, 74), WARM))
    img.alpha_composite(tinted(radial(cx, cy, 42, 132), (255, 228, 176)))

    d = ImageDraw.Draw(img)
    d.polygon([(cx - 8, cy + 26), (cx + 8, cy + 26), (cx + 6, cy + 88), (cx - 6, cy + 88)],
              fill=(38, 26, 20, 255))
    d.polygon([(cx - 8, cy + 26), (cx + 8, cy + 26), (cx + 7, cy + 42), (cx - 7, cy + 42)],
              fill=(58, 40, 28, 255))
    d.polygon([(cx, cy - 40), (cx + 17, cy + 4), (cx, cy + 28), (cx - 17, cy + 4)],
              fill=WARM_DEEP + (255,))
    d.polygon([(cx, cy - 26), (cx + 11, cy + 4), (cx, cy + 21), (cx - 11, cy + 4)],
              fill=(255, 206, 104, 255))
    d.polygon([(cx, cy - 11), (cx + 5, cy + 4), (cx, cy + 15), (cx - 5, cy + 4)],
              fill=(255, 246, 208, 255))

# ---- air: motes and vignette -------------------------------------------------
motes = Image.new("RGBA", (W, H), (0, 0, 0, 0))
md = ImageDraw.Draw(motes)
for _ in range(130):
    mx, my = rng.uniform(0, W), rng.uniform(H * 0.24, H * 0.94)
    d0 = min(math.hypot(mx - t[0] * W, my - t[1] * H) for t in TORCHES)
    a = int(max(0, 104 - d0 * 0.26))
    if a < 10:
        continue
    r = rng.uniform(1.0, 2.6)
    md.ellipse([mx - r, my - r, mx + r, my + r], fill=(255, 212, 160, a))
motes.putalpha(motes.getchannel("A").filter(ImageFilter.GaussianBlur(1.0)))
img.alpha_composite(motes)

vig = Image.new("RGBA", (W, H), (0, 0, 0, 255))
vm = Image.new("L", (W, H), 0)
ImageDraw.Draw(vm).ellipse([-W * 0.18, -H * 0.30, W * 1.18, H * 1.30], fill=255)
vig.putalpha(vm.filter(ImageFilter.GaussianBlur(210)).point(lambda v: int((255 - v) * 0.78)))
img.alpha_composite(vig)

img.convert("RGB").save(os.path.join(OUT, "cave_wide.png"))
print("cave_wide.png repainted, %dx%d" % (W, H))
