# -*- coding: utf-8 -*-
"""
Yomira v5.0 — capture the README screenshots from the real Android UI.

The debug-only `ShowcaseActivity` composes the shipping screens (`HomeScreen`,
`MangaDetailScreen`, `WebtoonReader` + `ReaderOverlay`, `YomiraNavigationRail`)
with the repository's own fixture artwork, so the gallery is produced by the app
itself rather than by a drawing.

One process owns the emulator for the whole run: `adb` is driven directly and the
emulator is terminated at the end, which keeps repeated runs deterministic.

    python scripts/capture_android_showcases.py

Requires: Android SDK (emulator + system image), AVD named "yomira_phone",
          `gradlew :app:assembleDebug` already run, Pillow.
"""

from __future__ import annotations

import os
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "art"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
SDK = Path(os.environ.get("ANDROID_SDK_ROOT") or Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk")
ADB = SDK / "platform-tools" / "adb.exe"
EMULATOR = SDK / "emulator" / "emulator.exe"
AVD = os.environ.get("YOMIRA_AVD", "yomira_phone")

# emulator pixel size -> logical dp the layout is tuned for
PHONE = (1080, 2400, 420, 412, 915)
TABLET = (1280, 800, 160, 1280, 800)


def run(*args: str, check: bool = True) -> str:
    result = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and result.returncode != 0:
        raise RuntimeError(f"{' '.join(args)} failed: {result.stdout}{result.stderr}")
    return (result.stdout or "") + (result.stderr or "")


def adb(*args: str, check: bool = True) -> str:
    return run(str(ADB), *args, check=check)


def wait_for_device(timeout: int = 120) -> None:
    """Blocks until adb reports an attached, online device."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        out = adb("get-state", check=False).strip()
        if out == "device":
            return
        adb("reconnect", "offline", check=False)
        time.sleep(2)
    raise RuntimeError("no online adb device")


def sh(*args: str) -> str:
    """Runs a shell command, reconnecting once if the transport dropped."""
    try:
        return adb("shell", *args)
    except RuntimeError:
        wait_for_device()
        return adb("shell", *args)


def wait_for_boot(timeout: int = 240) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if sh("getprop", "sys.boot_completed").strip() == "1":
                time.sleep(4)
                return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError("emulator did not finish booting")


def configure(width: int, height: int, density: int) -> None:
    sh("wm", "size", f"{width}x{height}")
    sh("wm", "density", str(density))
    # A deterministic status bar keeps the gallery free of carrier noise.
    sh("settings", "put", "global", "sysui_demo_allowed", "1")
    for command in (
        ("enter",),
        ("clock", "-e", "hhmm", "0930"),
        ("battery", "-e", "level", "87", "-e", "plugged", "false"),
        ("network", "-e", "wifi", "show", "-e", "level", "4"),
        ("network", "-e", "mobile", "show", "-e", "datatype", "none", "-e", "level", "4"),
        ("notifications", "-e", "visible", "false"),
    ):
        sh("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", *command)


def screencap(name: str, logical: tuple[int, int]) -> None:
    sh("screencap", "-p", "/sdcard/yomira.png")
    raw = OUT / f".{name}-raw.png"
    wait_for_device()
    adb("pull", "/sdcard/yomira.png", str(raw))
    image = Image.open(raw).convert("RGB")
    if image.size != logical:
        image = image.resize(logical, Image.LANCZOS)
    image.save(OUT / f"{name}.png", optimize=True)
    raw.unlink(missing_ok=True)
    print(f"  {name}.png  {image.width}x{image.height}")


def capture(screen: str, name: str, logical: tuple[int, int], *, settle: int = 9,
            swipes: int = 0, swipe: tuple[int, int] | None = None) -> None:
    sh("am", "force-stop", "com.yomira.app")
    sh("am", "start", "-n", "com.yomira.app/.ShowcaseActivity", "--es", "screen", screen)
    time.sleep(settle)
    if swipes and swipe:
        for _ in range(swipes):
            sh("input", "swipe", "540", str(swipe[0]), "540", str(swipe[1]), "500")
            time.sleep(2)
    screencap(name, logical)


def connected_device() -> bool:
    """True when an emulator is already attached (lets the script reuse one)."""
    try:
        listing = adb("devices", check=False)
    except Exception:
        return False
    return "emulator-" in listing and "device\n" in listing.replace("\r", "")


def main() -> int:
    if not APK.is_file():
        print(f"missing {APK}; run: gradlew :app:assembleDebug", file=sys.stderr)
        return 1
    OUT.mkdir(exist_ok=True)

    emulator = None
    if connected_device():
        print("== reusing the running emulator ==")
    else:
        print("== starting emulator ==")
        emulator = subprocess.Popen(
            [str(EMULATOR), "-avd", AVD, "-no-snapshot", "-no-boot-anim",
             "-gpu", "swiftshader_indirect", "-no-audio", "-no-window",
             "-memory", "4096"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    try:
        wait_for_boot()
        print("== installing debug APK ==")
        adb("install", "-r", str(APK))
        wait_for_device()
        time.sleep(4)

        width, height, density, dp_w, dp_h = PHONE
        configure(width, height, density)
        print("== phone (412x915dp) ==")
        capture("home", "screenshot_phone_home", (dp_w, dp_h))
        capture("detail", "screenshot_phone_detail", (dp_w, dp_h), swipes=1, swipe=(1800, 1730))
        capture("reader", "screenshot_phone_reader", (dp_w, dp_h))

        width, height, density, dp_w, dp_h = TABLET
        configure(width, height, density)
        print("== tablet (1280x800dp) ==")
        capture("tablet", "screenshot_tablet_2pane", (dp_w, dp_h), settle=11)
    finally:
        if emulator is not None:
            print("== stopping emulator ==")
            adb("emu", "kill", check=False)
            try:
                emulator.wait(timeout=20)
            except Exception:
                emulator.kill()
    print("done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
