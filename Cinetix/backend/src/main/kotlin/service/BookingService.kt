package service

import exception.DataTidakDitemukanException
import exception.KursiSudahDipesanException
import exception.PembayaranGagalException
import exception.ValidasiException
import model.BookingBaru
import model.BookingDetail
import model.StatusBooking
import repository.BookingRepository
import repository.JadwalRepository
import repository.KursiRepository
import repository.StudioRepository

/** Pilih kursi, hitung total, cegah double booking. */
class BookingService(
    private val jadwalService: JadwalService,
    private val jadwalRepo: JadwalRepository,
    private val studioRepo: StudioRepository,
    private val kursiRepo: KursiRepository,
    private val repo: BookingRepository
) {
    fun buat(penggunaId: Int, jadwalId: Int?, kursi: List<String>?): BookingDetail {
        bersihkanKedaluwarsa()
        val jadwal = jadwalRepo.cari(jadwalId ?: throw ValidasiException("jadwalId wajib diisi."))
            ?: throw DataTidakDitemukanException("Jadwal")
        jadwalService.pastikanBisaDipesan(jadwal)
        val studio = studioRepo.cariStudio(jadwal.studioId) ?: throw DataTidakDitemukanException("Studio")

        val dipilih = kursi.orEmpty().map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
            .sortedWith(compareBy({ it.first() }, { it.drop(1).toIntOrNull() ?: 0 }))
        if (dipilih.isEmpty()) throw ValidasiException("Pilih minimal 1 kursi.")
        if (dipilih.size > JadwalService.MAKS_KURSI)
            throw ValidasiException("Maksimal ${JadwalService.MAKS_KURSI} kursi per transaksi.")
        // kursi harus ada di denah studio (tabel `kursi`)
        val denah = kursiRepo.denah(studio.id).associateBy { it.kode }
        dipilih.firstOrNull { it !in denah }?.let {
            throw ValidasiException("Kursi $it tidak ada di ${studio.ruang}.")
        }
        // cek awal (ramah pengguna); pengaman sebenarnya adalah UNIQUE(jadwal_id, kursi) di database
        val terisi = jadwalRepo.kursiTerisi(jadwal.id)
        dipilih.firstOrNull { it in terisi }?.let { throw KursiSudahDipesanException(it) }

        val hargaSatuan = jadwalService.hargaTiket(studio, jadwal.tanggal)   // polymorphism Studio
        val subtotal = hargaSatuan * dipilih.size
        val diskon = studio.hitungDiskon(dipilih.size, subtotal)             // >= 5 tiket: 6% / 8% / 10%

        val id = repo.simpanBaru(
            BookingBaru(
                penggunaId = penggunaId,
                jadwalId = jadwal.id,
                kursi = dipilih.map { denah.getValue(it) },
                subtotalTiket = subtotal,
                diskon = diskon
            )
        )
        return detail(penggunaId, id)
    }

    /** Detail booking milik pengguna (admin boleh melihat semua). */
    fun detail(penggunaId: Int, bookingId: Int, admin: Boolean = false): BookingDetail {
        val b = repo.cari(bookingId)
        if (b == null || (!admin && b.penggunaId != penggunaId)) throw DataTidakDitemukanException("Booking")
        val bonus = studioRepo.cariStudio(b.jadwal.studioId)?.bonus(b.kursi.size)
        return b.copy(bonus = bonus)
    }

    fun batal(penggunaId: Int, bookingId: Int): BookingDetail {
        val b = detail(penggunaId, bookingId)
        if (b.status != StatusBooking.PENDING.name)
            throw PembayaranGagalException("Hanya booking yang menunggu pembayaran yang bisa dibatalkan.")
        repo.batalkan(bookingId)
        return detail(penggunaId, bookingId)
    }

    fun bersihkanKedaluwarsa(): Int = repo.batalkanKedaluwarsa(BATAS_BAYAR_MENIT)

    companion object {
        const val BATAS_BAYAR_MENIT = 15
    }
}
