package service

import exception.DataTidakDitemukanException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PosterServiceTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 9)
    private val fx = TestFixture()
    private val storage = util.PosterStorage(Files.createTempDirectory("poster-svc"))
    private val service = PosterService(fx.filmRepo, storage)

    @Test
    fun `unggah menyimpan lokasi di film dan mengganti poster lama`() {
        val pertama = service.unggah(1, jpeg).posterUrl!!
        Thread.sleep(2)
        val kedua = service.unggah(1, jpeg).posterUrl!!
        assertNotEquals(pertama, kedua)
        assertEquals(kedua, fx.filmRepo.cari(1)?.posterUrl)
        assertNull(storage.baca(pertama.removePrefix(util.PosterStorage.URL_DASAR)))   // file lama terhapus
        assertNotNull(storage.baca(kedua.removePrefix(util.PosterStorage.URL_DASAR)))
    }

    @Test
    fun `film tanpa poster dan film tidak ada`() {
        assertNull(fx.filmRepo.cari(2)?.posterUrl)
        assertFailsWith<DataTidakDitemukanException> { service.unggah(99, jpeg) }
    }

    @Test
    fun `hapus film ikut menghapus file poster`() {
        val lokasi = service.unggah(2, jpeg).posterUrl!!
        service.hapusFilm(2)
        assertNull(storage.baca(lokasi.removePrefix(util.PosterStorage.URL_DASAR)))
    }
}
