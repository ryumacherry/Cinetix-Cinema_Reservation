package repository

import exception.KursiSudahDipesanException
import exception.PembayaranGagalException
import model.BookingBaru
import model.BookingDetail
import model.Jadwal
import model.Laporan
import model.PeringkatFilm
import model.RingkasanMetode
import model.TipeStudio
import util.Database
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

class JdbcBookingRepository(private val batasBayarMenit: Int = 15) : BookingRepository {

    private val sqlDetail = """
        SELECT b.*, p.nama AS nama_pengguna,
               j.film_id, j.studio_id, j.tanggal, j.jam,
               f.judul, f.rating_usia, s.nama AS studio_nama, s.tipe, s.kapasitas,
               c.id AS cinema_id, c.nama AS cinema_nama,
               GREATEST(0, TIMESTAMPDIFF(SECOND, NOW(), b.dibuat + INTERVAL $batasBayarMenit MINUTE)) AS sisa_detik
        FROM booking b
        JOIN pengguna p ON p.id = b.pengguna_id
        JOIN jadwal j   ON j.id = b.jadwal_id
        JOIN film f     ON f.id = j.film_id
        JOIN studio s   ON s.id = j.studio_id
        JOIN cinema c   ON c.id = s.cinema_id
    """.trimIndent()

    // ------------------------------------------------------------------
    // Booking baru: booking + semua kursi harus sukses BERSAMAAN (transaksi)
    // ------------------------------------------------------------------
    override fun simpanBaru(data: BookingBaru): Int = Database.transaksi { conn ->
        val id = conn.insert(
            """INSERT INTO booking (pengguna_id, jadwal_id, kursi, jumlah_tiket, subtotal_tiket, diskon,
                                    biaya_admin, total, status)
               VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'PENDING')""",
            data.penggunaId, data.jadwalId, data.kursi.joinToString(",") { it.kode }, data.kursi.size,
            data.subtotalTiket, data.diskon, data.subtotalTiket - data.diskon
        )

        // UNIQUE(jadwal_id, kursi_id) adalah kunci pencegahan double booking
        for (k in data.kursi) {
            try {
                conn.insert("INSERT INTO kursi_dipesan (booking_id, jadwal_id, kursi_id) VALUES (?, ?, ?)", id, data.jadwalId, k.id)
            } catch (e: SQLException) {
                if (e.errorCode == ERROR_DUPLIKAT) throw KursiSudahDipesanException(k.kode)
                throw e
            }
        }
        id
    }

    override fun cari(id: Int): BookingDetail? = Database.connect().use { conn ->
        conn.query("$sqlDetail WHERE b.id = ?", id) { mapDetail(it) }.firstOrNull()
    }

    override fun riwayat(penggunaId: Int): List<BookingDetail> = Database.connect().use { conn ->
        conn.query("$sqlDetail WHERE b.pengguna_id = ? ORDER BY b.dibuat DESC, b.id DESC LIMIT 50", penggunaId) { mapDetail(it) }
    }

    override fun setPembayaran(id: Int, kodeBayar: String, metode: String, biayaAdmin: Double, total: Double) {
        val n = update(
            "UPDATE booking SET kode_bayar = ?, metode_bayar = ?, biaya_admin = ?, total = ? WHERE id = ? AND status = 'PENDING'",
            kodeBayar, metode, biayaAdmin, total, id
        )
        if (n == 0) throw PembayaranGagalException("Booking sudah tidak menunggu pembayaran.")
    }

    override fun tandaiLunas(id: Int, kodeTiket: String) {
        val n = update(
            "UPDATE booking SET status = 'LUNAS', kode_tiket = ?, dibayar = NOW() WHERE id = ? AND status = 'PENDING'",
            kodeTiket, id
        )
        if (n == 0) throw PembayaranGagalException("Booking sudah tidak menunggu pembayaran.")
    }

    override fun batalkan(id: Int) {
        Database.transaksi { conn -> batalkanDalam(conn, id) }
    }

