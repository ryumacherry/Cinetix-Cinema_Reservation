# Pembagian Tugas & Alur Kerja 3 Hari

Kelompok 6 orang. Jika 5 orang, tugas DevOps + Web digabung ke Project Lead atau QA.
Panduan langkah demi langkah yang lengkap ada di dokumen panduan terpisah; file ini ringkasannya.

![Pembagian tugas Cinetix](pembagian-tugas.png)

## Strategi Git
- Branch: `main` (rilis), `dev` (integrasi), `feature/<peran>-<fitur>` (contoh `feature/domain-studio`).
- Alur: buat branch dari `dev` → commit → push → Pull Request ke `dev` → direview Project Lead → merge.
- **Minimal 3 commit/hari/anggota**, dari akun masing-masing (dicek lewat riwayat GitHub).
- Format pesan commit: `feat: ...`, `fix: ...`, `test: ...`, `docs: ...`, `chore: ...`.
- Project board (GitHub Projects): kolom **To Do / Doing / Review / Done**; satu kartu = satu issue.

## Peran
| Peran | Folder / file utama |
|---|---|
| Project Lead | `docs/`, `README.md`, review PR |
| Domain Developer | `backend/.../model/`, `util/BarcodeGenerator.kt` |
| Business Logic | `backend/.../service/`, `exception/`, `api/Routes.kt`, `api/Dto.kt` |
| UI (Web) | `backend/src/main/resources/web/` (tampilan & interaksi) |
| QA + Data | `data/`, `backend/.../repository/`, `util/Database.kt`, `backend/src/test/` |
| DevOps + Web | `build.gradle.kts`, `Dockerfile`, `docker-compose.yml`, `.github/`, `api/Router.kt`, `Main.kt`, `backend/src/main/resources/web/` |

## Hari 1 — Definisi & Desain
| Peran | Tugas | Contoh commit |
|---|---|---|
| Project Lead | Finalisasi tema & 12 fitur, tulis `docs/01-user-story.md`, buat repo + branch + project board, kerangka README | `docs: user story`, `docs: kerangka README`, `chore: template issue` |
| Domain Developer | Bersama PL buat class diagram, kerangka `model/` (Studio, Diskon, Pembayaran, Pengguna) | `docs: class diagram`, `feat: kerangka Studio + interface Diskon`, `feat: kerangka Pembayaran & Pengguna` |
| Business Logic | Daftar aturan bisnis (3 hari, kursi, diskon ≥5 tiket), `exception/`, kerangka `service/` | `feat: custom exception`, `feat: kerangka JadwalService`, `docs: aturan bisnis` |
| UI (Web) | Wireframe 13 layar (termasuk denah kursi), tema + navigasi | `feat: tema & navigasi`, `feat: layar login (statis)` |
| QA + Data | `data/schema.sql` (11 tabel, termasuk tabel `kursi`), `data/seed.sql` (denah otomatis), rencana test case | `feat: schema 11 tabel`, `feat: seed + denah kursi`, `docs: rencana test` |
| DevOps + Web | `build.gradle.kts`, `.gitignore`, `Dockerfile` awal, kerangka web (`index.html`, `style.css`) | `chore: gradle backend`, `chore: Dockerfile awal`, `feat: kerangka web` |

## Hari 2 — Implementasi
| Peran | Tugas |
|---|---|
| Project Lead | Review & merge PR, jaga konsistensi dengan class diagram, update project board |
| Domain Developer | Isi `model/`: harga, kursi per baris, `buatDenah()` (section & jalan tengah), diskon 6/8/10%, pembayaran QRIS/Bank/Kredit, data class |
| Business Logic | `JadwalService` (maks. 3 hari + denah per section), `BookingService` (anti double booking + diskon), `PembayaranService`, `AuthService`, `RiwayatService`, endpoint di `api/Routes.kt` |
| UI (Web) | Layar film, bioskop, jadwal, **denah kursi** (layar, dekat/jauh layar, jalan tengah, legenda), pembayaran, riwayat |
| QA + Data | `repository/` JDBC (termasuk `JdbcKursiRepository`), koneksi DB lewat env var, unit test `StudioTest`, `DiskonTest`, `JadwalServiceTest`, `BookingServiceTest` |
| DevOps + Web | `Main.kt`, `api/Router.kt` (menyajikan file web), `docker-compose.yml`, GitHub Actions; web: login, film, jadwal, denah kursi |

## Hari 3 — Finalisasi
| Peran | Tugas |
|---|---|
| Project Lead | README final, koordinasi video demo (maks. 5 menit), merge `dev` → `main`, submit link GitHub ke LMS |
| Domain Developer | QRIS (QR sesuai nominal setelah diskon) & barcode tiket (`Cetakable`), format e-ticket |
| Business Logic | CRUD admin (studio + denah otomatis, validasi kapasitas), laporan penjualan & film terlaris |
| UI (Web) | Layar e-ticket + cetak, menu admin, rapikan pesan error, uji alur dari awal sampai akhir |
| QA + Data | Integrasi database penuh, test edge case (kursi ganda, hari ke-4, diskon 4 vs 5 tiket, kartu ditolak), uji versi web, perbaiki bug |
| DevOps + Web | Web: pembayaran, e-ticket + cetak, admin; finalisasi Docker, uji `docker compose up` dari clone bersih |

## Urutan video demo (maks. 5 menit)
1. (0:00) Perkenalan tim & arsitektur (web → API → MySQL) — 30 detik
2. (0:30) Login pelanggan → pilih kota → bioskop per kota
3. (1:00) Film sedang tayang → detail → pilih jadwal (tunjukkan hanya 3 hari)
4. (1:45) Denah kursi: layar, dekat/jauh layar, jalan tengah; pilih 5 kursi → diskon muncul
5. (2:30) Bayar QRIS (QR sesuai nominal setelah diskon) → konfirmasi → e-ticket + barcode → cetak/PDF
6. (3:30) Riwayat
7. (3:50) Login admin → laporan (total diskon) → tambah studio (denah otomatis) → tambah jadwal
8. (4:30) Tunjukkan `gradle test` hijau & GitHub Actions — penutup
