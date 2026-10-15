package service

import exception.DataTidakDitemukanException
import model.Film
import repository.FilmRepository
import util.PosterStorage

/** Mengelola poster film: unggah (mengganti poster lama), hapus poster, dan hapus film beserta file posternya. */
class PosterService(
    private val filmRepo: FilmRepository,
    private val storage: PosterStorage
) {
    fun unggah(filmId: Int, data: ByteArray): Film {
        val film = filmRepo.cari(filmId) ?: throw DataTidakDitemukanException("Film")
        val lokasiBaru = storage.simpan(filmId, data)
        try {
            filmRepo.ubahPoster(filmId, lokasiBaru)
        } catch (e: Exception) {
            storage.hapus(lokasiBaru)           // database gagal -> jangan tinggalkan file yatim
            throw e
        }
        storage.hapus(film.posterUrl)           // file poster lama tidak dipakai lagi
        return film.copy(posterUrl = lokasiBaru)
    }

    fun hapusPoster(filmId: Int) {
        val film = filmRepo.cari(filmId) ?: throw DataTidakDitemukanException("Film")
        filmRepo.ubahPoster(filmId, null)
        storage.hapus(film.posterUrl)
    }

    /** Menghapus film; file poster ikut dihapus hanya jika film berhasil dihapus dari database. */
    fun hapusFilm(filmId: Int) {
        val film = filmRepo.cari(filmId) ?: throw DataTidakDitemukanException("Film")
        filmRepo.hapus(filmId)
        storage.hapus(film.posterUrl)
    }

    fun baca(nama: String): Pair<ByteArray, String> =
        storage.baca(nama) ?: throw DataTidakDitemukanException("Gambar")
}
