package service

import exception.PembayaranGagalException
import exception.ValidasiException
import model.BookingDetail
import model.Cetakable
import model.PembayaranFactory
import model.Qris
import model.StatusBooking
import model.Tiket
import model.TransferBank
import repository.BookingRepository
import util.BarcodeGenerator
import util.KodeGenerator

/** Instruksi yang dikirim ke web setelah pelanggan memilih metode bayar. */
data class InstruksiBayar(
    val bookingId: Int,
    val kode: String,
    val metode: String,
    val subtotal: Double,             // harga tiket x jumlah kursi
    val diskon: Double,               // diskon >= 5 tiket
    val nominal: Double,              // subtotal - diskon
    val biayaAdmin: Double,
    val totalBayar: Double,
    val status: String,               // MENUNGGU atau LUNAS
    val instruksi: String,
    val qrisPayload: String? = null,
    val nomorVa: String? = null,
    val sisaDetik: Long = 0,
    val booking: BookingDetail? = null
)

/**
 * SIMULASI pembayaran (QRIS, transfer bank, kartu kredit).
 * Polymorphism: PembayaranFactory memilih subclass, lalu biayaAdmin()/proses() berbeda per metode.
 */
class PembayaranService(
    private val bookingService: BookingService,
    private val repo: BookingRepository,
    private val simpanTiket: (Cetakable) -> Unit = { BarcodeGenerator.simpanKeFolder(it) }
) {
    fun mulai(penggunaId: Int, bookingId: Int, kode: String?, bank: String?, noKartu: String?): InstruksiBayar {
        bookingService.bersihkanKedaluwarsa()
        val b = bookingService.detail(penggunaId, bookingId)
        if (b.status != StatusBooking.PENDING.name)
            throw PembayaranGagalException("Booking ini berstatus ${b.status}, tidak bisa dibayar.")

        val pembayaran = PembayaranFactory.buat(kode, b.nominal, bank, noKartu)
        if (pembayaran is TransferBank && !pembayaran.proses())
            throw ValidasiException("Bank tidak didukung. Pilih: ${TransferBank.DAFTAR_BANK.joinToString()}.")

        // Kartu kredit langsung diproses (tidak perlu tombol konfirmasi)
        if (!pembayaran.butuhKonfirmasi && !pembayaran.bayar())
            throw PembayaranGagalException("Kartu kredit ditolak. Periksa 16 digit nomor kartu.")

        repo.setPembayaran(b.id, pembayaran.kode, pembayaran.metode, pembayaran.biayaAdmin(), pembayaran.totalBayar())

        val lunas = if (!pembayaran.butuhKonfirmasi) finalisasi(penggunaId, b.id) else null
        return InstruksiBayar(
            bookingId = b.id,
            kode = pembayaran.kode,
            metode = pembayaran.metode,
            subtotal = b.subtotalTiket,
            diskon = b.diskon,
            nominal = b.nominal,
            biayaAdmin = pembayaran.biayaAdmin(),
            totalBayar = pembayaran.totalBayar(),
            status = if (lunas != null) "LUNAS" else "MENUNGGU",
            instruksi = pembayaran.instruksi(),
            qrisPayload = (pembayaran as? Qris)?.payload(),
            nomorVa = (pembayaran as? TransferBank)?.nomorVirtualAccount(b.id),
            sisaDetik = b.sisaDetikBayar,
            booking = lunas
        )
    }

    /** Tombol "Saya sudah bayar" (simulasi) untuk QRIS & transfer bank. */
    fun konfirmasi(penggunaId: Int, bookingId: Int): BookingDetail {
        bookingService.bersihkanKedaluwarsa()
        val b = bookingService.detail(penggunaId, bookingId)
        return when (b.status) {
            StatusBooking.LUNAS.name -> b
            StatusBooking.BATAL.name -> throw PembayaranGagalException("Waktu pembayaran habis, booking dibatalkan.")
            else -> {
                if (b.kodeBayar == null) throw PembayaranGagalException("Pilih metode pembayaran terlebih dahulu.")
                finalisasi(penggunaId, b.id)
            }
        }
    }

    private fun finalisasi(penggunaId: Int, bookingId: Int): BookingDetail {
        val b = bookingService.detail(penggunaId, bookingId)
        repo.tandaiLunas(b.id, KodeGenerator.kodeTiket(b.id, b.jadwal.tanggal))

        val lunas = bookingService.detail(penggunaId, bookingId)
        val tiket = Tiket.dari(lunas, lunas.bonus)
        simpanTiket(tiket)
        return lunas.copy(teksTiket = tiket.cetak())
    }
}
