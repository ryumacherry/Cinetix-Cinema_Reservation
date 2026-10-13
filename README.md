# 🎬 Cinetix — Aplikasi Reservasi Bioskop

Aplikasi pemesanan tiket bioskop ala CGV: lihat bioskop per kota, film sedang tayang & coming soon,
pilih jadwal (maks. 3 hari ke depan) dan studio (Reguler / 4DX / Premiere / Suites), pilih kursi,
pesan F&B + paket bundling, bayar dengan QRIS / transfer bank / kartu kredit, dapat cashback,
lalu simpan e-ticket ber-barcode. Admin dapat mengelola data dan melihat laporan penjualan.

**Teknologi:** Kotlin (OOP) · Android Jetpack Compose · MySQL 8 · JDBC · Gson · ZXing · JUnit 5 · Docker · GitHub Actions

> ⚠️ **Pembayaran QRIS, transfer bank, dan kartu kredit adalah SIMULASI.**
> Tidak terhubung ke payment gateway (Midtrans/Xendit). QR berisi nominal dibuat dengan ZXing,
> lalu pelanggan menekan "Saya sudah bayar" untuk mensimulasikan pembayaran berhasil.

## Arsitektur

```
┌────────────────────┐   HTTP/JSON    ┌───────────────────────────┐   JDBC   ┌──────────┐
│  Aplikasi Android  │ ─────────────▶ │  Backend API (Kotlin OOP) │ ───────▶ │ MySQL 8  │
│  Jetpack Compose   │   (Retrofit)   │  api → service → repository│          │ tiketku  │
└────────────────────┘                └───────────────────────────┘          └──────────┘
```

Aplikasi Android **tidak** terhubung langsung ke MySQL (tidak aman dan driver JDBC tidak cocok untuk Android).
Semua logika bisnis dan akses database berada di backend.

## Fitur
**Pelanggan:** daftar/login · bioskop per kota · film Sedang Tayang & Coming Soon · detail film ·
pilih hari (maks. 3 hari), lokasi, jam, studio · pilih kursi (anti double booking) · F&B: Popcorn, Sistagor,
Cola, Mineral + bundling (Popcorn+Cola, Sistagor+Cola, Sistagor+Mineral, Popcorn+Mineral) ·
bayar QRIS (QR otomatis sesuai nominal) / Transfer Bank (VA) / Kartu Kredit · cashback · riwayat · e-ticket + barcode untuk dicetak.

**Admin:** CRUD film, kota, cinema, studio, jadwal, harga studio · CRUD menu F&B & bundling · kelola promo cashback ·
laporan penjualan & film terlaris.

## Struktur folder
```
tiketku/
├── backend/                     # REST API Kotlin (JDK HttpServer + JDBC)
│   ├── src/main/kotlin/
│   │   ├── model/               # entity + abstract class + interface (OOP)
│   │   ├── repository/          # akses MySQL (JDBC)
│   │   ├── service/             # logika bisnis
│   │   ├── exception/           # custom exception
│   │   ├── util/                # koneksi DB, QR/barcode, hash password
│   │   ├── api/                 # router + endpoint REST
│   │   └── Main.kt
│   ├── src/test/kotlin/         # unit test (repository palsu, tanpa MySQL)
│   ├── build.gradle.kts
│   └── Dockerfile
├── android/                     # aplikasi Android (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/tiketku/app/
│       ├── data/                # Retrofit, DTO, session
│       ├── ui/                  # layar: auth, home, booking, tiket, profil, admin
│       └── util/                # format rupiah, QR/barcode, cetak tiket
├── data/schema.sql, seed.sql    # skema & data awal MySQL
├── docs/                        # user story, class diagram, API, pembagian tugas
├── docker-compose.yml           # MySQL + backend
└── .github/workflows/ci.yml     # unit test + build APK otomatis
```

## Cara menjalankan

### Cara 1 — Docker (paling mudah)
Butuh Docker Desktop.
```bash
git clone <link-repo>
cd tiketku
docker compose up -d --build      # MySQL + backend API
docker compose logs -f api        # tunggu sampai muncul "[OK] TIKETKU API berjalan"
```
Cek di browser: http://localhost:8080/api → `{"status":"OK"}`.
E-ticket (.txt) dan barcode (.png) yang terbit akan muncul di folder `output/`.

Reset database (mis. setelah mengubah `seed.sql`): `docker compose down -v` lalu `docker compose up -d --build`.

