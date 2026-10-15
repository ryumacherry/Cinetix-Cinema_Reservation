package util

import exception.ValidasiException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Penyimpanan poster film di DISK SERVER (bukan URL internet).
 *
 *  - Folder  : $UPLOAD_DIR/posters (default ./uploads/posters, di Docker di-mount ke host).
 *  - Nama file dibuat server sendiri: film-{id}-{waktu}.{jpg|png|webp}, jadi tidak bergantung pada
 *    berapa banyak film yang ditambah admin dan tidak bisa bentrok / menimpa file film lain.
 *  - Yang disimpan di kolom film.poster_url hanya LOKASI relatif, contoh "/uploads/posters/film-3-17600.jpg".
 *    File disajikan oleh route statis GET /uploads/posters/{nama}.
 *  - Jenis file ditentukan dari isi file (magic bytes), bukan dari nama/header yang dikirim klien.
 */
class PosterStorage(val folder: Path = bawaan()) {

    init { Files.createDirectories(folder) }

    /** Mengecek isi file lalu mengembalikan ekstensi (jpg/png/webp), atau null jika bukan gambar yang didukung. */
    fun deteksiEkstensi(data: ByteArray): String? = when {
        data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte() -> "jpg"
        data.size >= 8 && data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte() &&
            data[2] == 'N'.code.toByte() && data[3] == 'G'.code.toByte() -> "png"
        data.size >= 12 && String(data, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(data, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> null
    }

    /** Menyimpan poster film [filmId] dan mengembalikan lokasinya (untuk kolom poster_url). */
    fun simpan(filmId: Int, data: ByteArray): String {
        if (data.isEmpty()) throw ValidasiException("File poster kosong.")
        if (data.size > MAKS_BYTE) throw ValidasiException("Ukuran poster maksimal ${MAKS_BYTE / 1024 / 1024} MB.")
        val ekstensi = deteksiEkstensi(data) ?: throw ValidasiException("Poster harus berupa gambar JPG, PNG, atau WEBP.")
        val nama = "film-$filmId-${System.currentTimeMillis()}.$ekstensi"
        Files.write(folder.resolve(nama), data)
        return URL_DASAR + nama
    }

    /** Menghapus file dari lokasi yang tersimpan di database. Aman dipanggil dengan null / lokasi tak dikenal. */
    fun hapus(posterUrl: String?) {
        val nama = posterUrl?.takeIf { it.startsWith(URL_DASAR) }?.removePrefix(URL_DASAR) ?: return
        pathAman(nama)?.let { Files.deleteIfExists(it) }
    }

    /** Membaca file untuk disajikan. Mengembalikan (isi, content-type) atau null jika tidak ada / nama tidak valid. */
    fun baca(nama: String): Pair<ByteArray, String>? {
        val path = pathAman(nama)?.takeIf { Files.isRegularFile(it) } ?: return null
        val tipe = when (nama.substringAfterLast('.').lowercase()) {
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "image/webp"
        }
        return Files.readAllBytes(path) to tipe
    }

    /** Menolak nama aneh (../, subfolder, ekstensi lain) supaya tidak bisa membaca/menghapus file di luar folder poster. */
    private fun pathAman(nama: String): Path? {
        if (!NAMA_AMAN.matches(nama)) return null
        val path = folder.resolve(nama).normalize()
        return path.takeIf { it.startsWith(folder) }
    }

    companion object {
        const val URL_DASAR = "/uploads/posters/"
        const val MAKS_BYTE = 3 * 1024 * 1024
        private val NAMA_AMAN = Regex("^[A-Za-z0-9._-]+\\.(jpg|png|webp)$")

        private fun bawaan(): Path =
            Paths.get(System.getenv("UPLOAD_DIR") ?: "uploads", "posters").toAbsolutePath().normalize()
    }
}
