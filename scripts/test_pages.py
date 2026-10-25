"""Run against docs/ served as the web root (requires Python Playwright).

python <skill>/scripts/with_server.py --server "python -m http.server 8765 --directory docs" --port 8765 -- python scripts/test_pages.py
"""

import os
import re
from pathlib import Path

from playwright.sync_api import expect, sync_playwright


BASE_URL = os.environ.get("YOMIRA_PAGES_URL", "http://127.0.0.1:8765/")
SCREENSHOTS = Path(os.environ.get("LOCALAPPDATA", ".")) / "Temp/opencode"
SCREENSHOTS.mkdir(parents=True, exist_ok=True)
PUBLIC_COPY = re.compile(r"vault|\+18|ecchi|hentai|porhwa|porn|milf|midara", re.I)
ROOT = Path(__file__).resolve().parents[1]
for public_file in (ROOT / "README.md", ROOT / "docs/index.html", *ROOT.glob("art/*.svg"), *ROOT.glob("docs/art/*.svg")):
    assert not PUBLIC_COPY.search(public_file.read_text(encoding="utf-8")), public_file


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True, channel=os.environ.get("PLAYWRIGHT_CHANNEL"))
    page = browser.new_page()
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    for width in (320, 360, 768, 1440):
        page.set_viewport_size({"width": width, "height": 900})
        page.goto(BASE_URL, wait_until="networkidle")
        assert not PUBLIC_COPY.search(page.content())
        assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), width
        image = page.locator("#mockupImage")
        expect(image).to_have_js_property("complete", True)
        assert image.evaluate("image => image.naturalWidth > 0")
        buttons = page.locator(".tab-btn")
        for index in range(buttons.count()):
            button = buttons.nth(index)
            button.click()
            expect(button).to_have_attribute("aria-pressed", "true")
            expect(image).to_have_attribute("alt", button.get_attribute("data-alt"))
            assert image.evaluate("image => image.naturalWidth > 0")
        page.screenshot(path=str(SCREENSHOTS / f"yomira-pages-{width}.png"), full_page=True)

    # Rapid switching must settle on the last request.
    page.evaluate("document.querySelectorAll('.tab-btn').forEach(button => button.click())")
    expect(page.locator("#mockupImage")).to_have_attribute("aria-busy", "false")
    expect(page.locator(".tab-btn").last).to_have_attribute("aria-pressed", "true")
    expect(page.locator("#mockupImage")).to_have_attribute("src", re.compile("mockup_smart_match.svg$"))

    # A failed load keeps the prior selection and offers a retry.
    page.locator(".tab-btn").first.evaluate("button => button.dataset.src = 'art/missing.svg'")
    page.locator(".tab-btn").first.click()
    expect(page.locator("#previewStatus")).to_contain_text("yüklenemedi")
    expect(page.locator(".tab-btn").last).to_have_attribute("aria-pressed", "true")
    expect(page.locator("#mockupImage")).to_have_attribute("aria-busy", "false")
    page.locator(".tab-btn").first.evaluate("button => button.dataset.src = 'art/mockup_home.svg'")
    page.locator(".tab-btn").first.click()
    expect(page.locator(".tab-btn").first).to_have_attribute("aria-pressed", "true")

    page.reload(wait_until="networkidle")
    page.emulate_media(reduced_motion="reduce")
    page.locator(".tab-btn").nth(1).focus()
    page.keyboard.press("Enter")
    expect(page.locator(".tab-btn").nth(1)).to_have_attribute("aria-pressed", "true")
    assert page.locator("#mockupImage").evaluate("image => image.getAnimations().length === 0")
    page.set_viewport_size({"width": 320, "height": 900})
    page.add_style_tag(content="html { font-size: 200%; }")
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth")
    expect(page.locator(".btn-primary")).to_have_attribute(
        "href", "https://github.com/PaelenRune/Yomira/releases/latest"
    )
    for selector in ('meta[property="og:image"]', 'meta[name="twitter:image"]'):
        url = page.locator(selector).get_attribute("content")
        response = page.request.get(BASE_URL + "art/" + url.rsplit("/", 1)[-1])
        assert response.ok, url
    assert page.request.get(BASE_URL + ".nojekyll").ok
    assert not errors, errors
    browser.close()
    print("PASS: 4 viewports, images, switching, failure recovery, keyboard, reduced motion, 200% text, metadata and .nojekyll")
