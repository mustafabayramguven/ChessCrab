# 🦀 ChessCrab

Kendi sunucunda çalışan, **tamamen offline** kullanılabilen, chess.com/lichess tarzı bir satranç uygulaması. Baştan sona Rust.

- **CrabFish**: kendi satranç motorumuz (Rust). Telefonda, sunucuda ve web'de aynı motor.
- **Oyun doğruluğu analizi**: lichess formülüyle doğruluk yüzdesi, ACPL, chess.com tarzı hamle sınıfları (Parlak !!, Harika !, En iyi, Mükemmel, İyi, Kitap, Belirsiz ?!, Hata ?, Kaçırılan, Büyük hata ??, Zorunlu), değerlendirme grafiği, kritik anlar ve Türkçe yorumlar. Bütün çekirdeklerde paralel çalışır.
- **Dersler**: *Satranç Temelleri* ve *Açılıştan Orta Oyuna* kursları (53 ders): prensipler, İtalyan/İki At, İspanyol/İskoç/Petrov, Sicilya, Fransız/Caro-Kann/İskandinav, Vezir Gambiti/Londra, Hint savunmaları, piyon yapıları ve planlar, taktik motifler.
- **Repertuvar çalışması** (Chessable tarzı) + aralıklı tekrar (SRS): beyaz için 1.e4/İtalyan, siyah için Caro-Kann ve VG Reddi.
- **Senkronizasyon**: telefon ↔ sunucu ↔ web. Oyunlar, ders ilerlemesi, SRS kartları. Offline-first; çakışmada HLC + "son yazan kazanır".
- **Android**: Rust çekirdek (UniFFI) + Jetpack Compose, Material You. Motor, analiz ve dersler internetsiz çalışır.

## Mimari

```
crates/
  core/       oyun modeli, PGN (lichess %clk dahil), saat, ECO açılış tanıma   (kurallar: shakmaty)
  engine/     CrabFish: PVS + TT + null-move + LMR + SEE + PeSTO değerlendirme, MultiPV,
              8 zorluk seviyesi, açılış kitabı, UCI ikilisi (crabfish), harici UCI desteği
  analysis/   doğruluk, ACPL, hamle sınıflandırma, çok iş parçacıklı oyun analizi
  lessons/    kurs/ders/repertuvar içeriği (TOML, ikiliye gömülü), ders oturumu, SRS
  sync/       HLC, LWW birleştirme, SQLite yerel depo
  server/     Axum + SQLite: kimlik, /api/sync, analiz, dersler, web arayüzü
  ffi/        UniFFI → Kotlin (Android)
android/      Jetpack Compose uygulaması
```

Tüm istemciler aynı Rust kodunu kullanır: ders oturumu mantığı, analiz, motor ve birleştirme bir kez yazıldı.

## Sunucu

