package api

import com.google.gson.JsonParseException
import com.sun.net.httpserver.HttpExchange
import exception.AksesDitolakException
import exception.AutentikasiException
import exception.CinetixException
import exception.ValidasiException
import model.Pengguna
import service.AuthService
import util.Json
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.sql.SQLException
import java.time.format.DateTimeParseException

enum class Akses { PUBLIK, LOGIN, ADMIN }

/** Konteks satu request HTTP: parameter, body JSON, pengguna login, dan fungsi untuk membalas. */
class Konteks(
    private val exchange: HttpExchange,
    private val paramPath: Map<String, String>,
    private val penggunaLogin: Pengguna?,
    val token: String?
) {
    val query: Map<String, String> by lazy {
        exchange.requestURI.rawQuery.orEmpty().split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            dekode(k) to dekode(v)
        }
    }

    private val bodyTeks: String by lazy { exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8) }

    val pengguna: Pengguna get() = penggunaLogin ?: throw AutentikasiException()

    fun pathInt(nama: String): Int =
        paramPath[nama]?.toIntOrNull() ?: throw ValidasiException("Parameter '$nama' harus berupa angka.")

    fun path(nama: String): String = paramPath[nama] ?: throw ValidasiException("Parameter '$nama' wajib ada.")

    fun queryInt(nama: String): Int? = query[nama]?.takeIf { it.isNotBlank() }?.let {
        it.toIntOrNull() ?: throw ValidasiException("Parameter '$nama' harus berupa angka.")
    }

    fun bodyMentah(): String = bodyTeks

    /** Body mentah (biner), contoh isi file gambar. Ditolak jika lebih besar dari [maks] byte. */
    fun bodyBiner(maks: Int): ByteArray {
        val data = exchange.requestBody.readNBytes(maks + 1)
        if (data.size > maks) throw ValidasiException("Ukuran file maksimal ${maks / 1024 / 1024} MB.")
        return data
    }

    fun <T> body(kelas: Class<T>): T =
        Json.gson.fromJson(bodyTeks.ifBlank { "{}" }, kelas) ?: throw ValidasiException("Body JSON kosong.")

    fun json(data: Any?, status: Int = 200) =
        kirim(status, "application/json; charset=utf-8", Json.gson.toJson(data).toByteArray(StandardCharsets.UTF_8))

    fun png(data: ByteArray) = kirim(200, "image/png", data)

    /** Menyajikan file statis (poster). Nama file unik per unggahan, jadi aman di-cache browser/aplikasi. */
    fun berkas(data: ByteArray, tipe: String) {
        exchange.responseHeaders.set("Cache-Control", "public, max-age=86400")
        kirim(200, tipe, data)
    }

    fun teks(data: String) = kirim(200, "text/plain; charset=utf-8", data.toByteArray(StandardCharsets.UTF_8))

    internal fun kirim(status: Int, tipe: String, data: ByteArray) {
        exchange.responseHeaders.set("Content-Type", tipe)
        exchange.sendResponseHeaders(status, if (data.isEmpty()) -1 else data.size.toLong())
        if (data.isNotEmpty()) exchange.responseBody.use { it.write(data) }
    }

    private fun dekode(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8)
}

typealias Handler = (Konteks) -> Unit

/** Router mini di atas com.sun.net.httpserver (bawaan JDK) — tanpa framework tambahan. */
class Router(private val auth: AuthService) {

    private class Rute(val metode: String, val pola: Regex, val namaParam: List<String>, val akses: Akses, val handler: Handler)

    private val daftar = mutableListOf<Rute>()

    fun get(path: String, akses: Akses = Akses.PUBLIK, handler: Handler) = tambah("GET", path, akses, handler)
    fun post(path: String, akses: Akses = Akses.PUBLIK, handler: Handler) = tambah("POST", path, akses, handler)
    fun put(path: String, akses: Akses = Akses.PUBLIK, handler: Handler) = tambah("PUT", path, akses, handler)
    fun delete(path: String, akses: Akses = Akses.PUBLIK, handler: Handler) = tambah("DELETE", path, akses, handler)

