#!/usr/bin/env python3
"""
Resolve a Minecraft player-head texture to a PNG on disk.

Takes whatever form the texture happens to be in - the base64 GameProfile blob out of
an item's PROFILE component, a bare texture hash, or the full textures.minecraft.net
URL - and writes the 64x64 skin next to itself.

Two sources, tried in this order:

  1. Minecraft's own skin cache, .minecraft/assets/skins/<first two chars>/<hash>.
     Every head the client has ever rendered is already there - 226k of them on this
     machine - so anything seen in game resolves instantly, offline.
  2. https://textures.minecraft.net/texture/<hash>.

The texture hash IS the SHA-256 of the PNG bytes, so both routes are verifiable and
byte-identical; the file is checked against its own name before being kept.

  python3 tools/head_tex.py <blob|hash|url> [more...] [-o OUTDIR] [-z ZOOM]
  python3 tools/head_tex.py --recent <MINUTES>      what the client cached just now

Each one is written twice: the real 64x64 texture, and an <name>_x8.png blown up with
nearest-neighbour so it can actually be looked at. Use the small one in the mod.
"""
import base64, hashlib, io, json, os, re, sys, time, urllib.request

# Every skin cache on this machine, most-recently-written first.
#
# There is more than one, and picking the wrong one silently fails to find recent
# heads: the vanilla launcher's .minecraft cache stopped being written when play
# moved to the Modrinth app, so anything seen in the last year is only in that one.
CACHES = [
    os.path.expandvars("%APPDATA%/ModrinthApp/meta/assets/skins"),
    os.path.expandvars("%APPDATA%/.minecraft/assets/skins"),
]
HASH = re.compile(r"^[0-9a-f]{32,64}$")


def to_hash(text):
    """Pull a texture hash out of a blob, a URL, or a bare hash."""
    text = text.strip().strip('"').strip("'")
    if HASH.match(text):
        return text
    if "textures.minecraft.net" in text:
        return text.rstrip("/").rsplit("/", 1)[-1]
    # a base64 GameProfile blob: decode and read the SKIN url out of it
    try:
        blob = json.loads(base64.b64decode(text + "=" * (-len(text) % 4)))
        return blob["textures"]["SKIN"]["url"].rstrip("/").rsplit("/", 1)[-1]
    except Exception:
        raise ValueError(f"not a blob, hash or texture url: {text[:40]!r}")


def fetch(h):
    """The PNG bytes for one hash, from a local cache if any of them has it."""
    for cache in CACHES:
        local = os.path.join(cache, h[:2], h)
        if os.path.isfile(local):
            with open(local, "rb") as f:
                return f.read(), "cache"
    url = f"https://textures.minecraft.net/texture/{h}"
    with urllib.request.urlopen(url, timeout=30) as r:
        return r.read(), "network"


def recent(minutes):
    """Every skin cached in the last `minutes` minutes, newest first.

    For a head that cannot be held or looked up - a mob's, usually - where the only
    handle on it is having just been near it. The client writes the texture to disk the
    moment it renders it, so "what did I just see" is answerable by mtime.

    os.scandir rather than a find: these caches hold six figures of files, and walking
    them with a subprocess per entry takes minutes.
    """
    cutoff = time.time() - minutes * 60
    hits = []
    for cache in CACHES:
        if not os.path.isdir(cache):
            continue
        for sub in os.scandir(cache):
            if not sub.is_dir():
                continue
            for f in os.scandir(sub.path):
                try:
                    st = f.stat()
                except OSError:
                    continue
                if st.st_mtime >= cutoff:
                    hits.append((st.st_mtime, f.name, cache))
    return sorted(hits, reverse=True)


def main(argv):
    out = "."
    zoom = 8
    if "-o" in argv:
        i = argv.index("-o")
        out = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    if "-z" in argv:
        i = argv.index("-z")
        zoom = int(argv[i + 1])
        argv = argv[:i] + argv[i + 2:]
    if argv and argv[0] == "--recent":
        minutes = int(argv[1]) if len(argv) > 1 else 10
        hits = recent(minutes)
        print(f"{len(hits)} skin(s) cached in the last {minutes} minute(s), newest first:")
        for mt, h, cache in hits[:40]:
            print(f"  {time.strftime('%H:%M:%S', time.localtime(mt))}  {h}")
        if not hits:
            print("  (none - the client only writes a skin when it renders it)")
        return
    if not argv:
        raise SystemExit(__doc__)
    os.makedirs(out, exist_ok=True)

    bad = 0
    for arg in argv:
        # One bad input must not take the rest of the batch down with it: these get
        # pasted in bulk, and a truncated hash in the middle of twenty good ones should
        # cost that one line, not the run.
        try:
            h = to_hash(arg)
            data, where = fetch(h)
        except Exception as e:
            bad += 1
            print(f"{arg[:24]:<24}  FAILED  {type(e).__name__}: {e}")
            continue
        # Only a 64-char name is a Mojang texture hash, and those ARE the SHA-256 of
        # the PNG, so they check themselves. The 40-char names in the cache are an older
        # scheme that is not a hash of the content at all - neither sha1 nor sha256 of
        # the bytes reproduces one - so there is nothing to check them against, and
        # claiming "verified" for them would be a lie.
        if len(h) == 64:
            real = hashlib.sha256(data).hexdigest()
            ok = "verified" if real == h else f"HASH MISMATCH (got {real[:16]}...)"
        else:
            ok = "unverifiable (legacy cache name, not a content hash)"
        png = data[:8] == b"\x89PNG\r\n\x1a\n"
        path = os.path.join(out, h[:16] + ".png")
        with open(path, "wb") as f:
            f.write(data)
        # A 64x64 skin shown at its own size is too small to read - which matters,
        # because the whole reason for pulling one of these down is to look at it. The
        # nearest-neighbour copy beside it keeps every pixel square and hard-edged;
        # anything smoother would invent colours that are not in the texture.
        if zoom > 1:
            try:
                from PIL import Image
                img = Image.open(io.BytesIO(data))
                big = img.resize((img.width * zoom, img.height * zoom), Image.NEAREST)
                big.save(os.path.join(out, h[:16] + f"_x{zoom}.png"))
            except ImportError:
                pass
        print(f"{h[:16]}...  {len(data):>6} bytes  {where:<7}  "
              f"{'PNG' if png else 'NOT A PNG'}  {ok}  -> {path}")
    if bad:
        print(f"{bad} input(s) could not be resolved.")


if __name__ == "__main__":
    main(sys.argv[1:])
