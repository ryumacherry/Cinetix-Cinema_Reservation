package util

import java.sql.Connection
import java.sql.DriverManager

/**
 * Koneksi MySQL lewat JDBC. Jangan hardcode localhost:
 * nilai dibaca dari environment variable, dengan default untuk jalan lokal.
 * Di Docker, host MySQL-nya adalah "db" (nama service), bukan localhost.
 */
object Database {
    private val url = System.getenv("DB_URL")
        ?: "jdbc:mysql://localhost:3306/cinetix?useSSL=false&allowPublicKeyRetrieval=true"
    private val user = System.getenv("DB_USER") ?: "root"
    private val pass = System.getenv("DB_PASS") ?: ""

    fun connect(): Connection = DriverManager.getConnection(url, user, pass)

    /** Menjalankan beberapa query sebagai satu transaksi: COMMIT jika sukses, ROLLBACK jika gagal. */
    fun <T> transaksi(blok: (Connection) -> T): T = connect().use { conn ->
        conn.autoCommit = false
        try {
            val hasil = blok(conn)
            conn.commit()
            hasil
        } catch (e: Exception) {
            conn.rollback()
            throw e
        }
    }

    /** Menunggu MySQL siap (berguna kalau app dijalankan bersamaan dengan container DB). */
    fun tungguSiap(percobaan: Int = 30, jedaMs: Long = 2_000) {
        repeat(percobaan) { ke ->
            try {
                connect().use { it.createStatement().execute("SELECT 1") }
                println("[OK] Terhubung ke database: $url")
                return
            } catch (e: Exception) {
                println("[..] menunggu database (${ke + 1}/$percobaan): ${e.message}")
                Thread.sleep(jedaMs)
            }
        }
        error("Database tidak bisa dihubungi di $url")
    }
}
