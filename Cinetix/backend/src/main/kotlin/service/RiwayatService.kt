package service

import model.BookingDetail
import model.StatusBooking
import model.Tiket
import repository.BookingRepository

class RiwayatService(private val bookingService: BookingService, private val repo: BookingRepository) {

    fun riwayat(penggunaId: Int): List<BookingDetail> {
        bookingService.bersihkanKedaluwarsa()
        return repo.riwayat(penggunaId)
    }

    /** Detail booking + teks e-ticket (kalau sudah LUNAS). */
    fun eTicket(penggunaId: Int, bookingId: Int, admin: Boolean = false): BookingDetail {
        val b = bookingService.detail(penggunaId, bookingId, admin)
        return if (b.status == StatusBooking.LUNAS.name) b.copy(teksTiket = Tiket.dari(b, b.bonus).cetak()) else b
    }
}
