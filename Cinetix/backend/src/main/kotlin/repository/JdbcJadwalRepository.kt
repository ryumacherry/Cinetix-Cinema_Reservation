package repository

import model.Jadwal
import model.TipeStudio
import java.sql.ResultSet
import java.time.LocalDate

class JdbcJadwalRepository : JadwalRepository {
    private val sqlDasar = """
        SELECT j.id, j.film_id, j.studio_id, j.tanggal, j.jam,
               f.judul, f.rating_usia, s.nama AS studio_nama, s.tipe, s.kapasitas,
               c.id AS cinema_id, c.nama AS cinema_nama,
               (SELECT COUNT(*) FROM kursi_dipesan kd WHERE kd.jadwal_id = j.id) AS terisi
        FROM jadwal j
        JOIN film f   ON f.id = j.film_id
        JOIN studio s ON s.id = j.studio_id
        JOIN cinema c ON c.id = s.cinema_id
    """.trimIndent()
    private val urut = " ORDER BY c.nama, FIELD(s.tipe,'REGULER','PREMIERE','SUITES'), s.nama, j.jam"

    private fun map(rs: ResultSet) = Jadwal(
        id = rs.getInt("id"),
        filmId = rs.getInt("film_id"),
        studioId = rs.getInt("studio_id"),
        tanggal = rs.tanggal("tanggal"),
        jam = rs.jam("jam"),
        filmJudul = rs.getString("judul"),
        filmRating = rs.getString("rating_usia"),
        studioNama = rs.getString("studio_nama"),
        studioTipe = TipeStudio.dari(rs.getString("tipe")).label,
        kapasitas = rs.getInt("kapasitas"),
        cinemaId = rs.getInt("cinema_id"),
        cinemaNama = rs.getString("cinema_nama"),
        terisi = rs.getInt("terisi")
    )

    override fun cariJadwal(filter: FilterJadwal): List<Jadwal> {
        val kondisi = mutableListOf("j.tanggal = ?")
        val params = mutableListOf<Any?>(filter.tanggal)
        filter.filmId?.let { kondisi += "j.film_id = ?"; params += it }
        filter.cinemaId?.let { kondisi += "c.id = ?"; params += it }
        filter.kotaId?.let { kondisi += "c.kota_id = ?"; params += it }
        return query("$sqlDasar WHERE ${kondisi.joinToString(" AND ")}$urut", *params.toTypedArray()) { map(it) }
    }

    override fun mulaiTanggal(tanggal: LocalDate) =
        query("$sqlDasar WHERE j.tanggal >= ? ORDER BY j.tanggal, c.nama, s.nama, j.jam LIMIT 500", tanggal) { map(it) }

    override fun adaUntukStudioTanggal(studioId: Int, tanggal: LocalDate) =
        query("SELECT COUNT(*) AS n FROM jadwal WHERE studio_id = ? AND tanggal = ?", studioId, tanggal) { it.getInt("n") }
            .first() > 0

    override fun kursiTerisi(jadwalId: Int): Set<String> =
        query(
            "SELECT k.kode FROM kursi_dipesan kd JOIN kursi k ON k.id = kd.kursi_id WHERE kd.jadwal_id = ?", jadwalId
        ) { it.getString("kode") }.toSet()

    override fun semua() = mulaiTanggal(LocalDate.of(2000, 1, 1))
    override fun cari(id: Int) = query("$sqlDasar WHERE j.id = ?", id) { map(it) }.firstOrNull()
    override fun tambah(item: Jadwal) = insert(
        "INSERT INTO jadwal (film_id, studio_id, tanggal, jam) VALUES (?, ?, ?, ?)",
        item.filmId, item.studioId, item.tanggal, item.jam
    )
    override fun ubah(id: Int, item: Jadwal) {
        cekAda(update("UPDATE jadwal SET film_id = ?, studio_id = ?, tanggal = ?, jam = ? WHERE id = ?",
            item.filmId, item.studioId, item.tanggal, item.jam, id), "Jadwal")
    }
    override fun hapus(id: Int) { cekAda(update("DELETE FROM jadwal WHERE id = ?", id), "Jadwal") }
}
