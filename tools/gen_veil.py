"""
The pre-kill veil: darkness closing in over the HUD as a Scatha nears death.

Replaces the two solid black rectangles that used to be slapped over the hotbar
and the chat box. Those read as a bug - two hard-edged boxes appearing out of
nowhere - when the point of the moment is to build tension before the reveal.

The shape does the work. It is opaque across the bottom, where the hotbar, the
status rows and the chat all live, and fades upward to nothing, with a soft
vignette closing in from the edges. So the UI is swallowed by shadow while the
middle of the screen - where the Scatha actually is - stays readable until the
cutscene takes over. Drawn white so the blit tint sets both its colour and, more
importantly, its alpha, which is what lets it creep in over a second or so
instead of snapping on.
"""
from PIL import Image, ImageDraw, ImageFilter
import os

# Repo-relative, so this runs from a clone anywhere.
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                     "src", "main", "resources", "assets", "scathabutcher", "textures", "gui")


OUT = ASSETS
W, H = 512, 288

alpha = Image.new("L", (W, H), 0)
px = alpha.load()

# bottom band: solid, fading out as it climbs past the HUD
for y in range(H):
    f = y / float(H - 1)
    if f >= 0.86:
        v = 255
    elif f >= 0.50:
        t = (f - 0.50) / 0.36
        v = int(255 * (t ** 1.5))
    else:
        v = 0
    for x in range(W):
        px[x, y] = v

# vignette pressing in from every edge
vig = Image.new("L", (W, H), 255)
ImageDraw.Draw(vig).ellipse([-W * 0.04, -H * 0.08, W * 1.04, H * 1.08], fill=0)
vig = vig.filter(ImageFilter.GaussianBlur(46))
alpha = Image.eval(Image.merge("L", [alpha]), lambda v: v)
alpha.paste(Image.new("L", (W, H), 255), (0, 0),
            Image.eval(vig, lambda v: int(v * 0.86)))
alpha = alpha.filter(ImageFilter.GaussianBlur(5))

veil = Image.new("RGBA", (W, H), (255, 255, 255, 0))
veil.putalpha(alpha)
veil.save(os.path.join(OUT, "prekill_veil.png"))
print("prekill_veil.png %dx%d" % (W, H))