Docker (amd64 + arm64 imajı CI'da yayınlanır):

```sh
docker compose up -d        # http://sunucu:3437
```

Kaynaktan:

```sh
cargo run --release -p chesscrab-server
```

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `CHESSCRAB_ADDR` | `0.0.0.0:3437` | dinlenecek adres |
| `CHESSCRAB_DB` | `./data/chesscrab.db` | SQLite dosyası |
| `CHESSCRAB_ALLOW_REGISTER` | `0` | `1` ise herkes kayıt olabilir; değilse sadece ilk kullanıcı |
| `CHESSCRAB_STOCKFISH` | – | harici UCI motoru yolu (Docker imajında Stockfish hazır) |
| `CHESSCRAB_ANALYSIS_THREADS` | çekirdek sayısı | analiz iş parçacıkları |
| `CHESSCRAB_MAX_DEPTH` | `18` | izin verilen en büyük analiz derinliği |

Web arayüzü sunucunun kökünde: CrabFish'e karşı oyna (saatli/saatsiz), PGN analizi, dersler, repertuvar ve oyun listesi. İlk açılışta **Hesap** sekmesinden kayıt ol; ilk kullanıcı sunucunun sahibidir.

Caddy ile:

```
satranc.ornek.com {
    reverse_proxy 127.0.0.1:3437
}
```

## Android

Her `main` push'unda GitHub Actions APK üretir (**Actions → Android APK → ChessCrab-apk**). `v*` etiketi atınca APK sürüm olarak yayınlanır. APK, kişisel kullanım için debug anahtarıyla imzalıdır.

Yerelde derlemek için (Android SDK + NDK gerekir):

```sh
cargo install cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 --platform 26 -o android/app/src/main/jniLibs build --release -p chesscrab-ffi
cargo run -p chesscrab-ffi --features cli --bin uniffi-bindgen -- generate \
  --library target/aarch64-linux-android/release/libchesscrab_ffi.so --language kotlin --out-dir android/app/src/main/java
cd android && gradle assembleRelease      # ya da Android Studio ile aç
```

Uygulamada **Daha → Senkronizasyon** bölümüne sunucu adresini gir ve giriş yap. lichess/chess.com uygulamasından bir oyunu **Paylaş → ChessCrab** ile gönderirsen doğrudan analize düşer.

## CrabFish'i UCI motoru olarak kullan

```sh
cargo build --release -p chesscrab-engine
./target/release/crabfish       # Cute Chess, En Croissant, Arena… herhangi bir UCI arayüzü
```

Ek komutlar: `d` (FEN), `eval`, `perft N`.

Komut satırından PGN analizi:

```sh
cargo run --release -p chesscrab-analysis --example analyse 14 < oyun.pgn
```

## Doğruluk nasıl hesaplanır?

lichess'in açık kaynak formülleri:

- Kazanma yüzdesi: `50 + 50 · (2 / (1 + e^(−0.00368208 · cp)) − 1)` (cp ±1000'e kırpılır)
- Hamle doğruluğu: `103.1668 · e^(−0.04354 · (önce − sonra)) − 3.1669 + 1`
- Oyun doğruluğu: oynaklığa göre ağırlıklı ortalama ile harmonik ortalamanın ortalaması

Hamle sınıfları kazanma yüzdesi kaybına göre (chess.com eşikleri: ≤%2 Mükemmel, ≤%5 İyi, ≤%10 Belirsiz, ≤%20 Hata, üstü Büyük hata). Parlak = SEE'ye göre net taş fedası olan en iyi hamle; Harika = ikinci en iyi hamlenin belirgin şekilde kötü olduğu tek hamle; Kaçırılan = rakibin hatasını cezalandırmamak. Tahmini Elo tek oyundan kaba bir ACPL eşlemesidir, eğlence amaçlıdır.

## Ders yazmak

Dersler `crates/lessons/content/lessons/*.toml` altında:

```toml
id = "ornek"
title = "Örnek ders"
summary = "Kısa açıklama"
level = "orta"              # baslangic | orta | ileri
orientation = "white"
setup = "e4 e5 Nf3 Nc6"     # sessizce oynanır

[[steps]]
text = "Açıklama metni"
arrows = ["f1c4"]
highlights = ["f7"]

[[steps]]
text = "Fili geliştir"
expect = "Bc4"              # beklenen hamle (SAN)
also = ["Bb5"]              # kabul edilen alternatifler
reply = "Bc5"               # rakibin otomatik cevabı
puzzle = true               # motorla sıkı doğrulama

[[steps]]
quiz = { question = "Soru?", options = ["A", "B"], answer = 1, explain = "Neden" }
```

Sonra dersi `crates/lessons/content/courses/*.toml` içindeki bir bölüme ekle. Testler her dersi baştan sona oynar, her hamlenin yasallığını ve CrabFish'e göre makul olduğunu (bulmacalarda en iyi hamleye 0.8 piyon yakın, diğerlerinde 1.6) kontrol eder:

```sh
cargo test --release -p chesscrab-lessons
cargo run --release -p chesscrab-lessons --features audit --example audit <filtre>   # ayrıntılı rapor
```

## Lisans

GPL-3.0-or-later. Kurallar için kullanılan [shakmaty](https://github.com/niklasf/shakmaty) GPL-3.0 lisanslıdır. Taş çizimleri projeye özgüdür.
