"""
Cleaver swing frames.

The cleaver is drawn once in its impact pose (horizontal, edge down, handle
right) and then rotated about the handle butt to make the swing. Every frame
shares one canvas and one pivot, so the Java side positions a single rect and
the blade arcs into the cut on its own.

The trail is a WHITE silhouette fan - copies of the blade's own shape filled
flat white at low alpha, blurred, drawn behind - plus a bright streak off the
cutting edge. Ghosting with the real (dark-outlined) blade made a grey smear
instead of a trail, which is why the silhouette is recoloured first.

The canvas has to be tall enough that the blade stays inside it at full cock,
or the top of the swing gets sliced off.
"""
from PIL import Image, ImageDraw, ImageFilter
import math, os, os

# Repo-relative, so this runs from a clone anywhere.
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                     "src", "main", "resources", "assets", "scathabutcher", "textures", "gui")


OUT = os.path.join(ASSETS, "butcher")
os.makedirs(OUT, exist_ok=True)

CANVAS = 1100
DX, DY = -100, 210          # shift the whole drawing so the cocked blade fits
PIVOT = (762 + DX, 452 + DY)        # handle butt - the swing turns about this
EDGE_ANCHOR = (360 + DX, 556 + DY)  # middle of the cutting edge at impact (angle 0)
SWING_MAX_DEG = 62.0        # how far back it's cocked
FRAMES = 14
FRAME_PX = 450

STEEL_HI   = (238, 243, 248)
STEEL      = (206, 214, 224)
STEEL_MID  = (168, 178, 190)
STEEL_DARK = (120, 130, 144)
EDGE       = (252, 254, 255)
WOOD       = (126, 78, 44)
WOOD_HI    = (158, 102, 60)
WOOD_DARK  = (86, 52, 28)
BRASS      = (214, 168, 66)
OUTLINE    = (26, 24, 28)


def P(*pts):
    """Apply the canvas shift to a list of points."""
    return [(x + DX, y + DY) for x, y in pts]


def B(x0, y0, x1, y1):
    return [x0 + DX, y0 + DY, x1 + DX, y1 + DY]


def draw_cleaver():
    """One cleaver, impact pose: blade left, handle right, cutting edge down."""
    img = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # ---- handle: tapered wood with a rounded butt ----
    d.polygon(P((548, 412), (752, 424), (758, 486), (548, 486)), fill=OUTLINE)
    d.polygon(P((553, 417), (748, 429), (753, 481), (553, 481)), fill=WOOD)
    d.polygon(P((553, 417), (748, 429), (750, 448), (553, 446)), fill=WOOD_HI)
    d.polygon(P((553, 470), (751, 468), (753, 481), (553, 481)), fill=WOOD_DARK)
    d.ellipse(B(736, 424, 772, 484), fill=OUTLINE)
    d.ellipse(B(740, 428, 766, 480), fill=WOOD)
    d.ellipse(B(740, 428, 762, 452), fill=WOOD_HI)
    for rx in (596, 652, 708):
        d.ellipse(B(rx - 11, 441, rx + 11, 463), fill=OUTLINE)
        d.ellipse(B(rx - 8, 444, rx + 8, 460), fill=BRASS)
        d.ellipse(B(rx - 8, 444, rx + 2, 454), fill=(238, 200, 110))

    # ---- bolster ----
    d.polygon(P((528, 400), (556, 404), (558, 496), (528, 494)), fill=OUTLINE)
    d.polygon(P((533, 405), (551, 408), (553, 491), (533, 489)), fill=STEEL_MID)

    # ---- blade ----
    d.polygon(P((206, 352), (534, 372), (540, 520), (356, 560), (198, 546)), fill=OUTLINE)
    d.polygon(P((214, 360), (528, 379), (533, 514), (356, 552), (206, 538)), fill=STEEL)
    # spine sheen along the top
    d.polygon(P((214, 360), (528, 379), (528, 408), (214, 392)), fill=STEEL_HI)
    # broad shadow across the lower half so it doesn't read as flat paper
    d.polygon(P((208, 452), (531, 452), (533, 514), (356, 552), (206, 538)), fill=STEEL_MID)
    # bevel running just above the edge
    d.polygon(P((206, 500), (533, 486), (533, 508), (356, 546), (206, 532)), fill=STEEL_DARK)
    # the cutting edge itself, bright and slightly convex
    d.polygon(P((202, 524), (534, 500), (540, 520), (356, 560), (198, 546)), fill=EDGE)
    # hanging hole near the top front corner
    d.ellipse(B(233, 383, 281, 431), fill=OUTLINE)
    d.ellipse(B(239, 389, 275, 425), fill=(60, 62, 70, 255))
    d.ellipse(B(242, 392, 268, 418), fill=(96, 100, 110, 255))
    return img


