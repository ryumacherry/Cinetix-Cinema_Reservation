package service

import exception.DataTidakDitemukanException
import model.Cinema
import model.Film
import model.Kota
import model.StatusFilm
import repository.CinemaRepository
import repository.FilmRepository
import repository.KotaRepository

/** Lihat kota, cinema per kota, film sedang tayang & coming soon. */
class KatalogService(
    private val kotaRepo: KotaRepository,
    private val cinemaRepo: CinemaRepository,
    private val filmRepo: FilmRepository
) {
    fun daftarKota(): List<Kota> = kotaRepo.semua()

    fun daftarCinema(kotaId: Int?): List<Cinema> =
        if (kotaId == null) cinemaRepo.semua() else cinemaRepo.byKota(kotaId)

    fun detailCinema(id: Int): Cinema = cinemaRepo.cari(id) ?: throw DataTidakDitemukanException("Cinema")

    fun daftarFilm(status: String?): List<Film> =
        if (status.isNullOrBlank()) filmRepo.semua() else filmRepo.byStatus(StatusFilm.dari(status).name)

    fun detailFilm(id: Int): Film = filmRepo.cari(id) ?: throw DataTidakDitemukanException("Film")
}
