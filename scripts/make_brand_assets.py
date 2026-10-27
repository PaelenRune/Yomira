# -*- coding: utf-8 -*-
"""
Yomira v5.0 brand asset generator.

Single source of truth: the SVG template below. Produces:

  art/yomira_logo.svg                 master emblem (OLED #0A0A0C background)
  art/yomira_logo.png                 1024x1024 transparent mark
  art/yomira_logo-v2.png              2048x2048 transparent mark
  art/yomira_logo-oled.png            1024x1024 OLED background
  art/yomira_logo-oled-2048.png       2048x2048 OLED background
  art/banner.png                      2560x720 README banner

  app/src/main/res/mipmap-*/ic_launcher.png / ic_launcher_round.png      (legacy)
  app/src/main/res/drawable-*/ic_launcher_background.png                 (adaptive bg: halo)
  app/src/main/res/drawable-*/ic_launcher_foreground.png                 (adaptive fg: mark)
  app/src/main/res/drawable/ic_launcher_foreground.xml                   (vector fallback)
  app/src/main/res/drawable/ic_launcher_monochrome.xml                   (themed icon)
  app/src/main/res/drawable/ic_launcher_background.xml                   (solid fallback)

  desktop/icons/yomira.ico            multi-size Windows icon
  desktop/src/jvmMain/resources/yomira.png   window / in-app logo (512)

Requires: resvg-py, Pillow, numpy   (pip install resvg-py pillow numpy)
Run from anywhere:  python scripts/make_brand_assets.py
"""

from __future__ import annotations

import io
from pathlib import Path

import numpy as np
import resvg_py
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "art"
RES = ROOT / "app" / "src" / "main" / "res"
FONTS = Path("C:/Windows/Fonts")

# ---------------------------------------------------------------------------
# Geometry (1024 x 1024 design grid, halo centred at 512,512, r=402)
# ---------------------------------------------------------------------------

# Stylised white "Y": open-book spine + pages. The stem is the spine with a
# sharp slanted cut at the bottom-right; the left arm sweeps up-left.
Y_PATH = "M237 276 L512 446 L791 276 L770 425 L561 553 L561 684 L463 776 L463 553 L258 425 Z"
# Dimensional facets: page undersides + spine groove.
FACET_LEFT = "M258 425 L463 553 L463 508 L258 382 Z"
FACET_RIGHT = "M561 508 L561 553 L770 425 L770 382 Z"
SPINE_GROOVE = "M505 566 L519 566 L519 724 L505 737 Z"

# Three tapered page-leaves hinged on the right arm, fanned at ~40/55/72°,
# base ends hidden under the arm, tips piercing the halo.
BLADE_A = "M610 392 Q715 257 870 185 Q756 309 610 392 Z"
BLADE_B = "M626 386 Q681 244 801 149 Q734 283 626 386 Z"
BLADE_C = "M642 380 Q643 249 717 140 Q702 267 642 380 Z"
EDGE_A = "M610 392 Q715 257 870 185"
EDGE_B = "M626 386 Q681 244 801 149"
EDGE_C = "M642 380 Q643 249 717 140"

# Four-point sparkles (upper centre + two satellites).
SPARKLE_BIG = ("M512 132 C520 166 534 180 568 188 C534 196 520 210 512 244 "
               "C504 210 490 196 456 188 C490 180 504 166 512 132 Z")
SPARKLE_S1 = ("M610 104 C613 118 618 123 632 126 C618 129 613 134 610 148 "
              "C607 134 602 129 588 126 C602 123 607 118 610 104 Z")
SPARKLE_S2 = ("M430 234 C432 243 436 247 445 250 C436 253 432 257 430 266 "
              "C428 257 424 253 415 250 C424 247 428 243 430 234 Z")

# Halo light leaks (sweep=0 arcs along the bottom / top of the ring).
LEAK_BOTTOM = "M311 861 A402 402 0 0 0 713 861"
LEAK_TOP = "M713 164 A402 402 0 0 0 311 164"

# Visual centre of the mark (glyph + wings + sparkles) and its max radius.
MARK_CX, MARK_CY, MARK_R = 553.5, 458.0, 418.0

