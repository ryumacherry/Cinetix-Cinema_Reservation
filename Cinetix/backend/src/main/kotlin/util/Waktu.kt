package util

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Zona waktu aplikasi (default WIB). Service menerima Clock supaya mudah di-unit-test. */
object Waktu {
    val ZONA: ZoneId = ZoneId.of(System.getenv("APP_TZ") ?: "Asia/Jakarta")
    val clock: Clock = Clock.system(ZONA)

    fun akhirPekan(tanggal: LocalDate) =
        tanggal.dayOfWeek == DayOfWeek.SATURDAY || tanggal.dayOfWeek == DayOfWeek.SUNDAY
}
