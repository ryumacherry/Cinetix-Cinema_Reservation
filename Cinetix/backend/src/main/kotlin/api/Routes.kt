package api

import exception.AksesDitolakException
import exception.ValidasiException
import model.Peran
import repository.FilterJadwal
import service.AdminService
import service.AuthService
import service.BookingService
import service.JadwalService
import service.KatalogService
import service.LaporanService
import service.PembayaranService
import service.PosterService
import service.RiwayatService
import util.BarcodeGenerator
import util.PosterStorage
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Wadah untuk semua service (dependency container sederhana). */
class Layanan(
    val auth: AuthService,
    val katalog: KatalogService,
    val jadwal: JadwalService,
    val booking: BookingService,
    val pembayaran: PembayaranService,
    val riwayat: RiwayatService,
    val laporan: LaporanService,
    val admin: AdminService,
    val poster: PosterService
)

private fun Konteks.tanggalQuery(default: LocalDate): LocalDate = query["tanggal"]?.takeIf { it.isNotBlank() }?.let {
    try { LocalDate.parse(it) } catch (e: DateTimeParseException) { throw ValidasiException("Tanggal harus berformat YYYY-MM-DD.") }
} ?: default

fun daftarRute(r: Router, l: Layanan) {

    // ---------------- Umum ----------------
    r.get("/api") { it.json(mapOf("aplikasi" to "CINETIX API", "status" to "OK", "hariIni" to l.jadwal.hariIni())) }

    // ---------------- Auth ----------------
    r.post("/api/auth/daftar") { c ->
        val req = c.body(DaftarRequest::class.java)
        val hasil = l.auth.daftar(req.nama, req.email, req.password)
        c.json(LoginResponse(hasil.token, PenggunaDto.dari(hasil.pengguna)), 201)
    }
    r.post("/api/auth/login") { c ->
        val req = c.body(LoginRequest::class.java)
        val hasil = l.auth.login(req.email, req.password)
        c.json(LoginResponse(hasil.token, PenggunaDto.dari(hasil.pengguna)))
    }
    r.post("/api/auth/logout", Akses.LOGIN) { c ->
        l.auth.logout(c.token)
        c.json(PesanResponse("Berhasil logout."))
    }
    r.get("/api/me", Akses.LOGIN) { c -> c.json(PenggunaDto.dari(c.pengguna)) }

    // ---------------- Katalog ----------------
    r.get("/api/kota") { c -> c.json(l.katalog.daftarKota()) }
    r.get("/api/cinema") { c -> c.json(l.katalog.daftarCinema(c.queryInt("kotaId"))) }
    r.get("/api/cinema/{id}") { c -> c.json(l.katalog.detailCinema(c.pathInt("id"))) }
    r.get("/api/film") { c -> c.json(l.katalog.daftarFilm(c.query["status"])) }
    r.get("/api/film/{id}") { c -> c.json(l.katalog.detailFilm(c.pathInt("id"))) }

    // File poster film disajikan statis dari folder upload di server (nama file dibuat server, tidak per-film).
    r.get("/uploads/posters/{nama}") { c ->
        val (data, tipe) = l.poster.baca(c.path("nama"))
        c.berkas(data, tipe)
    }

    // ---------------- Jadwal & kursi ----------------
    r.get("/api/jadwal/tanggal") { c -> c.json(l.jadwal.tanggalTersedia()) }
    r.get("/api/jadwal") { c ->
        val filter = FilterJadwal(
            tanggal = c.tanggalQuery(l.jadwal.hariIni()),
            filmId = c.queryInt("filmId"),
            cinemaId = c.queryInt("cinemaId"),
            kotaId = c.queryInt("kotaId")
        )
        c.json(l.jadwal.cariTersedia(filter))
    }
    r.get("/api/jadwal/{id}/kursi") { c ->
        l.booking.bersihkanKedaluwarsa()
        c.json(l.jadwal.denahKursi(c.pathInt("id")))
    }

    // ---------------- Booking & pembayaran (wajib login) ----------------
    r.post("/api/booking", Akses.LOGIN) { c ->
        if (c.pengguna.peran == Peran.ADMIN) throw AksesDitolakException("Admin tidak dapat memesan tiket.")
        val req = c.body(BookingRequest::class.java)
        c.json(l.booking.buat(c.pengguna.id, req.jadwalId, req.kursi), 201)
    }
    r.get("/api/booking", Akses.LOGIN) { c -> c.json(l.riwayat.riwayat(c.pengguna.id)) }
    r.get("/api/booking/{id}", Akses.LOGIN) { c ->
        c.json(l.riwayat.eTicket(c.pengguna.id, c.pathInt("id"), c.pengguna.peran == Peran.ADMIN))
    }
    r.post("/api/booking/{id}/bayar", Akses.LOGIN) { c ->
        val req = c.body(BayarRequest::class.java)
        c.json(l.pembayaran.mulai(c.pengguna.id, c.pathInt("id"), req.metode, req.bank, req.noKartu))
    }
    r.post("/api/booking/{id}/konfirmasi", Akses.LOGIN) { c ->
        c.json(l.pembayaran.konfirmasi(c.pengguna.id, c.pathInt("id")))
    }
    r.post("/api/booking/{id}/batal", Akses.LOGIN) { c ->
        c.json(l.booking.batal(c.pengguna.id, c.pathInt("id")))
    }

    // Gambar QR/barcode (bisa dibuka di browser untuk dicetak)
    r.get("/api/qr") { c -> c.png(BarcodeGenerator.qrPng(c.query["isi"] ?: throw ValidasiException("Parameter isi wajib ada."))) }
    r.get("/api/barcode/{kode}") { c -> c.png(BarcodeGenerator.barcodePng(c.path("kode"))) }

    // ---------------- Admin ----------------
    r.get("/api/admin/laporan", Akses.ADMIN) { c -> c.json(l.laporan.laporan()) }
    r.get("/api/admin/harga-studio", Akses.ADMIN) { c -> c.json(l.admin.hargaStudio()) }
    r.put("/api/admin/harga-studio/{tipe}", Akses.ADMIN) { c ->
        l.admin.ubahHarga(c.path("tipe"), c.body(HargaRequest::class.java).harga)
        c.json(l.admin.hargaStudio())
    }
    r.post("/api/admin/jadwal/generate", Akses.ADMIN) { c ->
        c.json(PesanResponse("${l.admin.generateJadwal()} jadwal baru dibuat untuk 3 hari ke depan."))
    }
    // Poster film: body = isi file gambar (JPG/PNG/WEBP, maks 3 MB). Didaftarkan SEBELUM CRUD generik.
    r.post("/api/admin/film/{id}/poster", Akses.ADMIN) { c ->
        val film = l.poster.unggah(c.pathInt("id"), c.bodyBiner(PosterStorage.MAKS_BYTE))
        c.json(PesanResponse("Poster berhasil diunggah.", film.id))
    }
    r.delete("/api/admin/film/{id}/poster", Akses.ADMIN) { c ->
        l.poster.hapusPoster(c.pathInt("id"))
        c.json(PesanResponse("Poster berhasil dihapus.", c.pathInt("id")))
    }
    // Hapus film khusus: file poster ikut terhapus
    r.delete("/api/admin/film/{id}", Akses.ADMIN) { c ->
        l.poster.hapusFilm(c.pathInt("id"))
        c.json(PesanResponse("Data berhasil dihapus.", c.pathInt("id")))
    }
    // CRUD generik: kota, cinema, studio, film, jadwal
    r.get("/api/admin/{entitas}", Akses.ADMIN) { c -> c.json(l.admin.entitas(c.path("entitas")).daftar()) }
    r.post("/api/admin/{entitas}", Akses.ADMIN) { c ->
        val id = l.admin.entitas(c.path("entitas")).tambah(c.bodyMentah())
        c.json(PesanResponse("Data berhasil ditambahkan.", id), 201)
    }
    r.put("/api/admin/{entitas}/{id}", Akses.ADMIN) { c ->
        l.admin.entitas(c.path("entitas")).ubah(c.pathInt("id"), c.bodyMentah())
        c.json(PesanResponse("Data berhasil diubah.", c.pathInt("id")))
    }
    r.delete("/api/admin/{entitas}/{id}", Akses.ADMIN) { c ->
        val ent = l.admin.entitas(c.path("entitas"))
        ent.hapus(c.pathInt("id"))
        c.json(PesanResponse("Data berhasil dihapus.", c.pathInt("id")))
    }
}