    private fun tambah(metode: String, path: String, akses: Akses, handler: Handler) {
        val nama = mutableListOf<String>()
        val pola = path.split("/").joinToString("/") { seg ->
            if (seg.startsWith("{") && seg.endsWith("}")) { nama += seg.substring(1, seg.length - 1); "([^/]+)" }
            else Regex.escape(seg)
        }
        daftar += Rute(metode, Regex("^$pola/?$"), nama, akses, handler)
    }

    fun tangani(ex: HttpExchange) {
        val mulai = System.currentTimeMillis()
        var status = 200
        try {
            val path = ex.requestURI.path
            // Versi web: file statis dari src/main/resources/web
            if (ex.requestMethod == "GET" && !path.startsWith("/api") && kirimFileWeb(ex, path)) return
            val cocok = daftar.filter { it.pola.matches(path) }
            val rute = cocok.firstOrNull { it.metode == ex.requestMethod }
            if (rute == null) {
                status = if (cocok.isEmpty()) 404 else 405
                balasError(ex, status, if (status == 404) "Endpoint tidak ditemukan: $path" else "Metode ${ex.requestMethod} tidak didukung.")
                return
            }
            val nilai = rute.pola.find(path)!!.groupValues.drop(1).map { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            val token = ex.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")?.trim()
            val pengguna = when (rute.akses) {
                Akses.PUBLIK -> null
                Akses.LOGIN -> auth.dariToken(token)
                Akses.ADMIN -> auth.dariToken(token).also { if (!it.bolehAksesAdmin()) throw AksesDitolakException() }
            }
            rute.handler(Konteks(ex, rute.namaParam.zip(nilai).toMap(), pengguna, token))
        } catch (e: Throwable) {
            val (s, pesan) = terjemahkanError(e)
            status = s
            if (s >= 500) e.printStackTrace()
            try { balasError(ex, s, pesan) } catch (_: Exception) { /* header sudah terkirim */ }
        } finally {
            println("${ex.requestMethod} ${ex.requestURI} -> $status (${System.currentTimeMillis() - mulai} ms)")
            ex.close()
        }
    }

    /** Mengirim index.html, app.js, style.css, dll. Mengembalikan false jika file tidak ada. */
    private fun kirimFileWeb(ex: HttpExchange, path: String): Boolean {
        val nama = path.removePrefix("/").ifBlank { "index.html" }.let { if ('.' in it) it else "index.html" }
        if (nama.contains("..")) return false
        val data = Router::class.java.getResourceAsStream("/web/$nama")?.use { it.readBytes() } ?: return false
        val tipe = when (nama.substringAfterLast('.')) {
            "html" -> "text/html; charset=utf-8"
            "js" -> "application/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "ico" -> "image/x-icon"
            else -> "application/octet-stream"
        }
        ex.responseHeaders.set("Content-Type", tipe)
        ex.responseHeaders.set("Cache-Control", "no-cache")
        ex.sendResponseHeaders(200, data.size.toLong())
        ex.responseBody.use { it.write(data) }
        return true
    }

    private fun terjemahkanError(e: Throwable): Pair<Int, String> = when (e) {
        is CinetixException -> e.statusHttp to (e.message ?: "Terjadi kesalahan.")
        is JsonParseException, is DateTimeParseException, is NumberFormatException ->
            400 to "Format data tidak valid. Periksa kembali isian Anda."
        is SQLException -> when (e.errorCode) {
            1062 -> 409 to "Data yang sama sudah ada (duplikat)."
            1451 -> 409 to "Data tidak bisa dihapus/diubah karena masih dipakai data lain."
            1452 -> 400 to "Data relasi tidak ditemukan (periksa id yang dipilih)."
            else -> 500 to "Kesalahan database: ${e.message}"
        }
        else -> 500 to "Terjadi kesalahan server: ${e.message}"
    }

    private fun balasError(ex: HttpExchange, status: Int, pesan: String) {
        val data = Json.gson.toJson(mapOf("status" to status, "pesan" to pesan)).toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(status, data.size.toLong())
        ex.responseBody.use { it.write(data) }
    }
}
