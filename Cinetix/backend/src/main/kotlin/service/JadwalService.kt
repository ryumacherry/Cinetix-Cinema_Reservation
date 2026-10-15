package service

import exception.DataTidakDitemukanException
import exception.HariTidakValidException
import model.Diskon
import model.Jadwal
import model.Kursi
import model.KursiDenah
import model.Section
import model.Sisi
import model.StatusFilm
import model.Studio
import model.TipeStudio
import repository.FilmRepository
import repository.FilterJadwal
import repository.JadwalRepository
import repository.KursiRepository
import repository.StudioRepository
import util.Waktu
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Jadwal yang siap ditampilkan ke pelanggan (sudah ada harga, diskon & sisa kursi). */
data class JadwalTersedia(
    val id: Int,
    val filmId: Int,
    val filmJudul: String,
    val cinemaId: Int,
    val cinemaNama: String,
    val studioId: Int,
    val studioNama: String,
    val tipe: String,
    val tanggal: LocalDate,
    val jam: LocalTime,
    val harga: Double,
    val akhirPekan: Boolean,
    val persenDiskon: Double,
    val kapasitas: Int,
    val sisaKursi: Int,
    val deskripsiStudio: String
)

/** Satu baris denah: kursi kiri | jalan tengah | kursi kanan. */
data class BarisDenah(val baris: String, val kiri: List<Kursi>, val kanan: List<Kursi>)

/** Satu section denah: DEPAN (dekat layar) atau BELAKANG (jauh dari layar). */
data class SectionDenah(val kode: String, val label: String, val baris: List<BarisDenah>)

data class DenahKursi(
    val jadwal: JadwalTersedia,
    val kursiPerBaris: Int,
    val maksPilih: Int,
    val minTiketDiskon: Int,
    val persenDiskon: Double,
    val section: List<SectionDenah>
)

