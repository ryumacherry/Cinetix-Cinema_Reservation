# User Story & Requirement — Cinetix

Aplikasi reservasi bioskop (mirip CGV) berbasis web, dengan backend Kotlin OOP dan database MySQL.

## Aktor
- **Pelanggan**: mencari film, memesan tiket, membayar, menyimpan e-ticket.
- **Admin**: mengelola data master (film, bioskop, studio, jadwal, harga) dan melihat laporan.

## Daftar fitur (lebih dari 5)
| No | Fitur | Aktor |
|----|-------|-------|
| F1 | Daftar & login | Pelanggan, Admin |
| F2 | Lihat bioskop per kota | Pelanggan |
| F3 | Film Sedang Tayang & Coming Soon + detail film | Pelanggan |
| F4 | Pilih jadwal: hari (maks. 3 hari), lokasi, jam, studio (Reguler/Premiere/Suites) | Pelanggan |
| F5 | Pilih kursi dari denah studio: section dekat/jauh layar + jalan tengah (anti double booking) | Pelanggan |
| F6 | Bayar: QRIS (QR otomatis sesuai nominal), Transfer Bank, Kartu Kredit (simulasi) | Pelanggan |
| F7 | Diskon beli ≥5 tiket: Reguler 6%, Premiere 8%, Suites 10% | Pelanggan |
| F8 | Riwayat pembelian | Pelanggan |
| F9 | E-ticket + barcode untuk dicetak | Pelanggan |
| F10 | CRUD film, kota, cinema, studio (denah kursi otomatis), jadwal, harga studio | Admin |
| F11 | Laporan penjualan & film terlaris | Admin |
| F12 | Versi web | Pelanggan, Admin |

## User story + kriteria penerimaan

### US-01 Daftar & login (F1)
Sebagai **pelanggan**, saya ingin **mendaftar dan login** agar **riwayat dan tiket saya tersimpan**.
- [ ] Nama minimal 3 huruf, email valid, password minimal 6 karakter.
- [ ] Email yang sudah terdaftar ditolak dengan pesan jelas.
- [ ] Password disimpan dalam bentuk hash (PBKDF2), bukan teks asli.
- [ ] Setelah login, aplikasi menyimpan token sehingga tidak perlu login ulang.

### US-02 Bioskop per kota (F2)
Sebagai pelanggan, saya ingin **memilih kota lalu melihat daftar bioskop** beserta tipe studionya agar saya tahu lokasi terdekat.
- [ ] Kota bisa diganti dari top bar.
- [ ] Setiap bioskop menampilkan alamat dan tipe studio yang tersedia.
- [ ] Membuka bioskop menampilkan semua film & jam tayang di bioskop tersebut.

### US-03 Daftar film (F3)
Sebagai pelanggan, saya ingin **melihat film Sedang Tayang dan Coming Soon** agar bisa memilih tontonan.
- [ ] Dua tab terpisah. Coming Soon menampilkan tanggal rilis dan tidak bisa dipesan.
- [ ] Detail film: genre, durasi, rating usia, sinopsis.

### US-04 Pilih jadwal (F4)
Sebagai pelanggan, saya ingin **memilih hari, bioskop, studio, dan jam** dalam satu layar.
- [ ] Hanya hari ini sampai 2 hari berikutnya (maks. 3 hari) yang bisa dipilih.
- [ ] Permintaan hari ke-4 ditolak server (`HariTidakValidException`).
- [ ] Jam yang sudah lewat tidak ditampilkan.
- [ ] Harga berbeda per tipe studio; Sabtu/Minggu +Rp15.000.

### US-05 Pilih kursi dari denah (F5)
Sebagai pelanggan, saya ingin **memilih kursi sendiri dari denah studio** agar tahu posisi duduk terhadap layar.
- [ ] Denah diambil dari tabel `kursi` dan sesuai kapasitas studio (Reguler 10/baris, Premiere 8/baris, Suites 6/baris).
- [ ] Layar digambar di depan; baris dibagi dua section: **dekat layar** dan **jauh dari layar**.
- [ ] Setiap baris terbagi dua oleh **jalan tengah** (kursi kiri | kanan), label baris tampil di kiri dan kanan.
- [ ] Warna: Tersedia (kotak bergaris), Dipilih (hijau), Terisi (abu-abu, tidak bisa dipilih). Maksimal 6 kursi.
- [ ] Jika dua orang memesan kursi yang sama, yang kedua gagal (`UNIQUE(jadwal_id, kursi_id)` → `KursiSudahDipesanException`) dan tidak ada kursi lain yang ikut terkunci (rollback).
- [ ] Kursi ditahan 15 menit; jika tidak dibayar, otomatis dilepas.

