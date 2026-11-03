"""Build the app icon from SVG.

assets/icon/icon.svg is the single source of truth: the page, the folded
corner and the sound wave are plain SVG paths in a 108x108 design space.
The background is transparent — the icon's silhouette is the page itself,
so no dark plate shows around it.

This script writes that file and rasterises every PNG the project needs
with rsvg-convert (apt install librsvg2-bin).

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
FOLD = "#CDBA9A"
CREASE = "#B09C7C"
ACCENT = "#E9A43E"
EDGE = "#D8C7A8"

PX0, PY0, PX1, PY1, PR = 28, 24, 80, 84, 10
CUT = 16
WAVE_X0, WAVE_X1, WAVE_CY, WAVE_AMP, WAVE_CYCLES = 34, 74, 53, 10.5, 3.4
WAVE_THICK = 5.2

# adaptive foreground must stay inside the 66dp safe circle; legacy icons can
# use more of the canvas because nothing is masked away there.
FOREGROUND_SCALE = 1.16
LEGACY_SCALE = 1.32

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def wave_path(x0=WAVE_X0, x1=WAVE_X1, cy=WAVE_CY, amp=WAVE_AMP,
              cycles=WAVE_CYCLES, step=0.25) -> str:
    pts = []
    x = x0
    while x <= x1 + 1e-9:
        t = (x - x0) / (x1 - x0)
        env = math.sin(math.pi * t) ** 0.55
        y = cy - amp * env * math.sin(t * cycles * 2 * math.pi)
        pts.append((x, y))
        x += step
    head = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
    body = " ".join(f"L{x:.2f},{y:.2f}" for x, y in pts[1:])
    return f"{head} {body}"


PANEL = (f"M38,{PY0} H63.5 L{PX1},{PY0 + CUT} V74 A{PR},{PR} 0 0 1 {PX1 - PR},84 "
         f"H38 A{PR},{PR} 0 0 1 {PX0},74 V34 A{PR},{PR} 0 0 1 38,{PY0} Z")
FOLD_TRI = f"M63.5,{PY0} L{PX1},{PY0 + CUT} H63.5 Z"
CREASE_LINE = f"M63.5,{PY0} L{PX1},{PY0 + CUT}"


def artwork(transform: str = "") -> str:
    inner = (
        f'<path d="{PANEL}" fill="{PAPER}" stroke="{EDGE}" stroke-width="0.8"/>'
        f'<path d="{FOLD_TRI}" fill="{FOLD}"/>'
        f'<path d="{CREASE_LINE}" stroke="{CREASE}" stroke-width="0.9" fill="none"/>'
        f'<path d="{wave_path()}" stroke="{ACCENT}" stroke-width="{WAVE_THICK}" '
        f'stroke-linecap="round" stroke-linejoin="round" fill="none" '
        f'clip-path="url(#pageclip)"/>'
    )
    group = f'<g transform="{transform}">{inner}</g>' if transform else inner
    return f'<clipPath id="pageclip"><path d="{PANEL}"/></clipPath>{group}'


def centered(scale: float) -> str:
    off = (108 - 108 * scale) / 2
    return f"translate({off:.3f},{off:.3f}) scale({scale})"


def svg_document(body: str, px: int) -> str:
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" '
            f'viewBox="0 0 108 108">{body}</svg>')


def icon_svg(px: int = 1024) -> str:
    return svg_document(artwork(centered(LEGACY_SCALE)), px)


def foreground_svg(px: int = 432) -> str:
    return svg_document(artwork(centered(FOREGROUND_SCALE)), px)


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
        # transparent plate: the page itself is the icon silhouette
        render(f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108"/>',
               d / "ic_launcher_background.png", int(108 * mult))
        render(icon_svg(), d / "ic_launcher.png", int(48 * mult))
        render(icon_svg(), d / "ic_launcher_round.png", int(48 * mult))
        print("wrote", d)


if __name__ == "__main__":
    main()
