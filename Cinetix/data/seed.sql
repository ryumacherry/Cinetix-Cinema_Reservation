-- =====================================================================
-- CINETIX - Data awal (seed)
-- Jalankan setelah schema.sql: mysql -u root -p cinetix < data/seed.sql
--
-- Akun demo:
--   Admin     : admin@cinetix.id / admin123
--   Pelanggan : budi@mail.com    / budi123
-- =====================================================================
USE cinetix;

INSERT INTO kota (id, nama) VALUES
  (1, 'Jakarta'), (2, 'Bandung'), (3, 'Surabaya'), (4, 'Yogyakarta');

INSERT INTO cinema (id, nama, alamat, kota_id) VALUES
  (1, 'Cinetix Grand Indonesia', 'Grand Indonesia Lt. 8, Jl. M.H. Thamrin No. 1, Jakarta Pusat', 1),
  (2, 'Cinetix Pacific Place',   'Pacific Place Lt. 6, Jl. Jend. Sudirman Kav. 52-53, Jakarta Selatan', 1),
  (3, 'Cinetix Paris Van Java',  'Paris Van Java Lt. 3, Jl. Sukajadi No. 131-139, Bandung', 2),
  (4, 'Cinetix 23 Paskal',       '23 Paskal Shopping Center Lt. 3, Jl. Pasir Kaliki No. 25-27, Bandung', 2),
  (5, 'Cinetix Tunjungan Plaza', 'Tunjungan Plaza 5 Lt. 4, Jl. Basuki Rahmat No. 8-12, Surabaya', 3),
  (6, 'Cinetix Ambarrukmo',      'Plaza Ambarrukmo Lt. 3, Jl. Laksda Adisucipto, Sleman, Yogyakarta', 4);

INSERT INTO harga_studio (tipe, harga_dasar) VALUES
  ('REGULER', 50000), ('PREMIERE', 90000), ('SUITES', 150000);

-- Kapasitas = kelipatan kursi per baris: Reguler 10/baris, Premiere 8/baris, Suites 6/baris (maks. 10 baris)
INSERT INTO studio (id, cinema_id, nama, tipe, kapasitas) VALUES
  (1, 1, 'Studio 1', 'REGULER', 100), (2, 1, 'Studio 2', 'REGULER', 80),
  (3, 1, 'Studio 3', 'PREMIERE', 40), (4, 1, 'Studio 4', 'SUITES', 24),
  (5, 2, 'Studio 1', 'REGULER', 100), (6, 2, 'Studio 2', 'PREMIERE', 40), (7, 2, 'Studio 3', 'SUITES', 24),
  (8, 3, 'Studio 1', 'REGULER', 100), (9, 3, 'Studio 2', 'REGULER', 80), (10, 3, 'Studio 3', 'PREMIERE', 40),
  (11, 4, 'Studio 1', 'REGULER', 90), (12, 4, 'Studio 2', 'SUITES', 24),
  (13, 5, 'Studio 1', 'REGULER', 100), (14, 5, 'Studio 2', 'PREMIERE', 40),
  (15, 6, 'Studio 1', 'REGULER', 80), (16, 6, 'Studio 2', 'REGULER', 80);

-- DENAH KURSI setiap studio (aturan sama dengan Studio.buatDenah() di Kotlin):
--   baris A, B, C, ... ; nomor 1..kursi per baris
--   setengah baris pertama = DEPAN (dekat layar), sisanya BELAKANG (jauh dari layar)
--   setengah nomor pertama = KIRI jalan tengah, sisanya KANAN
INSERT INTO kursi (studio_id, kode, baris, nomor, section, sisi)
SELECT s.id,
       CONCAT(CHAR(65 + FLOOR(n.i / p.per) USING utf8mb4), MOD(n.i, p.per) + 1),
       CHAR(65 + FLOOR(n.i / p.per) USING utf8mb4),
       MOD(n.i, p.per) + 1,
       IF(FLOOR(n.i / p.per) < FLOOR(s.kapasitas / p.per / 2), 'DEPAN', 'BELAKANG'),
       IF(MOD(n.i, p.per) < p.per / 2, 'KIRI', 'KANAN')
FROM studio s
JOIN (SELECT 'REGULER' AS tipe, 10 AS per UNION ALL SELECT 'PREMIERE', 8 UNION ALL SELECT 'SUITES', 6) p
  ON p.tipe = s.tipe