    private fun batalkanDalam(conn: Connection, id: Int) {
        val status = conn.query("SELECT status FROM booking WHERE id = ? FOR UPDATE", id) { it.getString("status") }.firstOrNull()
        if (status != "PENDING") return
        // lepas kursi (baris UNIQUE dihapus sehingga kursi bisa dipesan lagi), lalu ubah status
        conn.update("DELETE FROM kursi_dipesan WHERE booking_id = ?", id)
        conn.update("UPDATE booking SET status = 'BATAL' WHERE id = ?", id)
    }

    override fun batalkanKedaluwarsa(menit: Int): Int {
        val ids = query(
            "SELECT id FROM booking WHERE status = 'PENDING' AND dibuat < NOW() - INTERVAL $menit MINUTE"
        ) { it.getInt("id") }
        ids.forEach { batalkan(it) }
        return ids.size
    }

    override fun laporan(): Laporan = Database.connect().use { conn ->
        data class Ringkas(val total: Double, val tiket: Double, val diskon: Double, val trx: Int, val jmlTiket: Int)

        val r = conn.query(
            """SELECT COALESCE(SUM(total),0) t, COALESCE(SUM(subtotal_tiket),0) st,
                      COALESCE(SUM(diskon),0) ds, COUNT(*) n, COALESCE(SUM(jumlah_tiket),0) jt
               FROM booking WHERE status = 'LUNAS'"""
        ) { Ringkas(it.getDouble("t"), it.getDouble("st"), it.getDouble("ds"), it.getInt("n"), it.getInt("jt")) }.first()

        val filmTerlaris = conn.query(
            """SELECT f.judul, SUM(b.jumlah_tiket) tiket, SUM(b.subtotal_tiket) pendapatan
               FROM booking b JOIN jadwal j ON j.id = b.jadwal_id JOIN film f ON f.id = j.film_id
               WHERE b.status = 'LUNAS'
               GROUP BY f.id, f.judul ORDER BY tiket DESC, pendapatan DESC LIMIT 5"""
        ) { PeringkatFilm(it.getString("judul"), it.getInt("tiket"), it.getDouble("pendapatan")) }

        val perMetode = conn.query(
            """SELECT metode_bayar, COUNT(*) n, SUM(total) t FROM booking
               WHERE status = 'LUNAS' GROUP BY metode_bayar ORDER BY t DESC"""
        ) { RingkasanMetode(it.getString("metode_bayar") ?: "-", it.getInt("n"), it.getDouble("t")) }

        Laporan(
            totalPendapatan = r.total, pendapatanTiket = r.tiket, totalDiskon = r.diskon,
            jumlahTransaksi = r.trx, tiketTerjual = r.jmlTiket,
            filmTerlaris = filmTerlaris, perMetode = perMetode
        )
    }

    // ------------------------------------------------------------------
    private fun mapDetail(rs: ResultSet) = BookingDetail(
        id = rs.getInt("id"),
        penggunaId = rs.getInt("pengguna_id"),
        namaPengguna = rs.getString("nama_pengguna"),
        jadwal = Jadwal(
            id = rs.getInt("jadwal_id"),
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
            cinemaNama = rs.getString("cinema_nama")
        ),
        kursi = rs.getString("kursi").split(",").filter { it.isNotBlank() },
        subtotalTiket = rs.getDouble("subtotal_tiket"),
        diskon = rs.getDouble("diskon"),
        biayaAdmin = rs.getDouble("biaya_admin"),
        total = rs.getDouble("total"),
        metodeBayar = rs.getString("metode_bayar"),
        kodeBayar = rs.getString("kode_bayar"),
        status = rs.getString("status"),
        kodeTiket = rs.getString("kode_tiket"),
        dibuat = rs.waktu("dibuat"),
        dibayar = rs.waktuAtauNull("dibayar"),
        sisaDetikBayar = rs.getLong("sisa_detik")
    )
}