def white_silhouette(img):
    """The blade's own shape, flat white - the trail is built from this, not from
    the blade itself, or the dark outline smears the whole fan grey."""
    sil = Image.new("RGBA", img.size, (255, 255, 255, 0))
    sil.putalpha(img.getchannel("A"))
    return sil


def _affine_about(deg, px, py):
    a = math.radians(deg)
    cos, sin = math.cos(a), math.sin(a)
    # inverse transform: destination -> source
    return (cos, sin, px - cos * px - sin * py,
            -sin, cos, py + sin * px - cos * py)


def rotate_about(img, deg, pivot):
    """Rotate around an arbitrary pivot, keeping the canvas fixed."""
    px, py = pivot
    return img.transform(img.size, Image.AFFINE, _affine_about(deg, px, py),
                         resample=Image.BICUBIC)


def scale_alpha(img, factor):
    out = img.copy()
    out.putalpha(out.getchannel("A").point(lambda v: int(v * factor)))
    return out


def soften_alpha(img, radius, rgb=(255, 255, 255)):
    """Blur the ALPHA only and keep the colour flat.

    Blurring an RGBA image blurs its colour channels too, and fully transparent
    pixels carry RGB 0 - so a plain GaussianBlur drags black into every soft
    edge and the "white" trail comes out as a grey smear. Blurring the mask and
    re-pairing it with a solid colour keeps the trail white.
    """
    a = img.getchannel("A").filter(ImageFilter.GaussianBlur(radius))
    flat = Image.new("RGB", img.size, rgb)
    out = flat.convert("RGBA")
    out.putalpha(a)
    return out


def bleed_rgb(img, passes=5):
    """Push colour outward into the transparent margin.

    Same problem one step later: downscaling to the final frame size averages
    colour across the sprite's edge, and transparent pixels are black, so the
    blade would pick up a dark halo. Filling the margin with the neighbouring
    colour first means the average has nothing dark to pull in.
    """
    rgb = img.convert("RGB")
    alpha = img.getchannel("A")
    for _ in range(passes):
        grown = alpha.filter(ImageFilter.MaxFilter(3))
        spread = rgb.filter(ImageFilter.MaxFilter(3))
        rgb = Image.composite(rgb, spread, alpha)
        alpha = grown
    out = rgb.convert("RGBA")
    out.putalpha(img.getchannel("A"))
    return out


def swing_angle(t):
    """Hangs cocked, then accelerates hard into the cut - so the trail lands on
    the last frames, where the blade is actually moving."""
    return SWING_MAX_DEG * (1.0 - t ** 2.4)


base = draw_cleaver()
sil = white_silhouette(base)

print("cleaver swing frames")
for i in range(FRAMES):
    t = i / (FRAMES - 1)
    ang = swing_angle(t)
    speed = abs(swing_angle(min(1.0, t + 1.0 / (FRAMES - 1))) - ang)

    frame = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))

    if speed > 2.0:
        # short white fan: four silhouettes behind the blade, fading back
        span = min(speed * 1.6, 20.0)
        fan = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
        for g in range(1, 5):
            ghost = rotate_about(sil, ang + span * (g / 4.0), PIVOT)
            fan = Image.alpha_composite(fan, scale_alpha(ghost, 0.55 * (1 - g / 5.0)))
        frame = Image.alpha_composite(frame, soften_alpha(fan, 4))

        # bright streak peeling off the cutting edge
        streak = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
        sd = ImageDraw.Draw(streak)
        for g in range(6):
            ga = ang + span * (0.25 + g * 0.30)
            ar = math.radians(ga)
            cos, sin = math.cos(ar), math.sin(ar)

            def rot(pt):
                # must match rotate_about's direction, or the trail sweeps the wrong way
                x, y = pt[0] + DX - PIVOT[0], pt[1] + DY - PIVOT[1]
                return (PIVOT[0] + x * cos - y * sin, PIVOT[1] + x * sin + y * cos)

            sd.line([rot((202, 534)), rot((540, 512))],
                    fill=(255, 255, 255, int(240 * (1 - g / 6.0) ** 1.15)), width=15)
        frame = Image.alpha_composite(frame, soften_alpha(streak, 5))

    frame = Image.alpha_composite(frame, rotate_about(base, ang, PIVOT))
    bleed_rgb(frame).resize((FRAME_PX, FRAME_PX), Image.LANCZOS).save(
        os.path.join(OUT, "cleaver_%02d.png" % i))

print("  %d frames, %dx%d" % (FRAMES, FRAME_PX, FRAME_PX))
print("  EDGE_ANCHOR as a fraction of the frame: (%.4ff, %.4ff)"
      % (EDGE_ANCHOR[0] / CANVAS, EDGE_ANCHOR[1] / CANVAS))
print("  PIVOT as a fraction of the frame:       (%.4ff, %.4ff)"
      % (PIVOT[0] / CANVAS, PIVOT[1] / CANVAS))
