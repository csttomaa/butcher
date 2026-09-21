"""
Impact sparks, as a short baked sequence.

Six frames rather than one sprite scaled at runtime: the GUI blit path has no
alpha tint, so a spark burst can only fade if the fade is painted into the
texture. Each frame throws the same shower a little further out and a little
dimmer, so playing them in order reads as sparks flying off and dying.
"""
from PIL import Image, ImageDraw, ImageFilter
import math, os, os, random

# Repo-relative, so this runs from a clone anywhere.
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir,
                     "src", "main", "resources", "assets", "butcher", "textures", "gui")


OUT = os.path.join(ASSETS, "butcher")
S = 256
FRAMES = 6
N = 30

rng = random.Random(7)
# one fixed shower, re-drawn further along its own path each frame
SHOWER = []
for i in range(N):
    ang = rng.uniform(-math.pi, 0.25)          # mostly upward and sideways off the cut
    SHOWER.append((ang, rng.uniform(0.55, 1.0), rng.uniform(0.7, 1.4)))

for f in range(FRAMES):
    t = f / (FRAMES - 1)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2

    for ang, reach, weight in SHOWER:
        # travel out along the ray, arcing downward as it goes
        travel = reach * (0.10 + 0.42 * t)
        r0 = S * max(0.0, travel - 0.075 * weight)
        r1 = S * travel
        drop = S * 0.20 * t * t * weight
        x0, y0 = c + math.cos(ang) * r0, c + math.sin(ang) * r0 + drop * 0.6
        x1, y1 = c + math.cos(ang) * r1, c + math.sin(ang) * r1 + drop

        dim = (1 - t) ** 1.4
        a = int(235 * dim)
        if a <= 4:
            continue
        w = max(1, int(round(2.4 * weight)))
        d.line([(x0, y0), (x1, y1)], fill=(255, 214, 120, a), width=w)
        d.line([((x0 + x1) / 2, (y0 + y1) / 2), (x1, y1)],
               fill=(255, 252, 226, min(255, a + 20)), width=w)

    # the hot flare right at the cut, gone quickly
    flare = (1 - t) ** 2.6
    if flare > 0.02:
        g = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        rr = S * (0.10 + 0.05 * t)
        ImageDraw.Draw(g).ellipse([c - rr, c - rr, c + rr, c + rr],
                                  fill=(255, 226, 150, int(190 * flare)))
        a = g.getchannel("A").filter(ImageFilter.GaussianBlur(S * 0.045))
        glow = Image.new("RGBA", (S, S), (255, 232, 168, 0))
        glow.putalpha(a)
        img = Image.alpha_composite(glow, img)

    img.save(os.path.join(OUT, "spark_%d.png" % f))
    print("  spark_%d.png" % f)

print("done")
