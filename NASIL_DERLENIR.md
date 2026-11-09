# Bilgi Kartları 3.0 – APK nasıl yapılır?

## Yol 1: Android Studio (bilgisayarda)
1. Android Studio'yu kur: https://developer.android.com/studio
2. Bu zip'i bir klasöre çıkar.
3. Android Studio → **Open** → **BilgiKartlari** klasörünü seç.
4. Alttaki "Gradle sync" bitene kadar bekle (ilk seferde birkaç dakika, internet gerekir).
5. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
6. Bildirimdeki **locate**'e tıkla: `app/build/outputs/apk/debug/app-debug.apk`
7. APK'yı telefona gönder ve yükle ("bilinmeyen kaynak" izni isteyebilir).
   Eski sürüm yüklüyse üstüne yüklenir, kartlar silinmez.

## Yol 2: GitHub (Android Studio kurmadan)
1. github.com'da ücretsiz hesap aç, **New repository** ile boş depo oluştur.
2. **uploading an existing file** ile zip içindeki tüm dosya ve klasörleri yükle (`.github` dahil).
3. **Actions** sekmesinde "APK oluştur" kendiliğinden çalışır (3-5 dk).
4. Bitince **Artifacts** bölümünden **BilgiKartlari-apk**'yı indir, içinden `app-debug.apk` çıkar.

## Claude'dan kart almak (her yeni konu için)
1. Konunun fotoğrafını Claude'a gönder.
2. Claude `@ders:` ve `@ünite:` ile başlayan bir liste verir → listeyi **kopyala**.
3. Uygulamada **Ekle → Panodan içe aktar** → önizlemeyi kontrol et → **İçe aktar**.
   Kartlar doğru dersin altına yerleşir; önizlemede dersi değiştirebilirsin.
   (Claude'un mesajını "Paylaş → Bilgi Kartlarına aktar" ile de gönderebilirsin.)

Liste biçimi:
```
@ders: Sosyal Bilgiler
@ünite: Kültürel Mirasımız
@simge: 🏛️
@renk: 3
Soru | Cevap
Soru | Cevap
```
Renk numaraları: 1 mor, 2 mavi, 3 turkuaz, 4 yeşil, 5 turuncu, 6 kırmızı, 7 pembe, 8 eflatun.
Aynı ünite adıyla tekrar içe aktarılırsa yeni kartlar o üniteye eklenir, aynı sorular iki kez eklenmez.

## Yenilikler (3.0)
- **Ders → Ünite düzeni**: ana ekranda dersler, dersin içinde üniteler, ünitenin içinde kartlar.
- Ders ekranında **Karışık çalış / Karışık test / Bu dersin tekrarları** (tüm üniteler birlikte).
- Ünite düzenlerken başka derse taşıma; ünite ekranında derse dönüş bağlantısı.
- **Pofi**: baykuş-kedi çalışma arkadaşı (mutlu, sevinçli, düşünceli, "olsun" yüzleri).
  Ana ekranda selam verir, testte tepki verir, sonuç ekranında kutlar, istatistikte yorum yapar.
- İçe aktarmada ders seçimi; olmayan ders otomatik oluşturulur.
- İstatistikler derslere göre, her dersin altında ünite çubuklarıyla.
- 2.0'dan güncellemede eski üniteler "Derslerim" adlı dersin altına taşınır, hiçbir kart kaybolmaz.

## 2.0'daki özellikler
- Yeni tasarım: Poppins yazı tipi, yeni logo, açılış ekranı, renkli ünite kartları, ilerleme halkaları.
- Ana ekranda karşılama, çalışma serisi (🔥), bugünkü kart sayısı ve genel başarı.
- Her üniteye simge ve renk seçimi.
- İçe aktarma: panodan, dosyadan ya da başka uygulamadan paylaşarak; önizleme ekranı.
- **Test çöz**: 4 şıklı test (şıklar ünitenin diğer cevaplarından otomatik oluşur).
- 🔊 Sesli okuma (telefonun Türkçe metin okuma sesiyle).
- Titreşimli geri bildirim, kart çevirme animasyonu.
- Sonuç ekranı: yıldızlar, başarı halkası, "Bilemediklerimi tekrar et".
- İstatistikler: son 7 gün grafiği, seri, ünite başarıları, en çok zorlanılan kartlar, test/kart geçmişi.
- Yedeği kaydet / yedekten yükle (başka telefona taşımak için).
- Ünite paylaşma (öğrencilere WhatsApp ile gönder, onlar içe aktarsın).