DEFS = """
  <defs>
    <linearGradient id="gWhite" x1="0" y1="240" x2="0" y2="820" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#FFFFFF"/>
      <stop offset="0.55" stop-color="#F4F4FA"/>
      <stop offset="1" stop-color="#D9D9E8"/>
    </linearGradient>
    <linearGradient id="gW1" x1="610" y1="392" x2="870" y2="185" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#7928CA"/>
      <stop offset="0.5" stop-color="#9D4EDD"/>
      <stop offset="0.85" stop-color="#C084FC"/>
      <stop offset="1" stop-color="#E9D5FF"/>
    </linearGradient>
    <linearGradient id="gW2" x1="626" y1="386" x2="801" y2="149" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#7928CA"/>
      <stop offset="0.5" stop-color="#9D4EDD"/>
      <stop offset="0.85" stop-color="#C084FC"/>
      <stop offset="1" stop-color="#E9D5FF"/>
    </linearGradient>
    <linearGradient id="gW3" x1="642" y1="380" x2="717" y2="140" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#7928CA"/>
      <stop offset="0.45" stop-color="#9D4EDD"/>
      <stop offset="0.85" stop-color="#C084FC"/>
      <stop offset="1" stop-color="#F5F3FF"/>
    </linearGradient>
    <linearGradient id="gRing" x1="150" y1="880" x2="880" y2="150" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#4C1D95"/>
      <stop offset="0.3" stop-color="#7928CA"/>
      <stop offset="0.6" stop-color="#9D4EDD"/>
      <stop offset="0.85" stop-color="#C084FC"/>
      <stop offset="1" stop-color="#E9D5FF"/>
    </linearGradient>
    <linearGradient id="gLeak" x1="311" y1="0" x2="713" y2="0" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#C084FC" stop-opacity="0.12"/>
      <stop offset="0.5" stop-color="#E9D5FF" stop-opacity="0.95"/>
      <stop offset="1" stop-color="#C084FC" stop-opacity="0.12"/>
    </linearGradient>
    <radialGradient id="gSparkle" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#FAF5FF"/>
      <stop offset="0.35" stop-color="#D8B4FE"/>
      <stop offset="1" stop-color="#A855F7"/>
    </radialGradient>
    <radialGradient id="gAmbient" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#7928CA" stop-opacity="0.34"/>
      <stop offset="0.55" stop-color="#4C1D95" stop-opacity="0.15"/>
      <stop offset="1" stop-color="#4C1D95" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="gBloom" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#E9D5FF" stop-opacity="0.7"/>
      <stop offset="1" stop-color="#C084FC" stop-opacity="0"/>
    </radialGradient>
    <filter id="fBig" x="-60%" y="-60%" width="220%" height="220%">
      <feGaussianBlur stdDeviation="24"/>
    </filter>
    <filter id="fMed" x="-60%" y="-60%" width="220%" height="220%">
      <feGaussianBlur stdDeviation="12"/>
    </filter>
    <filter id="fArc" x="-60%" y="-60%" width="220%" height="220%">
      <feGaussianBlur stdDeviation="10"/>
    </filter>
  </defs>
"""
# ---------------------------------------------------------------------------
# Layer fragments
# ---------------------------------------------------------------------------

def ring_layer() -> str:
    """Neon halo ring, ambient bloom and the top/bottom light leaks."""
    return f"""
  <circle cx="512" cy="512" r="402" fill="url(#gAmbient)"/>
  <circle cx="512" cy="512" r="402" fill="none" stroke="url(#gRing)"
          stroke-width="28" opacity="0.5" filter="url(#fMed)"/>
  <circle cx="512" cy="512" r="402" fill="none" stroke="url(#gRing)" stroke-width="4.5"/>
  <circle cx="512" cy="512" r="402" fill="none" stroke="#EDE9FE"
          stroke-width="1.4" opacity="0.55"/>
  <path d="{LEAK_BOTTOM}" fill="none" stroke="url(#gLeak)" stroke-width="15"
        stroke-linecap="round" filter="url(#fArc)"/>
  <path d="{LEAK_TOP}" fill="none" stroke="url(#gLeak)" stroke-width="12"
        stroke-linecap="round" opacity="0.8" filter="url(#fArc)"/>
  <circle cx="512" cy="900" r="140" fill="url(#gBloom)" filter="url(#fMed)" opacity="0.75"/>
"""


