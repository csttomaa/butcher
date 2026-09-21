"""
The gemstone outcrops that frame the cleaver cutscene.

Four textures per side, all drawn at the SAME rect so they line up:
  gem_*.png        crystals, greyscale, recoloured by the blit tint
  rock_*.png       the rock, in real stone colours, untinted, drawn OVER the gems
  rock_glow_*.png  the light the crystals throw onto the stone, tinted, drawn last
  (sparkle.png is shared by both sides and positioned by the Java side)

The crystals are greyscale because the colour comes from the blit tint, which
multiplies into the whole texture: a white highlight becomes the colour at full
strength and a mid-grey becomes a darker shade of it. The rock must NOT be
tinted - that is the whole reason it is a separate sprite, so stone stays stone
whatever colour the run rolls.

Two things that were making this look hand-cut rather than drawn:

1. Every facet was a flat fill with a hard black outline. Facets are now vertical
   gradients and the outline is a dark shade of the crystal itself, so the shapes
   read as faceted glass instead of coloured paper.

2. The dark undersides of the crystals poked out through the dips in the rock's
   top edge. The gem layer is now masked off below the ridge line, so a crystal
   simply does not exist below the stone and there is nothing to peek through.
"""
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import math, os, os

# Repo-relative, so this runs from a clone anywhere.
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                     "src", "main", "resources", "assets", "scathabutcher", "textures", "gui")


OUT = os.path.join(ASSETS, "cave")
os.makedirs(OUT, exist_ok=True)

W, H = 512, 850
TOP = 150          # headroom above the shelf so the tallest crystal isn't cropped

# rock: cave_wide.png's floor sampled at (20,15,26)-(30,22,40), stepped up a
# little so a foreground shelf reads as nearer than the painted background
ROCK_LIP = (66, 53, 83)
ROCK_FACE = (38, 30, 49)
ROCK_DEEP = (17, 13, 22)
ROCK_EDGE = (11, 8, 15)

_KNOTS = [(0, 392), (74, 404), (148, 438), (214, 470), (286, 452), (340, 500)]
_TAIL_TO = (500, 700)


def _catmull(pts, samples=14):
    ext = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(len(ext) - 3):
        p0, p1, p2, p3 = ext[i], ext[i + 1], ext[i + 2], ext[i + 3]
        for j in range(samples):
            t = j / float(samples)
            t2, t3 = t * t, t * t * t
            out.append((
                0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t
                       + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                       + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3),
                0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t
                       + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                       + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)))
    out.append(pts[-1])
    return out


def _tail(start, end, samples=30):
    """Quarter ellipse: steep at the top, flattening into the floor at the bottom."""
    (xa, ya), (xb, yb) = start, end
    return [(xa + (xb - xa) * (1 - math.cos(th)), ya + (yb - ya) * math.sin(th))
            for th in [(math.pi / 2) * (i / float(samples)) for i in range(1, samples + 1)]]


RIDGE = [(x, y + TOP) for x, y in _catmull(_KNOTS) + _tail(_KNOTS[-1], _TAIL_TO)]
RIDGE_END_X = RIDGE[-1][0]


def ridge_y(x):
    for (x0, y0), (x1, y1) in zip(RIDGE, RIDGE[1:]):
        if x0 <= x <= x1:
            t = (x - x0) / float(x1 - x0) if x1 != x0 else 0.0
            return y0 + (y1 - y0) * t
    return RIDGE[-1][1] if x > RIDGE_END_X else RIDGE[0][1]


# ------------------------------------------------------------------ helpers

