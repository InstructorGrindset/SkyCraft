"""Generate SkyCraft's mob effect icons: 18x18, like vanilla's textures/mob_effect.

Everything is drawn from simple shapes in code (no images are read), with an outline traced round each shape:

    skooma             Skooma High: a full bottle of Skooma's purple, corked
    stealth            Stealth: a slate hood, its face in shadow
    skooma_withdrawal  Skooma Withdrawal: the bottle empty but for the dregs, cracked, shaking

    python tools/gen_effect_icons.py                  # writes into the fabric resources
    python tools/gen_effect_icons.py --preview x.png  # also writes an enlarged contact sheet

Needs Pillow (pip install pillow). Output is deterministic. Minecraft 26.3 stitches every namespace's
textures/mob_effect/*.png into the gui atlas as mob_effect/<name> (assets/minecraft/atlases/gui.json), which is
where the HUD and the inventory look up an effect's icon by its id; the ids must match
fabric/src/main/java/dev/skycraft/skooma/SkyEffects.java.
"""
import argparse
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "fabric", "src", "main", "resources", "assets", "skycraft", "textures", "mob_effect")
SIZE = 18


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


OUTLINE = rgb("1c1420")
PURPLE = dict(dark=rgb("5e2f80"), mid=rgb("9b5fc0"), light=rgb("c99be6"), shine=rgb("f2e4ff"))
GLASS = dict(dark=rgb("8f8aa6"), mid=rgb("c4bfd8"), light=rgb("ebe8f5"))
CORK = dict(dark=rgb("5a3a22"), mid=rgb("8a5c36"), light=rgb("b07e4e"))
SLATE = dict(outline=rgb("12161e"), dark=rgb("262e3c"), mid=rgb("3e4a5e"), light=rgb("63738f"), shadow=rgb("0b0d12"), eye=rgb("b8cce6"))
SICK = dict(dark=rgb("55531e"), mid=rgb("8c8a3a"), light=rgb("bdbb6a"), crack=rgb("2a2810"))


def inside_ellipse(x, y, cx, cy, rx, ry):
    return ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0


def outlined(pixels, outline):
    """An image of the pixels {(x, y): color} with an outline round them (every empty pixel touching one)."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for (x, y), color in pixels.items():
        img.putpixel((x, y), color)
    for y in range(SIZE):
        for x in range(SIZE):
            if (x, y) in pixels:
                continue
            if any((x + dx, y + dy) in pixels for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), outline)
    return img


def shade(x, y, cx, cy, pal):
    """Light from the upper left: light on that side, dark on the lower right."""
    d = (x + 0.5 - cx) + (y + 0.5 - cy) * 0.7
    if d < -2.5:
        return pal["light"]
    if d > 2.0:
        return pal["dark"]
    return pal["mid"]


def bottle(liquid_from, liquid, glass, cork):
    """A Skooma bottle: cork, a narrow neck, a round body filled with liquid from row liquid_from down."""
    pixels = {}
    cx, cy, rx, ry = 9.0, 12.0, 5.6, 4.9
    for y in range(SIZE):
        for x in range(SIZE):
            if inside_ellipse(x, y, cx, cy, rx, ry) and y <= 16:
                pal = liquid if y >= liquid_from else glass
                pixels[(x, y)] = shade(x, y, cx, cy, pal)
            elif 7 <= x <= 10 and 4 <= y <= 7:
                pixels[(x, y)] = glass["light"] if x == 7 else glass["mid"] if x < 10 else glass["dark"]
            elif 7 <= x <= 10 and 1 <= y <= 3:
                pixels[(x, y)] = cork["light"] if x == 7 else cork["mid"] if x < 10 else cork["dark"]
    return pixels


def skooma_icon():
    pixels = bottle(9, PURPLE, GLASS, CORK)
    # A glint on the glass and in the liquid.
    for p in ((6, 10), (6, 11), (7, 9)):
        pixels[p] = PURPLE["shine"]
    pixels[(8, 5)] = GLASS["light"]
    return outlined(pixels, OUTLINE)


def stealth_icon():
    pal = SLATE
    pixels = {}
    cx, cy = 9.0, 8.6
    for y in range(SIZE):
        for x in range(SIZE):
            hood = inside_ellipse(x, y, cx, cy, 6.4, 7.2) and y <= 14
            cape = 13 <= y <= 16 and abs(x + 0.5 - cx) <= 4.5 + (y - 13) * 0.9
            if not (hood or cape):
                continue
            if inside_ellipse(x, y, cx, 10.0, 3.3, 3.9) and y <= 13:
                pixels[(x, y)] = pal["shadow"]  # the face, in shadow
            else:
                pixels[(x, y)] = shade(x, y, cx, cy, pal)
    # The peak of the hood, and two eyes catching the light.
    pixels[(8, 1)] = pal["light"]
    pixels[(9, 1)] = pal["mid"]
    pixels[(7, 10)] = pal["eye"]
    pixels[(10, 10)] = pal["eye"]
    return outlined(pixels, pal["outline"])


def withdrawal_icon():
    pixels = bottle(15, PURPLE, SICK, CORK)  # empty but for the dregs
    # A crack across the glass.
    for p in ((12, 8), (11, 9), (11, 10), (10, 11), (11, 12), (10, 13), (10, 14)):
        pixels[p] = SICK["crack"]
    img = outlined(pixels, OUTLINE)
    # Shaking: short strokes either side.
    for x, ys in ((1, (8, 9)), (0, (11, 12)), (16, (8, 9)), (17, (11, 12))):
        for y in ys:
            img.putpixel((x, y), SICK["light"])
    return img


ICONS = {
    "skooma": skooma_icon,
    "stealth": stealth_icon,
    "skooma_withdrawal": withdrawal_icon,
}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--preview", help="also write an enlarged contact sheet here")
    args = parser.parse_args()
    os.makedirs(OUT_DIR, exist_ok=True)
    images = []
    for name, draw in ICONS.items():
        img = draw()
        img.save(os.path.join(OUT_DIR, name + ".png"))
        images.append(img)
        print("wrote", os.path.relpath(os.path.join(OUT_DIR, name + ".png"), ROOT))
    if args.preview:
        scale = 12
        sheet = Image.new("RGBA", (len(images) * (SIZE + 2) * scale, (SIZE + 2) * scale), rgb("6f6f6f"))
        for i, img in enumerate(images):
            big = img.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
            sheet.alpha_composite(big, (i * (SIZE + 2) * scale + scale, scale))
        sheet.save(args.preview)


if __name__ == "__main__":
    main()
