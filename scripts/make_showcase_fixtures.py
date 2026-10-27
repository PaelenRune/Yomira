# -*- coding: utf-8 -*-
"""
Yomira v5.0 showcase artwork fixtures.

The README screenshots are rendered by the real Compose UI (desktop render
test) and by the real Android screens (debug showcase activity). Both need
artwork that loads offline and can be published with the repository, so this
script draws *original* genre posters and webtoon pages — no panel, character
or cover is copied from a published work. Series titles stay as catalogue
labels only.

Output (canonical, shared by the desktop test and the Android debug assets):

    art/showcase/covers/<slug>.png          720 x 1080  (2:3 poster)
    art/showcase/banners/<slug>.png        2400 x 1000  (hero banner)
    art/showcase/pages/<slug>-<n>.png       900 x 1500  (webtoon page)

Requires: Pillow, numpy.  Run:  python scripts/make_showcase_fixtures.py
"""

from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "art" / "showcase"
FONTS = Path("C:/Windows/Fonts")

SS = 2  # supersampling factor for the poster pass

# ---------------------------------------------------------------------------
# Text helpers
# ---------------------------------------------------------------------------


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def tracked(draw: ImageDraw.ImageDraw, xy: tuple[float, float], text: str,
            f: ImageFont.FreeTypeFont, tracking: float, fill, anchor_y: str = "la") -> None:
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=f, fill=fill, anchor=anchor_y)
        x += f.getlength(ch) + tracking


def tracked_width(text: str, f: ImageFont.FreeTypeFont, tracking: float) -> float:
    return sum(f.getlength(c) for c in text) + tracking * max(0, len(text) - 1)


def wrap(draw: ImageDraw.ImageDraw, text: str, f: ImageFont.FreeTypeFont,
         max_width: float) -> list[str]:
    """Word wrap, falling back to hard breaks for a single word that is too wide."""
    words, lines, current = text.split(), [], ""
    for word in words:
        probe = f"{current} {word}".strip()
        if draw.textlength(probe, font=f) <= max_width or not current:
            current = probe
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    out: list[str] = []
    for line in lines:
        while draw.textlength(line, font=f) > max_width and len(line) > 1:
            cut = len(line)
            while cut > 1 and draw.textlength(line[:cut], font=f) > max_width:
                cut -= 1
            out.append(line[:cut])
            line = line[cut:]
        out.append(line)
    return out


# ---------------------------------------------------------------------------
# Raster helpers
# ---------------------------------------------------------------------------


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gradient(size: tuple[int, int], stops: list[tuple[float, tuple[int, int, int]]],
             angle: float = 90.0) -> Image.Image:
    """Multi-stop linear gradient. angle 90 = top→bottom, 0 = left→right."""
    w, h = size
    ys, xs = np.mgrid[0:h, 0:w]
    rad = math.radians(angle)
    proj = xs * math.cos(rad) + ys * math.sin(rad)
    proj = (proj - proj.min()) / max(1e-6, proj.max() - proj.min())
    out = np.zeros((h, w, 3), dtype=np.float32)
    stops = sorted(stops)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        mask = (proj >= p0) & (proj <= p1)
        t = np.clip((proj - p0) / max(1e-6, p1 - p0), 0, 1)
        for ch in range(3):
            out[..., ch] = np.where(mask, c0[ch] + (c1[ch] - c0[ch]) * t, out[..., ch])
    out = np.clip(out, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGB").convert("RGBA")


def radial_layer(size: tuple[int, int], center: tuple[float, float], radius: float,
                 color: tuple[int, int, int], alpha: int = 120) -> Image.Image:
    w, h = size
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    cx, cy = center
    steps = 28
    for i in range(steps, 0, -1):
        t = i / steps
        r = radius * t
        a = int(alpha * (1 - t) ** 1.7)
        draw.ellipse((cx - r, cy - r * 0.92, cx + r, cy + r * 0.92), fill=color + (a,))
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.12))


def noise_layer(size: tuple[int, int], seed: int, strength: float = 10.0) -> Image.Image:
    rng = np.random.default_rng(seed)
    w, h = size
    noise = rng.normal(0, strength, (h, w, 1)).repeat(3, axis=2)
    base = np.full((h, w, 3), 128, dtype=np.float32)
    arr = np.clip(base + noise, 0, 255).astype(np.uint8)
    img = Image.fromarray(arr, "RGB").convert("RGBA")
    img.putalpha(26)
    return img


def vignette(size: tuple[int, int], strength: int = 165) -> Image.Image:
    w, h = size
    ys, xs = np.mgrid[0:h, 0:w]
    dx = (xs - w / 2) / (w / 2)
    dy = (ys - h / 2) / (h / 2)
    d = np.sqrt(dx ** 2 + dy ** 2) / 1.42
    alpha = np.clip((d ** 2.2) * strength, 0, strength).astype(np.uint8)
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    layer.putalpha(Image.fromarray(alpha, "L"))
    return layer


def halftone(size: tuple[int, int], color: tuple[int, int, int], step: int = 16,
             alpha: int = 60, angle: int = 0) -> Image.Image:
    w, h = size
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for y in range(-step, h + step, step):
        for x in range(-step, w + step, step):
            draw.ellipse((x - 2, y - 2, x + 2, y + 2), fill=color + (alpha,))
    if angle:
        layer = layer.rotate(angle, resample=Image.BICUBIC, expand=False)
    return layer


def paste(base: Image.Image, layer: Image.Image, pos=(0, 0)) -> None:
    base.alpha_composite(layer, dest=(int(pos[0]), int(pos[1])))


# ---------------------------------------------------------------------------
# Motifs (all original, drawn from primitives)
# ---------------------------------------------------------------------------


def motif_ocean(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, horizon = (x0 + x1) / 2, y0 + (y1 - y0) * 0.46
    d.ellipse((cx - 210, horizon - 250, cx + 210, horizon + 170), fill=(255, 214, 130, 235))
    d.ellipse((cx - 150, horizon - 190, cx + 150, horizon + 110), fill=(255, 244, 200, 255))
    for i, (yy, col) in enumerate([
        (0.50, (32, 92, 120)), (0.58, (22, 74, 104)), (0.68, (15, 56, 86)), (0.80, (10, 40, 66)),
    ]):
        base_y = y0 + (y1 - y0) * yy
        pts = [(x0, y1), (x0, base_y)]
        for sx in range(x0, x1 + 24, 24):
            t = (sx - x0) / max(1, x1 - x0)
            pts.append((sx, base_y + math.sin(t * math.pi * (2 + i) + i) * (18 + i * 9)))
        pts += [(x1, y1)]
        d.polygon(pts, fill=col + (250,))
    # sails
    bx, by = cx - 150, horizon + 60
    d.polygon([(bx, by), (bx, by - 96), (bx + 54, by - 8)], fill=(245, 241, 230, 245))
    d.polygon([(bx + 64, by), (bx + 64, by - 112), (bx + 124, by - 6)], fill=(250, 246, 234, 240))
    d.rectangle((bx - 34, by - 2, bx + 132, by + 12), fill=(38, 28, 24, 255))
    d.line((bx - 6, by - 130, bx + 6, by + 12), fill=(30, 24, 22, 255), width=5)
    for sx, sy, s in [(cx + 300, horizon - 260, 22), (cx + 360, horizon - 226, 14), (cx - 330, horizon - 290, 17)]:
        d.arc((sx - s * 2, sy - s, sx + s * 2, sy + s), 200, 340, fill=(255, 240, 210, 220), width=4)


def motif_eclipse(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.40
    r = (x1 - x0) * 0.30
    for i in range(9, 0, -1):
        t = i / 9
        rr = r * (1 + t * 0.42)
        d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr),
                  outline=(198 + int(30 * (1 - t)), 40 + int(30 * (1 - t)), 46, int(70 * (1 - t))),
                  width=int(2 + 8 * (1 - t)))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(214, 62, 54, 255))
    d.ellipse((cx - r * 0.94, cy - r * 0.94, cx + r * 0.94, cy + r * 0.94), fill=(12, 9, 12, 255))
    # jagged brands cutting the sun
    rng = random.Random(7)
    for k in range(7):
        pts = []
        ang = rng.uniform(0, math.tau)
        for i in range(6):
            a = ang + i * 0.055
            rr = r * (0.55 + 0.5 * (i / 5))
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
        d.line(pts, fill=(10, 8, 11, 255), width=9)
    # great sword
    sx, sy = cx, y1 + 20
    d.polygon([(sx - 26, sy), (sx - 20, sy - 560), (sx, sy - 620), (sx + 20, sy - 560), (sx + 26, sy)], fill=(150, 152, 162, 255))
    d.line((sx - 4, sy - 40, sx - 4, sy - 585), fill=(228, 230, 240, 190), width=4)
    d.rectangle((sx - 96, sy - 70, sx + 96, sy - 42), fill=(38, 34, 44, 255))
    d.rectangle((sx - 16, sy - 42, sx + 16, sy + 80), fill=(44, 38, 48, 255))


