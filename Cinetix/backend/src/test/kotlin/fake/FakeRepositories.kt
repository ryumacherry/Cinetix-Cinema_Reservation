package fake

import exception.KursiSudahDipesanException
import model.BookingBaru
import model.BookingDetail
import model.Film
import model.Jadwal
import model.KursiDenah
import model.Laporan
import model.Studio
import model.StudioData
import repository.BookingRepository
import repository.FilmRepository
import repository.FilterJadwal
import repository.JadwalRepository
import repository.KursiRepository
import repository.StudioRepository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * "Database palsu" di memori untuk unit test.
 * Service diuji tanpa MySQL karena hanya bergantung pada interface repository.
 */
class Penyimpanan {
    val studio = mutableMapOf<Int, Studio>()
    val denah = mutableMapOf<Int, List<KursiDenah>>()          // studioId -> kursi (tabel kursi)
    val film = mutableListOf<Film>()
    val jadwal = mutableListOf<Jadwal>()
    val booking = mutableMapOf<Int, BookingDetail>()
    val kursi = mutableMapOf<Int, MutableSet<String>>()       // jadwalId -> kode kursi terisi
    val kedaluwarsa = mutableSetOf<Int>()                       // booking yang dianggap lewat 15 menit

    /** Menambah studio sekaligus denahnya (meniru JdbcStudioRepository.tambah). */
    fun tambahStudio(s: Studio) {
        studio[s.id] = s
        denah[s.id] = s.buatDenah().mapIndexed { i, k -> k.copy(id = s.id * 1000 + i + 1) }
    }
}

class FakeStudioRepository(private val db: Penyimpanan) : StudioRepository {
    private fun data(s: Studio) = StudioData(s.id, 1, s.ruang, s.tipe.kodeDb, s.kapasitas, "Cinema Uji")
    override fun cariStudio(id: Int) = db.studio[id]
    override fun semua() = db.studio.values.map { data(it) }
    override fun byCinema(cinemaId: Int) = semua()
    override fun cari(id: Int) = db.studio[id]?.let { data(it) }
    override fun tambah(item: StudioData): Int { error("tidak dipakai di test") }
    override fun ubah(id: Int, item: StudioData) { error("tidak dipakai di test") }
    override fun hapus(id: Int) { error("tidak dipakai di test") }
}

class FakeKursiRepository(private val db: Penyimpanan) : KursiRepository {
    override fun denah(studioId: Int) = db.denah[studioId].orEmpty()
    override fun simpanDenah(studioId: Int, denah: List<KursiDenah>) {
        db.denah[studioId] = denah.mapIndexed { i, k -> k.copy(id = studioId * 1000 + i + 1) }
    }
    override fun studioTanpaDenah() = db.studio.keys.filter { db.denah[it].isNullOrEmpty() }
}

class FakeFilmRepository(private val db: Penyimpanan) : FilmRepository {
    override fun byStatus(status: String) = db.film.filter { it.status == status }
    override fun semua() = db.film.toList()
    override fun cari(id: Int) = db.film.firstOrNull { it.id == id }
    override fun tambah(item: Film): Int { db.film += item.copy(id = db.film.size + 1); return db.film.size }
    override fun ubah(id: Int, item: Film) { error("tidak dipakai di test") }
    override fun ubahPoster(id: Int, posterUrl: String?) {
        val i = db.film.indexOfFirst { it.id == id }
        if (i < 0) throw exception.DataTidakDitemukanException("Film")
        db.film[i] = db.film[i].copy(posterUrl = posterUrl)
    }
    override fun hapus(id: Int) { db.film.removeAll { it.id == id } }
}

