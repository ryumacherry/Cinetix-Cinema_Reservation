package repository

import model.Pengguna
import model.PenggunaFactory
import java.sql.ResultSet

class JdbcPenggunaRepository : PenggunaRepository {
    private fun map(rs: ResultSet): Pengguna = PenggunaFactory.buat(
        rs.getInt("id"), rs.getString("nama"), rs.getString("email"), rs.getString("peran")
    )

    override fun cariByEmail(email: String) =
        query("SELECT * FROM pengguna WHERE email = ?", email) { AkunTersimpan(map(it), it.getString("password_hash")) }
            .firstOrNull()

    override fun cariById(id: Int) = query("SELECT * FROM pengguna WHERE id = ?", id) { map(it) }.firstOrNull()

    override fun tambah(nama: String, email: String, passwordHash: String, peran: String) =
        insert("INSERT INTO pengguna (nama, email, password_hash, peran) VALUES (?, ?, ?, ?)", nama, email, passwordHash, peran)

    override fun simpanSesi(token: String, penggunaId: Int) {
        update("INSERT INTO sesi (token, pengguna_id) VALUES (?, ?)", token, penggunaId)
    }

    override fun penggunaDariSesi(token: String) = query(
        "SELECT p.* FROM sesi s JOIN pengguna p ON p.id = s.pengguna_id WHERE s.token = ?", token
    ) { map(it) }.firstOrNull()

    override fun hapusSesi(token: String) {
        update("DELETE FROM sesi WHERE token = ?", token)
    }
}