def motif_ink(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    rng = random.Random(21)
    for layer, (yy, col, alpha) in enumerate([
        (0.72, (62, 74, 92), 200), (0.60, (44, 56, 76), 215), (0.47, (26, 36, 55), 235),
    ]):
        base_y = y0 + (y1 - y0) * yy
        pts = [(x0, y1), (x0, base_y)]
        x = x0
        while x < x1 + 40:
            peak = base_y - rng.uniform(60, 240) * (1 - layer * 0.16)
            pts += [(x, peak), (x + rng.uniform(40, 90), base_y - rng.uniform(0, 50))]
            x += rng.uniform(80, 140)
        pts += [(x1, y1)]
        d.polygon(pts, fill=col + (alpha,))
    d.ellipse((x0 + (x1 - x0) * 0.66, y0 + (y1 - y0) * 0.16,
               x0 + (x1 - x0) * 0.66 + 150, y0 + (y1 - y0) * 0.16 + 150), fill=(196, 74, 60, 210))
    # lone wanderer
    fx, fy = x0 + (x1 - x0) * 0.30, y0 + (y1 - y0) * 0.80
    d.line((fx, fy + 150, fx - 10, fy), fill=(18, 20, 26, 255), width=9)
    d.line((fx - 2, fy + 46, fx - 46, fy + 92), fill=(18, 20, 26, 255), width=7)
    d.ellipse((fx - 18, fy - 70, fx + 14, fy - 36), fill=(18, 20, 26, 255))
    d.line((fx - 10, fy + 150, fx - 46, fy + 214), fill=(18, 20, 26, 255), width=8)
    d.line((fx - 10, fy + 150, fx + 30, fy + 216), fill=(18, 20, 26, 255), width=8)


def motif_gate(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.52
    for i in range(16, 0, -1):
        t = i / 16
        rw, rh = 250 * t, 420 * t
        d.ellipse((cx - rw, cy - rh, cx + rw, cy + rh),
                  outline=(120 + int(90 * (1 - t)), 60 + int(90 * (1 - t)), 255, int(170 * (1 - t))),
                  width=int(2 + 5 * (1 - t)))
    d.ellipse((cx - 240, cy - 410, cx + 240, cy + 410), fill=(38, 22, 78, 255))
    d.ellipse((cx - 200, cy - 350, cx + 200, cy + 350), fill=(94, 62, 190, 255))
    d.ellipse((cx - 140, cy - 260, cx + 140, cy + 260), fill=(178, 150, 255, 235))
    d.ellipse((cx - 70, cy - 140, cx + 70, cy + 140), fill=(240, 236, 255, 255))
    rng = random.Random(11)
    for _ in range(60):
        px, py = cx + rng.uniform(-260, 260), cy + rng.uniform(-420, 300)
        s = rng.uniform(1, 4)
        d.ellipse((px - s, py - s, px + s, py + s), fill=(200, 180, 255, int(rng.uniform(60, 210))))
    # kneeling silhouette
    d.ellipse((cx - 30, y1 - 320, cx + 34, y1 - 250), fill=(8, 8, 14, 255))
    d.polygon([(cx - 62, y1 - 40), (cx - 40, y1 - 252), (cx + 44, y1 - 252), (cx + 70, y1 - 40)], fill=(8, 8, 14, 255))
    d.line((cx + 70, y1 - 40, cx + 128, y1 - 8), fill=(8, 8, 14, 255), width=16)


def motif_saw(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.44
    for i in range(22, 0, -1):
        t = i / 22
        d.polygon([(cx, cy), (cx - 620 * t, y0 + 30), (cx - 620 * t, y0 + 30 + 40 * (1 - t))],
                  fill=(255, 120, 30, int(16 * (1 - t))))
    # diagonal split
    d.polygon([(x0, y1), (x1, y1), (x1, y0 + (y1 - y0) * 0.30)], fill=(12, 12, 14, 255))
    # blade teeth
    blade = [(cx - 160, cy + 190), (cx + 110, cy - 190), (cx + 150, cy - 150), (cx - 118, cy + 232)]
    d.polygon(blade, fill=(206, 210, 218, 255))
    for i in range(9):
        t = i / 8
        px = cx - 140 + t * 250
        py = cy + 170 - t * 330
        d.polygon([(px, py), (px + 44, py - 10), (px + 34, py - 52)], fill=(238, 240, 248, 255))
    d.line((cx - 150, cy + 200, cx + 112, cy - 188), fill=(120, 124, 136, 200), width=5)
    d.rectangle((cx - 200, cy + 180, cx - 100, cy + 240), fill=(40, 36, 40, 255))
    for i in range(7):
        d.line((x0 + 40 + i * 74, y1 - 20, x0 + 120 + i * 74, y1 - 240), fill=(255, 152, 60, 90), width=3)


def motif_dusk(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    for i, yy in enumerate([0.34, 0.41, 0.47]):
        y = y0 + (y1 - y0) * yy
        d.ellipse((x0 + 60 + i * 70, y, x0 + 520 + i * 70, y + 74), fill=(255, 245, 250, 70))
    d.ellipse((x0 + 40, y0 + 90, x0 + 250, y0 + 300), fill=(255, 252, 235, 120))
    # layered hills behind the road so the lower two-thirds is not empty
    rng = random.Random(41)
    for k, (yy, col) in enumerate([(0.56, (150, 136, 176)), (0.63, (116, 104, 146)), (0.70, (86, 76, 116))]):
        ridge = y0 + (y1 - y0) * yy
        pts = [(x0, y1)]
        for sx in range(x0, x1 + 30, 30):
            t = (sx - x0) / max(1, x1 - x0)
            pts.append((sx, ridge + math.sin(t * 5 + k * 1.7) * 34 + math.sin(t * 12 + k) * 12))
        pts.append((x1, y1))
        d.polygon(pts, fill=col + (235,))
    # road converging on the horizon
    hz = y0 + (y1 - y0) * 0.66
    d.polygon([(x0 + 40, y1), (x0 + (x1 - x0) * 0.44, hz), (x0 + (x1 - x0) * 0.56, hz), (x1 - 40, y1)],
              fill=(96, 86, 120, 220))
    d.polygon([(x0 + 40, y1), (x0 + (x1 - x0) * 0.39, hz), (x0 + (x1 - x0) * 0.44, hz)],
              fill=(230, 224, 236, 60))
    for i in range(7):
        t = (i + 1) / 8
        y = hz + (y1 - hz) * (t ** 1.5)
        half = 12 + (x1 - x0) * 0.30 * t
        d.line((x0 + (x1 - x0) * 0.50 - half, y, x0 + (x1 - x0) * 0.50 + half, y),
               fill=(214, 206, 226, 110), width=max(2, int(6 * t)))
    px, py = x0 + (x1 - x0) * 0.50, hz + 30
    d.ellipse((px - 9, py - 40, px + 9, py - 22), fill=(24, 20, 30, 255))
    d.polygon([(px - 13, py + 60), (px - 8, py - 26), (px + 8, py - 26), (px + 13, py + 60)],
              fill=(24, 20, 30, 255))
    for sx, sy, s in [(x0 + 120, y0 + 360, 12), (x0 + 180, y0 + 330, 8), (x1 - 160, y0 + 300, 10)]:
        d.arc((sx - s * 3, sy - s, sx + s * 3, sy + s), 200, 340, fill=(60, 50, 70, 220), width=3)


def motif_nordic(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    rng = random.Random(4)
    for k, col in enumerate([(90, 240, 190), (110, 180, 235), (180, 140, 240)]):
        pts_top, pts_bot = [], []
        for sx in range(x0, x1 + 20, 20):
            t = (sx - x0) / max(1, x1 - x0)
            base = y0 + (y1 - y0) * (0.18 + 0.10 * k)
            wave = math.sin(t * 6.2 + k * 1.4) * 54 + math.sin(t * 13 + k) * 18
            pts_top.append((sx, base + wave))
            pts_bot.append((sx, base + wave + 26 + 10 * math.sin(t * 9)))
        d.polygon(pts_top + pts_bot[::-1], fill=col + (60,))
    # sea
    hz = y0 + (y1 - y0) * 0.56
    d.rectangle((x0, hz, x1, y1), fill=(14, 38, 58, 255))
    for i in range(24):
        y = hz + 16 + i * 16
        d.line((x0 + rng.randint(0, 60), y, x1 - rng.randint(0, 60), y), fill=(40, 90, 122, 120), width=2)
    # longship
    cx, cy = (x0 + x1) / 2 - 40, hz + 120
    d.polygon([(cx - 190, cy), (cx + 190, cy), (cx + 130, cy + 54), (cx - 130, cy + 54)], fill=(28, 22, 20, 255))
    d.line((cx - 60, cy, cx - 60, cy - 210), fill=(36, 28, 24, 255), width=10)
    d.polygon([(cx - 56, cy - 200), (cx + 132, cy - 40), (cx - 56, cy - 30)], fill=(226, 72, 62, 235))
    d.polygon([(cx - 64, cy - 200), (cx - 240, cy - 46), (cx - 64, cy - 36)], fill=(206, 62, 56, 225))
    d.line((cx + 150, cy - 6, cx + 190, cy - 74), fill=(28, 22, 20, 255), width=8)


def motif_eye(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    rng = random.Random(33)
    # city
    x = x0
    while x < x1:
        bw = rng.randint(40, 96)
        bh = rng.randint(120, 340)
        by = y1 - bh
        d.rectangle((x, by, x + bw, y1), fill=(18 + rng.randint(0, 12), 26, 34, 255))
        for wy in range(by + 18, y1 - 16, 30):
            for wx in range(x + 12, x + bw - 12, 26):
                if rng.random() > 0.55:
                    d.rectangle((wx, wy, wx + 8, wy + 10), fill=(140, 210, 230, rng.randint(70, 190)))
        x += bw + 10
    # eye
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.34
    d.ellipse((cx - 300, cy - 190, cx + 300, cy + 190), fill=(232, 240, 244, 235))
    d.ellipse((cx - 190, cy - 190, cx + 190, cy + 190), fill=(50, 120, 150, 255))
    d.ellipse((cx - 120, cy - 120, cx + 120, cy + 120), fill=(16, 52, 70, 255))
    d.ellipse((cx - 46, cy - 46, cx + 46, cy + 46), fill=(6, 12, 16, 255))
    d.ellipse((cx - 88, cy - 90, cx - 30, cy - 36), fill=(240, 250, 252, 230))
    d.line((cx - 300, cy, cx + 300, cy), fill=(10, 18, 22, 255), width=3)
    # rain
    for _ in range(90):
        rx, ry = rng.uniform(x0, x1), rng.uniform(y0, y1)
        d.line((rx, ry, rx - 6, ry + 34), fill=(170, 220, 235, rng.randint(30, 90)), width=2)


def motif_retro(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.40
    for i in range(24):
        a0 = i * 15
        d.pieslice((cx - 900, cy - 900, cx + 900, cy + 900), a0, a0 + 7.5, fill=(206, 54, 46, 46))
    d.ellipse((cx - 250, cy - 250, cx + 250, cy + 250), fill=(206, 54, 46, 255))
    d.ellipse((cx - 210, cy - 210, cx + 210, cy + 210), fill=(240, 226, 196, 255))
    # crowd silhouettes
    rng = random.Random(9)
    for i in range(16):
        px = x0 + 40 + i * ((x1 - x0 - 80) / 15)
        ph = rng.randint(110, 190)
        d.ellipse((px - 24, y1 - ph - 90, px + 24, y1 - ph - 42), fill=(26, 26, 32, 255))
        d.rectangle((px - 34, y1 - ph - 40, px + 34, y1), fill=(26, 26, 32, 255))


def motif_elf(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    rng = random.Random(15)
    for _ in range(180):
        sx, sy = rng.uniform(x0, x1), rng.uniform(y0, y0 + (y1 - y0) * 0.62)
        s = rng.uniform(0.6, 2.6)
        d.ellipse((sx - s, sy - s, sx + s, sy + s), fill=(240, 236, 255, rng.randint(60, 235)))
    cx = (x0 + x1) / 2
    d.ellipse((cx - 300, y0 + 90, cx + 190, y0 + 580), fill=(240, 236, 255, 235))
    d.ellipse((cx - 250, y0 + 140, cx + 140, y0 + 530), fill=(50, 40, 78, 255))
    # meadow
    hz = y0 + (y1 - y0) * 0.66
    d.rectangle((x0, hz, x1, y1), fill=(38, 46, 62, 255))
    for i in range(26):
        gx = x0 + i * ((x1 - x0) / 25)
        gh = rng.randint(60, 180)
        d.line((gx, hz + 20, gx + rng.randint(-14, 14), hz - gh), fill=(52, 62, 80, 255), width=4)
    # staff
    sx = cx + 150
    d.line((sx, y1 - 30, sx - 26, y0 + 220), fill=(214, 200, 176, 255), width=10)
    d.ellipse((sx - 60, y0 + 170, sx + 12, y0 + 242), fill=(226, 186, 255, 255))
    for i in range(3):
        d.arc((sx - 90 - i * 12, y0 + 140 - i * 12, sx + 42 + i * 12, y0 + 272 + i * 12),
              200, 340, fill=(226, 186, 255, 150), width=3)


def motif_sigil(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.44
    for r, width, alpha in [(330, 3, 150), (280, 2, 110), (210, 4, 190)]:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(120, 240, 255, alpha), width=width)
    for i in range(12):
        a = i * math.tau / 12
        d.line((cx + math.cos(a) * 210, cy + math.sin(a) * 210,
                cx + math.cos(a) * 330, cy + math.sin(a) * 330), fill=(120, 240, 255, 120), width=2)
    d.polygon([(cx, cy - 190), (cx + 170, cy + 90), (cx - 170, cy + 90)], outline=(190, 250, 255, 210), width=5)
    d.polygon([(cx, cy + 170), (cx + 150, cy - 60), (cx - 150, cy - 60)], outline=(90, 180, 255, 170), width=4)
    d.ellipse((cx - 70, cy - 70, cx + 70, cy + 70), fill=(140, 250, 255, 90))
    d.ellipse((cx - 26, cy - 26, cx + 26, cy + 26), fill=(230, 255, 255, 235))
    for i in range(9):
        yy = y1 - 40 - i * 34
        d.line((x0 + 30, yy, x1 - 30, yy), fill=(30, 60, 80, 140), width=2)


def motif_striker(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.46
    rng = random.Random(6)
    for i in range(40):
        yy = y0 + rng.uniform(0, 1) * (y1 - y0)
        d.line((x0, yy, x1, yy + rng.uniform(-40, 40)), fill=(90, 200, 255, rng.randint(12, 60)), width=rng.choice([1, 2, 3]))
    d.polygon([(cx - 200, cy + 210), (cx + 210, cy - 190), (cx + 130, cy - 240), (cx - 250, cy + 170)],
              fill=(20, 30, 60, 230))
    # football geometry
    d.ellipse((cx - 140, cy - 140, cx + 140, cy + 140), fill=(240, 250, 255, 245))
    for i in range(5):
        a = -math.pi / 2 + i * math.tau / 5
        px, py = cx + math.cos(a) * 72, cy + math.sin(a) * 72
        d.polygon([(px, py), (px + math.cos(a + 2.2) * 46, py + math.sin(a + 2.2) * 46),
                   (px + math.cos(a - 2.2) * 46, py + math.sin(a - 2.2) * 46)], fill=(18, 34, 60, 255))
    d.polygon([(cx, cy - 46), (cx + 42, cy - 12), (cx + 26, cy + 40), (cx - 26, cy + 40), (cx - 42, cy - 12)],
              fill=(18, 34, 60, 255))
    d.ellipse((cx - 200, y1 - 240, cx + 210, y1 + 60), outline=(90, 230, 255, 90), width=8)


def motif_occult(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    step = 90
    for row, y in enumerate(range(y0, y1 + step, step)):
        for col, x in enumerate(range(x0, x1 + step, step)):
            if (row + col) % 2 == 0:
                d.polygon([(x + step / 2, y), (x + step, y + step / 2), (x + step / 2, y + step), (x, y + step / 2)],
                          fill=(26, 10, 30, 120))
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.36
    for i in range(10, 0, -1):
        t = i / 10
        d.ellipse((cx - 250 * t, cy - 130 * t, cx + 250 * t, cy + 130 * t),
                  fill=(255, 80, 180, int(20 * (1 - t))))
    d.ellipse((cx - 210, cy - 40, cx + 210, cy + 84), fill=(210, 220, 235, 245))
    d.ellipse((cx - 210, cy - 84, cx + 210, cy + 40), fill=(150, 170, 200, 255))
    for i in range(5):
        d.ellipse((cx - 60 + i * 26, cy - 130, cx - 30 + i * 26, cy - 60), fill=(255, 230, 120, 170))
    d.line((cx - 210, cy, cx + 210, cy), fill=(60, 70, 96, 255), width=4)
    d.ellipse((cx - 30, y1 - 300, cx + 30, y1 - 240), fill=(14, 10, 18, 255))
    d.polygon([(cx - 50, y1), (cx - 34, y1 - 250), (cx + 34, y1 - 250), (cx + 54, y1)], fill=(14, 10, 18, 255))


def motif_star(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.34
    for i in range(26):
        a = i * math.tau / 26
        d.polygon([(cx, cy),
                   (cx + math.cos(a) * 900, cy + math.sin(a) * 900),
                   (cx + math.cos(a + 0.055) * 900, cy + math.sin(a + 0.055) * 900)],
                  fill=(255, 240, 200, 22))
    def star(px, py, r, fill):
        pts = []
        for i in range(10):
            a = -math.pi / 2 + i * math.pi / 5
            rr = r if i % 2 == 0 else r * 0.42
            pts.append((px + math.cos(a) * rr, py + math.sin(a) * rr))
        d.polygon(pts, fill=fill)
    star(cx, cy, 210, (255, 246, 214, 245))
    star(cx, cy, 120, (255, 214, 120, 255))
    star(x0 + 150, y0 + 240, 46, (255, 250, 230, 220))
    star(x1 - 130, y0 + 380, 30, (255, 250, 230, 190))
    # stage light beams
    for i in range(5):
        bx = x0 + 60 + i * ((x1 - x0) / 5)
        d.polygon([(bx, y1), (bx + 120, y1), (bx + 260, y0 + 500)], fill=(255, 240, 210, 16))
    d.rectangle((x0, y1 - 120, x1, y1), fill=(28, 20, 34, 220))


def motif_hitman(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.38
    # shop shelving behind the target so the pale palette still reads as a scene
    rng = random.Random(55)
    for i in range(7):
        sy = y0 + 70 + i * 52
        d.line((x0 + 10, sy, x1 - 10, sy), fill=(90, 120, 118, 70), width=16)
        for j in range(14):
            bx = x0 + 26 + j * ((x1 - x0 - 52) / 13)
            bh = rng.randint(14, 30)
            d.rectangle((bx, sy - bh, bx + rng.randint(10, 22), sy), fill=(70, 108, 104, 90))
    for r, alpha in [(300, 200), (210, 170), (120, 150), (54, 235)]:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(32, 44, 52, alpha), width=max(4, int(r * 0.09)))
    d.line((cx - 340, cy, cx + 340, cy), fill=(32, 44, 52, 120), width=3)
    d.line((cx, cy - 340, cx, cy + 340), fill=(32, 44, 52, 120), width=3)
    for i in range(6):
        d.line((x0 + 20, y0 + 120 + i * 46, x1 - 20, y0 + 100 + i * 46), fill=(32, 44, 52, 34), width=18)
    d.ellipse((cx - 54, cy - 54, cx + 54, cy + 54), fill=(206, 52, 48, 255))
    # retired legend in an apron, back turned to the counter
    sx, sy = cx - 190, y1 - 20
    d.ellipse((sx - 34, sy - 340, sx + 34, sy - 268), fill=(30, 42, 46, 255))
    d.polygon([(sx - 66, sy), (sx - 46, sy - 272), (sx + 46, sy - 272), (sx + 66, sy)], fill=(30, 42, 46, 255))
    d.polygon([(sx - 50, sy - 120), (sx - 40, sy - 274), (sx + 40, sy - 274), (sx + 50, sy - 60)],
              fill=(226, 234, 230, 235))
    d.line((sx + 62, sy - 250, sx + 120, sy - 300), fill=(30, 42, 46, 255), width=12)


def motif_kaiju(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    rng = random.Random(19)
    # huge silhouette
    cx = (x0 + x1) / 2 + 60
    d.polygon([(cx - 300, y1), (cx - 220, y0 + 260), (cx - 120, y0 + 120), (cx - 20, y0 + 300),
               (cx + 120, y0 + 140), (cx + 220, y0 + 320), (cx + 300, y1)], fill=(6, 22, 26, 255))
    for i in range(9):
        d.ellipse((cx - 90 + i * 20, y0 + 330 + (i % 3) * 22, cx - 74 + i * 20, y0 + 348 + (i % 3) * 22),
                  fill=(255, 210, 90, 200))
    # city in front
    x = x0
    while x < x1:
        bw = rng.randint(36, 80)
        bh = rng.randint(90, 220)
        d.rectangle((x, y1 - bh, x + bw, y1), fill=(10, 16, 22, 255))
        x += bw + 8
    # hazard stripes
    for i in range(6):
        d.polygon([(x0, y1 - 40 - i * 16), (x0 + 90, y1 - 56 - i * 16),
                   (x0 + 120, y1 - 40 - i * 16), (x0 + 30, y1 - 24 - i * 16)], fill=(230, 190, 60, 200))


def motif_garden(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.36
    rng = random.Random(23)
    for ring, count, radius in [(0, 16, 130), (1, 24, 210), (2, 32, 290)]:
        for i in range(count):
            a = i * math.tau / count + ring * 0.2
            px, py = cx + math.cos(a) * radius, cy + math.sin(a) * radius * 0.92
            r = 26 - ring * 6
            for k in range(5):
                aa = k * math.tau / 5
                d.ellipse((px + math.cos(aa) * r * 0.5 - r * 0.5, py + math.sin(aa) * r * 0.5 - r * 0.5,
                           px + math.cos(aa) * r * 0.5 + r * 0.5, py + math.sin(aa) * r * 0.5 + r * 0.5),
                          fill=(226, 74, 88, 210 - ring * 40))
            d.ellipse((px - 5, py - 5, px + 5, py + 5), fill=(255, 230, 160, 230))
    d.ellipse((cx - 66, cy - 66, cx + 66, cy + 66), fill=(18, 24, 22, 240))
    d.line((cx, cy + 40, cx - 8, y1), fill=(198, 202, 210, 255), width=14)
    d.polygon([(cx, y1 - 40), (cx - 26, y1 - 10), (cx + 26, y1 - 10)], fill=(214, 218, 226, 255))
    for i in range(40):
        vx, vy = rng.uniform(x0, x1), rng.uniform(cy, y1)
        d.line((vx, vy, vx + rng.uniform(-16, 16), vy + rng.uniform(20, 60)), fill=(28, 46, 34, 160), width=2)


def motif_atelier(d: ImageDraw.ImageDraw, w: int, h: int, art: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = art
    cx = (x0 + x1) / 2
    # hat silhouette
    d.ellipse((cx - 300, y0 + 330, cx + 300, y0 + 430), fill=(18, 24, 48, 250))
    d.polygon([(cx - 130, y0 + 380), (cx - 90, y0 + 130), (cx + 10, y0 + 60),
               (cx + 120, y0 + 120), (cx + 150, y0 + 380)], fill=(18, 24, 48, 250))
    d.polygon([(cx - 96, y0 + 340), (cx - 70, y0 + 160), (cx + 8, y0 + 100),
               (cx + 90, y0 + 150), (cx + 110, y0 + 340)], fill=(42, 54, 96, 255))
    d.rectangle((cx - 8, y0 + 210, cx + 30, y0 + 240), fill=(226, 196, 120, 255))
    rng = random.Random(31)
    for _ in range(120):
        sx, sy = rng.uniform(x0, x1), rng.uniform(y0, y1)
        s = rng.uniform(0.8, 3)
        d.ellipse((sx - s, sy - s, sx + s, sy + s), fill=(210, 230, 255, rng.randint(50, 220)))
    for i in range(3):
        d.arc((cx - 260 - i * 30, y0 + 120 - i * 20, cx + 260 + i * 30, y0 + 660 + i * 20),
              20, 160, fill=(120, 160, 255, 60), width=2)


MOTIFS = {
    "ocean": motif_ocean,
    "eclipse": motif_eclipse,
    "ink": motif_ink,
    "gate": motif_gate,
    "saw": motif_saw,
    "dusk": motif_dusk,
    "nordic": motif_nordic,
    "eye": motif_eye,
    "retro": motif_retro,
    "elf": motif_elf,
    "sigil": motif_sigil,
    "striker": motif_striker,
    "occult": motif_occult,
    "star": motif_star,
    "hitman": motif_hitman,
    "kaiju": motif_kaiju,
    "garden": motif_garden,
    "atelier": motif_atelier,
}

# ---------------------------------------------------------------------------
# Catalogue of fixture posters (titles are catalogue labels; art is original)
# ---------------------------------------------------------------------------

SERIES = [
    # slug, title, author, kicker, motif, sky stops, ink
    ("one-piece", "ONE PIECE", "Eiichiro Oda", "GRAND LINE · ADVENTURE", "ocean",
     [(0.0, (255, 186, 94)), (0.42, (255, 226, 160)), (0.62, (86, 176, 190)), (1.0, (10, 44, 70))],
     (255, 250, 240)),
    ("berserk", "BERSERK", "Kentaro Miura", "DARK FANTASY · ECLIPSE", "eclipse",
     [(0.0, (28, 10, 14)), (0.55, (78, 18, 20)), (1.0, (8, 7, 10))],
     (240, 232, 228)),
    ("vagabond", "VAGABOND", "Takehiko Inoue", "INK · THE WAY", "ink",
     [(0.0, (244, 238, 226)), (0.6, (222, 214, 198)), (1.0, (196, 190, 176))],
     (26, 28, 34)),
    ("solo-leveling", "SOLO LEVELING", "Chugong · DUBU", "WEBTOON · THE GATE", "gate",
     [(0.0, (12, 10, 30)), (0.5, (36, 22, 74)), (1.0, (8, 8, 18))],
     (238, 236, 255)),
    ("chainsaw-man", "CHAINSAW MAN", "Tatsuki Fujimoto", "ACTION · DEVIL HOUR", "saw",
     [(0.0, (255, 168, 64)), (0.5, (238, 96, 38)), (1.0, (26, 12, 12))],
     (255, 242, 226)),
    ("oyasumi-punpun", "OYASUMI PUNPUN", "Inio Asano", "SLICE OF LIFE · DUSK", "dusk",
     [(0.0, (255, 214, 198)), (0.45, (250, 190, 208)), (0.7, (196, 170, 214)), (1.0, (70, 62, 92))],
     (34, 28, 40)),
    ("vinland-saga", "VINLAND SAGA", "Makoto Yukimura", "HISTORICAL · NORTH SEA", "nordic",
     [(0.0, (10, 22, 42)), (0.55, (16, 44, 66)), (1.0, (8, 18, 30))],
     (232, 244, 250)),
    ("monster", "MONSTER", "Naoki Urasawa", "THRILLER · COLD CASE", "eye",
     [(0.0, (196, 216, 224)), (0.4, (108, 148, 164)), (1.0, (16, 28, 36))],
     (16, 24, 30)),
    ("20th-century-boys", "20TH CENTURY BOYS", "Naoki Urasawa", "MYSTERY · 1969", "retro",
     [(0.0, (244, 232, 204)), (0.6, (236, 220, 188)), (1.0, (214, 198, 168))],
     (32, 30, 34)),
    ("frieren", "FRIEREN", "Kanehito Yamada", "FANTASY · AFTER JOURNEY", "elf",
     [(0.0, (24, 18, 52)), (0.55, (66, 44, 104)), (0.7, (108, 74, 140)), (1.0, (30, 34, 52))],
     (246, 240, 255)),
    ("jujutsu-kaisen", "JUJUTSU KAISEN", "Gege Akutami", "CURSES · TECHNIQUE", "sigil",
     [(0.0, (8, 14, 18)), (0.5, (14, 32, 40)), (1.0, (6, 10, 14))],
     (224, 248, 255)),
    ("blue-lock", "BLUE LOCK", "Muneyuki Kaneshiro", "SPORTS · EGO", "striker",
     [(0.0, (8, 30, 72)), (0.5, (14, 74, 140)), (1.0, (6, 14, 34))],
     (236, 250, 255)),
    ("dandadan", "DANDADAN", "Yukinobu Tatsu", "OCCULT · CHAOS", "occult",
     [(0.0, (38, 8, 34)), (0.5, (78, 16, 60)), (1.0, (18, 8, 20))],
     (255, 240, 250)),
    ("oshi-no-ko", "OSHI NO KO", "Aka Akasaka", "DRAMA · STAGE", "star",
     [(0.0, (28, 16, 46)), (0.5, (64, 30, 82)), (1.0, (18, 12, 28))],
     (255, 250, 236)),
    ("sakamoto-days", "SAKAMOTO DAYS", "Yuto Suzuki", "ACTION · RETIRED", "hitman",
     [(0.0, (222, 240, 230)), (0.6, (196, 222, 210)), (1.0, (160, 192, 184))],
     (28, 40, 44)),
    ("kaiju-no-8", "KAIJU NO. 8", "Naoya Matsumoto", "DEFENSE · TOKYO", "kaiju",
     [(0.0, (12, 44, 48)), (0.5, (16, 68, 70)), (1.0, (6, 22, 26))],
     (236, 250, 246)),
    ("hells-paradise", "HELL'S PARADISE", "Yuji Kaku", "EDO · THE ISLAND", "garden",
     [(0.0, (30, 12, 18)), (0.5, (64, 20, 28)), (1.0, (16, 10, 14))],
     (252, 238, 228)),
    ("witch-hat-atelier", "WITCH HAT ATELIER", "Kamome Shirahama", "MAGIC · APPRENTICE", "atelier",
     [(0.0, (10, 18, 44)), (0.5, (24, 40, 86)), (1.0, (8, 12, 30))],
     (238, 244, 255)),
]


def cover(entry, size=(720, 1080)) -> Image.Image:
    slug, title, author, kicker, motif_name, stops, ink = entry
    w, h = size[0] * SS, size[1] * SS
    img = gradient((w, h), stops, angle=104).convert("RGBA")
    art = (int(w * 0.03), int(h * 0.05), int(w * 0.97), int(h * 0.66))
    draw = ImageDraw.Draw(img, "RGBA")
    MOTIFS[motif_name](draw, w, h, art)

    # film grain, halftone texture and vignette keep the flat primitives organic
    img.alpha_composite(noise_layer((w, h), abs(hash(slug)) % 9999, 9.0))
    img.alpha_composite(halftone((w, h), ink, step=18 * SS, alpha=14))
    img.alpha_composite(vignette((w, h), 190))

    # bottom title panel
    panel_top = int(h * 0.655)
    panel = Image.new("RGBA", (w, h - panel_top), (0, 0, 0, 0))
    pd = ImageDraw.Draw(panel)
    for y in range(panel.height):
        t = y / panel.height
        pd.line((0, y, w, y), fill=(8, 9, 14, int(196 + 56 * t)))
    img.alpha_composite(panel, (0, panel_top))
    draw = ImageDraw.Draw(img, "RGBA")
    draw.line((int(w * 0.075), panel_top, int(w * 0.925), panel_top), fill=ink + (70,), width=2 * SS)

    # kicker (art ink) — quiet, so it never fights the title
    kf = font("consola.ttf", 22 * SS)
    tracked(draw, (int(w * 0.075), panel_top + 46 * SS), kicker, kf, 5 * SS, ink + (185,))

    # title, wrapped to the panel width. The bottom panel is always near-black,
    # so the title uses a light ink regardless of the artwork palette.
    title_ink = (248, 247, 252, 255)
    tf = font("segoeuib.ttf", 92 * SS)
    lines = wrap(draw, title, tf, w * 0.85)
    while len(lines) > 2 and tf.size > 40 * SS:
        tf = font("segoeuib.ttf", tf.size - 6 * SS)
        lines = wrap(draw, title, tf, w * 0.85)
    y = panel_top + 100 * SS
    for line in lines:
        tracked(draw, (int(w * 0.072), y), line, tf, 1.5 * SS, title_ink)
        y += int(tf.size * 1.02)

    # author + micro brand line
    af = font("segoeui.ttf", 26 * SS)
    draw.text((int(w * 0.075), h - 108 * SS), author, font=af, fill=(214, 210, 226, 220), anchor="la")
    bf = font("consola.ttf", 18 * SS)
    tracked(draw, (int(w * 0.075), h - 62 * SS), "YOMIRA · CATALOG EDITION", bf, 4 * SS, (168, 160, 200, 170))
    # small violet spine mark
    draw.rectangle((int(w * 0.93), panel_top + 40 * SS, int(w * 0.935), h - 40 * SS), fill=(157, 78, 221, 200))

    return img.resize(size, Image.LANCZOS)


def banner(entry, size=(2400, 1000)) -> Image.Image:
    slug, title, author, kicker, motif_name, stops, ink = entry
    w, h = size
    img = gradient((w, h), stops, angle=100).convert("RGBA")
    MOTIFS[motif_name](ImageDraw.Draw(img, "RGBA"), w, h, (0, 0, w, int(h * 0.94)))
    img.alpha_composite(noise_layer((w, h), 7, 7.0))
    img.alpha_composite(vignette((w, h), 170))
    return img


# ---------------------------------------------------------------------------
# Webtoon pages (original "dungeon gate" sequence, vertical scroll)
# ---------------------------------------------------------------------------


def panel(d: ImageDraw.ImageDraw, box) -> None:
    """Gutter rule only: the panel keeps the artwork already pasted into it."""
    d.rectangle(box, outline=(64, 60, 84, 255), width=3)


def page_frame(size=(900, 1500)) -> Image.Image:
    img = Image.new("RGBA", size, (7, 8, 12, 255))
    return img


def page_one(size=(900, 1500)) -> Image.Image:
    w, h = size
    img = page_frame(size)
    d = ImageDraw.Draw(img, "RGBA")
    # establishing panel: night city + gate
    top = (30, 30, w - 30, 700)
    inner = gradient((top[2] - top[0], top[3] - top[1]),
                     [(0.0, (12, 16, 30)), (0.55, (24, 26, 54)), (1.0, (42, 26, 68))]).convert("RGBA")
    mask = Image.new("L", inner.size, 0)
    ImageDraw.Draw(mask).rectangle((0, 0, inner.width, inner.height), fill=255)
    img.paste(inner, (top[0], top[1]), mask)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(101)
    for i in range(60):
        bx = top[0] + i * 14
        bh = rng.randint(40, 150)
        d.rectangle((bx, top[3] - bh, bx + 12, top[3]), fill=(8, 10, 18, 255))
        if rng.random() > 0.6:
            d.rectangle((bx + 3, top[3] - bh + 14, bx + 8, top[3] - bh + 22), fill=(255, 214, 120, 190))
    cx = w // 2
    for i in range(12, 0, -1):
        t = i / 12
        d.ellipse((cx - 150 * t, 420 - 250 * t, cx + 150 * t, 420 + 190 * t),
                  outline=(150, 110, 255, int(150 * (1 - t))), width=3)
    d.ellipse((cx - 120, 240, cx + 120, 600), fill=(16, 12, 30, 255))
    d.ellipse((cx - 96, 268, cx + 96, 570), fill=(64, 44, 150, 255))
    d.ellipse((cx - 62, 320, cx + 62, 520), fill=(168, 140, 255, 235))
    d.ellipse((cx - 26, 380, cx + 26, 460), fill=(250, 246, 255, 255))
    panel(d, top)
    # narration
    d.rectangle((56, 740, w - 56, 880), fill=(16, 15, 24, 255), outline=(86, 74, 120, 255), width=2)
    f = font("segoeui.ttf", 34)
    d.text((80, 762), "Kapı ilk kez bu gece açıldı.", font=f, fill=(238, 236, 248, 255), anchor="la")
    d.text((80, 812), "Kimse geri dönmeyi beklemiyordu.", font=font("segoeui.ttf", 28),
           fill=(168, 160, 190, 255), anchor="la")
    # SFX
    sf = font("segoeuib.ttf", 120)
    d.text((w // 2, 980), "VRRR", font=sf, fill=(196, 132, 252, 235), anchor="mm")
    # second panel: the protagonist stepping into the light
    p2 = (30, 1060, w - 30, 1460)
    inner2 = gradient((p2[2] - p2[0], p2[3] - p2[1]),
                      [(0.0, (14, 16, 30)), (0.55, (30, 26, 52)), (1.0, (10, 12, 20))]).convert("RGBA")
    img.paste(inner2, (p2[0], p2[1]), inner2)
    d = ImageDraw.Draw(img, "RGBA")
    for i in range(16):
        y = p2[1] + 18 + i * 22
        d.line((p2[0] + 10, y, p2[2] - 10, y - 10), fill=(40, 42, 62, 210), width=5)
    # wet asphalt reflections
    for i in range(9):
        rx = p2[0] + 40 + i * 84
        d.line((rx, p2[3] - 10, rx + 6, p2[3] - 90), fill=(150, 120, 255, 40), width=10)
    px, py = cx - 30, p2[3] - 24
    d.ellipse((px - 20, py - 250, px + 20, py - 210), fill=(16, 14, 24, 255))
    d.polygon([(px - 44, py), (px - 28, py - 214), (px + 28, py - 214), (px + 44, py)], fill=(16, 14, 24, 255))
    d.polygon([(px - 30, py), (px - 22, py - 200), (px + 4, py - 200), (px + 6, py)],
              fill=(26, 22, 38, 255))
    d.line((px + 40, py - 220, px + 96, py - 292), fill=(16, 14, 24, 255), width=13)
    d.ellipse((px - 70, py + 4, px + 70, py + 34), fill=(150, 110, 255, 40))
    panel(d, p2)
    return img


def page_two(size=(900, 1500)) -> Image.Image:
    w, h = size
    img = page_frame(size)
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle((0, 0, w, h), fill=(10, 8, 14, 255))
    cx, cy = w // 2, 520
    rng = random.Random(202)
    for i in range(90):
        a = rng.uniform(0, math.tau)
        d.line((cx, cy, cx + math.cos(a) * rng.uniform(300, 900), cy + math.sin(a) * rng.uniform(280, 780)),
               fill=(255, 240, 230, rng.randint(30, 110)), width=rng.choice([3, 5, 8]))
    for i in range(10, 0, -1):
        t = i / 10
        d.ellipse((cx - 210 * t, cy - 210 * t, cx + 210 * t, cy + 210 * t),
                  fill=(190, 90, 220, int(30 * (1 - t))))
    d.ellipse((cx - 140, cy - 150, cx + 140, cy + 150), fill=(14, 10, 18, 255))
    d.polygon([(cx, cy - 150), (cx + 140, cy + 40), (cx + 80, cy + 150), (cx - 80, cy + 150), (cx - 140, cy + 40)],
              fill=(18, 12, 24, 255))
    d.ellipse((cx - 62, cy - 90, cx - 34, cy - 58), fill=(255, 128, 200, 235))
    d.ellipse((cx + 34, cy - 90, cx + 62, cy - 58), fill=(255, 128, 200, 235))
    d.line((cx - 40, cy + 40, cx + 40, cy + 40), fill=(255, 200, 230, 200), width=5)
    d.text((cx, 800), "KRAAK", font=font("segoeuib.ttf", 168), fill=(255, 255, 255, 240), anchor="mm")
    d.line((40, 920, w - 40, 920), fill=(70, 60, 90, 255), width=3)
    f = font("segoeui.ttf", 32)
    lines = ["Zırhımdaki çatlaklar", "artık beni korumuyor.", "Ama gölge hâlâ dinliyor."]
    y = 980
    for line in lines:
        d.text((60, y), line, font=f, fill=(214, 208, 226, 255), anchor="la")
        y += 52
    d.rectangle((60, 1200, 300, 1206), fill=(157, 78, 221, 255))
    d.ellipse((w - 200, 1130, w - 80, 1250), fill=(24, 20, 36, 255))
    d.ellipse((w - 190, 1140, w - 90, 1240), outline=(150, 110, 255, 170), width=3)
    return img


def page_three(size=(900, 1500)) -> Image.Image:
    w, h = size
    img = page_frame(size)
    d = ImageDraw.Draw(img, "RGBA")
    # two stacked panels
    p1 = (30, 30, w - 30, 700)
    inner = gradient((p1[2] - p1[0], p1[3] - p1[1]),
                     [(0.0, (18, 20, 38)), (0.6, (36, 30, 68)), (1.0, (16, 22, 40))]).convert("RGBA")
    img.paste(inner, (p1[0], p1[1]), inner)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(303)
    for i in range(11):
        bx = p1[0] + 30 + i * 76
        bh = rng.randint(90, 300)
        d.rectangle((bx, p1[3] - bh, bx + 54, p1[3]), fill=(10, 12, 22, 255))
        for wy in range(p1[3] - bh + 16, p1[3] - 20, 34):
            for wx in range(bx + 10, bx + 46, 18):
                if rng.random() > 0.4:
                    d.rectangle((wx, wy, wx + 6, wy + 10), fill=(240, 202, 120, 190))
    # speech bubble
    d.rounded_rectangle((110, 200, 610, 360), radius=48, fill=(245, 243, 250, 245))
    d.polygon([(210, 352), (250, 432), (300, 352)], fill=(245, 243, 250, 245))
    d.text((140, 228), "Bu güç...", font=font("segoeui.ttf", 44), fill=(24, 20, 30, 255), anchor="la")
    d.text((140, 288), "benim mi?", font=font("segoeui.ttf", 44), fill=(24, 20, 30, 255), anchor="la")
    panel(d, p1)
    # second panel: response
    p2 = (30, 730, w - 30, 1470)
    inner2 = gradient((p2[2] - p2[0], p2[3] - p2[1]),
                      [(0.0, (10, 10, 18)), (0.5, (28, 18, 46)), (1.0, (8, 8, 14))]).convert("RGBA")
    img.paste(inner2, (p2[0], p2[1]), inner2)
    d = ImageDraw.Draw(img, "RGBA")
    d.ellipse((w // 2 - 190, 940, w // 2 + 190, 1320), fill=(14, 10, 22, 255))
    d.ellipse((w // 2 - 150, 980, w // 2 + 150, 1280), outline=(190, 140, 255, 190), width=5)
    d.ellipse((w // 2 - 90, 1040, w // 2 + 90, 1220), fill=(86, 56, 150, 255))
    d.ellipse((w // 2 - 40, 1090, w // 2 + 40, 1170), fill=(240, 236, 255, 240))
    panel(d, p2)
    d.rounded_rectangle((430, 760, 800, 880), radius=40, fill=(30, 24, 46, 245), outline=(150, 110, 255, 200), width=2)
    d.text((456, 786), "Sen değilsin.", font=font("segoeui.ttf", 36), fill=(214, 200, 250, 255), anchor="la")
    d.text((456, 832), "Henüz.", font=font("segoeuib.ttf", 34), fill=(240, 236, 255, 255), anchor="la")
    return img


def page_four(size=(900, 1500)) -> Image.Image:
    w, h = size
    img = page_frame(size)
    inner = gradient((w, h), [(0.0, (16, 14, 34)), (0.45, (34, 24, 62)), (1.0, (8, 8, 14))]).convert("RGBA")
    img.paste(inner, (0, 0), inner)
    d = ImageDraw.Draw(img, "RGBA")
    cx = w // 2

    # --- Wide panel: the empty hall, throne and the erupted blade -------------
    top, bottom = 60, 780
    hall = gradient((w - 40, bottom - top),
                    [(0.0, (26, 20, 48)), (0.55, (44, 30, 78)), (1.0, (12, 10, 22))]).convert("RGBA")
    img.paste(hall, (20, top), hall)
    d = ImageDraw.Draw(img, "RGBA")

    arch_cx, arch_cy = cx, 300
    for i in range(4):
        d.arc((arch_cx - 240 - i * 16, arch_cy - 190 - i * 16,
               arch_cx + 240 + i * 16, arch_cy + 210 + i * 16),
              180, 360, fill=(150, 110, 255, 140 - i * 28), width=4)
    for side in (-1, 1):
        bx = cx + side * 170
        d.polygon([(bx - 20, bottom), (bx - 14, 330), (bx, 300), (bx + 14, 330), (bx + 20, bottom)],
                  fill=(20, 16, 34, 255))
        d.ellipse((bx - 5, 322, bx + 5, 356), fill=(196, 156, 255, 170))
    d.polygon([(cx - 58, bottom - 24), (cx - 44, 470), (cx - 16, 446), (cx, 408), (cx + 16, 446),
               (cx + 44, 470), (cx + 58, bottom - 24)], fill=(10, 8, 16, 255))

    # floor perspective
    for i in range(7):
        t = i / 6
        yy = bottom - 190 + (190) * (t ** 0.6)
        half = 150 + 130 * t
        d.line((cx - half, yy, cx + half, yy), fill=(128, 100, 190, 55), width=2)

    # blade: violet aura, white core, guard
    for i in range(12, 0, -1):
        t = i / 12
        d.polygon([(cx - 30 * t, bottom - 40), (cx - 7 * t, 430), (cx + 7 * t, 430),
                   (cx + 30 * t, bottom - 40)], fill=(170, 120, 255, int(12 * (1 - t))))
    d.polygon([(cx - 8, bottom - 40), (cx - 4, 442), (cx, 416), (cx + 4, 442), (cx + 8, bottom - 40)],
              fill=(246, 242, 255, 245))
    d.polygon([(cx - 32, bottom - 158), (cx + 32, bottom - 158), (cx + 20, bottom - 138),
               (cx - 20, bottom - 138)], fill=(44, 34, 62, 255))

    # speech/narration strip
    d.rectangle((52, 600, w - 52, 700), fill=(16, 15, 24, 245), outline=(86, 74, 120, 255), width=2)
    d.text((76, 622), "Sistem yeniden yazıldı.", font=font("segoeui.ttf", 30),
           fill=(236, 232, 246, 255), anchor="la")
    d.text((76, 662), "Tek oyuncu kalmadı.", font=font("segoeui.ttf", 26),
           fill=(172, 164, 196, 255), anchor="la")
    panel(d, (20, top, w - 20, bottom))

    # --- Title block ---------------------------------------------------------
    d.text((cx, 872), "ARISE", font=font("segoeuib.ttf", 100), fill=(248, 244, 255, 250), anchor="mm")
    d.line((110, 930, w - 110, 930), fill=(150, 110, 255, 170), width=3)
    d.text((cx, 966), "GÖLGE ORDUSU · 01", font=font("consola.ttf", 24),
           fill=(186, 174, 214, 255), anchor="mm")

    # --- Two ranks of summoned soldiers -------------------------------------
    rng = random.Random(404)
    for row, (base_y, scale, count) in enumerate([(1300, 0.9, 7), (1450, 1.35, 5)]):
        step = (w - 120) / max(1, count - 1)
        for i in range(count):
            px = 60 + i * step + (row * 24)
            if px > w - 40:
                continue
            py = base_y
            head = 20 * scale
            d.ellipse((px - head, py - 150 * scale - head, px + head, py - 150 * scale + head),
                      fill=(15, 12, 24, 255))
            d.polygon([(px - 26 * scale, py), (px - 20 * scale, py - 140 * scale),
                       (px + 20 * scale, py - 140 * scale), (px + 26 * scale, py)], fill=(15, 12, 24, 255))
            d.polygon([(px - 34 * scale, py - 128 * scale), (px - 21 * scale, py - 198 * scale),
                       (px + 21 * scale, py - 198 * scale), (px + 34 * scale, py - 128 * scale)],
                      fill=(21, 17, 32, 255))
            d.rounded_rectangle((px - 11 * scale, py - 158 * scale, px + 11 * scale, py - 149 * scale),
                                radius=3 * scale, fill=(172, 124, 255, 200))
            if rng.random() > 0.45:
                d.line((px + 26 * scale, py - 168 * scale, px + 44 * scale, py - 244 * scale),
                       fill=(24, 18, 34, 255), width=max(2, int(4 * scale)))
    return img


PAGES = {
    "solo-leveling": [page_one, page_two, page_three, page_four],
}


def main() -> None:
    (OUT / "covers").mkdir(parents=True, exist_ok=True)
    (OUT / "banners").mkdir(parents=True, exist_ok=True)
    (OUT / "pages").mkdir(parents=True, exist_ok=True)

    for entry in SERIES:
        slug = entry[0]
        img = cover(entry)
        # JPEG for the painterly posters: the grain gradient compresses ~10x better
        # than PNG, and the render suite/Coil both decode it through the same path.
        img.convert("RGB").save(OUT / "covers" / f"{slug}.jpg", quality=90, optimize=True, progressive=True)
        print(f"  cover  {slug}.jpg  {img.width}x{img.height}")
        if slug in ("one-piece", "solo-leveling", "frieren"):
            b = banner(entry)
            b.convert("RGB").save(OUT / "banners" / f"{slug}.jpg", quality=90, optimize=True, progressive=True)
            print(f"  banner {slug}.jpg  {b.width}x{b.height}")

    for slug, makers in PAGES.items():
        for index, maker in enumerate(makers, start=1):
            page = maker()
            page.convert("RGB").save(OUT / "pages" / f"{slug}-{index}.png", optimize=True)
            print(f"  page   {slug}-{index}.png  {page.width}x{page.height}")


if __name__ == "__main__":
    main()
