package service

import exception.PembayaranGagalException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PembayaranServiceTest {
    private val f = TestFixture()
    private val bayar = f.pembayaranService

    /** Default 2 kursi Reguler hari kerja = 2 x 50.000 = 100.000 */
    private fun bookingBaru(vararg kursi: String) =
        f.bookingService.buat(7, 1, if (kursi.isEmpty()) listOf("A1", "A2") else kursi.toList())

    @Test
    fun `QRIS menunggu konfirmasi lalu LUNAS`() {
        val b = bookingBaru()
        val instruksi = bayar.mulai(7, b.id, "QRIS", null, null)
        assertEquals("MENUNGGU", instruksi.status)
        assertTrue(instruksi.qrisPayload!!.startsWith("CINETIX-QRIS|NOMINAL=100000"))

        val lunas = bayar.konfirmasi(7, b.id)
        assertEquals("LUNAS", lunas.status)
        assertTrue(lunas.kodeTiket!!.startsWith("CTX-261016-"))
        assertNotNull(lunas.teksTiket)
        assertTrue(lunas.teksTiket!!.contains(lunas.kodeTiket!!))
    }

    @Test
    fun `nominal QR sudah dipotong diskon 5 tiket`() {
        val b = bookingBaru("B1", "B2", "B3", "B4", "B5")          // 250.000 - 6% = 235.000
        val instruksi = bayar.mulai(7, b.id, "QRIS", null, null)
        assertEquals(250_000.0, instruksi.subtotal)
        assertEquals(15_000.0, instruksi.diskon)
        assertEquals(235_000.0, instruksi.totalBayar)
        assertTrue(instruksi.qrisPayload!!.startsWith("CINETIX-QRIS|NOMINAL=235000"))
        assertTrue(bayar.konfirmasi(7, b.id).teksTiket!!.contains("Diskon"))
    }

    @Test
    fun `transfer bank menambah biaya admin`() {
        val b = bookingBaru()
        val instruksi = bayar.mulai(7, b.id, "BANK", "BRI", null)
        assertEquals(102_500.0, instruksi.totalBayar)
        assertTrue(instruksi.nomorVa!!.startsWith("26215"))
    }

    @Test
    fun `kartu kredit tidak valid ditolak dan booking tetap menunggu`() {
        val b = bookingBaru()
        assertFailsWith<PembayaranGagalException> { bayar.mulai(7, b.id, "KREDIT", null, "1234567812345678") }
        assertEquals("PENDING", f.bookingRepo.cari(b.id)!!.status)
    }

    @Test
    fun `kartu kredit valid langsung LUNAS`() {
        val b = bookingBaru()
        val hasil = bayar.mulai(7, b.id, "KREDIT", null, "4111111111111111")
        assertEquals("LUNAS", hasil.status)
        assertEquals(102_000.0, hasil.totalBayar)                    // + 2% biaya admin
        assertNotNull(hasil.booking?.kodeTiket)
    }

    @Test
    fun `konfirmasi gagal jika belum memilih metode atau waktu habis`() {
        val b1 = bookingBaru("A1")
        assertFailsWith<PembayaranGagalException> { bayar.konfirmasi(7, b1.id) }

        val b2 = bookingBaru("A2")
        bayar.mulai(7, b2.id, "QRIS", null, null)
        f.db.kedaluwarsa += b2.id                                   // anggap sudah lewat 15 menit
        assertFailsWith<PembayaranGagalException> { bayar.konfirmasi(7, b2.id) }
        assertEquals("BATAL", f.bookingRepo.cari(b2.id)!!.status)
    }

    @Test
    fun `booking yang sudah lunas tidak bisa dibayar lagi`() {
        val b = bookingBaru()
        bayar.mulai(7, b.id, "QRIS", null, null)
        bayar.konfirmasi(7, b.id)
        assertFailsWith<PembayaranGagalException> { bayar.mulai(7, b.id, "QRIS", null, null) }
    }
}