def mark_layer() -> str:
    """White book-`Y`, violet page-wings and the sparkles (the 'mark')."""
    return f"""
  <path d="{Y_PATH}" fill="#E6E6F5" opacity="0.9" filter="url(#fBig)"/>
  <g filter="url(#fMed)" opacity="0.55" fill="#9D4EDD">
    <path d="{BLADE_A}"/><path d="{BLADE_B}"/><path d="{BLADE_C}"/>
  </g>
  <path d="{BLADE_A}" fill="url(#gW1)"/>
  <path d="{BLADE_B}" fill="url(#gW2)"/>
  <path d="{BLADE_C}" fill="url(#gW3)"/>
  <path d="{EDGE_A}" fill="none" stroke="#F3E8FF" stroke-width="3.5"
        stroke-linecap="round" opacity="0.9"/>
  <path d="{EDGE_B}" fill="none" stroke="#F3E8FF" stroke-width="3.5"
        stroke-linecap="round" opacity="0.9"/>
  <path d="{EDGE_C}" fill="none" stroke="#F3E8FF" stroke-width="3.2"
        stroke-linecap="round" opacity="0.85"/>
  <path d="{Y_PATH}" fill="url(#gWhite)"/>
  <path d="{FACET_LEFT}" fill="#D6D6E2" opacity="0.38"/>
  <path d="{FACET_RIGHT}" fill="#C4C4D6" opacity="0.42"/>
  <path d="{SPINE_GROOVE}" fill="#B7B7CC" opacity="0.30"/>
  <g fill="#C084FC" opacity="0.65" filter="url(#fMed)">
    <path d="{SPARKLE_BIG}"/><path d="{SPARKLE_S1}"/><path d="{SPARKLE_S2}"/>
  </g>
  <path d="{SPARKLE_BIG}" fill="url(#gSparkle)"/>
  <path d="{SPARKLE_S1}" fill="url(#gSparkle)"/>
  <path d="{SPARKLE_S2}" fill="url(#gSparkle)" opacity="0.9"/>
"""


def wrap_svg(body: str) -> str:
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" '
            f'width="1024" height="1024">\n{DEFS}\n{body}\n</svg>\n')


def svg_master() -> str:
    """Full emblem on an OLED #0A0A0C plate (transparent if the rect is stripped)."""
    body = ('  <rect id="bg" width="1024" height="1024" fill="#0A0A0C"/>\n'
            + ring_layer() + mark_layer())
    return wrap_svg(body)


def svg_transparent() -> str:
    """Master without the background plate (the rect line is one line, easy to drop)."""
    return "\n".join(line for line in svg_master().splitlines()
                     if 'id="bg"' not in line) + "\n"


def scale_group(inner: str, s: float, cx: float, cy: float, size: int) -> str:
    """Scale an emblem fragment about (cx, cy) into a `size` x `size` canvas."""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" '
            f'width="{size}" height="{size}">\n{DEFS}\n'
            f'  <g transform="translate({size / 2},{size / 2}) scale({s:.6f}) '
            f'translate(-{cx},-{cy})">\n{inner}\n  </g>\n</svg>\n')


def svg_adaptive_fg() -> str:
    """Adaptive foreground: mark only, tips reach r=35dp of the 108dp layer."""
    return scale_group(mark_layer(), 35.0 / MARK_R, MARK_CX, MARK_CY, 108)


def svg_adaptive_bg() -> str:
    """Adaptive background: OLED plate + halo aligned with the foreground scale."""
    s = 35.0 / MARK_R
    body = (f'  <rect width="108" height="108" fill="#0A0A0C"/>\n'
            f'  <g transform="translate(54,54) scale({s:.6f}) translate(-512,-512)">\n'
            + ring_layer() + "  </g>\n")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" '
            f'width="108" height="108">\n{DEFS}\n{body}</svg>\n')


