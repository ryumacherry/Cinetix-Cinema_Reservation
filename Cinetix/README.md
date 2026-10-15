# 🎬 Cinetix — Aplikasi Reservasi Bioskop

Aplikasi pemesanan tiket bioskop: lihat bioskop per kota, film sedang tayang & coming soon,
pilih jadwal (maks. 3 hari ke depan) dan studio (**Reguler / Premiere / Suites**), pilih kursi langsung
dari **denah studio** (section dekat/jauh layar + jalan tengah), bayar dengan QRIS / transfer bank /
kartu kredit, lalu simpan e-ticket ber-barcode. Beli **minimal 5 tiket** dapat **diskon** sesuai tipe studio.
Admin dapat mengelola data dan melihat laporan penjualan.

Aplikasi berbasis **web**: dibuka di browser pada `http://localhost:8080/`, tanpa instalasi tambahan.

**Teknologi:** Kotlin (OOP) · HTML/CSS/JavaScript · MySQL 8 · JDBC · Gson · ZXing · JUnit 5 · Docker · GitHub Actions

> ⚠️ **Pembayaran QRIS, transfer bank, dan kartu kredit adalah SIMULASI.**
> Tidak terhubung ke payment gateway. QR berisi nominal dibuat dengan ZXing,
> lalu pelanggan menekan "Saya sudah bayar" untuk mensimulasikan pembayaran berhasil.

## Arsitektur

```
┌────────────────────┐   HTTP/JSON     ┌────────────────────────────┐   JDBC   ┌──────────┐
│     Versi web      │───────────────▶ │  Backend API (Kotlin OOP)  │ ───────▶ │ MySQL 8  │
│    HTML/CSS/JS     │                 │  api → service → repository│          │ cinetix  │
└────────────────────┘                 │  + menyajikan versi web    │          └──────────┘
                                       └────────────────────────────┘
```

Versi web **tidak** terhubung langsung ke MySQL. Semua logika bisnis dan akses database berada di backend.

## Fitur
**Pelanggan:** daftar/login · bioskop per kota · film Sedang Tayang & Coming Soon · detail film ·
pilih hari (maks. 3 hari), lokasi, jam, studio · pilih kursi dari denah (anti double booking) ·
diskon beli ≥5 tiket (Reguler 6%, Premiere 8%, Suites 10%) · bayar QRIS / Transfer Bank (VA) / Kartu Kredit ·
riwayat · e-ticket + barcode untuk dicetak.

**Admin:** CRUD film, kota, cinema, studio (denah kursi dibuat otomatis), jadwal, harga studio ·
laporan penjualan & film terlaris.

## Tipe studio
| Tipe | Harga dasar | Akhir pekan | Kursi per baris (kiri \| kanan) | Diskon beli ≥5 tiket |
|---|---|---|---|---|
| Reguler | Rp50.000 | Rp65.000 | 10 (5 \| 5) | 6% |
| Premiere | Rp90.000 | Rp105.000 | 8 (4 \| 4) | 8% |
| Suites | Rp150.000 | Rp165.000 | 6 (3 \| 3) | 10% |

Kapasitas studio harus kelipatan kursi per baris, maksimal 10 baris (A–J).

## Denah kursi (tabel `kursi`)
Setiap studio punya denah di tabel `kursi`. Satu baris tabel = satu kursi fisik, berisi kode (A1),
baris (A), nomor (1), **section** (`DEPAN` = dekat layar, `BELAKANG` = jauh dari layar) dan
**sisi** (`KIRI` / `KANAN` dari jalan tengah). Tabel ini dipakai untuk menggambar denah di aplikasi
sekaligus menjadi kursi yang dipilih pelanggan (`kursi_dipesan.kursi_id`).

```
              LAYAR BIOSKOP
              DEPAN STUDIO
          ─── DEKAT LAYAR ───
     A  [1][2][3][4]   [5][6][7][8]  A
     B  [1][2][3][4]   [5][6][7][8]  B
          ── JAUH DARI LAYAR ──
     C  [1][2][3][4]   [5][6][7][8]  C
     D  [1][2][3][4]   [5][6][7][8]  D
     E  [1][2][3][4]   [5][6][7][8]  E
            (contoh studio Premiere 40 kursi)
```
Aturan: setengah baris pertama (dibulatkan ke bawah) = dekat layar; setengah nomor pertama = kiri jalan tengah.
Denah dibuat oleh `Studio.buatDenah()` saat admin menambah/mengubah studio, dan oleh `seed.sql` untuk data awal.

## Struktur folder
```
cinetix/
├── backend/                       # REST API Kotlin + penyaji versi web
│   ├── src/main/kotlin/
│   │   ├── model/                 # entity + abstract class + interface (OOP)
│   │   ├── repository/            # akses MySQL (JDBC), termasuk tabel kursi
│   │   ├── service/               # logika bisnis
│   │   ├── exception/             # custom exception
│   │   ├── util/                  # koneksi DB, QR/barcode, hash password
│   │   ├── api/                   # router + endpoint REST
│   │   └── Main.kt
│   ├── src/main/resources/web/    # VERSI WEB: index.html, style.css, app.js
│   ├── src/test/kotlin/           # unit test (repository palsu, tanpa MySQL)
│   ├── build.gradle.kts
│   └── Dockerfile
├── data/schema.sql, seed.sql      # skema & data awal MySQL (11 tabel)
├── docs/                          # user story, class diagram, API, pembagian tugas
├── docker-compose.yml             # MySQL + backend
└── .github/workflows/ci.yml       # unit test otomatis
```

## Cara menjalankan