class JadwalService(
    private val repo: JadwalRepository,
    private val studioRepo: StudioRepository,
    private val filmRepo: FilmRepository,
    private val kursiRepo: KursiRepository,
    private val clock: Clock = Waktu.clock
) {
    fun hariIni(): LocalDate = LocalDate.now(clock)

    /** Hari ini + 2 hari berikutnya (maksimal 3 hari). */
    fun tanggalTersedia(): List<LocalDate> = (0L until MAKS_HARI).map { hariIni().plusDays(it) }

    fun validasiTanggal(tanggal: LocalDate) {
        val hariIni = hariIni()
        if (tanggal.isBefore(hariIni) || tanggal.isAfter(hariIni.plusDays(MAKS_HARI - 1)))
            throw HariTidakValidException("Jadwal hanya tersedia 3 hari ke depan")
    }

    /** Versi dari dokumen desain: validasi maksimal 3 hari lalu ambil jadwal film. */
    fun cariJadwal(filmId: Int, tanggal: LocalDate): List<Jadwal> {
        validasiTanggal(tanggal)
        return repo.findByFilmAndTanggal(filmId, tanggal)
    }

    /** Jadwal lengkap dengan harga (polymorphism Studio.hargaTiket) untuk versi web. */
    fun cariTersedia(filter: FilterJadwal): List<JadwalTersedia> {
        validasiTanggal(filter.tanggal)
        val cache = mutableMapOf<Int, Studio?>()
        return repo.cariJadwal(filter)
            .filter { belumMulai(it) }
            .mapNotNull { j ->
                val studio = cache.getOrPut(j.studioId) { studioRepo.cariStudio(j.studioId) } ?: return@mapNotNull null
                keTersedia(j, studio)
            }
    }

    fun pastikanBisaDipesan(jadwal: Jadwal) {
        validasiTanggal(jadwal.tanggal)
        if (!belumMulai(jadwal)) throw HariTidakValidException("Jadwal jam ${jadwal.jam} sudah lewat.")
    }

    /**
     * Denah kursi dari tabel `kursi`, dikelompokkan per section (dekat/jauh layar),
     * per baris, lalu dibagi kiri/kanan jalan tengah. Kursi yang sudah dipesan ditandai terisi.
     */
    fun denahKursi(jadwalId: Int): DenahKursi {
        val jadwal = repo.cari(jadwalId) ?: throw DataTidakDitemukanException("Jadwal")
        pastikanBisaDipesan(jadwal)
        val studio = studioRepo.cariStudio(jadwal.studioId) ?: throw DataTidakDitemukanException("Studio")
        val terisi = repo.kursiTerisi(jadwalId)
        val denah = kursiRepo.denah(studio.id)
        if (denah.isEmpty()) throw DataTidakDitemukanException("Denah kursi ${studio.ruang}")

        fun keKursi(k: KursiDenah) = Kursi(k.id, k.kode, k.nomor, k.kode in terisi)
        val section = Section.entries.mapNotNull { s ->
            val isi = denah.filter { it.section == s.name }
            if (isi.isEmpty()) return@mapNotNull null
            SectionDenah(
                kode = s.name,
                label = s.label,
                baris = isi.groupBy { it.baris }.map { (baris, kursi) ->
                    BarisDenah(
                        baris = baris,
                        kiri = kursi.filter { it.sisi == Sisi.KIRI.name }.sortedBy { it.nomor }.map { keKursi(it) },
                        kanan = kursi.filter { it.sisi == Sisi.KANAN.name }.sortedBy { it.nomor }.map { keKursi(it) }
                    )
                }
            )
        }
        return DenahKursi(
            jadwal = keTersedia(jadwal.copy(terisi = terisi.size), studio),
            kursiPerBaris = studio.kursiPerBaris,
            maksPilih = MAKS_KURSI,
            minTiketDiskon = Diskon.MIN_TIKET,
            persenDiskon = studio.persenDiskon,
            section = section
        )
    }

    fun hargaTiket(studio: Studio, tanggal: LocalDate) = studio.hargaTiket(Waktu.akhirPekan(tanggal))

    private fun belumMulai(j: Jadwal): Boolean =
        LocalDateTime.of(j.tanggal, j.jam).isAfter(LocalDateTime.now(clock))

    private fun keTersedia(j: Jadwal, studio: Studio) = JadwalTersedia(
        id = j.id,
        filmId = j.filmId,
        filmJudul = j.filmJudul ?: "",
        cinemaId = j.cinemaId ?: 0,
        cinemaNama = j.cinemaNama ?: "",
        studioId = studio.id,
        studioNama = studio.ruang,
        tipe = studio.tipe.label,
        tanggal = j.tanggal,
        jam = j.jam,
        harga = hargaTiket(studio, j.tanggal),
        akhirPekan = Waktu.akhirPekan(j.tanggal),
        persenDiskon = studio.persenDiskon,
        kapasitas = studio.kapasitas,
        sisaKursi = studio.kapasitas - j.terisi,
        deskripsiStudio = studio.deskripsiPengalaman()
    )

    /** Membuat denah (tabel `kursi`) untuk studio yang belum punya. Dipanggil saat backend start. */
    fun pastikanDenahLengkap(): Int {
        var dibuat = 0
        kursiRepo.studioTanpaDenah().forEach { id ->
            val studio = studioRepo.cariStudio(id) ?: return@forEach
            kursiRepo.simpanDenah(id, studio.buatDenah())
            dibuat++
        }
        return dibuat
    }

    /**
     * Mengisi jadwal otomatis untuk 3 hari ke depan pada studio yang belum punya jadwal,
     * supaya demo selalu punya data walaupun seed.sql dijalankan beberapa hari lalu.
     */
    fun generateOtomatis(): Int {
        val film = filmRepo.byStatus(StatusFilm.NOW_PLAYING.name)
        if (film.isEmpty()) return 0
        var dibuat = 0
        studioRepo.semua().forEachIndexed { idxStudio, studio ->
            tanggalTersedia().forEachIndexed { idxHari, tanggal ->
                if (repo.adaUntukStudioTanggal(studio.id, tanggal)) return@forEachIndexed
                val slot = if (TipeStudio.dari(studio.tipe) == TipeStudio.REGULER) SLOT_REGULER else SLOT_PREMIUM
                slot.forEachIndexed { idxSlot, jam ->
                    val f = film[(idxStudio + idxHari + idxSlot / 2) % film.size]
                    repo.tambah(Jadwal(filmId = f.id, studioId = studio.id, tanggal = tanggal, jam = LocalTime.parse(jam)))
                    dibuat++
                }
            }
        }
        return dibuat
    }

    companion object {
        const val MAKS_HARI = 3L
        const val MAKS_KURSI = 6
        val SLOT_REGULER = listOf("10:30", "13:15", "16:00", "18:45", "21:30")
        val SLOT_PREMIUM = listOf("12:00", "15:00", "19:00", "21:45")
    }
}
