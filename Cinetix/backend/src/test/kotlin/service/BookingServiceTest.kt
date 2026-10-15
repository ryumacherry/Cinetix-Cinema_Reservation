package service

import exception.DataTidakDitemukanException
import exception.KursiSudahDipesanException
import exception.ValidasiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BookingServiceTest {
    private val f = TestFixture()
    private val service = f.bookingService

    @Test
    fun `total booking = harga tiket x jumlah kursi, tanpa diskon di bawah 5 tiket`() {
        val b = service.buat(7, 1, listOf("a2", "A1", "A10"))
        assertEquals(listOf("A1", "A2", "A10"), b.kursi)          // dirapikan & diurutkan (A10 setelah A2)
        assertEquals(150_000.0, b.subtotalTiket)                  // 3 x 50.000 (Reguler, hari kerja)
        assertEquals(0.0, b.diskon)
        assertEquals(150_000.0, b.total)
        assertEquals("PENDING", b.status)
    }

    @Test
    fun `beli 5 tiket Reguler dapat diskon 6 persen`() {
        val b = service.buat(7, 1, listOf("C1", "C2", "C3", "C4", "C5"))
        assertEquals(250_000.0, b.subtotalTiket)
        assertEquals(15_000.0, b.diskon)
        assertEquals(235_000.0, b.total)
    }

    @Test
    fun `beli 5 tiket Premiere akhir pekan dapat diskon 8 persen`() {
        val b = service.buat(7, 3, listOf("A1", "A2", "A3", "A4", "A5"))
        assertEquals(525_000.0, b.subtotalTiket)                  // 5 x (90.000 + 15.000)
        assertEquals(42_000.0, b.diskon)                          // 8%
        assertEquals(483_000.0, b.total)
    }

    @Test
    fun `kursi yang sudah dipesan tidak bisa dipesan lagi`() {
        service.buat(7, 1, listOf("C5"))
        assertFailsWith<KursiSudahDipesanException> { service.buat(8, 1, listOf("C4", "C5")) }
        // C4 tidak ikut terkunci karena seluruh booking dibatalkan
        assertEquals(setOf("C5"), f.jadwalRepo.kursiTerisi(1))
    }

    @Test
    fun `kursi harus ada di denah studio`() {
        assertFailsWith<ValidasiException> { service.buat(7, 1, listOf("A11")) }    // Reguler hanya 10 per baris
        assertFailsWith<ValidasiException> { service.buat(7, 3, listOf("A9")) }     // Premiere hanya 8 per baris
        assertFailsWith<ValidasiException> { service.buat(7, 1, listOf("K1")) }     // tidak ada baris K
    }

    @Test
    fun `validasi jumlah kursi dan jadwal`() {
        assertFailsWith<ValidasiException> { service.buat(7, 1, emptyList()) }
        assertFailsWith<ValidasiException> { service.buat(7, 1, listOf("A1", "A2", "A3", "A4", "A5", "A6", "A7")) }
        assertFailsWith<ValidasiException> { service.buat(7, null, listOf("A1")) }
        assertFailsWith<DataTidakDitemukanException> { service.buat(7, 99, listOf("A1")) }
    }

    @Test
    fun `batal melepas kursi sehingga bisa dipesan lagi`() {
        val b = service.buat(7, 1, listOf("E1"))
        val batal = service.batal(7, b.id)
        assertEquals("BATAL", batal.status)
        assertTrue(f.jadwalRepo.kursiTerisi(1).isEmpty())
        assertEquals("PENDING", service.buat(8, 1, listOf("E1")).status)
    }

    @Test
    fun `pengguna lain tidak bisa melihat booking orang`() {
        val b = service.buat(7, 1, listOf("F1"))
        assertFailsWith<DataTidakDitemukanException> { service.detail(8, b.id) }
        assertEquals(b.id, service.detail(8, b.id, admin = true).id)
    }
}
