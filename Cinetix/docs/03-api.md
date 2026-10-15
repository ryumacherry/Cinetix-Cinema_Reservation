# Dokumentasi REST API — Cinetix

Base URL lokal: `http://localhost:8080`.
`GET /` (tanpa `/api`) menyajikan **versi web** (`backend/src/main/resources/web/`).
Semua respons berformat JSON. Error selalu berbentuk `{"status": 409, "pesan": "Kursi A1 sudah dipesan orang lain."}`.

Endpoint bertanda 🔒 wajib header `Authorization: Bearer <token>` (dari login). 🛡️ = khusus admin.

## Auth
| Metode | Endpoint | Body | Keterangan |
|---|---|---|---|
| POST | `/api/auth/daftar` | `{nama, email, password}` | Daftar pelanggan, langsung login |
| POST | `/api/auth/login` | `{email, password}` | Mengembalikan `{token, pengguna}` |
| POST | `/api/auth/logout` 🔒 | – | Hapus sesi |
| GET | `/api/me` 🔒 | – | Profil pengguna (nama, email, peran) |

## Katalog
| Metode | Endpoint | Keterangan |
|---|---|---|
| GET | `/api/kota` | Daftar kota |
| GET | `/api/cinema?kotaId=2` | Bioskop per kota (+ tipe studio) |
| GET | `/api/cinema/{id}` | Detail bioskop |
| GET | `/api/film?status=NOW_PLAYING` | `NOW_PLAYING` / `COMING_SOON` |
| GET | `/api/film/{id}` | Detail film. Field `posterUrl` = lokasi poster di server (mis. `/uploads/posters/film-3-1760000000000.jpg`), `null` jika belum ada |
| GET | `/uploads/posters/{nama}` | Menyajikan file poster dari folder `uploads/posters/` di server (statis). Alamat lengkap = alamat server + `posterUrl` |

## Jadwal & kursi
| Metode | Endpoint | Keterangan |
|---|---|---|
| GET | `/api/jadwal/tanggal` | 3 tanggal yang boleh dipilih |
| GET | `/api/jadwal?tanggal=2026-10-14&filmId=1&kotaId=2` | Bisa juga `cinemaId=`. Tanggal di luar 3 hari → 400 |
| GET | `/api/jadwal/{id}/kursi` | Denah kursi dari tabel `kursi` per section (dekat/jauh layar) + kiri/kanan jalan tengah, kursi terisi, harga, aturan diskon |

## Booking & pembayaran 🔒
| Metode | Endpoint | Body | Keterangan |
|---|---|---|---|
| POST | `/api/booking` | `{jadwalId, kursi:["A1","A2"]}` | Buat booking PENDING (kursi ditahan 15 menit). ≥5 kursi otomatis dapat diskon |
| GET | `/api/booking` | – | Riwayat pembelian |
| GET | `/api/booking/{id}` | – | Detail + `teksTiket` jika LUNAS |
| POST | `/api/booking/{id}/bayar` | `{metode:"QRIS"}` / `{metode:"BANK", bank:"BCA"}` / `{metode:"KREDIT", noKartu:"4111111111111111"}` | QRIS & bank → instruksi (QR payload / VA). Kredit → langsung LUNAS |
| POST | `/api/booking/{id}/konfirmasi` | – | "Saya sudah bayar" (simulasi) → LUNAS + kode tiket |
| POST | `/api/booking/{id}/batal` | – | Batalkan booking & lepas kursi |
| GET | `/api/barcode/{kodeTiket}` | – | PNG barcode CODE_128 (bisa dibuka di browser untuk dicetak) |
| GET | `/api/qr?isi=...` | – | PNG QR code |

## Admin 🔒🛡️
| Metode | Endpoint | Keterangan |
|---|---|---|
| GET | `/api/admin/laporan` | Pendapatan, tiket terjual, total diskon, film terlaris, per metode |
| GET / PUT | `/api/admin/harga-studio`, `/api/admin/harga-studio/{tipe}` body `{harga}` | Harga dasar, persen diskon, kursi per baris per tipe studio (`REGULER`, `PREMIERE`, `SUITES`) |
| POST | `/api/admin/jadwal/generate` | Isi jadwal otomatis 3 hari ke depan |
| GET / POST | `/api/admin/{entitas}` | CRUD generik. `entitas` = `kota`, `cinema`, `studio`, `film`, `jadwal`. Membuat/mengubah `studio` otomatis membuat denah di tabel `kursi` |
| PUT / DELETE | `/api/admin/{entitas}/{id}` | Ubah / hapus. Hapus film ikut menghapus file posternya. Ubah data film tidak mengubah poster |
| POST | `/api/admin/film/{id}/poster` | Unggah poster. **Body = isi file gambar** (JPG/PNG/WEBP, maks 3 MB; jenis dicek dari isi file). Poster lama otomatis diganti |
| DELETE | `/api/admin/film/{id}/poster` | Hapus poster film (kembali ke poster pengganti) |

## Contoh respons denah kursi
`GET /api/jadwal/52/kursi` (studio Premiere, 8 kursi per baris):
```json
{
  "jadwal": { "id": 52, "filmJudul": "Langit Senja di Bandung", "tipe": "Premiere", "harga": 90000.0, "persenDiskon": 8.0 },
  "kursiPerBaris": 8,
  "maksPilih": 6,
  "minTiketDiskon": 5,
  "persenDiskon": 8.0,
  "section": [
    { "kode": "DEPAN", "label": "Dekat layar", "baris": [
      { "baris": "A",
        "kiri":  [ {"id": 241, "kode": "A1", "nomor": 1, "terisi": false} ],
        "kanan": [ {"id": 245, "kode": "A5", "nomor": 5, "terisi": true } ] }
    ]},
    { "kode": "BELAKANG", "label": "Jauh dari layar", "baris": [] }
  ]
}
```
(Dipersingkat: setiap baris berisi 4 kursi kiri dan 4 kursi kanan; section BELAKANG berisi baris C–E.)

## Contoh dengan curl
```bash
# login
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"budi@mail.com","password":"budi123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# jadwal film 1 di Bandung hari ini
curl "localhost:8080/api/jadwal?filmId=1&kotaId=2"

# booking 5 kursi (dapat diskon sesuai tipe studio)
curl -X POST localhost:8080/api/booking -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"jadwalId":111,"kursi":["C1","C2","C3","C4","C5"]}'

# bayar QRIS lalu konfirmasi
curl -X POST localhost:8080/api/booking/3/bayar -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"metode":"QRIS"}'
curl -X POST localhost:8080/api/booking/3/konfirmasi -H "Authorization: Bearer $TOKEN"
```

## Kode status
| Kode | Arti |
|---|---|
| 200/201 | Berhasil |
| 400 | Input tidak valid (format, hari ke-4, kursi tidak ada, kartu ditolak) |
| 401 | Belum login / token tidak berlaku |
| 403 | Bukan admin |
| 404 | Data tidak ditemukan |
| 409 | Konflik: kursi sudah dipesan, data duplikat, data masih dipakai |
