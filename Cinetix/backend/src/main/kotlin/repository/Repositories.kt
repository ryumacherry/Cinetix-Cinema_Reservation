package repository

import model.BookingBaru
import model.BookingDetail
import model.Cinema
import model.Film
import model.Jadwal
import model.Kota
import model.KursiDenah
import model.Laporan
import model.Pengguna
import model.Studio
import model.StudioData
import model.TipeStudio
import java.time.LocalDate

/**
 * Interface repository. Service hanya bergantung pada interface ini,
 * jadi saat unit test bisa diganti repository palsu (in-memory) tanpa MySQL.
 */
interface CrudRepository<T> {
    fun semua(): List<T>
    fun cari(id: Int): T?
    fun tambah(item: T): Int
    fun ubah(id: Int, item: T)
    fun hapus(id: Int)
}

interface KotaRepository : CrudRepository<Kota>

interface CinemaRepository : CrudRepository<Cinema> {
    fun byKota(kotaId: Int): List<Cinema>
}

interface FilmRepository : CrudRepository<Film> {
    fun byStatus(status: String): List<Film>
    /** Menyimpan lokasi file poster (null = hapus poster). Dipisah dari [ubah] supaya edit data film tidak menghapus poster. */
    fun ubahPoster(id: Int, posterUrl: String?)
}

interface StudioRepository : CrudRepository<StudioData> {
    fun byCinema(cinemaId: Int): List<StudioData>
    /** Objek OOP Studio (Reguler/Premiere/Suites) lengkap dengan harga dasarnya. */
    fun cariStudio(id: Int): Studio?
}

/** Tabel `kursi`: denah setiap studio (baris, nomor, section dekat/jauh layar, sisi kiri/kanan). */
interface KursiRepository {
    fun denah(studioId: Int): List<KursiDenah>
    /** Mengganti seluruh denah studio (dipakai saat studio dibuat/diubah). */
    fun simpanDenah(studioId: Int, denah: List<KursiDenah>)
    /** Studio yang belum punya denah sama sekali. */
    fun studioTanpaDenah(): List<Int>
}

interface HargaStudioRepository {
    fun semua(): Map<TipeStudio, Double>
    fun ubah(tipe: TipeStudio, harga: Double)
}

data class FilterJadwal(
    val tanggal: LocalDate,
    val filmId: Int? = null,
    val cinemaId: Int? = null,
    val kotaId: Int? = null
)

interface JadwalRepository : CrudRepository<Jadwal> {
    fun cariJadwal(filter: FilterJadwal): List<Jadwal>
    fun findByFilmAndTanggal(filmId: Int, tanggal: LocalDate) = cariJadwal(FilterJadwal(tanggal, filmId = filmId))
    fun mulaiTanggal(tanggal: LocalDate): List<Jadwal>
    fun adaUntukStudioTanggal(studioId: Int, tanggal: LocalDate): Boolean
    /** Kode kursi (A1, B2, ...) yang sudah dipesan untuk jadwal ini. */
    fun kursiTerisi(jadwalId: Int): Set<String>
}

data class AkunTersimpan(val pengguna: Pengguna, val passwordHash: String)

interface PenggunaRepository {
    fun cariByEmail(email: String): AkunTersimpan?
    fun cariById(id: Int): Pengguna?
    fun tambah(nama: String, email: String, passwordHash: String, peran: String = "PELANGGAN"): Int
    fun simpanSesi(token: String, penggunaId: Int)
    fun penggunaDariSesi(token: String): Pengguna?
    fun hapusSesi(token: String)
}

interface BookingRepository {
    /** Menyimpan booking + kursi dalam SATU transaksi. Kursi ganda -> KursiSudahDipesanException. */
    fun simpanBaru(data: BookingBaru): Int
    fun cari(id: Int): BookingDetail?
    fun riwayat(penggunaId: Int): List<BookingDetail>
    fun setPembayaran(id: Int, kodeBayar: String, metode: String, biayaAdmin: Double, total: Double)
    /** PENDING -> LUNAS + simpan kode tiket. */
    fun tandaiLunas(id: Int, kodeTiket: String)
    /** PENDING -> BATAL dan lepas kursinya. */
    fun batalkan(id: Int)
    /** Membatalkan booking PENDING yang melewati batas waktu bayar. Mengembalikan jumlah yang dibatalkan. */
    fun batalkanKedaluwarsa(menit: Int): Int
    fun laporan(): Laporan
}
