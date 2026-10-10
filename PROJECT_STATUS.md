# Kinetica Dual Circle — Turkish Edition

## Mimari
- Kinetica ana motoru korunur: DTW, trie, iki başparmak akışı, tahmin ve kişisel öğrenme.
- Dual Circle zorunlu değildir; `pref_dual_circle_mode` ile Standart / Dual Circle seçilebilir.
- Radial UI, `DualCircleSurface` altında izole edilmiştir; `KeyboardView` yalnızca yönlendirme ve standart yüzey kararını yapar.
- Latin ve Türkçe Dual Circle kullanılabilir; diğer alfabeler otomatik olarak standart Kinetica yüzeyine döner.

## Aşamalar 1–6
1. **Input/hit-test düzeltmesi:** Unicode/ASCII kodları yerine alphabet-local 0..N-1 kodlar kullanılıyor; halkalar gerçek segment sayılarıyla hit-test ediliyor.
2. **Seçilebilir mod:** Dual Circle varsayılan olarak kapalı; mevcut Kinetica standart klavye güvenli geri dönüş olarak kalıyor.
3. **Surface izolasyonu:** Radial çizim, geometri, özel tuşlar ve hit-test `DualCircleSurface` + `DualCircleLayout` içinde.
4. **Türkçe alfabe ve kayıt:** 29 harf, `tr_TR` IME subtype, `Alphabet.TURKISH`, `qwerty_tr.json`.
5. **Türkçe veri/case:** Türkçe FrequencyWords/Tatoeba asset üretimi CI'ya eklendi; yerel seed sözlük mevcut; `İ/ı` locale-aware; Türkçe native harfler AccentFolder tarafından Latin tabanlara çöktürülmüyor; kişisel bigram anahtarları alfabe-aware.
6. **Test kapsamı:** Dual Circle partition, radial math, 29 kod, Türkçe dictionary/bigram izolasyonu, locale case ve otomatik büyük harf testleri eklendi.

## Derleme öncesi düzeltmeler
- `tools/generate_assets.py` içinde Türkçe `WORD_RE` tanımı eklendi; `--lang tr` artık `KeyError: 'tr'` ile düşmüyor.
- `NonLatinDictionaryTest` Türkçenin bilinçli olarak opt-in olması ve eski dokuz varsayılan dil davranışının korunmasıyla hizalandı.
- İtalyanca `language_entries` 14 öğeye çıkarıldı ve yeni `subtype_tr_tr`, Dual Circle etiketleri ve tercih metinleri eklendi.
- Yerel sandbox'ta asset indirme hâlâ DNS nedeniyle mümkün değil; CI'ın gerçek GitHub Actions ortamında ağ erişimiyle üreteceği full Türkçe assetler bekleniyor.

## Türkçe Dual Circle bölümü
- Sol iç: `a e ı i o u` (6)
- Sol dış: `ö ü ç c f g j p` (8)
- Sağ iç: `n r l k d m` (6)
- Sağ dış: `y t s b z ş h v ğ` (9)

## Doğrulama
- XML kaynaklarının parse kontrolü: geçti
- İtalyanca/İngilizce resource ve array hizalama kontrolü: geçti
- Türkçe JSON layout: 29/29 harf, eksik/fazla yok
- `generate_assets.py` syntax ve Türkçe regex kontrolü: geçti
- `generate_assets.py --lang tr`: doğrulama aşamasını geçti, ardından sandbox DNS nedeniyle kaynak indirme başarısız oldu
- Saf Kinetica engine Kotlin derlemesi (`WordComposer.kt` hariç): geçti; yalnızca mevcut warning'ler kaldı
- Stage 1–6 JVM test harness: geçti
- Dual Circle geometry/hit-test entegrasyonu: geçti
- Türkçe dictionary/bigram izolasyonu: geçti
- Tam `./gradlew test`: **ortam engeli**; Gradle 8.7 indirilemedi (`services.gradle.org` DNS/network erişimi yok)

## Not
Tam Android derleme/testi GitHub Actions veya Gradle 8.7 dağıtımı ve Android SDK erişimi olan bir ortamda çalıştırılmalıdır.

## Manuel APK derleme
- `.github/workflows/build-apk.yml` eklendi.
- GitHub Actions sekmesinde **APK Derle** workflow'u görünür.
- `Run workflow` ile seçilen branch'teki kaynak derlenir.
- Build öncesi Türkçe sözlük/bigram asset'leri GitHub Actions ortamında üretilir.
- `./gradlew assembleDebug` çalıştırılır.
- Başarılı olursa `kinetica-debug.apk` adıyla `kinetica-debug-apk` artifact'i yüklenir.
- Workflow `gradlew` için `chmod +x` uyguladığı için zip/repository dosya izinleri 644 olsa bile wrapper çalıştırılabilir.