### Cara 1 — Docker (paling mudah)
Butuh Docker Desktop.
```bash
git clone <link-repo>
cd cinetix
docker compose up -d --build      # MySQL + backend API + versi web
docker compose logs -f api        # tunggu sampai muncul "[OK] CINETIX API berjalan"
```
- Versi web: buka **http://localhost:8080/** di browser.
- Cek API: http://localhost:8080/api → `{"status":"OK"}`.
- E-ticket (.txt) dan barcode (.png) yang terbit muncul di folder `output/`.
- Poster film yang diunggah admin tersimpan di folder `uploads/posters/` (tetap ada walau container dibuat ulang).

Reset database (misal setelah mengubah `seed.sql`): `docker compose down -v` lalu `docker compose up -d --build`.

### Poster film
Admin menambah/mengubah film di tab **Admin → Film** lalu memilih file gambar. Gambar dikirim ke backend
dan disimpan di server (`$UPLOAD_DIR/posters`, default `uploads/posters`) dengan nama yang dibuat otomatis (`film-{id}-{waktu}.jpg`).
Kolom `film.poster_url` hanya berisi **lokasi file** (bukan URL internet), dan file disajikan lewat `/uploads/posters/{nama}`.
Jumlah film tidak dibatasi: tidak ada nama file atau film yang ditulis manual di kode. Tanpa poster, tampil poster pengganti.

### Cara 2 — Tanpa Docker
Butuh JDK 17+ dan MySQL 8.
```bash
mysql -u root -p < data/schema.sql
mysql -u root -p cinetix < data/seed.sql

cd backend
# Linux/Mac
DB_URL="jdbc:mysql://localhost:3306/cinetix?useSSL=false&allowPublicKeyRetrieval=true" DB_USER=root DB_PASS=passwordmu ./gradlew run
# Windows (PowerShell)
$env:DB_URL="jdbc:mysql://localhost:3306/cinetix?useSSL=false&allowPublicKeyRetrieval=true"; $env:DB_USER="root"; $env:DB_PASS="passwordmu"; .\gradlew.bat run
```
Lalu buka http://localhost:8080/ untuk versi web.

### Akun demo
| Peran | Email | Password |
|---|---|---|
| Pelanggan | `budi@mail.com` | `budi123` |
| Admin | `admin@cinetix.id` | `admin123` |

Kartu kredit uji (simulasi): `4111 1111 1111 1111`.

### Unit test
```bash
cd backend && ./gradlew test
```
41 unit test menguji model (harga & denah tiap studio, diskon 6/8/10%, pembayaran) dan service
(validasi 3 hari, denah per section, double booking + rollback, alur pembayaran) memakai repository palsu, tanpa MySQL.
GitHub Actions menjalankannya otomatis setiap push, sekaligus memvalidasi `schema.sql`/`seed.sql`.

## Konsep OOP
| Konsep | Penerapan |
|---|---|
| Abstract class | `Studio`, `Pembayaran`, `Pengguna` |
| Inheritance | `Reguler/Premiere/Suites`, `Qris/TransferBank/KartuKredit`, `Pelanggan/Admin` |
| Polymorphism | `studio.hargaTiket()`, `studio.persenDiskon`/`hitungDiskon()`, `kursiPerBaris`, `pembayaran.biayaAdmin()` & `proses()` |
| Interface | `Diskon` (diimplementasikan `Studio`), `Cetakable`, `CrudRepository<T>` dan repository lain |
| Encapsulation | `status` bayar hanya berubah lewat `bayar()`; `pesananLayanan` Suites `private` |
| Data class | `Film`, `Cinema`, `Jadwal`, `Kursi`, `KursiDenah`, `Tiket` |
| Enum | `TipeStudio`, `Section` (dekat/jauh layar), `Sisi` (kiri/kanan) |
| Custom exception | `KursiSudahDipesanException`, `HariTidakValidException`, `PembayaranGagalException`, `DataTidakDitemukanException` |

Diagram lengkap: [`docs/02-class-diagram.md`](docs/02-class-diagram.md).

## Dokumentasi
- [User story & requirement](docs/01-user-story.md)
- [Class diagram, ERD, arsitektur](docs/02-class-diagram.md)
- [Dokumentasi REST API](docs/03-api.md)
- [Pembagian tugas & alur 3 hari](docs/04-pembagian-tugas.md)

## Masalah yang sering terjadi
| Masalah | Solusi |
|---|---|
| Web: halaman kosong / "Tidak bisa terhubung" | Pastikan membuka `http://localhost:8080/` (bukan membuka file `index.html` langsung) dan backend berjalan |
| `Public Key Retrieval is not allowed` | Tambahkan `allowPublicKeyRetrieval=true&useSSL=false` di `DB_URL` |
| `Connection refused` di Docker | Di dalam container, host database adalah `db`, bukan `localhost` |
| Seed tidak berubah | Script init MySQL hanya jalan sekali. Jalankan `docker compose down -v` |
| Port 3306 bentrok | MySQL Docker dipetakan ke port host `3307` |
| Admin gagal mengubah kapasitas studio | Denah tidak bisa dibuat ulang jika kursinya sudah pernah dipesan; buat studio baru |
| Jadwal kosong | Backend mengisi jadwal 3 hari otomatis saat start. Admin juga bisa tekan "Generate 3 hari" |

## Tim
| Nama | NIM | Peran |
|---|---|---|
| ... | ... | Project Lead |
| ... | ... | Domain Developer |
| ... | ... | Business Logic |
| ... | ... | UI (Web) |
| ... | ... | QA + Data |
| ... | ... | DevOps + Web |