JOIN (SELECT a.d * 10 + b.d AS i
      FROM (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
      CROSS JOIN
           (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b) n
  ON n.i < s.kapasitas
ORDER BY s.id, n.i;

-- Film fiktif untuk tugas kuliah
INSERT INTO film (id, judul, genre, durasi, rating_usia, sinopsis, poster_url, status, tanggal_rilis) VALUES
  (1, 'Agensi Rumah Tangga', 'Drama, Komedi', 115, 'R13',
   'Seorang pengusaha yang gigih membangun agensi penyalur asisten rumah tangga harus menghadapi berbagai tingkah unik para stafnya yang sering kali mengundang tawa. Di tengah kesibukan mengurus bisnis dan segala drama di tempat kerja, ia juga harus berjuang menghadapi masalah dalam kehidupan pribadinya.', 'poster_1.jpg', 'NOW_PLAYING', NULL),
  (2, 'Reawakened Man', 'Aksi', 101, 'D17',
   'Meski didukung sahabatnya, Young-ha, dan adiknya, Ye-rin, Seok Hwan selalu gagal mendapatkan pekerjaan. Suatu malam, ia tewas diserang orang asing. Namun, 72 jam kemudian, ia hidup kembali dan menyadari bahwa dirinya memiliki kemampuan untuk bangkit setiap kali mati. Saat berusaha memahami kekuatannya, sosok misterius bernama Black mulai memburunya.', 'poster_2.jpg', 'NOW_PLAYING', NULL),
  (3, 'Forgotten Island', 'Animasi, Petualangan', 109, 'R13',
   'Saat merayakan malam terakhir mereka bersama setelah lulus SMA, Jo dan Raissa menemukan portal misterius yang membawa mereka ke pulau ajaib bernama Nakali. Untuk bisa pulang, mereka harus mengorbankan seluruh kenangan persahabatan mereka.', 'poster_3.jpg', 'NOW_PLAYING', NULL),
  (4, 'Operasi Pesta Copet', 'Drama', 113, 'R13',
   'Demi mendapatkan uang dengan cepat, Ijal dan teman-temannya nekat menjalankan aksi pencopetan di Pestapora, salah satu festival musik terbesar di Indonesia. Namun, misi berisiko tinggi ini membawa mereka ke situasi yang tak terduga.', 'poster_4.jpg', 'NOW_PLAYING', NULL),
  (5, 'Street Fighter', 'Aksi', 119, 'R13',
   'Pada tahun 1993, Ryu dan Ken Masters yang sudah lama berpisah kembali terlibat dalam pertarungan setelah Chun-Li merekrut mereka untuk mengikuti World Warrior Tournament.', 'poster_5.jpg', 'NOW_PLAYING', NULL),
  (6, 'Ibu, Bagaimana Aku Tanpamu?', 'Drama, Keluarga', 111, 'SU',
   'Saat mengetahui ibunya mengidap penyakit yang tak bisa disembuhkan, seorang perempuan muda berusaha mewujudkan keinginan sang ibu yang belum sempat tercapai.', 'poster_6.jpg', 'NOW_PLAYING', NULL),
  (7, 'Pulang Kampung', 'Drama, Musikal', 100, 'R13',
   'Seorang profesional muda dari Jakarta dipanggil pulang ke kampung halamannya di Sumatra Utara oleh sesepuh keluarga untuk bertanding dalam pemilihan jawatan awam.', 'poster_7.jpg', 'COMING_SOON', DATE_ADD(CURDATE(), INTERVAL 42 DAY)),
  (8, 'The Mongoose', 'Aksi, Kriminal', 96, 'R13',
   'Seorang agen rahasia veteran terpaksa kembali dari masa pensiunnya untuk melacak kelompok kejahatan internasional yang mengancam keselamatan negaranya.', 'poster_8.jpg', 'COMING_SOON', DATE_ADD(CURDATE(), INTERVAL 13 DAY)),
  (9, 'Laut Bercerita', 'Fiksi, Sejarah', 135, 'D17',
   'Di Yogyakarta akhir 1990-an, Biru Laut bergabung dengan kelompok studi Winatra demi memperjuangkan kebebasan berekspresi bersama para sahabatnya.', 'poster_9.jpg', 'COMING_SOON', DATE_ADD(CURDATE(), INTERVAL 15 DAY));

-- Jadwal 3 hari (hari ini s/d +2). Backend juga mengisi otomatis kalau jadwal hari berikutnya belum ada.
INSERT INTO jadwal (film_id, studio_id, tanggal, jam)
SELECT ((s.id + h.n + sl.urut DIV 2) MOD 6) + 1, s.id, CURDATE() + INTERVAL h.n DAY, sl.jam
FROM studio s
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2) h
JOIN (
  SELECT 0 AS urut, '10:30:00' AS jam, 'R' AS grup UNION ALL
  SELECT 1, '13:15:00', 'R' UNION ALL SELECT 2, '16:00:00', 'R' UNION ALL
  SELECT 3, '18:45:00', 'R' UNION ALL SELECT 4, '21:30:00', 'R' UNION ALL
  SELECT 0, '12:00:00', 'P' UNION ALL SELECT 1, '15:00:00', 'P' UNION ALL
  SELECT 2, '19:00:00', 'P' UNION ALL SELECT 3, '21:45:00', 'P'
) sl ON sl.grup = IF(s.tipe = 'REGULER', 'R', 'P');

INSERT INTO pengguna (id, nama, email, password_hash, peran) VALUES
  (1, 'Admin Cinetix', 'admin@cinetix.id',
   'pbkdf2$60000$g8X2syGaoUHVgE3rr66WxQ==$gciklsO4FIsFhG49jPI9vGirlh3w9L68GrrIItHR4PM=', 'ADMIN'),
  (2, 'Budi Santoso', 'budi@mail.com',
   'pbkdf2$60000$5eIsO/SASSJw8QYQCmwBGw==$kYYvXvoq4Tmwrsp46ci2VGSaICxmeM7/OAOrHfAilfQ=', 'PELANGGAN');

-- Riwayat contoh (kemarin) supaya laporan admin & riwayat pelanggan tidak kosong saat demo
INSERT INTO jadwal (film_id, studio_id, tanggal, jam) VALUES (1, 1, CURDATE() - INTERVAL 1 DAY, '19:00:00');
SET @j1 = LAST_INSERT_ID();
INSERT INTO jadwal (film_id, studio_id, tanggal, jam) VALUES (5, 3, CURDATE() - INTERVAL 1 DAY, '19:00:00');
SET @j2 = LAST_INSERT_ID();

-- 2 tiket Reguler, QRIS (tanpa diskon)
INSERT INTO booking (pengguna_id, jadwal_id, kursi, jumlah_tiket, subtotal_tiket, diskon, biaya_admin, total,
                     kode_bayar, metode_bayar, status, kode_tiket, dibuat, dibayar)
VALUES (2, @j1, 'E5,E6', 2, 100000, 0, 0, 100000, 'QRIS', 'QRIS', 'LUNAS', 'CTX-DEMO-000001',
        NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);
SET @b1 = LAST_INSERT_ID();
INSERT INTO kursi_dipesan (booking_id, jadwal_id, kursi_id)
SELECT @b1, @j1, id FROM kursi WHERE studio_id = 1 AND kode IN ('E5', 'E6');

-- 5 tiket Reguler -> diskon 6% (250.000 x 6% = 15.000)
INSERT INTO booking (pengguna_id, jadwal_id, kursi, jumlah_tiket, subtotal_tiket, diskon, biaya_admin, total,
                     kode_bayar, metode_bayar, status, kode_tiket, dibuat, dibayar)
VALUES (2, @j1, 'H1,H2,H3,H4,H5', 5, 250000, 15000, 0, 235000, 'QRIS', 'QRIS', 'LUNAS', 'CTX-DEMO-000002',
        NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);
SET @b2 = LAST_INSERT_ID();
INSERT INTO kursi_dipesan (booking_id, jadwal_id, kursi_id)
SELECT @b2, @j1, id FROM kursi WHERE studio_id = 1 AND kode IN ('H1', 'H2', 'H3', 'H4', 'H5');

-- 1 tiket Premiere, transfer BCA (biaya admin 2.500)
INSERT INTO booking (pengguna_id, jadwal_id, kursi, jumlah_tiket, subtotal_tiket, diskon, biaya_admin, total,
                     kode_bayar, metode_bayar, status, kode_tiket, dibuat, dibayar)
VALUES (2, @j2, 'C3', 1, 90000, 0, 2500, 92500, 'BANK', 'Transfer BCA', 'LUNAS', 'CTX-DEMO-000003',
        NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);
SET @b3 = LAST_INSERT_ID();
INSERT INTO kursi_dipesan (booking_id, jadwal_id, kursi_id)
SELECT @b3, @j2, id FROM kursi WHERE studio_id = 3 AND kode = 'C3';
