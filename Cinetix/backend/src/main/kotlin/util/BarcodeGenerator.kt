package util

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import model.Cetakable
import java.io.ByteArrayOutputStream
import java.io.File

/** Membuat gambar QR (QRIS) dan barcode tiket (CODE_128) memakai ZXing. */
object BarcodeGenerator {
    private val hints = mapOf(EncodeHintType.MARGIN to 1)

    fun qrPng(isi: String, ukuran: Int = 360): ByteArray =
        png(isi, BarcodeFormat.QR_CODE, ukuran, ukuran)

    fun barcodePng(isi: String, lebar: Int = 600, tinggi: Int = 160): ByteArray =
        png(isi, BarcodeFormat.CODE_128, lebar, tinggi)

    private fun png(isi: String, format: BarcodeFormat, w: Int, h: Int): ByteArray {
        val matrix = MultiFormatWriter().encode(isi, format, w, h, hints)
        return ByteArrayOutputStream().use { out ->
            MatrixToImageWriter.writeToStream(matrix, "PNG", out)
            out.toByteArray()
        }
    }

    /** Menyimpan e-ticket (.txt) + barcode (.png) ke folder output/ (di Docker di-mount ke host). */
    fun simpanKeFolder(item: Cetakable, folder: String = System.getenv("OUTPUT_DIR") ?: "output") {
        try {
            val dir = File(folder).apply { mkdirs() }
            File(dir, "${item.isiBarcode()}.txt").writeText(item.cetak())
            File(dir, "${item.isiBarcode()}.png").writeBytes(barcodePng(item.isiBarcode()))
        } catch (e: Exception) {
            println("[!] Gagal menyimpan e-ticket ke folder $folder: ${e.message}")
        }
    }
}
