package service

import exception.DataTidakDitemukanException
import exception.ValidasiException
import model.Cinema
import model.Film
import model.Jadwal
import model.Kota
import model.StatusFilm
import model.StudioData
import model.StudioFactory
import model.TipeStudio
import repository.CrudRepository
import repository.HargaStudioRepository
import util.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

// =====================================================================
// GENERICS + INTERFACE: satu kelas CRUD untuk semua entitas admin.
// Setiap Form memvalidasi input lalu mengubahnya menjadi objek domain.
// =====================================================================

interface Formulir<T> {
    fun keDomain(): T
}

class SumberDayaAdmin<T, F : Formulir<T>>(
    val nama: String,
    private val kelasForm: Class<F>,
    private val repo: CrudRepository<T>
) {
    fun daftar(): List<T> = repo.semua()
    fun tambah(json: String): Int = repo.tambah(baca(json).keDomain())
    fun ubah(id: Int, json: String) = repo.ubah(id, baca(json).keDomain())
    fun hapus(id: Int) = repo.hapus(id)

    private fun baca(json: String): F =
        Json.gson.fromJson(json.ifBlank { "{}" }, kelasForm) ?: throw ValidasiException("Data kosong.")
}

data class HargaStudioItem(
    val tipe: String, val label: String, val hargaDasar: Double, val hargaAkhirPekan: Double,
    val persenDiskon: Double, val kursiPerBaris: Int
)

class AdminService(
    private val sumberDaya: Map<String, SumberDayaAdmin<*, *>>,
    private val hargaRepo: HargaStudioRepository,
    private val jadwalService: JadwalService
) {
    fun entitas(nama: String): SumberDayaAdmin<*, *> =
        sumberDaya[nama] ?: throw DataTidakDitemukanException("Entitas admin '$nama'")

    fun hargaStudio(): List<HargaStudioItem> {
        val harga = hargaRepo.semua()
        return TipeStudio.entries.map { t ->
            val studio = StudioFactory.buat(t, 0, 1, hargaDasar = harga[t])
            HargaStudioItem(t.kodeDb, t.label, studio.hargaTiket(false), studio.hargaTiket(true), studio.persenDiskon, studio.kursiPerBaris)
        }
    }

    fun ubahHarga(tipe: String, harga: Double?) {
        if (harga == null || harga < 10_000) throw ValidasiException("Harga dasar minimal Rp10.000.")
        hargaRepo.ubah(TipeStudio.dari(tipe), harga)
    }

    fun generateJadwal(): Int = jadwalService.generateOtomatis()
}

// ---------------------------------------------------------------------
// Form input (semua field nullable supaya pesan error bisa ramah)
// ---------------------------------------------------------------------
private fun wajib(nilai: String?, label: String): String =
    nilai?.trim().takeUnless { it.isNullOrEmpty() } ?: throw ValidasiException("$label wajib diisi.")

private fun <N : Comparable<N>> wajibAngka(nilai: N?, label: String, rentang: ClosedRange<N>): N {
    if (nilai == null) throw ValidasiException("$label wajib diisi.")
    if (nilai !in rentang) throw ValidasiException("$label harus di antara ${rentang.start} dan ${rentang.endInclusive}.")
    return nilai
}

private fun tanggal(teks: String?, label: String): LocalDate = try {
    LocalDate.parse(wajib(teks, label))
} catch (e: DateTimeParseException) {
    throw ValidasiException("$label harus berformat YYYY-MM-DD.")
}

data class KotaForm(val nama: String? = null) : Formulir<Kota> {
    override fun keDomain() = Kota(nama = wajib(nama, "Nama kota"))
}

data class CinemaForm(val nama: String? = null, val alamat: String? = null, val kotaId: Int? = null) : Formulir<Cinema> {
    override fun keDomain() = Cinema(
        nama = wajib(nama, "Nama cinema"),
        alamat = wajib(alamat, "Alamat"),
        kotaId = kotaId ?: throw ValidasiException("Kota wajib dipilih.")
    )
}

data class StudioForm(
    val cinemaId: Int? = null, val nama: String? = null, val tipe: String? = null, val kapasitas: Int? = null
) : Formulir<StudioData> {
    override fun keDomain(): StudioData {
        val t = TipeStudio.dari(wajib(tipe, "Tipe studio"))
        val k = wajibAngka(kapasitas, "Kapasitas", 1..500)
        StudioFactory.buat(t, 0, k).validasiDenah()      // kelipatan kursi per baris & maks. baris A-J
        return StudioData(
            cinemaId = cinemaId ?: throw ValidasiException("Cinema wajib dipilih."),
            nama = wajib(nama, "Nama studio"), tipe = t.kodeDb, kapasitas = k
        )
    }
}

data class FilmForm(
    val judul: String? = null, val genre: String? = null, val durasi: Int? = null, val ratingUsia: String? = null,
    val sinopsis: String? = null, val status: String? = null, val tanggalRilis: String? = null
) : Formulir<Film> {
    override fun keDomain(): Film {
        val rating = ratingUsia?.trim()?.uppercase().takeUnless { it.isNullOrEmpty() } ?: "SU"
        if (rating !in RATING) throw ValidasiException("Rating usia harus salah satu: ${RATING.joinToString()}.")
        return Film(
            judul = wajib(judul, "Judul"),
            genre = wajib(genre, "Genre"),
            durasi = wajibAngka(durasi, "Durasi (menit)", 30..300),
            ratingUsia = rating,
            sinopsis = sinopsis?.trim().orEmpty(),
            status = StatusFilm.dari(status ?: StatusFilm.NOW_PLAYING.name).name,
            tanggalRilis = if (tanggalRilis.isNullOrBlank()) null else tanggal(tanggalRilis, "Tanggal rilis")
        )
    }

    companion object { val RATING = listOf("SU", "R13", "D17", "D21") }
}

data class JadwalForm(
    val filmId: Int? = null, val studioId: Int? = null, val tanggal: String? = null, val jam: String? = null
) : Formulir<Jadwal> {
    override fun keDomain() = Jadwal(
        filmId = filmId ?: throw ValidasiException("Film wajib dipilih."),
        studioId = studioId ?: throw ValidasiException("Studio wajib dipilih."),
        tanggal = tanggal(tanggal, "Tanggal"),
        jam = try { LocalTime.parse(wajib(jam, "Jam")) } catch (e: DateTimeParseException) {
            throw ValidasiException("Jam harus berformat HH:MM, contoh 18:45.")
        }
    )
}
