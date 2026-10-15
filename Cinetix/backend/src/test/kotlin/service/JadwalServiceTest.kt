package service

import exception.HariTidakValidException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import repository.FilterJadwal

class JadwalServiceTest {
    private val f = TestFixture()
    private val service = f.jadwalService

    @Test
    fun `hanya tiga hari yang tersedia`() {
        assertEquals(listOf(f.hariIni, f.hariIni.plusDays(1), f.hariIni.plusDays(2)), service.tanggalTersedia())
    }

    @Test
    fun `hari ke-4 dan hari kemarin ditolak`() {
        assertFailsWith<HariTidakValidException> { service.cariJadwal(1, f.hariIni.plusDays(3)) }
        assertFailsWith<HariTidakValidException> { service.cariJadwal(1, f.hariIni.minusDays(1)) }
        service.cariJadwal(1, f.hariIni.plusDays(2))     // hari ke-3 masih boleh
    }

    @Test
    fun `jadwal yang jamnya sudah lewat tidak ditampilkan`() {
        val hasil = service.cariTersedia(FilterJadwal(f.hariIni))
        assertEquals(listOf(1), hasil.map { it.id })
    }

    @Test
    fun `harga akhir pekan lebih mahal`() {
        val jumat = service.cariTersedia(FilterJadwal(f.hariIni)).single()
        val sabtu = service.cariTersedia(FilterJadwal(f.hariIni.plusDays(1))).single()
        assertEquals(50_000.0, jumat.harga)          // Reguler, hari kerja
        assertEquals(105_000.0, sabtu.harga)         // Premiere, akhir pekan
        assertTrue(sabtu.akhirPekan)
        assertEquals(6.0, jumat.persenDiskon)
        assertEquals(8.0, sabtu.persenDiskon)
    }

    @Test
    fun `denah kursi dibagi section dekat-jauh layar dan kiri-kanan jalan tengah`() {
        f.db.kursi[1] = mutableSetOf("A1", "F7")
        val denah = service.denahKursi(1)
        assertEquals(10, denah.kursiPerBaris)
        assertEquals(listOf("DEPAN", "BELAKANG"), denah.section.map { it.kode })
        assertEquals(listOf("Dekat layar", "Jauh dari layar"), denah.section.map { it.label })
        assertEquals(listOf("A", "B", "C", "D", "E"), denah.section.first().baris.map { it.baris })
        val barisA = denah.section.first().baris.first()
        assertEquals(listOf("A1", "A2", "A3", "A4", "A5"), barisA.kiri.map { it.kode })
        assertEquals(listOf("A6", "A7", "A8", "A9", "A10"), barisA.kanan.map { it.kode })
        val terisi = denah.section.flatMap { it.baris }.flatMap { it.kiri + it.kanan }.filter { it.terisi }.map { it.kode }
        assertEquals(listOf("A1", "F7"), terisi)
        assertEquals(98, denah.jadwal.sisaKursi)
        assertEquals(5, denah.minTiketDiskon)
    }

    @Test
    fun `studio tanpa denah dibuatkan otomatis`() {
        f.db.denah.remove(2)
        assertEquals(1, service.pastikanDenahLengkap())
        assertEquals(40, f.kursiRepo.denah(2).size)
        assertEquals(0, service.pastikanDenahLengkap())
    }

    @Test
    fun `jadwal yang sudah lewat tidak bisa dipesan`() {
        assertFailsWith<HariTidakValidException> { service.denahKursi(2) }
    }

    @Test
    fun `generate otomatis mengisi studio yang belum punya jadwal`() {
        val dibuat = service.generateOtomatis()
        // Studio 1: hari +1 dan +2 (5 slot), Studio 2: hari ini dan +2 (4 slot)
        assertEquals(5 * 2 + 4 * 2, dibuat)
        assertEquals(0, service.generateOtomatis())   // dijalankan lagi: tidak ada duplikat
    }
}
