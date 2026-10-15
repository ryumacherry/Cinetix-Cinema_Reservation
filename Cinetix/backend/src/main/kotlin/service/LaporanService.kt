package service

import model.Laporan
import repository.BookingRepository

class LaporanService(private val repo: BookingRepository) {
    fun laporan(): Laporan = repo.laporan()
}
