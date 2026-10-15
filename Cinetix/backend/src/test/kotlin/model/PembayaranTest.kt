package model

import exception.ValidasiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PembayaranTest {

    @Test
    fun `QRIS tanpa biaya admin dan payload memuat nominal`() {
        val q = Qris(100_000.0, "REF1")
        assertEquals(0.0, q.biayaAdmin())
        assertEquals(100_000.0, q.totalBayar())
        assertEquals("CINETIX-QRIS|NOMINAL=100000|REF=REF1", q.payload())
        assertTrue(q.butuhKonfirmasi)
    }

    @Test
    fun `transfer bank biaya admin 2500 dan nomor VA`() {
        val t = TransferBank(50_000.0, "BCA")
        assertEquals(52_500.0, t.totalBayar())
        assertEquals("3900100000000042", t.nomorVirtualAccount(42))
        assertTrue(t.proses())
        assertFalse(TransferBank(50_000.0, "BANKX").proses())
    }

    @Test
    fun `kartu kredit biaya 2 persen dan validasi Luhn`() {
        val valid = KartuKredit(100_000.0, "4111111111111111")
        assertEquals(2_000.0, valid.biayaAdmin())
        assertEquals(102_000.0, valid.totalBayar())
        assertTrue(valid.bayar())
        assertEquals(StatusBayar.BERHASIL, valid.status)

        val salah = KartuKredit(100_000.0, "1234567812345678")
        assertFalse(salah.bayar())
        assertEquals(StatusBayar.GAGAL, salah.status)
        assertFalse(KartuKredit(100_000.0, "411111111111111").proses())  // 15 digit
    }

    @Test
    fun `status awal menunggu sebelum dibayar`() {
        assertEquals(StatusBayar.MENUNGGU, Qris(10_000.0).status)
    }

    @Test
    fun `factory memilih subclass yang tepat`() {
        assertTrue(PembayaranFactory.buat("qris", 10_000.0) is Qris)
        assertTrue(PembayaranFactory.buat("BANK", 10_000.0, bank = "bni") is TransferBank)
        assertTrue(PembayaranFactory.buat("KREDIT", 10_000.0, noKartu = "4111 1111 1111 1111") is KartuKredit)
        assertFailsWith<ValidasiException> { PembayaranFactory.buat("OVO", 10_000.0) }
        assertFailsWith<ValidasiException> { PembayaranFactory.buat("BANK", 10_000.0) }
        assertFailsWith<ValidasiException> { PembayaranFactory.buat("QRIS", 0.0) }
    }
}
