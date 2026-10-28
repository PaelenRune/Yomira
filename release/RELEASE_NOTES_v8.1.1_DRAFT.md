# 🌟 Yomira v8.1.1 — Sürüm Notları Taslağı

Yomira v8.1.1, kaynak seçimini sizin elinize bırakıyor. Temiz kurulum, seçerek eklediğiniz depolar ve sevdiğiniz serilerle şekillenen öneriler; daha anlaşılır ayarlar ve iyileştirilmiş kaynak eşleştirmesiyle buluşuyor.

> **Taslak:** Nihai QA sonuçları bekleniyor. Aşağıdaki yenilikler uygulanmış davranışları anlatır; tüm eklentilerin cihaz üzerinde sorunsuz çalıştığı anlamına gelmez.

---

### 🚀 Öne Çıkan Yenilikler ve İyileştirmeler

#### 🌱 1. Temiz Kurulum: Kaynaklarınızı Siz Seçin
- **Sıfır önceden eklenmiş depo ve eklenti:** Yeni kurulumda okuma kaynakları otomatik olarak eklenmez.
- **Başlangıçta açık seçenekler:** Sunulan deponun resmî dizinini uygulamaya ekleyebilir, resmî web sayfasını açabilir veya bu adımı atlayabilirsiniz.
- **Kontrol sizde:** Depo eklemek, eklenti kurmak değildir. Hiçbir eklenti otomatik kurulmaz; kullanmak istediklerinizi ayrıca seçersiniz.

#### 🧩 2. Depo Bağlantısından Eklenti Kurulumuna
- **Bağlantıyla depo ekleme:** `yomira://add-repo` ve desteklenen uyumlu depo bağlantıları uygulamayı açar. Depo adresini onayladıktan sonra ekleme yapılır.
- **Yol gösteren Eklentiler ekranı:** Henüz deponuz yoksa nereden başlayacağınız açıklanır. Eklediğiniz depoların sunduğu eklentileri ve kaynaklarını listeleyebilir, arama ve filtrelerle seçiminizi daraltabilirsiniz.
- **Tek dokunuşla kurulum başlatma:** İstediğiniz eklentinin kurulumunu kendi satırından başlatabilirsiniz. Kurulum, sizin seçiminizle gerçekleşir.

#### 🔄 3. Yükseltmede Kaynak Yönetimine Yeni Başlangıç
- **Bir defalık temizlik:** Önceki sürümden yükseltirken kayıtlı depolar ve kurulu eklentiler kaldırılır. Bu işlem yalnızca otomatik eklenenlerle sınırlı değildir; ardından kullanmak istediğiniz kaynakları yeniden seçmeniz gerekir.
- **Kişisel koleksiyonunuz korunur:** Kütüphane, gizli kasa ve okuma geçmişi bu temizliğin kapsamı dışındadır. Dil, içerik tercihleri ve başarımlar da korunur.
- **Açık bilgilendirme:** Değişikliği açıklayan bildirim, siz onaylayana kadar bekleyen durumunu korur. Başarıyla tamamlanan temizlik sonraki açılışlarda tekrarlanmaz; kesintiye uğrarsa yeniden denenir.
- **Eski kaynaklar kendiliğinden geri gelmez:** Temizlenen kaynakların otomatik eşitlemeyle yeniden eklenmesini önleyen düzenleme yapıldı.

#### 💜 4. Öneriler Sevdiğiniz Serilerle Başlasın
- **En fazla beş favori seri:** Başlangıç anketindeki tür seçiminin yerini sevdiğiniz serileri seçebileceğiniz adım alır. Bir seriyle devam edebilir veya seçimi atlayabilirsiniz.
- **Zevkinize göre başlangıç:** Seçtiğiniz serilerin türleri, okuma ve kaydetme geçmişinizle birlikte ana sayfadaki kişiselleştirilmiş önerilere katkıda bulunur.
- **Önceki tercihler kaybolmaz:** Favori seri seçimi bulunmayan mevcut kullanıcılar için önceki tür tercihleri kullanılmaya devam eder.

#### 📚 5. Okumaya Geçerken Daha İyi Kaynak Eşleştirmesi
- **Kurulu kaynaklar arasında değerlendirme:** Güvenilen ve kurulu kaynaklar; dil uyumu, bölüm bütünlüğü, güncellik, doğrulanmış erişilebilirlik ve hız ölçütleriyle sıralanır. Seçim yalnızca bölüm sayısına dayanmaz.
- **Önbellekteki seriler de yeniden değerlendirilir:** Seri detayından okuma, ana sayfadan devam etme ve güncellemelerden bölüme geçiş akışlarındaki eşleştirmeyi atlayan yollar düzeltildi.
- **Bölümünüz korunur:** Kaynak değiştiğinde istenen bölüm numarası yeni kaynakla eşleştirilir. Uygun kaynak veya aynı bölüm bulunamazsa başka bir bölüme sessizce geçilmez.

#### 🔗 6. Güncel Depo Biçimleriyle Uyumluluk
- **Protobuf dizin desteği:** Desteklenen yeni depo dizinleri okunabilir; başlangıçta sunulan depo adresi yeni dizin biçimine güncellendi.
- **Daha açıklayıcı hata:** Depo eski uygulama sürümüne işaret eden bir yanıt döndürdüğünde, genel bağlantı hatası yerine deponun bu sürümü tanımadığını açıklayan mesaj gösterilir.
- **Eski adresler için not:** Daha önce kaydedilmiş eski JSON depo adresleri otomatik dönüştürülmez. Eski adres hâlâ kayıtlıysa kaldırıp güncel depo adresini ekleyin.
- **Uyumluluğun sınırı:** Ayrı bir haricî eklenti listesine yönlendiren depo biçimleri henüz desteklenmez. Dizin desteği, listelenen her eklentinin kurulup çalışacağının garantisi değildir.

#### 🎨 7. Daha Düzenli Ayarlar, Tutarlı Görünüm
- **İhtiyacınıza göre gruplama:** Dil ve okuma, içerik filtreleri, eklentiler ve depolar, gizlilik ve kasa, eşitleme ve yedekleme, depolama ve sıfırlama ile uygulama bilgileri daha anlaşılır bir sırada sunulur.
- **Türkçe ve İngilizce metin uyumu:** Ayarlardaki karşılıklar ve depo açıklamaları eşitlendi; doğrulanmamış kaynak sayıları yerine işlevi açıklayan metinler kullanıldı.
- **OLED Plum bütünlüğü:** Ayarların bölüm aralıkları, işlem satırları ve diyalogları mevcut koyu tema tonlarıyla tutarlı hâle getirildi. Uzun yerelleştirilmiş başlıklar için alan korundu.

---

### 📦 Sürüm ve Doğrulama Bilgileri

| Bilgi | Değer |
| --- | --- |
| Sürüm | `8.1.1` |
| Sürüm Kodu (versionCode) | `21` |
| Nihai test sayısı ve QA sonucu | &lt;QA tamamlanınca netleşecek&gt; |
| Release APK | `release/yomira-v8.1.1.apk` |
| APK boyutu | `19.527.165 bayt` |
| SHA-256 | `1eedd3dde7ab813e409672a0c12a928a6cca64f4b8906a4effe709c15af4a7bd` |

APK bilgileri son derleme teslim kaydından (`HO-V811-FIX-2.json`) alınmıştır. Bu kayıt, birim testleri ile release ve QA derlemelerinin başarıyla tamamlandığını bildirir. Eklenti kurulumu/çalıştırılması ve yükseltme bildiriminin cihaz üzerindeki son doğrulaması henüz tamamlanmış olarak belgelenmemiştir.
