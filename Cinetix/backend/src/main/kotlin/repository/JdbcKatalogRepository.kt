package repository

import exception.DataTidakDitemukanException
import model.Cinema
import model.Film
import model.Kota
import model.Studio
import model.StudioData
import model.StudioFactory
import model.TipeStudio
import util.Database
import java.sql.ResultSet

class JdbcKotaRepository : KotaRepository {
    private fun map(rs: ResultSet) = Kota(rs.getInt("id"), rs.getString("nama"))

    override fun semua() = query("SELECT * FROM kota ORDER BY nama") { map(it) }
    override fun cari(id: Int) = query("SELECT * FROM kota WHERE id = ?", id) { map(it) }.firstOrNull()
    override fun tambah(item: Kota) = insert("INSERT INTO kota (nama) VALUES (?)", item.nama)
    override fun ubah(id: Int, item: Kota) { cekAda(update("UPDATE kota SET nama = ? WHERE id = ?", item.nama, id), "Kota") }
    override fun hapus(id: Int) { cekAda(update("DELETE FROM kota WHERE id = ?", id), "Kota") }
}

class JdbcCinemaRepository : CinemaRepository {
    private val sqlDasar = """
        SELECT c.id, c.nama, c.alamat, c.kota_id, k.nama AS kota_nama,
               GROUP_CONCAT(DISTINCT s.tipe ORDER BY FIELD(s.tipe,'REGULER','PREMIERE','SUITES')) AS tipe_studio
        FROM cinema c
        JOIN kota k ON k.id = c.kota_id
        LEFT JOIN studio s ON s.cinema_id = c.id
    """.trimIndent()
    private val groupBy = " GROUP BY c.id, c.nama, c.alamat, c.kota_id, k.nama ORDER BY k.nama, c.nama"

    private fun map(rs: ResultSet) = Cinema(
        id = rs.getInt("id"),
        nama = rs.getString("nama"),
        alamat = rs.getString("alamat"),
        kotaId = rs.getInt("kota_id"),
        kotaNama = rs.getString("kota_nama"),
        tipeStudio = rs.getString("tipe_studio")?.split(",")?.map { TipeStudio.dari(it).label } ?: emptyList()
    )

    override fun semua() = query(sqlDasar + groupBy) { map(it) }
    override fun byKota(kotaId: Int) = query("$sqlDasar WHERE c.kota_id = ?$groupBy", kotaId) { map(it) }
    override fun cari(id: Int) = query("$sqlDasar WHERE c.id = ?$groupBy", id) { map(it) }.firstOrNull()
    override fun tambah(item: Cinema) =
        insert("INSERT INTO cinema (nama, alamat, kota_id) VALUES (?, ?, ?)", item.nama, item.alamat, item.kotaId)
    override fun ubah(id: Int, item: Cinema) {
        cekAda(update("UPDATE cinema SET nama = ?, alamat = ?, kota_id = ? WHERE id = ?",
            item.nama, item.alamat, item.kotaId, id), "Cinema")
    }
    override fun hapus(id: Int) { cekAda(update("DELETE FROM cinema WHERE id = ?", id), "Cinema") }
}

class JdbcFilmRepository : FilmRepository {
    private fun map(rs: ResultSet) = Film(
        id = rs.getInt("id"),
        judul = rs.getString("judul"),
        genre = rs.getString("genre"),
        durasi = rs.getInt("durasi"),
        ratingUsia = rs.getString("rating_usia"),
        sinopsis = rs.getString("sinopsis") ?: "",
        status = rs.getString("status"),
        tanggalRilis = rs.tanggalAtauNull("tanggal_rilis"),
        posterUrl = rs.getString("poster_url")
    )

    override fun semua() = query("SELECT * FROM film ORDER BY status, judul") { map(it) }
    override fun byStatus(status: String) =
        query("SELECT * FROM film WHERE status = ? ORDER BY COALESCE(tanggal_rilis, '2000-01-01'), judul", status) { map(it) }
    override fun cari(id: Int) = query("SELECT * FROM film WHERE id = ?", id) { map(it) }.firstOrNull()
    override fun tambah(item: Film) = insert(
        "INSERT INTO film (judul, genre, durasi, rating_usia, sinopsis, status, tanggal_rilis) VALUES (?, ?, ?, ?, ?, ?, ?)",
        item.judul, item.genre, item.durasi, item.ratingUsia, item.sinopsis, item.status, item.tanggalRilis
    )
    override fun ubah(id: Int, item: Film) {
        cekAda(update(
            "UPDATE film SET judul = ?, genre = ?, durasi = ?, rating_usia = ?, sinopsis = ?, status = ?, tanggal_rilis = ? WHERE id = ?",
            item.judul, item.genre, item.durasi, item.ratingUsia, item.sinopsis, item.status, item.tanggalRilis, id
        ), "Film")
    }
    override fun ubahPoster(id: Int, posterUrl: String?) {
        cekAda(update("UPDATE film SET poster_url = ? WHERE id = ?", posterUrl, id), "Film")
    }
    override fun hapus(id: Int) { cekAda(update("DELETE FROM film WHERE id = ?", id), "Film") }
}

