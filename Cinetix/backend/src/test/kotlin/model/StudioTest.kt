package model

import exception.ValidasiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudioTest {

    @Test
    fun `harga tiket berbeda per tipe studio (polymorphism)`() {
        val studios: List<Studio> = listOf(Reguler(1, 100), Premiere(2, 40), Suites(3, 24))
        assertEquals(listOf(50_000.0, 90_000.0, 150_000.0), studios.map { it.hargaTiket(akhirPekan = false) })
    }

    @Test
    fun `akhir pekan menambah 15 ribu`() {
        assertEquals(65_000.0, Reguler(1, 100).hargaTiket(akhirPekan = true))
        assertEquals(165_000.0, Suites(1, 24).hargaTiket(akhirPekan = true))
    }

    @Test
    fun `factory memakai harga dari database jika ada`() {
        val s = StudioFactory.buat(TipeStudio.PREMIERE, 9, 40, "Studio 9", 95_000.0)
        assertTrue(s is Premiere)
        assertEquals(95_000.0, s.hargaTiket(false))
        assertEquals("Studio 9", s.ruang)
    }

    @Test
    fun `denah Reguler A1 sampai J10 dengan section dan jalan tengah`() {
        val denah = Reguler(1, 100).buatDenah()
        assertEquals(100, denah.size)
        assertEquals("A1", denah.first().kode)
        assertEquals("J10", denah.last().kode)
        // baris A-E dekat layar, F-J jauh dari layar
        assertEquals(setOf("A", "B", "C", "D", "E"), denah.filter { it.section == "DEPAN" }.map { it.baris }.toSet())
        assertEquals(setOf("F", "G", "H", "I", "J"), denah.filter { it.section == "BELAKANG" }.map { it.baris }.toSet())
        // nomor 1-5 di kiri jalan tengah, 6-10 di kanan
        val barisA = denah.filter { it.baris == "A" }
        assertEquals((1..5).toList(), barisA.filter { it.sisi == "KIRI" }.map { it.nomor })
        assertEquals((6..10).toList(), barisA.filter { it.sisi == "KANAN" }.map { it.nomor })
    }

    @Test
    fun `denah Premiere 5 baris x 8 kursi`() {
        val denah = Premiere(1, 40).buatDenah()
        assertEquals(40, denah.size)
        assertEquals("E8", denah.last().kode)
        assertEquals(16, denah.count { it.section == "DEPAN" })   // A-B
        assertEquals(20, denah.count { it.sisi == "KIRI" })        // nomor 1-4
        assertTrue(Premiere(1, 40).kursiValid("E8"))
        assertFalse(Premiere(1, 40).kursiValid("A9"))
        assertFalse(Premiere(1, 40).kursiValid("F1"))
    }

    @Test
    fun `kapasitas harus kelipatan kursi per baris dan maksimal 10 baris`() {
        assertFailsWith<ValidasiException> { Reguler(1, 95).validasiDenah() }     // bukan kelipatan 10
        assertFailsWith<ValidasiException> { Suites(1, 66).validasiDenah() }      // 11 baris
        Suites(1, 60).validasiDenah()                                             // pas 10 baris x 6 kursi
    }

    @Test
    fun `atribut dan perilaku khas tiap studio`() {
        assertEquals(null, Premiere(1, 40).bonus(3))
        assertEquals(null, Reguler(1, 100).bonus(2))
        val suite = Suites(1, 24).apply { pesanLayananKursi("Selimut") }
        assertEquals(listOf("Selimut"), suite.daftarLayanan())
        assertEquals("Selimut & bantal untuk 2 kursi", suite.bonus(2))
    }

    @Test
    fun `tipe studio dibaca dari teks database`() {
        assertEquals(TipeStudio.PREMIERE, TipeStudio.dari("premiere"))
        assertEquals(TipeStudio.REGULER, TipeStudio.dari("REGULER"))
        assertFailsWith<ValidasiException> { TipeStudio.dari("4DX") }
    }
}
