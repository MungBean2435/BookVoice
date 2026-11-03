"""Build the app icon from SVG.

assets/icon/icon.svg is the source of truth: a cream rounded tile with a
folded corner and an amber sound wave, drawn in a 108x108 design space.

Launcher icons are rendered as bitmaps (rsvg-convert) rather than vector
drawables so OEM launchers that ignore vector layers still get an icon.

Adaptive icon: the background layer is an opaque cream plate that fills
the whole canvas, so launchers never show their default black plate
around the art; the foreground layer carries the wave.

Requires: rsvg-convert (apt install librsvg2-bin)

Usage: python3 assets/icon/build_icons.py
"""
import math
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ICON_DIR = ROOT / "assets/icon"
RES = ROOT / "app/src/main/res"

PAPER = "#F5EBD8"
FOLD_FILL = "#CDBA9A"
CREASE = "#B09C7C"
ACCENT = "#E9A43E"
EDGE = "#E6D7BA"

TILE_R = 24
FOLD_SIZE = 24  # size of the folded corner
WAVE_CX, WAVE_CY = 54.0, 56.0
WAVE_HALF, WAVE_AMP, WAVE_CYCLES = 23.0, 11.8, 3.4
WAVE_THICK = 5.6

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def wave_path(cx=WAVE_CX, cy=WAVE_CY, half=WAVE_HALF, amp=WAVE_AMP,
              cycles=WAVE_CYCLES, step=0.25) -> str:
    pts = []
    x = cx - half
    while x <= cx + half + 1e-9:
        t = (x - (cx - half)) / (2 * half)
        env = math.sin(math.pi * t) ** 0.55
        y = cy - amp * env * math.sin(t * cycles * 2 * math.pi)
        pts.append((x, y))
        x += step
    head = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
    body = " ".join(f"L{x:.2f},{y:.2f}" for x, y in pts[1:])
    return f"{head} {body}"


TILE = (f"M{TILE_R},0 H{108 - FOLD_SIZE} L108,{FOLD_SIZE} V{108 - TILE_R} "
        f"A{TILE_R},{TILE_R} 0 0 1 {108 - TILE_R},108 H{TILE_R} "
        f"A{TILE_R},{TILE_R} 0 0 1 0,{108 - TILE_R} V{TILE_R} "
        f"A{TILE_R},{TILE_R} 0 0 1 {TILE_R},0 Z")
FOLD_TRI = f"M{108 - FOLD_SIZE},0 L108,{FOLD_SIZE} H{108 - FOLD_SIZE} Z"
CREASE_LINE = f"M{108 - FOLD_SIZE},0 L108,{FOLD_SIZE}"


def wave() -> str:
    return (f'<path d="{wave_path()}" stroke="{ACCENT}" stroke-width="{WAVE_THICK}" '
            f'stroke-linecap="round" stroke-linejoin="round" fill="none"/>')


def svg_document(body: str, px: int) -> str:
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" '
            f'viewBox="0 0 108 108">{body}</svg>')


def icon_svg(px: int = 1024) -> str:
    """Standalone icon: cream tile + fold + wave, transparent outside the tile."""
    body = (f'<path d="{TILE}" fill="{PAPER}" stroke="{EDGE}" stroke-width="0.9"/>'
            f'<path d="{FOLD_TRI}" fill="{FOLD_FILL}"/>'
            f'<path d="{CREASE_LINE}" stroke="{CREASE}" stroke-width="1" fill="none"/>'
            + wave())
    return svg_document(body, px)


def foreground_svg(px: int = 432) -> str:
    """Adaptive foreground: only the wave — the plate lives in the background."""
    return svg_document(wave(), px)


def background_svg(px: int = 432) -> str:
    """Adaptive background: opaque cream plate so no black shows through."""
    return svg_document(f'<rect width="108" height="108" fill="{PAPER}"/>', px)


def render(svg: str, out: Path, size: int) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as fh:
        fh.write(svg)
        tmp = fh.name
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), "-o", str(out), tmp],
                   check=True)
    Path(tmp).unlink()


def main() -> None:
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    (ICON_DIR / "icon.svg").write_text(icon_svg())
    print("wrote", ICON_DIR / "icon.svg")

    render(icon_svg(), ICON_DIR / "icon-1024.png", 1024)
    render(icon_svg(), ICON_DIR / "icon-512.png", 512)

    for name, mult in DENSITIES.items():
        d = RES / f"mipmap-{name}"
        render(foreground_svg(), d / "ic_launcher_foreground.png", int(108 * mult))
        render(background_svg(), d / "ic_launcher_background.png", int(108 * mult))
        render(icon_svg(), d / "ic_launcher.png", int(48 * mult))
        render(icon_svg(), d / "ic_launcher_round.png", int(48 * mult))
        print("wrote", d)


if __name__ == "__main__":
    main()
