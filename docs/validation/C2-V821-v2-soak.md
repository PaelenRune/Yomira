# C2-V821-v2 — cihaz kontrol listesi

Durum: otomatik birim testleri cihaz doğrulaması yerine geçmez. Aşağıdaki maddeler bu oturumda henüz cihazda çalıştırılmadı.

## Onboarding depo adımı

- TR ve EN ile ilk kurulumda dil → yaş → etiketler → favoriler → depo adımına ilerle.
- Ana mor düğme **Depo ekle / Add repository** göstermeli; **Atla / Skip** ikincil metin düğmesi olmalı.
- Ana düğme yalnız onay diyaloğunu açmalı; iptal, depo eklemeden aynı adımda kalmalı.
- Onaydan sonra depo bir kez eklenmeli, kurulum tamamlanmalı; kaydetme sırasında çift dokunma engellenmeli.
- İkincil Atla, depo eklemeden kurulumu bitirmeli. Geri/ileri favori seçimlerini korumalı.
- Ayarlardan anketi tekrarlama akışı depo adımını göstermemeli; son düğme Başla eylemini korumalı.

## Diğer paket kontrolleri

- Detay üst bloğunda Anime/K-Drama/Donghua/OVA rozetleri ve başta kademe rozeti; Vault kilitliyken explicit uyarlama sızıntısı olmamalı.
- Depolama: tarama ilerlemesi, iptal, elle yeniden başlatma; ekran kilidi/veri tasarrufu/ağ değişiminde duraklama. Okuma ve kütüphane durumu değişmemeli.
- Depolama: SAF ile yedek oluştur; adı `yomira-backup-YYYYMMDD-HHmm.tachibk` olmalı. İptal sessiz, bozuk dosya hata toast'u olmalı (TR/EN).
- Yeni/boş kurulumda yedeği geri yükle: ad/kapak, SAFE/MATURE/EXPLICIT kademesi, favoriler/etiketler ve okuma geçmişi korunmalı. Mevcut yeni durum eski yedekle geriye alınmamalı.
- Kaynak erişimini keserek okuyucuda mevcut hata metnini gör; ağı düzeltip tek **Yeniden dene / Retry** ile yeniden çözümleme başlat. Beklerken düğme kaybolmalı; tekrar hata ve Geri çalışmalı.

Yedek kapsamı kitaplık ve okuma geçmişidir; indirilen dosyalar ve güvenlik tercihleri dahil değildir. Yedek şifrelenmez; Vault başlıkları dahil olabileceği kullanıcıya gösterilir.
