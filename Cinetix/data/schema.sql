-- =====================================================================
-- CINETIX - Skema database MySQL 8
-- Jalankan: mysql -u root -p < data/schema.sql
-- (Docker menjalankan file ini otomatis saat volume database pertama kali dibuat)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS cinetix CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cinetix;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS kursi_dipesan, booking, sesi, pengguna, jadwal, film, kursi, studio,
                     harga_studio, cinema, kota;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE kota (
  id   INT PRIMARY KEY AUTO_INCREMENT,
  nama VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE cinema (
  id      INT PRIMARY KEY AUTO_INCREMENT,
  nama    VARCHAR(80)  NOT NULL,
  alamat  VARCHAR(200) NOT NULL,
  kota_id INT NOT NULL,
  FOREIGN KEY (kota_id) REFERENCES kota(id)
);

-- Harga dasar per tipe studio (dikelola admin). Akhir pekan +Rp15.000 dihitung di Kotlin.
CREATE TABLE harga_studio (
  tipe        ENUM('REGULER','PREMIERE','SUITES') PRIMARY KEY,
  harga_dasar DECIMAL(12,2) NOT NULL
);

-- Kapasitas harus kelipatan kursi per baris: Reguler 10, Premiere 8, Suites 6 (maks. 10 baris A-J)
CREATE TABLE studio (
  id        INT PRIMARY KEY AUTO_INCREMENT,
  cinema_id INT NOT NULL,
  nama      VARCHAR(30) NOT NULL,                 -- contoh: "Studio 1"
  tipe      ENUM('REGULER','PREMIERE','SUITES') NOT NULL,
  kapasitas INT NOT NULL,
  FOREIGN KEY (cinema_id) REFERENCES cinema(id) ON DELETE CASCADE,
  UNIQUE (cinema_id, nama)
);

-- DENAH STUDIO: satu baris = satu kursi fisik di studio.
-- Tabel ini menggambarkan denah sekaligus menjadi pilihan kursi pelanggan.
--   section : DEPAN (dekat layar) / BELAKANG (jauh dari layar)
--   sisi    : KIRI / KANAN dari jalan tengah
CREATE TABLE kursi (
  id        INT PRIMARY KEY AUTO_INCREMENT,
  studio_id INT NOT NULL,
  kode      VARCHAR(4) NOT NULL,                  -- A1, A2, ..., J10
  baris     CHAR(1) NOT NULL,                     -- A..J (A paling dekat layar)
  nomor     INT NOT NULL,                         -- 1..kursi per baris
  section   ENUM('DEPAN','BELAKANG') NOT NULL,
  sisi      ENUM('KIRI','KANAN') NOT NULL,
  FOREIGN KEY (studio_id) REFERENCES studio(id) ON DELETE CASCADE,
  UNIQUE (studio_id, kode)
);

CREATE TABLE film (
  id            INT PRIMARY KEY AUTO_INCREMENT,
  judul         VARCHAR(120) NOT NULL,
  genre         VARCHAR(50)  NOT NULL,
  durasi        INT NOT NULL,                     -- menit
  rating_usia   VARCHAR(5) NOT NULL DEFAULT 'SU', -- SU, R13, D17, D21
  sinopsis      TEXT,
  poster_url    VARCHAR(255) NULL,                -- LOKASI file poster di server, contoh /uploads/posters/film-3-1760000000000.jpg (bukan URL internet)
  status        ENUM('NOW_PLAYING','COMING_SOON') NOT NULL,
  tanggal_rilis DATE NULL
);

CREATE TABLE jadwal (
  id        INT PRIMARY KEY AUTO_INCREMENT,
  film_id   INT NOT NULL,
  studio_id INT NOT NULL,
  tanggal   DATE NOT NULL,
  jam       TIME NOT NULL,
  FOREIGN KEY (film_id)   REFERENCES film(id),
  FOREIGN KEY (studio_id) REFERENCES studio(id) ON DELETE CASCADE,
  UNIQUE (studio_id, tanggal, jam),               -- satu studio tidak bisa dua film di jam yang sama
  INDEX idx_jadwal_tanggal (tanggal, film_id)
);

CREATE TABLE pengguna (
  id             INT PRIMARY KEY AUTO_INCREMENT,
  nama           VARCHAR(80)  NOT NULL,
  email          VARCHAR(100) NOT NULL UNIQUE,
  password_hash  VARCHAR(255) NOT NULL,
  peran          ENUM('PELANGGAN','ADMIN') NOT NULL DEFAULT 'PELANGGAN',
  dibuat         DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Token login versi web (Authorization: Bearer <token>)
CREATE TABLE sesi (
  token       CHAR(64) PRIMARY KEY,
  pengguna_id INT NOT NULL,
  dibuat      DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (pengguna_id) REFERENCES pengguna(id) ON DELETE CASCADE
);

CREATE TABLE booking (
  id             INT PRIMARY KEY AUTO_INCREMENT,
  pengguna_id    INT NOT NULL,
  jadwal_id      INT NOT NULL,
  kursi          VARCHAR(120) NOT NULL,           -- salinan daftar kursi untuk riwayat, contoh "A1,A2"
  jumlah_tiket   INT NOT NULL,
  subtotal_tiket DECIMAL(12,2) NOT NULL,          -- harga tiket x jumlah kursi
  diskon         DECIMAL(12,2) NOT NULL DEFAULT 0,-- beli >= 5 tiket: Reguler 6%, Premiere 8%, Suites 10%
  biaya_admin    DECIMAL(12,2) NOT NULL DEFAULT 0,
  total          DECIMAL(12,2) NOT NULL,          -- subtotal - diskon + biaya admin
  kode_bayar     VARCHAR(10) NULL,                -- QRIS / BANK / KREDIT
  metode_bayar   VARCHAR(30) NULL,                -- label, contoh "Transfer BCA"
  status         ENUM('PENDING','LUNAS','BATAL') NOT NULL DEFAULT 'PENDING',
  kode_tiket     VARCHAR(30) UNIQUE,
  dibuat         DATETIME DEFAULT CURRENT_TIMESTAMP,
  dibayar        DATETIME NULL,
  FOREIGN KEY (pengguna_id) REFERENCES pengguna(id),
  FOREIGN KEY (jadwal_id)   REFERENCES jadwal(id),
  INDEX idx_booking_status (status, dibuat)
);

-- Kursi yang dipesan pada jadwal tertentu (menunjuk ke tabel kursi)
CREATE TABLE kursi_dipesan (
  id         INT PRIMARY KEY AUTO_INCREMENT,
  booking_id INT NOT NULL,
  jadwal_id  INT NOT NULL,
  kursi_id   INT NOT NULL,
  FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE CASCADE,
  FOREIGN KEY (jadwal_id)  REFERENCES jadwal(id),
  FOREIGN KEY (kursi_id)   REFERENCES kursi(id),
  UNIQUE (jadwal_id, kursi_id)                    -- KUNCI pencegahan double booking
);