def svg_icon_plate() -> str:
    """Legacy / desktop icon: full emblem inside an OLED rounded plate."""
    body = ('  <rect width="1024" height="1024" rx="224" fill="#0A0A0C"/>\n'
            f'  <g transform="translate(512,512) scale(0.90) translate(-{MARK_CX},-{MARK_CY})">\n'
            + ring_layer() + mark_layer() + "  </g>\n")
    return wrap_svg(body)


# ---------------------------------------------------------------------------
# Rendering helpers
# ---------------------------------------------------------------------------

def render(svg: str, px: int) -> Image.Image:
    data = resvg_py.svg_to_bytes(svg_string=svg, width=px, height=px)
    return Image.open(io.BytesIO(data)).convert("RGBA")


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print(f"  wrote {path.relative_to(ROOT)}  {img.width}x{img.height}")


def circle_mask(img: Image.Image) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, img.size[0] - 1, img.size[1] - 1), fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out
# ---------------------------------------------------------------------------
# Android launcher icons
# ---------------------------------------------------------------------------

LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def make_android_icons() -> None:
    plate = svg_icon_plate()
    fg_svg = svg_adaptive_fg()
    bg_svg = svg_adaptive_bg()

    # Legacy bitmap launchers (pre-adaptive + fallbacks).
    for density, px in LEGACY_SIZES.items():
        icon = render(plate, px)
        save(icon, RES / f"mipmap-{density}" / "ic_launcher.png")
        save(circle_mask(icon), RES / f"mipmap-{density}" / "ic_launcher_round.png")

    # Adaptive layers (halo lives in the background so the mark stays large).
    for density, px in ADAPTIVE_SIZES.items():
        save(render(bg_svg, px), RES / f"drawable-{density}" / "ic_launcher_background.png")
        save(render(fg_svg, px), RES / f"drawable-{density}" / "ic_launcher_foreground.png")


FOREGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<!--
    Yomira v5.0 adaptive icon foreground: white open-book "Y" with violet
    page-wings and sparkles, scaled into the 66dp safe zone. The density
    buckets carry the glowing PNG render; this vector is the fallback.
    Halo + ambient bloom live in ic_launcher_background.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:translateX="54"
        android:translateY="54"
        android:scaleX="__S__"
        android:scaleY="__S__">
        <group android:translateX="-__CX__" android:translateY="-__CY__">
            <!-- page-wings, dark to light violet, fanning off the right arm -->
            <path android:fillColor="#7C34CE" android:pathData="__BLADE_A__"/>
            <path android:fillColor="#9D4EDD" android:pathData="__BLADE_B__"/>
            <path android:fillColor="#B266F0" android:pathData="__BLADE_C__"/>
            <!-- open-book Y: spine + pages -->
            <path android:fillColor="#F2F2F8" android:pathData="__Y__"/>
            <path android:fillColor="#D9D9E6" android:fillAlpha="0.55"
                android:pathData="__FACET_LEFT__"/>
            <!-- sparkles -->
            <path android:fillColor="#C084FC" android:pathData="__SPARKLE_BIG__"/>
            <path android:fillColor="#D8B4FE" android:pathData="__SPARKLE_S1__"/>
            <path android:fillColor="#D8B4FE" android:pathData="__SPARKLE_S2__"/>
        </group>
    </group>
</vector>
"""

MONOCHROME_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Themed (Android 13+) monochrome layer: book-Y silhouette, wings, sparkle. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:translateX="54"
        android:translateY="54"
        android:scaleX="__S__"
        android:scaleY="__S__">
        <group android:translateX="-__CX__" android:translateY="-__CY__">
            <path android:fillColor="#FFFFFF" android:pathData="__BLADE_A__"/>
            <path android:fillColor="#FFFFFF" android:pathData="__BLADE_B__"/>
            <path android:fillColor="#FFFFFF" android:pathData="__BLADE_C__"/>
            <path android:fillColor="#FFFFFF" android:pathData="__Y__"/>
            <path android:fillColor="#FFFFFF" android:pathData="__SPARKLE_BIG__"/>
        </group>
    </group>
</vector>
"""

BACKGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Solid OLED fallback; the density buckets add the halo ring render. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#0A0A0C"
        android:pathData="M0,0h108v108h-108z"/>