class JdbcStudioRepository : StudioRepository {
    private val sqlDasar = """
        SELECT s.id, s.cinema_id, s.nama, s.tipe, s.kapasitas, c.nama AS cinema_nama, h.harga_dasar
        FROM studio s
        JOIN cinema c ON c.id = s.cinema_id
        LEFT JOIN harga_studio h ON h.tipe = s.tipe
    """.trimIndent()
    private val urut = " ORDER BY c.nama, s.nama"

    private fun map(rs: ResultSet) = StudioData(
        id = rs.getInt("id"),
        cinemaId = rs.getInt("cinema_id"),
        nama = rs.getString("nama"),
        tipe = rs.getString("tipe"),
        kapasitas = rs.getInt("kapasitas"),
        cinemaNama = rs.getString("cinema_nama")
    )

    override fun semua() = query(sqlDasar + urut) { map(it) }
    override fun byCinema(cinemaId: Int) = query("$sqlDasar WHERE s.cinema_id = ?$urut", cinemaId) { map(it) }
    override fun cari(id: Int) = query("$sqlDasar WHERE s.id = ?", id) { map(it) }.firstOrNull()

    override fun cariStudio(id: Int): Studio? = query("$sqlDasar WHERE s.id = ?", id) { rs ->
        val harga = rs.getDouble("harga_dasar").takeIf { !rs.wasNull() }
        StudioFactory.buat(TipeStudio.dari(rs.getString("tipe")), rs.getInt("id"), rs.getInt("kapasitas"),
            rs.getString("nama"), harga)
    }.firstOrNull()

    /** Studio baru langsung dibuatkan denah kursinya (tabel `kursi`) dalam satu transaksi. */
    override fun tambah(item: StudioData): Int = Database.transaksi { conn ->
        val id = conn.insert(
            "INSERT INTO studio (cinema_id, nama, tipe, kapasitas) VALUES (?, ?, ?, ?)",
            item.cinemaId, item.nama, item.tipe, item.kapasitas
        )
        JdbcKursiRepository.simpanDenahDalam(conn, id, denahUntuk(id, item))
        id
    }

    /** Jika tipe/kapasitas berubah, denah dibuat ulang (ditolak jika kursinya sudah pernah dipesan). */
    override fun ubah(id: Int, item: StudioData) {
        Database.transaksi { conn ->
            val lama = conn.query("SELECT tipe, kapasitas FROM studio WHERE id = ?", id) {
                it.getString("tipe") to it.getInt("kapasitas")
            }.firstOrNull() ?: throw DataTidakDitemukanException("Studio")
            conn.update(
                "UPDATE studio SET cinema_id = ?, nama = ?, tipe = ?, kapasitas = ? WHERE id = ?",
                item.cinemaId, item.nama, item.tipe, item.kapasitas, id
            )
            if (lama.first != item.tipe || lama.second != item.kapasitas) {
                JdbcKursiRepository.simpanDenahDalam(conn, id, denahUntuk(id, item))
            }
        }
    }

    private fun denahUntuk(id: Int, item: StudioData) =
        StudioFactory.buat(TipeStudio.dari(item.tipe), id, item.kapasitas, item.nama).buatDenah()

    override fun hapus(id: Int) { cekAda(update("DELETE FROM studio WHERE id = ?", id), "Studio") }
}

class JdbcHargaStudioRepository : HargaStudioRepository {
    override fun semua(): Map<TipeStudio, Double> =
        query("SELECT tipe, harga_dasar FROM harga_studio") { TipeStudio.dari(it.getString("tipe")) to it.getDouble("harga_dasar") }
            .toMap()

    override fun ubah(tipe: TipeStudio, harga: Double) {
        // INSERT ... ON DUPLICATE KEY UPDATE: tambah kalau belum ada, ubah kalau sudah ada
        update("INSERT INTO harga_studio (tipe, harga_dasar) VALUES (?, ?) ON DUPLICATE KEY UPDATE harga_dasar = VALUES(harga_dasar)",
            tipe.kodeDb, harga)
    }
}

internal fun cekAda(barisTerpengaruh: Int, apa: String) {
    if (barisTerpengaruh == 0) throw DataTidakDitemukanException(apa)
}
