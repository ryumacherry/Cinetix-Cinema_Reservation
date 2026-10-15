import api.Layanan
import api.Router
import api.daftarRute
import com.sun.net.httpserver.HttpServer
import repository.JdbcBookingRepository
import repository.JdbcCinemaRepository
import repository.JdbcFilmRepository
import repository.JdbcHargaStudioRepository
import repository.JdbcJadwalRepository
import repository.JdbcKotaRepository
import repository.JdbcKursiRepository
import repository.JdbcPenggunaRepository
import repository.JdbcStudioRepository
import service.AdminService
import service.AuthService
import service.BookingService
import service.CinemaForm
import service.FilmForm
import service.JadwalForm
import service.JadwalService
import service.KatalogService
import service.KotaForm
import service.LaporanService
import service.PembayaranService
import service.PosterService
import service.RiwayatService
import service.StudioForm
import service.SumberDayaAdmin
import util.Database
import util.PosterStorage
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    println("=== CINETIX API ===")
    Database.tungguSiap()

    // 1. Repository (mengakses MySQL lewat JDBC)
    val kotaRepo = JdbcKotaRepository()
    val cinemaRepo = JdbcCinemaRepository()
    val filmRepo = JdbcFilmRepository()
    val studioRepo = JdbcStudioRepository()
    val hargaRepo = JdbcHargaStudioRepository()
    val jadwalRepo = JdbcJadwalRepository()
    val penggunaRepo = JdbcPenggunaRepository()
    val kursiRepo = JdbcKursiRepository()
    val bookingRepo = JdbcBookingRepository(BookingService.BATAS_BAYAR_MENIT)

    // 2. Service (logika bisnis)
    val auth = AuthService(penggunaRepo)
    val jadwal = JadwalService(jadwalRepo, studioRepo, filmRepo, kursiRepo)
    val booking = BookingService(jadwal, jadwalRepo, studioRepo, kursiRepo, bookingRepo)
    val adminResources = listOf(
        SumberDayaAdmin("kota", KotaForm::class.java, kotaRepo),
        SumberDayaAdmin("cinema", CinemaForm::class.java, cinemaRepo),
        SumberDayaAdmin("studio", StudioForm::class.java, studioRepo),
        SumberDayaAdmin("film", FilmForm::class.java, filmRepo),
        SumberDayaAdmin("jadwal", JadwalForm::class.java, jadwalRepo)
    ).associateBy { it.nama }

    val posterStorage = PosterStorage()          // folder poster di disk server ($UPLOAD_DIR/posters)

    val layanan = Layanan(
        auth = auth,
        katalog = KatalogService(kotaRepo, cinemaRepo, filmRepo),
        jadwal = jadwal,
        booking = booking,
        pembayaran = PembayaranService(booking, bookingRepo),
        riwayat = RiwayatService(booking, bookingRepo),
        laporan = LaporanService(bookingRepo),
        admin = AdminService(adminResources, hargaRepo, jadwal),
        poster = PosterService(filmRepo, posterStorage)
    )

    // 3. Pastikan setiap studio punya denah kursi (tabel `kursi`)
    val denahBaru = jadwal.pastikanDenahLengkap()
    if (denahBaru > 0) println("[i] Denah kursi dibuat untuk $denahBaru studio")

    // 4. Tugas berkala: isi jadwal 3 hari ke depan & batalkan booking yang tidak dibayar
    val penjadwal = Executors.newSingleThreadScheduledExecutor()
    penjadwal.scheduleAtFixedRate({
        try {
            val baru = jadwal.generateOtomatis()
            val batal = booking.bersihkanKedaluwarsa()
            if (baru > 0 || batal > 0) println("[i] Jadwal baru: $baru, booking kedaluwarsa dibatalkan: $batal")
        } catch (e: Exception) {
            println("[!] Tugas berkala gagal: ${e.message}")
        }
    }, 0, 30, TimeUnit.MINUTES)

    // 5. HTTP server (REST API + versi web di http://localhost:PORT/)
    val router = Router(auth)
    daftarRute(router, layanan)
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.createContext("/") { router.tangani(it) }
    server.executor = Executors.newFixedThreadPool(16)
    server.start()
    println("[OK] CINETIX API berjalan di http://0.0.0.0:$port")
    println("[OK] Versi web: buka http://localhost:$port/ di browser")
}