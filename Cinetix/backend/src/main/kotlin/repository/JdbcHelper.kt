package repository

import util.Database
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Fungsi bantu JDBC supaya kode repository pendek dan selalu memakai PreparedStatement
 * (aman dari SQL injection).
 */

internal fun PreparedStatement.isiParameter(params: Array<out Any?>) {
    params.forEachIndexed { i, p ->
        val idx = i + 1
        when (p) {
            null -> setNull(idx, Types.NULL)
            is Boolean -> setBoolean(idx, p)
            is Int -> setInt(idx, p)
            is Double -> setDouble(idx, p)
            is String -> setString(idx, p)
            else -> setObject(idx, p)   // LocalDate, LocalTime, LocalDateTime
        }
    }
}

internal fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { ps ->
        ps.isiParameter(params)
        ps.executeQuery().use { rs ->
            val hasil = mutableListOf<T>()
            while (rs.next()) hasil += map(rs)
            hasil
        }
    }

internal fun Connection.update(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { ps -> ps.isiParameter(params); ps.executeUpdate() }

internal fun Connection.insert(sql: String, vararg params: Any?): Int =
    prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
        ps.isiParameter(params)
        ps.executeUpdate()
        ps.generatedKeys.use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

// Versi yang membuka koneksi sendiri (untuk query tunggal di luar transaksi)
internal fun <T> query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    Database.connect().use { it.query(sql, *params, map = map) }

internal fun update(sql: String, vararg params: Any?): Int =
    Database.connect().use { it.update(sql, *params) }

internal fun insert(sql: String, vararg params: Any?): Int =
    Database.connect().use { it.insert(sql, *params) }

internal fun ResultSet.tanggal(kolom: String): LocalDate = getObject(kolom, LocalDate::class.java)
internal fun ResultSet.tanggalAtauNull(kolom: String): LocalDate? = getObject(kolom, LocalDate::class.java)
internal fun ResultSet.jam(kolom: String): LocalTime = getObject(kolom, LocalTime::class.java)
internal fun ResultSet.waktu(kolom: String): LocalDateTime = getObject(kolom, LocalDateTime::class.java)
internal fun ResultSet.waktuAtauNull(kolom: String): LocalDateTime? = getObject(kolom, LocalDateTime::class.java)

/** Kode error MySQL untuk UNIQUE yang dilanggar. */
internal const val ERROR_DUPLIKAT = 1062