class FakeJadwalRepository(private val db: Penyimpanan) : JadwalRepository {
    private fun lengkapi(j: Jadwal) = j.copy(
        filmJudul = db.film.firstOrNull { it.id == j.filmId }?.judul ?: "Film ${j.filmId}",
        studioNama = db.studio[j.studioId]?.ruang,
        studioTipe = db.studio[j.studioId]?.tipe?.label,
        cinemaId = 1, cinemaNama = "Cinema Uji",
        terisi = db.kursi[j.id]?.size ?: 0
    )
    override fun cariJadwal(filter: FilterJadwal) = db.jadwal
        .filter { it.tanggal == filter.tanggal && (filter.filmId == null || it.filmId == filter.filmId) }
        .map { lengkapi(it) }
    override fun mulaiTanggal(tanggal: LocalDate) = db.jadwal.filter { !it.tanggal.isBefore(tanggal) }.map { lengkapi(it) }
    override fun adaUntukStudioTanggal(studioId: Int, tanggal: LocalDate) =
        db.jadwal.any { it.studioId == studioId && it.tanggal == tanggal }
    override fun kursiTerisi(jadwalId: Int): Set<String> = db.kursi[jadwalId]?.toSet() ?: emptySet()
    override fun semua() = db.jadwal.map { lengkapi(it) }
    override fun cari(id: Int) = db.jadwal.firstOrNull { it.id == id }?.let { lengkapi(it) }
    override fun tambah(item: Jadwal): Int {
        val id = (db.jadwal.maxOfOrNull { it.id } ?: 0) + 1
        db.jadwal += item.copy(id = id); return id
    }
    override fun ubah(id: Int, item: Jadwal) { error("tidak dipakai di test") }
    override fun hapus(id: Int) { error("tidak dipakai di test") }
}

class FakeBookingRepository(private val db: Penyimpanan, private val jadwalRepo: FakeJadwalRepository) : BookingRepository {
    private var urut = 0

    override fun simpanBaru(data: BookingBaru): Int {
        // meniru transaksi + UNIQUE(jadwal_id, kursi_id): cek semua kursi dulu, baru simpan
        val terisi = db.kursi.getOrPut(data.jadwalId) { mutableSetOf() }
        data.kursi.firstOrNull { it.kode in terisi }?.let { throw KursiSudahDipesanException(it.kode) }
        terisi += data.kursi.map { it.kode }

        val id = ++urut
        db.booking[id] = BookingDetail(
            id = id, penggunaId = data.penggunaId, namaPengguna = "Pengguna ${data.penggunaId}",
            jadwal = jadwalRepo.cari(data.jadwalId)!!, kursi = data.kursi.map { it.kode },
            subtotalTiket = data.subtotalTiket, diskon = data.diskon, biayaAdmin = 0.0,
            total = data.subtotalTiket - data.diskon, metodeBayar = null, kodeBayar = null,
            status = "PENDING", kodeTiket = null, dibuat = LocalDateTime.now(), dibayar = null, sisaDetikBayar = 900
        )
        return id
    }

    override fun cari(id: Int) = db.booking[id]
    override fun riwayat(penggunaId: Int) = db.booking.values.filter { it.penggunaId == penggunaId }

    override fun setPembayaran(id: Int, kodeBayar: String, metode: String, biayaAdmin: Double, total: Double) {
        db.booking[id] = db.booking[id]!!.copy(kodeBayar = kodeBayar, metodeBayar = metode, biayaAdmin = biayaAdmin, total = total)
    }

    override fun tandaiLunas(id: Int, kodeTiket: String) {
        db.booking[id] = db.booking[id]!!.copy(status = "LUNAS", kodeTiket = kodeTiket, dibayar = LocalDateTime.now())
    }

    override fun batalkan(id: Int) {
        val b = db.booking[id] ?: return
        if (b.status != "PENDING") return
        db.kursi[b.jadwal.id]?.removeAll(b.kursi.toSet())
        db.booking[id] = b.copy(status = "BATAL")
    }

    override fun batalkanKedaluwarsa(menit: Int): Int {
        val ids = db.kedaluwarsa.filter { db.booking[it]?.status == "PENDING" }
        ids.forEach { batalkan(it) }
        return ids.size
    }

    override fun laporan() = Laporan(0.0, 0.0, 0.0, 0, 0, emptyList(), emptyList())
}
