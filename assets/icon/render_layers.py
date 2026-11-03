"""Render launcher icons as bitmaps (adaptive layers + legacy) from the same
geometry as render_icon.py, so every Android launcher can draw them.

Adaptive icons are shipped as PNG layers instead of vector drawables on
purpose: a few OEM launchers (MIUI / HyperOS among them) fail to render
vector <foreground>/<background> and end up showing a blank icon.

Usage: python3 assets/icon/render_layers.py
"""
import math
from pathlib import Path

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
SAFE_SCALE = 0.84

RES = Path(__file__).resolve().parents[2] / "app/src/main/res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def gradient(size):
    img = Image.new("RGB", (size, size), BG_BOTTOM)
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        d.line([(0, y), (size, y)], fill=tuple(
            int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)))
    return img


def page_mask(n, k, ox=0.0, oy=0.0):
    mask = Image.new("L", (n, n), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([ox + PX0 * k, oy + PY0 * k, ox + PX1 * k, oy + PY1 * k],
                         radius=PR * k, fill=255)
    md.polygon([(ox + (PX1 - CUT) * k, oy + PY0 * k - 1),
                (ox + PX1 * k + 1, oy + PY0 * k - 1),
                (ox + PX1 * k + 1, oy + (PY0 + CUT) * k)], fill=0)
    return mask


def draw_page(img, mask, n, k, ox=0.0, oy=0.0):
    d = ImageDraw.Draw(img)
    img.paste(PAPER, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.polygon([(ox + (PX1 - CUT) * k, oy + PY0 * k),
               (ox + PX1 * k, oy + (PY0 + CUT) * k),
               (ox + (PX1 - CUT) * k, oy + (PY0 + CUT) * k)], fill=FOLD)
    d.line([(ox + (PX1 - CUT) * k, oy + PY0 * k),
            (ox + PX1 * k, oy + (PY0 + CUT) * k)],
           fill=CREASE, width=max(1, int(0.9 * k)))

    layer = Image.new("RGB", (n, n), PAPER)
    ld = ImageDraw.Draw(layer)
    x0, x1, cy = 34.0, 74.0, 53.0
    r = WAVE_THICK * k / 2
    x = x0
    while x <= x1 + 1e-6:
        t = (x - x0) / (x1 - x0)
        env = math.sin(math.pi * t) ** 0.55
        y = cy - WAVE_AMP * env * math.sin(t * WAVE_CYCLES * 2 * math.pi)
        ld.ellipse([ox + x * k - r, oy + y * k - r,
                    ox + x * k + r, oy + y * k + r], fill=ACCENT)
        x += 0.08
    img.paste(layer, (0, 0), mask)


def foreground(size):
    """Adaptive foreground: transparent, content scaled into the safe zone."""
    ss = 4
    n = size * ss
    k = n / 108.0 * SAFE_SCALE
    off = (n - 108.0 * k) / 2
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    mask = page_mask(n, k, off, off)
    draw_page(img, mask, n, k, off, off)
    return img.resize((size, size), Image.LANCZOS)


def background(size):
    return gradient(size)


def legacy(size, rounded=True):
    ss = 4
    n = size * ss
    img = gradient(n).convert("RGBA")
    k = n / 108.0
    mask = page_mask(n, k)
    draw_page(img, mask, n, k)
    shape = Image.new("L", (n, n), 0)
    sd = ImageDraw.Draw(shape)
    if rounded:
        sd.rounded_rectangle([0, 0, n - 1, n - 1], radius=n * 0.22, fill=255)
    else:
        sd.ellipse([0, 0, n - 1, n - 1], fill=255)
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(img, (0, 0), shape)
    return out.resize((size, size), Image.LANCZOS)


def main():
    for name, mult in DENSITIES.items():
        d = RES / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        foreground(int(108 * mult)).save(d / "ic_launcher_foreground.png")
        background(int(108 * mult)).save(d / "ic_launcher_background.png")
        legacy(int(48 * mult)).save(d / "ic_launcher.png")
        legacy(int(48 * mult), rounded=False).save(d / "ic_launcher_round.png")
        print("wrote", d)


if __name__ == "__main__":
    main()
