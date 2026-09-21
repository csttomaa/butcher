#!/usr/bin/env python3
"""Generates simple flat-shaded pixel-art textures for the Butcher mod.
No external assets/network used - everything is drawn procedurally with PIL."""

from PIL import Image, ImageDraw
import os

OUT = "src/main/resources/assets/butcher/textures/gui"
os.makedirs(OUT, exist_ok=True)

def save(img, name):
    img.save(os.path.join(OUT, name))
    print("wrote", name, img.size)

def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))

# ---------------------------------------------------------------- cutting board
def cutting_board():
    w, h = 128, 48
    img = new(w, h)
    d = ImageDraw.Draw(img)
    base = (120, 84, 47, 255)
    dark = (96, 65, 33, 255)
    light = (145, 105, 62, 255)
    edge = (60, 40, 20, 255)
    d.rounded_rectangle([2, 2, w - 3, h - 3], radius=10, fill=base, outline=edge, width=3)
    # wood grain stripes
    for i, y in enumerate(range(10, h - 6, 6)):
        col = light if i % 2 == 0 else dark
        d.line([(10, y), (w - 10, y)], fill=col, width=2)
    # handle hole
    d.ellipse([w - 22, h // 2 - 6, w - 8, h // 2 + 6], outline=edge, width=2)
    save(img, "cutting_board.png")

# ---------------------------------------------------------------- knife
def knife():
    w, h = 96, 32
    img = new(w, h)
    d = ImageDraw.Draw(img)
    blade = (222, 226, 230, 255)
    blade_edge = (180, 186, 192, 255)
    shine = (255, 255, 255, 180)
    handle = (61, 38, 22, 255)
    handle_dark = (40, 24, 12, 255)
    rivet = (200, 170, 90, 255)
    # blade (tapers to a point on the left)
    d.polygon([(0, h // 2), (60, 4), (78, h // 2 - 2), (78, h // 2 + 2), (60, h - 4)],
              fill=blade, outline=blade_edge)
    d.line([(8, h // 2 - 1), (56, 6)], fill=shine, width=2)
    # handle
    d.rounded_rectangle([74, h // 2 - 8, w - 2, h // 2 + 8], radius=6, fill=handle, outline=handle_dark, width=2)
    for rx in (82, 92, 102):
        d.ellipse([rx - 2, h // 2 - 2, rx + 2, h // 2 + 2], fill=rivet)
    save(img, "knife.png")

# ---------------------------------------------------------------- scatha head (whole)
SCATHA_DARK = (46, 26, 58, 255)
SCATHA_MID = (78, 45, 92, 255)
SCATHA_LIGHT = (120, 74, 138, 255)
SCATHA_PLATE = (30, 16, 38, 255)
EYE_RED = (214, 32, 32, 255)
MANDIBLE = (222, 200, 150, 255)

def _draw_scatha_head(d, ox, oy, s):
    # s = scale unit
    # armored head shape
    d.rounded_rectangle([ox, oy, ox + 22 * s, oy + 18 * s], radius=5 * s, fill=SCATHA_MID, outline=SCATHA_DARK, width=max(1, s))
    # plates
    for i in range(3):
        x0 = ox + (2 + i * 7) * s
        d.line([(x0, oy + 2 * s), (x0, oy + 16 * s)], fill=SCATHA_PLATE, width=max(1, s))
    # highlight
    d.rounded_rectangle([ox + 2 * s, oy + 2 * s, ox + 8 * s, oy + 6 * s], radius=2 * s, fill=SCATHA_LIGHT)
    # eyes
    d.ellipse([ox + 4 * s, oy + 7 * s, ox + 8 * s, oy + 11 * s], fill=EYE_RED)
    d.ellipse([ox + 15 * s, oy + 7 * s, ox + 19 * s, oy + 11 * s], fill=EYE_RED)
    # mandibles
    d.polygon([(ox - 4 * s, oy + 14 * s), (ox + 2 * s, oy + 12 * s), (ox + 2 * s, oy + 17 * s)], fill=MANDIBLE)
    d.polygon([(ox + 26 * s, oy + 14 * s), (ox + 20 * s, oy + 12 * s), (ox + 20 * s, oy + 17 * s)], fill=MANDIBLE)

def scatha_head():
    s = 4
    w, h = 22 * s + 20, 18 * s + 10
    img = new(w, h)
    d = ImageDraw.Draw(img)
    _draw_scatha_head(d, 10, 5, s)
    save(img, "scatha_head.png")

def scatha_head_top():
    # top half only (crop of the full head raster), used after the cut
    s = 4
    w, h = 22 * s + 20, 18 * s + 10
    img = new(w, h)
    d = ImageDraw.Draw(img)
    _draw_scatha_head(d, 10, 5, s)
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    md.rectangle([0, 0, w, h // 2 + 2], fill=255)
    out = new(w, h)
    out.paste(img, (0, 0), mask)
    save(out, "scatha_head_top.png")

def scatha_head_bottom():
    s = 4
    w, h = 22 * s + 20, 18 * s + 10
    img = new(w, h)
    d = ImageDraw.Draw(img)
    _draw_scatha_head(d, 10, 5, s)
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    md.rectangle([0, h // 2 - 2, w, h], fill=255)
    out = new(w, h)
    out.paste(img, (0, 0), mask)
    save(out, "scatha_head_bottom.png")

# ---------------------------------------------------------------- gemstone (blocked outcome)
def gemstone():
    w, h = 40, 40
    img = new(w, h)
    d = ImageDraw.Draw(img)
    face = (64, 200, 220, 255)
    face_light = (150, 235, 245, 255)
    face_dark = (30, 140, 160, 255)
    cx, cy, r = 20, 20, 16
    top = [(cx, cy - r), (cx - r * 0.6, cy - r * 0.3), (cx - r * 0.35, cy + r), (cx + r * 0.35, cy + r), (cx + r * 0.6, cy - r * 0.3)]
    d.polygon(top, fill=face, outline=face_dark)
    d.polygon([(cx, cy - r), (cx - r * 0.6, cy - r * 0.3), (cx, cy - r * 0.2)], fill=face_light)
    d.line([(cx, cy - r), (cx, cy + r)], fill=face_dark, width=1)
    save(img, "gemstone.png")

# ---------------------------------------------------------------- spark / clang burst
def spark():
    w, h = 48, 48
    img = new(w, h)
    d = ImageDraw.Draw(img)
    cx, cy = w // 2, h // 2
    col = (255, 241, 148, 255)
    col2 = (255, 255, 255, 255)
    import math
    for i in range(8):
        ang = i * math.pi / 4
        x1 = cx + math.cos(ang) * 6
        y1 = cy + math.sin(ang) * 6
        x2 = cx + math.cos(ang) * (20 if i % 2 == 0 else 12)
        y2 = cy + math.sin(ang) * (20 if i % 2 == 0 else 12)
        d.line([(x1, y1), (x2, y2)], fill=col, width=3)
    d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=col2)
    save(img, "spark.png")

# ---------------------------------------------------------------- blood splash (pet-drop hit)
def slash():
    w, h = 96, 20
    img = new(w, h)
    d = ImageDraw.Draw(img)
    col = (176, 22, 22, 255)
    col_dark = (110, 10, 10, 255)
    d.line([(2, h // 2), (w - 2, h // 2)], fill=col, width=5)
    d.line([(2, h // 2), (w - 2, h // 2)], fill=col_dark, width=1)
    for x in range(6, w - 6, 10):
        d.ellipse([x - 2, h // 2 - 6, x + 2, h // 2 - 2], fill=col)
    save(img, "slash.png")

# ---------------------------------------------------------------- mod icon
def icon():
    w, h = 128, 128
    img = Image.new("RGBA", (w, h), (46, 26, 58, 255))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([10, 70, 118, 118], radius=10, fill=(120, 84, 47, 255), outline=(60, 40, 20, 255), width=4)
    _draw_scatha_head(d, 30, 20, 4)
    # knife diagonally
    d.polygon([(20, 100), (95, 25), (105, 35), (30, 110)], fill=(222, 226, 230, 255), outline=(150, 155, 160, 255))
    d.polygon([(95, 25), (112, 8), (120, 16), (105, 35)], fill=(61, 38, 22, 255))
    save(img, "../../icon.png")

if __name__ == "__main__":
    # NOTE: as of the guillotine redesign, guillotine_frame.png / blade.png /
    # scatha_head*.png are real custom art (ported from the reference
    # artifact), not this script's placeholder pixel art anymore. The
    # functions below (cutting_board, knife, scatha_head*, slash) are kept
    # for reference/history only - do NOT call them here, or they will
    # overwrite the real art with placeholders again.
    gemstone()
    spark()
    # icon() is NOT called any more. The mod's icon is rendered from the real cleaver
    # mesh and texture by tools/IconGen.java, so it cannot drift from what the mod
    # draws; running the placeholder version below would overwrite it with the old
    # hand-drawn one. Kept for reference only.