### US-06 Pembayaran (F6)
Sebagai pelanggan, saya ingin **membayar dengan QRIS, transfer bank, atau kartu kredit**.
- [ ] QRIS: QR dibuat otomatis berisi nominal yang harus dibayar, tanpa biaya admin.
- [ ] Transfer bank: nomor Virtual Account, biaya admin Rp2.500.
- [ ] Kartu kredit: 16 digit + validasi Luhn, biaya admin 2%, langsung lunas.
- [ ] Semua metode adalah **simulasi** (tidak terhubung payment gateway).

### US-07 Diskon pembelian banyak tiket (F7)
Sebagai pelanggan, saya ingin **mendapat diskon jika membeli minimal 5 tiket** sekaligus.
- [ ] Persen diskon berbeda per tipe studio: Reguler 6%, Premiere 8%, Suites 10%.
- [ ] Diskon tampil di layar kursi saat kursi ke-5 dipilih, di halaman pembayaran, dan di e-ticket.
- [ ] Nominal QRIS/VA/kartu sudah dipotong diskon. Tidak ada saldo atau poin yang disimpan.

### US-08 Riwayat (F8) & US-09 E-ticket (F9)
Sebagai pelanggan, saya ingin **melihat riwayat dan membuka e-ticket ber-barcode** untuk ditunjukkan/dicetak.
- [ ] Riwayat menampilkan status Lunas / Menunggu bayar / Dibatalkan.
- [ ] E-ticket berisi kode tiket + barcode CODE_128; bisa dicetak atau disimpan sebagai PDF.
- [ ] Booking yang masih menunggu bisa dilanjutkan pembayarannya.

### US-10 & US-11 Admin (F10–F11)
Sebagai **admin**, saya ingin **mengelola data dan melihat laporan**.
- [ ] CRUD film, kota, cinema, studio, jadwal; ubah harga dasar per tipe studio.
- [ ] Membuat/mengubah studio otomatis membuat denah kursinya. Kapasitas wajib kelipatan kursi per baris.
- [ ] Data yang masih dipakai (misal film yang punya jadwal, denah yang kursinya sudah dipesan) tidak bisa dihapus/diubah.
- [ ] Laporan: total pendapatan, tiket terjual, total diskon, pendapatan per metode, 5 film terlaris.
- [ ] Pelanggan tidak bisa mengakses endpoint admin (HTTP 403).

### US-12 Versi web (F12)
Sebagai **pelanggan/admin**, saya ingin **memakai Cinetix lewat browser**.
- [ ] Dibuka di `http://localhost:8080/` dan memakai REST API yang sama.
- [ ] Fitur lengkap: login, film, bioskop, jadwal, denah kursi, pembayaran, e-ticket + cetak, riwayat, admin.

## Aturan bisnis ringkas
| Aturan | Diterapkan di |
|---|---|
| Jadwal maksimal 3 hari ke depan | `JadwalService.validasiTanggal()` |
| Kursi tidak boleh ganda | `UNIQUE(jadwal_id, kursi_id)` + `JdbcBookingRepository.simpanBaru()` |
| Denah sesuai kapasitas & tipe | `Studio.buatDenah()` + tabel `kursi` |
| Booking + semua kursi sukses bersamaan | `Database.transaksi {}` (COMMIT/ROLLBACK) |
| Batas bayar 15 menit | `BookingService.bersihkanKedaluwarsa()` |
| Harga akhir pekan +Rp15.000 | `Studio.hargaTiket(akhirPekan)` |
| Diskon ≥5 tiket (6/8/10%) | `Studio.hitungDiskon()` (interface `Diskon`) |
