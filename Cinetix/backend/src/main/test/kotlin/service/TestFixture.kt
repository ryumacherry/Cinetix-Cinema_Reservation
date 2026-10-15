package service

import fake.FakeBookingRepository
import fake.FakeFilmRepository
import fake.FakeJadwalRepository
import fake.FakeKursiRepository
import fake.FakeStudioRepository
import fake.Penyimpanan
import model.Film
import model.Jadwal
import model.Premiere
import model.Reguler
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Data uji bersama. Waktu "dibekukan" di Jumat, 16 Okt 2026 pukul 10:00 WIB,
 * jadi hari yang valid adalah Jumat (16), Sabtu (17), Minggu (18).
 */
class TestFixture {
    val zona: ZoneId = ZoneId.of("Asia/Jakarta")
    val hariIni: LocalDate = LocalDate.of(2026, 10, 16)
    val clock: Clock = Clock.fixed(LocalDateTime.of(hariIni, LocalTime.of(10, 0)).atZone(zona).toInstant(), zona)

    val db = Penyimpanan().apply {
        tambahStudio(Reguler(1, 100, "Studio 1"))      // 10 baris x 10 kursi
        tambahStudio(Premiere(2, 40, "Studio 2"))      // 5 baris x 8 kursi
        film += Film(1, "Film A", "Aksi", 120)
        film += Film(2, "Film B", "Drama", 100)
        jadwal += Jadwal(1, 1, 1, hariIni, LocalTime.of(13, 0))                 // Jumat siang, Reguler
        jadwal += Jadwal(2, 1, 1, hariIni, LocalTime.of(9, 0))                  // sudah lewat
        jadwal += Jadwal(3, 2, 2, hariIni.plusDays(1), LocalTime.of(19, 0))     // Sabtu, Premiere
    }

    val studioRepo = FakeStudioRepository(db)
    val kursiRepo = FakeKursiRepository(db)
    val filmRepo = FakeFilmRepository(db)
    val jadwalRepo = FakeJadwalRepository(db)
    val bookingRepo = FakeBookingRepository(db, jadwalRepo)

    val jadwalService = JadwalService(jadwalRepo, studioRepo, filmRepo, kursiRepo, clock)
    val bookingService = BookingService(jadwalService, jadwalRepo, studioRepo, kursiRepo, bookingRepo)
    val pembayaranService = PembayaranService(bookingService, bookingRepo, simpanTiket = {})
}