</vector>
"""


def _fill_vector(template: str, scale: float) -> str:
    return (template
            .replace("__S__", f"{scale:.6f}")
            .replace("__CX__", f"{MARK_CX}")
            .replace("__CY__", f"{MARK_CY}")
            .replace("__Y__", Y_PATH)
            .replace("__FACET_LEFT__", FACET_LEFT)
            .replace("__BLADE_A__", BLADE_A)
            .replace("__BLADE_B__", BLADE_B)
            .replace("__BLADE_C__", BLADE_C)
            .replace("__SPARKLE_BIG__", SPARKLE_BIG)
            .replace("__SPARKLE_S1__", SPARKLE_S1)
            .replace("__SPARKLE_S2__", SPARKLE_S2))


def make_android_xml() -> None:
    out = RES / "drawable"
    out.mkdir(parents=True, exist_ok=True)
    (out / "ic_launcher_foreground.xml").write_text(
        _fill_vector(FOREGROUND_XML, 35.0 / MARK_R), encoding="utf-8")
    (out / "ic_launcher_monochrome.xml").write_text(
        _fill_vector(MONOCHROME_XML, 33.0 / MARK_R), encoding="utf-8")
    (out / "ic_launcher_background.xml").write_text(BACKGROUND_XML, encoding="utf-8")
    print("  wrote Android vector drawables (foreground / monochrome / background)")


# ---------------------------------------------------------------------------
# Desktop icons
# ---------------------------------------------------------------------------

def make_desktop_icons() -> None:
    plate = svg_icon_plate()
    ico_base = render(plate, 256)
    ico_path = ROOT / "desktop" / "icons" / "yomira.ico"
    ico_path.parent.mkdir(parents=True, exist_ok=True)
    ico_base.save(ico_path, format="ICO",
                  sizes=[(16, 16), (24, 24), (32, 32), (48, 48),
                         (64, 64), (128, 128), (256, 256)])
    print(f"  wrote {ico_path.relative_to(ROOT)}")
    # Window / in-app logo (Branding.kt + window icon load /yomira.png).
    save(render(svg_transparent(), 512),
         ROOT / "desktop" / "src" / "jvmMain" / "resources" / "yomira.png")
# ---------------------------------------------------------------------------
# README banner
# ---------------------------------------------------------------------------

def _glow(size: int, color: tuple, alpha: int, blur: int) -> Image.Image:
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse((size * 0.18,) * 2 + (size * 0.82,) * 2,
                                  fill=color + (alpha,))
    return layer.filter(ImageFilter.GaussianBlur(blur))


def draw_tracked(draw: ImageDraw.ImageDraw, x: int, y: int, text: str,
                 font: ImageFont.FreeTypeFont, tracking: int, fill: tuple) -> float:
    cx = float(x)
    for ch in text:
        draw.text((cx, y), ch, font=font, fill=fill)
        cx += font.getlength(ch) + tracking
    return cx - tracking


def tracked_width(text: str, font: ImageFont.FreeTypeFont, tracking: int) -> float:
    return sum(font.getlength(c) for c in text) + tracking * (len(text) - 1)


def make_banner() -> None:
    W, H = 2560, 720
    base = Image.new("RGBA", (W, H), (10, 10, 12, 255))

    # Ambient violet glow behind the mark.
    g = _glow(920, (121, 40, 202), 96, 170)
    base.alpha_composite(g, (40, int(H / 2 - 460)))

    # Faint halo arcs bleeding in from the right edge.
    deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(deco)
    dd.ellipse((W - 400, -130, W + 940, 850), outline=(157, 78, 221, 80), width=14)
    dd.ellipse((W - 360, -90, W + 900, 810), outline=(192, 132, 252, 110), width=4)
    deco = deco.filter(ImageFilter.GaussianBlur(4))
    base.alpha_composite(deco)

    # Neon horizon line at the bottom.
    xs = np.linspace(0.0, 1.0, W)
    fade = np.clip(np.minimum(xs, 1.0 - xs) * 12.0, 0.0, 1.0)
    c0 = np.array((121, 40, 202), dtype=float)
    c1 = np.array((192, 132, 252), dtype=float)
    line = (c0[None, :] * (1 - xs[:, None]) + c1[None, :] * xs[:, None]).astype(np.uint8)
    alpha = (fade * 200).astype(np.uint8)[:, None]
    line_img = np.concatenate([line, alpha], axis=1)[None, :, :]
    horizon = Image.fromarray(line_img)
    strip = Image.new("RGBA", (W, 9), (0, 0, 0, 0))
    strip.paste(horizon, (0, 4))
    glow_strip = strip.filter(ImageFilter.GaussianBlur(3))
    base.alpha_composite(glow_strip, (0, H - 96))
    base.alpha_composite(strip, (0, H - 96))

    # Emblem + wordmark block, optically centred. The version sits inline with the
    # wordmark so the banner literally reads "YOMIRA v5.0".
    mark = render(svg_transparent(), 560)
    wm = ImageFont.truetype(str(FONTS / "segoeuisl.ttf"), 216)
    tl = ImageFont.truetype(str(FONTS / "segoeui.ttf"), 58)
    vf = ImageFont.truetype(str(FONTS / "segoeuisl.ttf"), 104)
    wm_track, tl_gap, v_track = 76, 20, 6
    tw = tracked_width("YOMIRA", wm, wm_track)
    vw = tracked_width("v5.0", vf, v_track)
    version_gap = 54
    brand_w = tw + version_gap + vw
    ttw = tracked_width("NEXT-GEN MANGA & COMIC READER", tl, tl_gap)
    tl_track = tl_gap + max(0, int((tw - ttw) / 28))
    ttw = tracked_width("NEXT-GEN MANGA & COMIC READER", tl, tl_track)
    text_w = max(brand_w, ttw)

    gap = 130
    x0 = int((W - (560 + gap + text_w)) / 2)
    base.alpha_composite(mark, (x0, int((H - 560) / 2)))

    tx = x0 + 560 + gap
    wm_box = wm.getbbox("YOMIRA")
    wm_h = wm_box[3] - wm_box[1]
    tl_box = tl.getbbox("XY")
    tl_h = tl_box[3] - tl_box[1]
    block_h = wm_h + 30 + 5 + 42 + tl_h
    top = int((H - block_h) / 2)

    d = ImageDraw.Draw(base)
    draw_tracked(d, tx, top - wm_box[1], "YOMIRA", wm, wm_track, (245, 245, 250, 255))
    # Version sits optically centred on the wordmark.
    v_box = vf.getbbox("v5.0")
    v_h = v_box[3] - v_box[1]
    v_shift = int((wm_h - v_h) * 0.5) + 6
    draw_tracked(d, tx + tw + version_gap, top + v_shift - v_box[1],
                 "v5.0", vf, v_track, (192, 132, 252, 255))
    rule_y = top + wm_h + 30
    xs2 = np.linspace(0.0, 1.0, int(text_w))
    rl = (c0[None, :] * (1 - xs2[:, None]) + c1[None, :] * xs2[:, None]).astype(np.uint8)
    rule = Image.fromarray(rl[None, :, :]).convert("RGBA").resize((int(text_w), 5))
    base.alpha_composite(rule, (tx, rule_y))
    draw_tracked(d, tx, rule_y + 47 - tl_box[1], "NEXT-GEN MANGA & COMIC READER",
                 tl, tl_track, (185, 185, 207, 255))

    save(base.convert("RGB"), ART / "banner.png")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    ART.mkdir(exist_ok=True)
    master_path = ART / "yomira_logo.svg"
    master_path.write_text(svg_master(), encoding="utf-8")
    print(f"  wrote {master_path.relative_to(ROOT)}")

    transparent = svg_transparent()
    master = svg_master()
    save(render(transparent, 1024), ART / "yomira_logo.png")
    save(render(transparent, 2048), ART / "yomira_logo-v2.png")
    save(render(master, 1024), ART / "yomira_logo-oled.png")
    save(render(master, 2048), ART / "yomira_logo-oled-2048.png")

    make_banner()
    make_android_icons()
    make_android_xml()
    make_desktop_icons()
    print("done.")


if __name__ == "__main__":
    main()