def rot(pt, piv, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y = pt[0] - piv[0], pt[1] - piv[1]
    return (piv[0] + x * c - y * s, piv[1] + x * s + y * c)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def grad_fill(img, poly, top_v, bot_v):
    """Fill a polygon with a vertical gradient - the single biggest difference
    between 'flat shape' and 'lit surface'."""
    ys = [p[1] for p in poly]
    y0, y1 = int(min(ys)) - 1, int(max(ys)) + 1
    if y1 <= y0:
        return
    band = Image.new("L", (W, y1 - y0))
    bd = ImageDraw.Draw(band)
    for y in range(y1 - y0):
        t = y / float(max(1, y1 - y0 - 1))
        bd.line([(0, y), (W, y)], fill=int(top_v + (bot_v - top_v) * t))
    mask = Image.new("L", (W, y1 - y0), 0)
    ImageDraw.Draw(mask).polygon([(p[0], p[1] - y0) for p in poly], fill=255)
    layer = Image.merge("RGBA", (band, band, band, mask))
    img.alpha_composite(layer, (0, y0))


def soft(img, radius):
    """Blur the alpha only, keeping colour flat - blurring RGBA drags black in
    from the transparent margin and turns a bright glow into grey smear."""
    a = img.getchannel("A").filter(ImageFilter.GaussianBlur(radius))
    out = Image.new("RGBA", img.size, (255, 255, 255, 0))
    out.putalpha(a)
    return out


# ------------------------------------------------------------------ crystals

# (x, height, width, lean)
SHARDS = [
    (196, 600, 100, -9),
    (272, 470, 82, 16),
    (140, 380, 70, -26),
    (330, 330, 66, 31),
    (232, 250, 56, 4),
    (118, 206, 48, -34),
]
BURY = 96          # deep enough that no crystal bottom can reach the rock's dips
SPARKS = []        # filled in while drawing, exported as fractions for the Java side


def shard(img, cx, base, w, h, lean):
    piv = (cx, base)
    half = w / 2.0
    shoulder = base - h * 0.70

    def P(p):
        return rot(p, piv, lean)

    bl, br = P((cx - half, base)), P((cx + half, base))
    sl, sr = P((cx - half, shoulder)), P((cx + half, shoulder))
    tip = P((cx, base - h))
    rb, rs = P((cx + half * 0.12, base)), P((cx + half * 0.12, shoulder))

    # a dark shade of the crystal, not black - a black keyline reads as clip art
    grad_fill(img, [bl, sl, tip, sr, br], 92, 34)

    # lit face, shadow face, and the narrow outer facet that gives it thickness
    grad_fill(img, [lerp(bl, br, 0.04), lerp(sl, sr, 0.04), tip, rs, rb], 255, 176)
    grad_fill(img, [rb, rs, tip, lerp(sr, sl, 0.05), lerp(br, bl, 0.05)], 196, 118)
    grad_fill(img, [lerp(br, bl, 0.05), lerp(sr, sl, 0.05), tip], 148, 82)

    # rim light down the leading edge, and a hot sliver up the ridge
    grad_fill(img, [lerp(bl, br, 0.02), lerp(sl, sr, 0.02),
                    lerp(tip, sl, 0.12), lerp(sl, sr, 0.10), lerp(bl, br, 0.10)], 255, 214)
    grad_fill(img, [lerp(rb, rs, 0.10), lerp(rs, tip, 0.55),
                    lerp(lerp(rs, tip, 0.55), sr, 0.10), lerp(rb, rs, 0.10)], 255, 232)

    # the tip catches the most light of anything on the crystal
    grad_fill(img, [lerp(tip, sl, 0.22), tip, lerp(tip, sr, 0.22),
                    lerp(tip, rb, 0.30)], 255, 206)

    # where a sparkle will sit: partway up the ridge, on the bright side
    SPARKS.append(lerp(rs, tip, 0.45))


def build_gems():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for cx, h, w, lean in sorted(SHARDS, key=lambda s: -s[1]):
        shard(img, cx, ridge_y(cx) + BURY, w, h, lean)

    # Cut the gem layer off below the stone. Everything under the ridge is the
    # rock's business, so nothing can show through a dip in its top edge.
    cut = Image.new("L", (W, H), 255)
    ImageDraw.Draw(cut).polygon(
        [(x, y + 12) for x, y in RIDGE] + [(RIDGE_END_X, H), (W, H), (W, 0), (0, 0), (0, H)], fill=255)
    below = Image.new("L", (W, H), 0)
    ImageDraw.Draw(below).polygon(
        [(x, y + 12) for x, y in RIDGE] + [(RIDGE_END_X, H), (0, H)], fill=255)
    img.putalpha(ImageChops.subtract(img.getchannel("A"), below))

    # soft coloured bloom behind the cluster, so the gems light their own air
    bloom = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bloom.putalpha(img.getchannel("A").point(lambda v: v * 70 // 255))
    bloom = soft(bloom, 34)
    bloom = Image.merge("RGBA", (
        Image.new("L", (W, H), 255), Image.new("L", (W, H), 255),
        Image.new("L", (W, H), 255), bloom.getchannel("A")))
    return Image.alpha_composite(bloom, img)


# ------------------------------------------------------------------ rock

def build_rock():
    body = RIDGE + [(RIDGE_END_X, H), (0, H)]
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon(body, fill=255)

    # vertical gradient down the whole slab: lit at the lip, lost in the dark below
    grad = Image.new("RGB", (W, H))
    gd = ImageDraw.Draw(grad)
    top_y = min(y for _, y in RIDGE)
    for y in range(H):
        t = min(1.0, max(0.0, (y - top_y) / float(H - top_y)))
        t = t ** 0.75
        gd.line([(0, y), (W, y)], fill=tuple(
            int(ROCK_FACE[i] + (ROCK_DEEP[i] - ROCK_FACE[i]) * t) for i in range(3)))
    img = grad.convert("RGBA")
    img.putalpha(mask)

    # angular facets, masked to the body so none float past the tapering end
    facets = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(facets)

    def F(pts):
        return [(x, y + TOP) for x, y in pts]
    fd.polygon(F([(0, 470), (118, 498), (182, 566), (116, 700), (0, 700)]), fill=(0, 0, 0, 46))
    fd.polygon(F([(206, 544), (300, 580), (340, 668), (232, 700)]), fill=(0, 0, 0, 52))
    fd.polygon(F([(300, 616), (392, 660), (430, 700), (286, 700)]), fill=(0, 0, 0, 64))
    fd.polygon(F([(36, 648), (146, 672), (192, 700), (18, 700)]), fill=(0, 0, 0, 58))
    facets.putalpha(ImageChops.multiply(facets.getchannel("A"), mask))
    img = Image.alpha_composite(img, facets)

    # lit lip along the top edge, thinning toward the tail so the taper doesn't
    # finish on a bright stripe
    lip = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    n = len(RIDGE)
    top_edge = [(x, y + 1) for x, y in RIDGE]
    under = [(x, y + 1 + 16 * (1.0 - (i / float(n - 1)) ** 0.7)) for i, (x, y) in enumerate(RIDGE)]
    ImageDraw.Draw(lip).polygon(top_edge + list(reversed(under)), fill=ROCK_LIP + (255,))
    lip = Image.alpha_composite(lip, Image.new("RGBA", (W, H), (0, 0, 0, 0)))
    lip.putalpha(lip.getchannel("A").filter(ImageFilter.GaussianBlur(1.2)))
    img = Image.alpha_composite(img, lip)

    # crisp dark edge, so the silhouette stays readable against the cave
    edge = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(edge).line(RIDGE, fill=ROCK_EDGE + (255,), width=3, joint="curve")
    return Image.alpha_composite(img, edge)


def build_rock_glow():
    """The light the crystals throw down onto the stone. Tinted, drawn after the
    rock - it has to be its own sprite because the rock underneath must not be."""
    body = Image.new("L", (W, H), 0)
    ImageDraw.Draw(body).polygon(RIDGE + [(RIDGE_END_X, H), (0, H)], fill=255)

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for cx, h, w, lean in SHARDS:
        y = ridge_y(cx)
        r = w * 1.05
        gd.ellipse([cx - r, y - r * 0.45, cx + r, y + r * 0.6], fill=(255, 255, 255, 52))
    glow = soft(glow, 20)
    glow.putalpha(ImageChops.multiply(glow.getchannel("A"), body))
    return glow


# ------------------------------------------------------------------ sparkle

def build_sparkle():
    """A four-point star with a soft core. Drawn white so the tint can colour it,
    and scaled/faded at runtime for the twinkle."""
    S = 192
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2.0

    core = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(core).ellipse([c - S * 0.10, c - S * 0.10, c + S * 0.10, c + S * 0.10],
                                 fill=(255, 255, 255, 255))
    core = soft(core, S * 0.05)

    long_r, short_r = S * 0.46, S * 0.11
    d.polygon([(c, c - long_r), (c + short_r, c), (c, c + long_r), (c - short_r, c)],
              fill=(255, 255, 255, 255))
    d.polygon([(c - long_r, c), (c, c - short_r), (c + long_r, c), (c, c + short_r)],
              fill=(255, 255, 255, 255))
    diag = S * 0.20
    d.polygon([(c - diag, c - diag), (c + diag * 0.12, c - diag * 0.12),
               (c + diag, c + diag), (c - diag * 0.12, c + diag * 0.12)],
              fill=(255, 255, 255, 150))
    d.polygon([(c + diag, c - diag), (c + diag * 0.12, c + diag * 0.12),
               (c - diag, c + diag), (c - diag * 0.12, c - diag * 0.12)],
              fill=(255, 255, 255, 150))
    img.putalpha(img.getchannel("A").filter(ImageFilter.GaussianBlur(1.6)))
    return Image.alpha_composite(core, img)


# ------------------------------------------------------------------ build

print("gemstone outcrops")
gems = build_gems()
rock = build_rock()
glow = build_rock_glow()

gems.save(os.path.join(OUT, "gem_left.png"))
rock.save(os.path.join(OUT, "rock_left.png"))
glow.save(os.path.join(OUT, "rock_glow_left.png"))
gems.transpose(Image.FLIP_LEFT_RIGHT).save(os.path.join(OUT, "gem_right.png"))
rock.transpose(Image.FLIP_LEFT_RIGHT).save(os.path.join(OUT, "rock_right.png"))
glow.transpose(Image.FLIP_LEFT_RIGHT).save(os.path.join(OUT, "rock_glow_right.png"))
build_sparkle().save(os.path.join(OUT, "sparkle.png"))

print("  6 outcrop sprites + sparkle.png")
print("  sparkle anchors, as fractions of the sprite rect:")
print("    { " + ", ".join("{%.4ff, %.4ff}" % (x / float(W), y / float(H))
                           for x, y in SPARKS if 0 < x < W and 0 < y < H) + " }")
