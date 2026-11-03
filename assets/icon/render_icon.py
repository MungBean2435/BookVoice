"""Render the app icon: a dog-eared page carrying a sound wave.

Pure Pillow. Geometry lives in a 108x108 design space that matches
app/src/main/res/drawable/ic_launcher_foreground.xml.

Usage: python3 assets/icon/render_icon.py [out.png] [size]
"""
import math

from PIL import Image, ImageDraw, ImageFilter

BG_TOP = (28, 46, 76)
BG_BOTTOM = (9, 16, 28)
PAPER = (245, 235, 216)
FOLD = (205, 186, 154)
CREASE = (176, 156, 124)
ACCENT = (233, 164, 62)

PX0, PY0, PX1, PY1, PR = 28, 24, 80, 84, 10
CUT = 16
WAVE_THICK, WAVE_AMP, WAVE_CYCLES = 5.2, 10.5, 3.4


def render(size: int = 1024, ss: int = 4) -> Image.Image:
    n = size * ss
    k = n / 108.0
    img = Image.new("RGB", (n, n), BG_BOTTOM)
    d = ImageDraw.Draw(img)
    for y in range(n):
        t = y / (n - 1)
        d.line([(0, y), (n, y)], fill=tuple(
            int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)))

    def px(v):
        return v * k

    # page silhouette: rounded rect with the top-right corner cut away
    mask = Image.new("L", (n, n), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([px(PX0), px(PY0), px(PX1), px(PY1)], radius=px(PR), fill=255)
    md.polygon([(px(PX1 - CUT), px(PY0) - 1), (px(PX1) + 1, px(PY0) - 1),
                (px(PX1) + 1, px(PY0 + CUT))], fill=0)

    shadow = mask.filter(ImageFilter.GaussianBlur(px(3)))
    img.paste((0, 0, 0), (0, 0), shadow.point(lambda v: int(v * 0.5)))
    img.paste(PAPER, (0, 0), mask)

    # folded corner underside
    d.polygon([(px(PX1 - CUT), px(PY0)), (px(PX1), px(PY0 + CUT)),
               (px(PX1 - CUT), px(PY0 + CUT))], fill=FOLD)
    d.line([(px(PX1 - CUT), px(PY0)), (px(PX1), px(PY0 + CUT))],
           fill=CREASE, width=max(1, int(px(0.9))))

    # sound wave, clipped to the page
    layer = Image.new("RGB", (n, n), PAPER)
    ld = ImageDraw.Draw(layer)
    x0, x1, cy = 34.0, 74.0, 53.0
    r = px(WAVE_THICK) / 2
    x = x0
    while x <= x1 + 1e-6:
        t = (x - x0) / (x1 - x0)
        env = math.sin(math.pi * t) ** 0.55
        y = cy - WAVE_AMP * env * math.sin(t * WAVE_CYCLES * 2 * math.pi)
        ld.ellipse([px(x) - r, px(y) - r, px(x) + r, px(y) + r], fill=ACCENT)
        x += 0.08
    img.paste(layer, (0, 0), mask)

    return img.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "icon-1024.png"
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 1024
    render(size).save(out)
    print("wrote", out, size)
