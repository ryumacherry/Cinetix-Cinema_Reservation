package util

import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object KodeGenerator {
    private val acak = SecureRandom()
    private const val HURUF = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // tanpa O/0/I/1 agar tidak tertukar

    private fun acakTeks(panjang: Int) = (1..panjang).map { HURUF[acak.nextInt(HURUF.length)] }.joinToString("")

    /** Contoh: CTX-261013-000042-K7QX (maks 30 karakter, cocok untuk barcode CODE_128). */
    fun kodeTiket(bookingId: Int, tanggal: LocalDate): String =
        "CTX-${tanggal.format(DateTimeFormatter.ofPattern("yyMMdd"))}-${bookingId.toString().padStart(6, '0')}-${acakTeks(4)}"

    /** Token sesi login (64 karakter hex). */
    fun token(): String {
        val bytes = ByteArray(32).also { acak.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
