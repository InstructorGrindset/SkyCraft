"""Generate the Skooma brewer's item icon: an original 16x16 heap of moon sugar.

Drawn from simple shapes in code (no images are read), with the same bevelled pixel style as
tools/gen_gear_textures.py: a low heap of pale lilac crystals, lit from the top left, with a few
bright grains glinting on it and loose grains at its foot (moon sugar, Skyrim's Khajiit sugar that
Skooma is brewed from).

    python tools/gen_skooma_textures.py                 # writes into the fabric resources
    python tools/gen_skooma_textures.py --preview x.png # also writes an enlarged preview

Needs Pillow (pip install pillow). Output is deterministic. The item id must match
fabric/src/main/java/dev/skycraft/skooma/SkoomaBrewing.java (MOON_SUGAR_ID).
"""
import argparse
import os

from PIL import Image

from gen_gear_textures import ITEM_DIR, Icon, rgb

SUGAR = dict(outline=rgb("5b4a78"), dark=rgb("a58fc9"), mid=rgb("d9cdf0"), light=rgb("f7f3ff"))
GLINT = rgb("ffffff")
GRAIN = rgb("c8b6e8")
SHADE = rgb("8a76b0")


def moon_sugar():
    """A heap: narrow at the top, widest at its foot, one row per step."""
    ic = Icon()
    rows = {6: (7, 8), 7: (6, 9), 8: (5, 10), 9: (4, 11), 10: (3, 12), 11: (2, 13), 12: (2, 13), 13: (3, 12)}
    for y, (x0, x1) in rows.items():
        ic.rect(x0, y, x1, y, "s")
    img = ic.render({"s": SUGAR})
    px = img.load()
    # Crystal facets: a few darker grains in the heap's shaded side, brighter ones in its lit side.
    for x, y in ((9, 9), (11, 11), (8, 12), (10, 10), (12, 12)):
        px[x, y] = SHADE
    for x, y in ((6, 9), (5, 11), (7, 10), (4, 12)):
        px[x, y] = GRAIN
    # Glints: the moonlight sparkle Skyrim's moon sugar has.
    for x, y in ((7, 7), (5, 10), (9, 11), (11, 12)):
        px[x, y] = GLINT
    # Loose grains spilled at the foot and one in the air.
    for x, y in ((1, 14), (14, 14), (13, 14), (2, 15), (10, 4)):
        px[x, y] = GRAIN
    px[12, 3] = GLINT
    return img


ICONS = {"moon_sugar": moon_sugar}


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--preview", help="also write an enlarged preview of the icons to this path")
    args = ap.parse_args()
    os.makedirs(ITEM_DIR, exist_ok=True)
    written = []
    for name, fn in ICONS.items():
        img = fn()
        img.save(os.path.join(ITEM_DIR, name + ".png"))
        written.append(img)
    print("wrote %d item textures" % len(written))
    if args.preview:
        scale = 12
        sheet = Image.new("RGBA", (len(written) * 18 * scale, 18 * scale), (70, 80, 90, 255))
        for i, img in enumerate(written):
            sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST), (i * 18 * scale + scale, scale))
        sheet.save(args.preview)
        print("preview:", args.preview)


if __name__ == "__main__":
    main()