### Cara 2 — Tanpa Docker
Butuh JDK 17+ dan MySQL 8.
```bash
mysql -u root -p < data/schema.sql
mysql -u root -p tiketku < data/seed.sql

cd backend
# Linux/Mac
DB_URL="jdbc:mysql://localhost:3306/tiketku?useSSL=false&allowPublicKeyRetrieval=true" DB_USER=root DB_PASS=passwordmu ./gradlew run
# Windows (PowerShell)
$env:DB_URL="jdbc:mysql://localhost:3306/tiketku?useSSL=false&allowPublicKeyRetrieval=true"; $env:DB_USER="root"; $env:DB_PASS="passwordmu"; .\gradlew.bat run
```

### Menjalankan aplikasi Android
1. Buka folder `android/` di **Android Studio** (Ladybug atau lebih baru), tunggu Gradle sync selesai.
2. Pastikan backend sudah berjalan.
3. **Emulator:** langsung Run ▶. Aplikasi memakai `http://10.0.2.2:8080/` (alamat komputer dari dalam emulator).
4. **HP asli:** HP dan laptop harus satu Wi-Fi. Cari IP laptop (`ipconfig` / `ip addr`), lalu di layar login
   tekan ikon ⚙️ dan isi `http://<IP-laptop>:8080/`. Izinkan port 8080 di firewall.

### Akun demo
| Peran | Email | Password |
|---|---|---|
| Pelanggan | `budi@mail.com` | `budi123` |
| Admin | `admin@tiketku.id` | `admin123` |

Kartu kredit uji (simulasi): `4111 1111 1111 1111`.

### Unit test
```bash
cd backend && ./gradlew test
```
39 unit test menguji model (polymorphism harga studio, pembayaran, cashback, stok F&B) dan service
(validasi 3 hari, double booking, rollback stok, alur pembayaran) memakai repository palsu, tanpa MySQL.
GitHub Actions menjalankannya otomatis setiap push, sekaligus memvalidasi `schema.sql`/`seed.sql` dan membangun APK.

## Konsep OOP
| Konsep | Penerapan |
|---|---|
| Abstract class | `Studio`, `Pembayaran`, `MenuFnb`, `Pengguna` |
| Inheritance | `Reguler/FourDX/Premiere/Suites`, `Qris/TransferBank/KartuKredit`, `Makanan/Minuman/Bundling`, `Pelanggan/Admin` |
| Polymorphism | `studio.hargaTiket()`, `pembayaran.biayaAdmin()` & `proses()`, `cashback.hitung()` |
| Interface | `Cashback`, `Cetakable`, `CrudRepository<T>` dan repository lain |
| Encapsulation | stok F&B & saldo cashback `private set`; status bayar hanya berubah lewat `bayar()` |
| Data class | `Film`, `Cinema`, `Jadwal`, `Kursi`, `Tiket` |
| Custom exception | `KursiSudahDipesanException`, `HariTidakValidException`, `PembayaranGagalException`, `StokHabisException` |

Diagram lengkap: [`docs/02-class-diagram.md`](docs/02-class-diagram.md).

## Dokumentasi
- [User story & requirement](docs/01-user-story.md)
- [Class diagram, ERD, arsitektur](docs/02-class-diagram.md)
- [Dokumentasi REST API](docs/03-api.md)
- [Pembagian tugas & alur 3 hari](docs/04-pembagian-tugas.md)

## Masalah yang sering terjadi
| Masalah | Solusi |
|---|---|
| Android: "Tidak bisa terhubung ke server" | Backend belum jalan, atau alamat server salah. Emulator: `http://10.0.2.2:8080/`. HP asli: IP laptop + satu Wi-Fi |
| `Public Key Retrieval is not allowed` | Tambahkan `allowPublicKeyRetrieval=true&useSSL=false` di `DB_URL` |
| `Connection refused` di Docker | Di dalam container, host database adalah `db`, bukan `localhost` |
| Seed tidak berubah | Script init MySQL hanya jalan sekali. Jalankan `docker compose down -v` |
| Port 3306 bentrok | MySQL Docker dipetakan ke port host `3307` |
| Jadwal kosong | Backend mengisi jadwal 3 hari otomatis saat start. Admin juga bisa tekan "Generate 3 hari" |

## Tim
| Nama | NIM | Peran |
|---|---|---|
| ... | ... | Project Lead |
| ... | ... | Domain Developer |
| ... | ... | Business Logic |
| ... | ... | UI (Android) |
| ... | ... | QA + Data |
| ... | ... | DevOps |
