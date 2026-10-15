package repository

import model.KursiDenah
import util.Database
import java.sql.Connection
import java.sql.ResultSet

/** Akses tabel `kursi` (denah studio). */
class JdbcKursiRepository : KursiRepository {

    private fun map(rs: ResultSet) = KursiDenah(
        id = rs.getInt("id"),
        studioId = rs.getInt("studio_id"),
        kode = rs.getString("kode"),
        baris = rs.getString("baris"),
        nomor = rs.getInt("nomor"),
        section = rs.getString("section"),
        sisi = rs.getString("sisi")
    )

    override fun denah(studioId: Int) =
        query("SELECT * FROM kursi WHERE studio_id = ? ORDER BY baris, nomor", studioId) { map(it) }

    override fun simpanDenah(studioId: Int, denah: List<KursiDenah>) {
        Database.transaksi { conn -> simpanDenahDalam(conn, studioId, denah) }
    }

    override fun studioTanpaDenah() = query(
        "SELECT s.id FROM studio s LEFT JOIN kursi k ON k.studio_id = s.id GROUP BY s.id HAVING COUNT(k.id) = 0"
    ) { it.getInt("id") }

    companion object {
        /**
         * Hapus denah lama lalu simpan denah baru, di dalam transaksi milik pemanggil.
         * Jika ada kursi yang sudah pernah dipesan, MySQL menolak penghapusan (error 1451 -> HTTP 409).
         */
        internal fun simpanDenahDalam(conn: Connection, studioId: Int, denah: List<KursiDenah>) {
            conn.update("DELETE FROM kursi WHERE studio_id = ?", studioId)
            conn.prepareStatement(
                "INSERT INTO kursi (studio_id, kode, baris, nomor, section, sisi) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                denah.forEach { k ->
                    ps.setInt(1, studioId)
                    ps.setString(2, k.kode)
                    ps.setString(3, k.baris)
                    ps.setInt(4, k.nomor)
                    ps.setString(5, k.section)
                    ps.setString(6, k.sisi)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }
}
