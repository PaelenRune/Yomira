# GitHub Pages yayını

GitHub Pages kaynağı `main` dalındaki `/docs` klasörü olmalıdır. `.nojekyll`
dosyası bu klasörün doğrudan statik içerik olarak yayımlanmasını sağlar.

Depo kökünden bakıldığında `docs/index.html` için `../art/` geçerli olsa da
Pages yalnızca `docs/` klasörünü yayımladığında bu yol yayın kökünün dışına çıkar.
Bu nedenle sayfa `docs/art/` içindeki yayın kopyalarına `art/` yoluyla erişir.
Tasarım görselleri değiştiğinde `art/mockup_*.svg` ve `art/banner.png` dosyalarını
`docs/art/` içine de kopyalayın. Sosyal önizleme PNG kullanır; SVG kullanmaz.

## Tarayıcı doğrulaması

Python Playwright ve Chromium kurulu olmalıdır:

```powershell
python -m pip install playwright
python -m playwright install chromium
python -m http.server 8765 --directory docs
```

Ayrı terminalde depo kökünden:

```powershell
python scripts/test_pages.py
```

Kurulu Microsoft Edge ile test etmek için `PLAYWRIGHT_CHANNEL=msedge` ortam
değişkenini ayarlayın. Farklı bir sunucu için `YOMIRA_PAGES_URL` kullanın.
Test 320, 360, 768 ve 1440 px düzenlerini, %200 metin büyütmesini, dört SVG'yi,
hızlı seçimleri, hata sonrası yeniden denemeyi, klavyeyi, azaltılmış hareketi,
sosyal önizleme görselini ve `.nojekyll` erişimini denetler. Windows ekran
görüntüleri `%LOCALAPPDATA%/Temp/opencode/yomira-pages-*.png` konumuna yazılır.
