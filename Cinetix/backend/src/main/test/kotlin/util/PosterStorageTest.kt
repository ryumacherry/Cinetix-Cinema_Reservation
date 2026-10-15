package util

import exception.ValidasiException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PosterStorageTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    private val webp = "RIFF....WEBP".toByteArray(Charsets.US_ASCII)

    private fun storage() = PosterStorage(Files.createTempDirectory("poster-test"))

    @Test
    fun `jenis gambar dikenali dari isi file`() {
        val s = storage()
        assertEquals("jpg", s.deteksiEkstensi(jpeg))
        assertEquals("png", s.deteksiEkstensi(png))
        assertEquals("webp", s.deteksiEkstensi(webp))
        assertNull(s.deteksiEkstensi("<html>bukan gambar</html>".toByteArray()))
    }

    @Test
    fun `simpan mengembalikan lokasi lokal dan file bisa dibaca kembali`() {
        val s = storage()
        val lokasi = s.simpan(7, jpeg)
        assertTrue(lokasi.startsWith("/uploads/posters/film-7-") && lokasi.endsWith(".jpg"))
        val (isi, tipe) = s.baca(lokasi.removePrefix(PosterStorage.URL_DASAR))!!
        assertEquals("image/jpeg", tipe)
        assertTrue(isi.contentEquals(jpeg))
    }

    @Test
    fun `file bukan gambar, kosong, atau terlalu besar ditolak`() {
        val s = storage()
        assertFailsWith<ValidasiException> { s.simpan(1, "teks biasa".toByteArray()) }
        assertFailsWith<ValidasiException> { s.simpan(1, ByteArray(0)) }
        assertFailsWith<ValidasiException> { s.simpan(1, jpeg + ByteArray(PosterStorage.MAKS_BYTE)) }
    }

    @Test
    fun `hapus menghapus file dan nama berbahaya tidak bisa dibaca`() {
        val s = storage()
        val lokasi = s.simpan(2, png)
        s.hapus(lokasi)
        assertNull(s.baca(lokasi.removePrefix(PosterStorage.URL_DASAR)))
        assertNull(s.baca("../../etc/passwd"))
        assertNull(s.baca("..%2Fsecret.jpg"))
        s.hapus(null)                       // tidak error
        s.hapus("https://situs.lain/x.jpg")  // bukan lokasi lokal -> diabaikan
    }
}
