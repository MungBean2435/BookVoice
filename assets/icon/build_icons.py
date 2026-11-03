"""Build the app icon from SVG.

assets/icon/icon.svg is the single source of truth: the page, the folded
corner and the sound wave are plain SVG paths in a 108x108 design space.
This script writes that file and rasterises every PNG the project needs
(store icon, adaptive-icon layers, legacy launcher icons) with rsvg-convert.

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

BG_TOP = "#1C2E4C"
BG_BOTTOM = "#09101C"
PAPER = "#F5EBD8"
FOLD = "#CDBA9A"
CREASE = "#B09C7C"
ACCENT = "#E9A43E"

PX0, PY0, PX1, PY1, PR = 28, 24, 80, 84, 10
CUT = 16
WAVE_X0, WAVE_X1, WAVE_CY, WAVE_AMP, WAVE_CYCLES = 34, 74, 53, 10.5, 3.4
WAVE_THICK = 5.2
SAFE_SCALE = 0.84

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


def svg_document(body: str, size=108, extra_defs="", square_bg=True,
                 clip="" ) -> str:
    bg = ""
    if square_bg:
        bg = (f'<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
              f'<stop offset="0" stop-color="{BG_TOP}"/>'
              f'<stop offset="1" stop-color="{BG_BOTTOM}"/></linearGradient>{extra_defs}</defs>'
              f'<rect width="108" height="108" fill="url(#bg)"/>')
    else:
        bg = f"<defs>{extra_defs}</defs>"
    clip_def = ""
    if clip:
        clip_def = f'<clipPath id="clip">{clip}</clipPath>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
            f'viewBox="0 0 108 108">{bg}{clip_def}{body}</svg>')


def artwork(group="") -> str:
    """Page + fold + wave, drawn in the 108x108 space."""
    inner = (
        f'<path d="{PANEL}" fill="{PAPER}"/>'
        f'<path d="{FOLD_TRI}" fill="{FOLD}"/>'
        f'<path d="{CREASE_LINE}" stroke="{CREASE}" stroke-width="0.9" fill="none"/>'
        f'<path d="{wave_path()}" stroke="{ACCENT}" stroke-width="{WAVE_THICK}" '
        f'stroke-linecap="round" stroke-linejoin="round" fill="none" clip-path="url(#pageclip)"/>'
    )
    return f'<g {group}>{inner}</g>'


def icon_svg() -> str:
    body = (f'<clipPath id="pageclip"><path d="{PANEL}"/></clipPath>'
            + artwork())
    return svg_document(body, size=1024)


def foreground_svg() -> str:
    gap = (108 - 108 * SAFE_SCALE) / 2
    body = (f'<clipPath id="pageclip"><path d="{PANEL}"/></clipPath>'
            + artwork(f'transform="translate({gap:.3f},{gap:.3f}) scale({SAFE_SCALE})"'))
    return svg_document(body, size=432, square_bg=False)


def background_svg() -> str:
    return svg_document("", size=432)


def legacy_svg(round_shape: bool) -> str:
    shape = ('<circle cx="54" cy="54" r="52"/>' if round_shape
             else '<rect x="2" y="2" width="104" height="104" rx="24" ry="24"/>')
    body = (f'<clipPath id="pageclip"><path d="{PANEL}"/></clipPath>' + artwork())
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" '
            f'viewBox="0 0 108 108">'
            f'<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0" stop-color="{BG_TOP}"/>'
            f'<stop offset="1" stop-color="{BG_BOTTOM}"/></linearGradient>'
            f'<clipPath id="shape">{shape}</clipPath></defs>'
            f'<g clip-path="url(#shape)">'
            f'<rect width="108" height="108" fill="url(#bg)"/>{body}</g></svg>')


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

    fg, bg = foreground_svg(), background_svg()
    for name, mult in DENSITIES.items():
        d = RES / f"mipmap-{name}"
        render(fg, d / "ic_launcher_foreground.png", int(108 * mult))
        render(bg, d / "ic_launcher_background.png", int(108 * mult))
        render(legacy_svg(False), d / "ic_launcher.png", int(48 * mult))
        render(legacy_svg(True), d / "ic_launcher_round.png", int(48 * mult))
        print("wrote", d)


if __name__ == "__main__":
    main()
