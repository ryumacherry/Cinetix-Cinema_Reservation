package model

import util.rupiah
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Kota(val id: Int = 0, val nama: String)

data class Cinema(
    val id: Int = 0,
    val nama: String,
    val alamat: String,
    val kotaId: Int,
    val kotaNama: String? = null,
    val tipeStudio: List<String> = emptyList()
)

enum class StatusFilm { NOW_PLAYING, COMING_SOON;
    companion object {
        fun dari(teks: String?): StatusFilm = entries.firstOrNull { it.name.equals(teks?.trim(), true) }
            ?: throw exception.ValidasiException("Status film harus NOW_PLAYING atau COMING_SOON.")
    }
}

data class Film(
    val id: Int = 0,
    val judul: String,
    val genre: String,
    val durasi: Int,
    val ratingUsia: String = "SU",
    val sinopsis: String = "",
    val status: String = StatusFilm.NOW_PLAYING.name,
    val tanggalRilis: LocalDate? = null,
    /** Lokasi file poster di server, contoh "/uploads/posters/film-3-1760000000000.jpg". null = belum ada poster. */
    val posterUrl: String? = null
)

data class StudioData(
    val id: Int = 0,
    val cinemaId: Int,
    val nama: String,
    val tipe: String,
    val kapasitas: Int,
    val cinemaNama: String? = null
)

data class Jadwal(
    val id: Int = 0,
    val filmId: Int,
    val studioId: Int,
    val tanggal: LocalDate,
    val jam: LocalTime,
    val filmJudul: String? = null,
    val filmRating: String? = null,
    val studioNama: String? = null,
    val studioTipe: String? = null,
    val kapasitas: Int = 0,
    val cinemaId: Int? = null,
    val cinemaNama: String? = null,
    val terisi: Int = 0
)

data class Kursi(val id: Int, val kode: String, val nomor: Int, val terisi: Boolean)

enum class StatusBooking { PENDING, LUNAS, BATAL }

data class BookingBaru(
    val penggunaId: Int,
    val jadwalId: Int,
    val kursi: List<KursiDenah>,    
    val subtotalTiket: Double,       
    val diskon: Double               
)

data class BookingDetail(
    val id: Int,
    val penggunaId: Int,
    val namaPengguna: String,
    val jadwal: Jadwal,
    val kursi: List<String>,
    val subtotalTiket: Double,
    val diskon: Double,
    val biayaAdmin: Double,
    val total: Double,
    val metodeBayar: String?,
    val kodeBayar: String?,          // QRIS / BANK / KREDIT
    val status: String,
    val kodeTiket: String?,
    val dibuat: LocalDateTime,
    val dibayar: LocalDateTime?,
    val sisaDetikBayar: Long = 0,
    val bonus: String? = null,      
    val teksTiket: String? = null    
) {
    val nominal get() = subtotalTiket - diskon
}

interface Cetakable {
    fun isiBarcode(): String
    fun cetak(): String
}

data class Tiket(
    val kodeTiket: String,
    val namaPemesan: String,
    val judulFilm: String,
    val rating: String,
    val cinema: String,
    val studio: String,
    val tipeStudio: String,
    val tanggal: LocalDate,
    val jam: LocalTime,
    val kursi: List<String>,
    val diskon: Double,
    val totalBayar: Double,
    val metodeBayar: String,
    val bonus: String?
) : Cetakable {

    override fun isiBarcode() = kodeTiket

    override fun cetak(): String {
        val fmtTanggal = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        val garis = "=".repeat(40)
        return buildString {
            appendLine(garis)
            appendLine("            E-TICKET CINETIX")
            appendLine(garis)
            appendLine("Kode     : $kodeTiket")
            appendLine("Pemesan  : $namaPemesan")
            appendLine("Film     : $judulFilm ($rating)")
            appendLine("Bioskop  : $cinema")
            appendLine("Studio   : $studio - $tipeStudio")
            appendLine("Tanggal  : ${tanggal.format(fmtTanggal)}")
            appendLine("Jam      : ${jam.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            appendLine("Kursi    : ${kursi.joinToString(", ")}")
            bonus?.let { appendLine("Bonus    : $it") }
            if (diskon > 0) appendLine("Diskon   : ${diskon.rupiah()} (beli ${kursi.size} tiket)")
            appendLine("Bayar    : ${totalBayar.rupiah()} via $metodeBayar")
            appendLine(garis)
            appendLine("Tunjukkan barcode ini di pintu studio.")
        }
    }

    companion object {
        fun dari(b: BookingDetail, bonus: String?) = Tiket(
            kodeTiket = b.kodeTiket ?: "-",
            namaPemesan = b.namaPengguna,
            judulFilm = b.jadwal.filmJudul ?: "-",
            rating = b.jadwal.filmRating ?: "SU",
            cinema = b.jadwal.cinemaNama ?: "-",
            studio = b.jadwal.studioNama ?: "-",
            tipeStudio = b.jadwal.studioTipe ?: "-",
            tanggal = b.jadwal.tanggal,
            jam = b.jadwal.jam,
            kursi = b.kursi,
            diskon = b.diskon,
            totalBayar = b.total,
            metodeBayar = b.metodeBayar ?: "-",
            bonus = bonus
        )
    }
}

data class PeringkatFilm(val judul: String, val tiket: Int, val pendapatan: Double)
data class RingkasanMetode(val metode: String, val transaksi: Int, val total: Double)

data class Laporan(
    val totalPendapatan: Double,
    val pendapatanTiket: Double,
    val totalDiskon: Double,
    val jumlahTransaksi: Int,
    val tiketTerjual: Int,
    val filmTerlaris: List<PeringkatFilm>,
    val perMetode: List<RingkasanMetode>
)
