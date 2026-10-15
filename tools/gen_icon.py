"""The mod icon: a gold pixel-art B on a dark badge, its top-left corner chopped off by a cleaver.

32 x 32 pixel art, written 8x up (256 x 256, nearest neighbour) to src/main/resources/assets/butcher/icon.png.
The menu shows it at its native 32 (MenuIcon halves it back down exactly), Mod Menu and Modrinth at whatever size.

    python3 tools/gen_icon.py

Replaced the rendered cleaver (tools/IconGen.java) on 2026-10-15 - the user found that one cheap-looking and picked
this from a sheet of options. IconGen is kept as the only source of the old one; running it would overwrite this.
"""
import os
from PIL import Image, ImageDraw

N = 32
INK = (16, 12, 18, 255)

# The B: 16 x 18, a 4-pixel stroke, counters big enough to read at 16 px.
B = ["#############...",
     "##############..",
     "###############.",
     "####......#####.",
     "####.......####.",
     "####.......####.",
     "####......#####.",
     "##############..",
     "#############...",
     "###############.",
     "####.......#####",
     "####........####",
     "####........####",
     "####........####",
     "####.......#####",
     "################",
     "###############.",
     "##############.."]
OX, OY = 7, 6
CUT = 5            # the chop: the line x + y = OX + OY + CUT, through the B's top-left corner only (counters untouched)

LIGHT, GOLD, SHADE, DEPTH = (255, 222, 128, 255), (240, 178, 64, 255), (184, 116, 34, 255), (122, 72, 22, 255)
HOT_PIECE, HOT_LETTER = (255, 250, 226, 255), (255, 232, 170, 255)
SPARK = (255, 238, 180)


def badge():
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, N - 1, N - 1), radius=5, fill=(28, 31, 40, 255), outline=(60, 52, 40, 255))
    d.line((5, 1, N - 6, 1), fill=(90, 80, 60, 255))
    return im


def letter():
    """The B's pixels, lit from the top left."""
    px = {}
    h = len(B)
    for r, row in enumerate(B):
        w = len(row)
        for c, ch in enumerate(row):
            if ch != '#':
                continue
            col = GOLD
            if r <= 1 or c <= 1:
                col = LIGHT
            if r >= h - 2 or c >= w - 2:
                col = SHADE
            px[(OX + c, OY + r)] = col
    return px


def chop(px):
    """Cuts along x + y = K: the line itself goes, the corner piece moves a pixel up-left, both cut faces glow."""
    k = OX + OY + CUT
    out = {}
    for (x, y), col in px.items():
        s = x + y - k
        if s == 0:
            continue
        out[(x - 1, y - 1) if s < 0 else (x, y)] = col
    for (x, y) in px:
        s = x + y - k
        if s == -1:
            out[(x - 1, y - 1)] = HOT_PIECE
        if s == 1:
            out[(x, y)] = HOT_LETTER
    return out


def extrude(px):
    """A pixel of depth down and to the right."""
    out = dict(px)
    for (x, y) in px:
        if (x + 1, y + 1) not in out:
            out[(x + 1, y + 1)] = DEPTH
    return out


def outlined(px):
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    p = im.load()
    for (x, y), c in px.items():
        p[x, y] = c
    out = im.copy()
    o = out.load()
    for y in range(N):
        for x in range(N):
            if p[x, y][3]:
                continue
            if any(0 <= x + dx < N and 0 <= y + dy < N and p[x + dx, y + dy][3]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                o[x, y] = INK
    return out


def icon():
    im = outlined(extrude(chop(letter())))
    p = im.load()
    # Sparks flying off along the line of the cut, either side of the letter.
    for x, y, a in ((13, 5, 255), (14, 4, 190), (15, 3, 120), (4, 14, 255), (3, 15, 190), (2, 16, 120)):
        p[x, y] = SPARK + (a,)
    out = badge()
    out.alpha_composite(im)
    return out


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    dest = os.path.join(here, "..", "src", "main", "resources", "assets", "butcher", "icon.png")
    icon().resize((N * 8, N * 8), Image.NEAREST).save(dest)
    print("wrote", os.path.normpath(dest))
