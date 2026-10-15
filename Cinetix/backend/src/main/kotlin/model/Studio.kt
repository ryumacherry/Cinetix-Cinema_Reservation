package model

import exception.ValidasiException

/** Tipe studio. Nilai [kodeDb] sama persis dengan ENUM di tabel studio. */
enum class TipeStudio(val kodeDb: String, val label: String) {
    REGULER("REGULER", "Reguler"),
    PREMIERE("PREMIERE", "Premiere"),
    SUITES("SUITES", "Suites");

    companion object {
        fun dari(teks: String?): TipeStudio =
            entries.firstOrNull { it.kodeDb.equals(teks?.trim(), true) || it.label.equals(teks?.trim(), true) }
                ?: throw ValidasiException("Tipe studio tidak dikenal: $teks (pilih REGULER/PREMIERE/SUITES)")
    }
}

/** Section denah: dekat layar (depan) atau jauh dari layar (belakang). */
enum class Section(val label: String) {
    DEPAN("Dekat layar"),
    BELAKANG("Jauh dari layar")
}

/** Sisi kursi terhadap jalan tengah. */
enum class Sisi { KIRI, KANAN }

/** Satu kursi pada denah studio = satu baris tabel `kursi`. */
data class KursiDenah(
    val id: Int = 0,
    val studioId: Int,
    val kode: String,       // A1, A2, ...
    val baris: String,      // A, B, ...
    val nomor: Int,         // 1, 2, ...
    val section: String,    // DEPAN / BELAKANG
    val sisi: String        // KIRI / KANAN
)

/**
 * ABSTRACT CLASS + POLYMORPHISM + INTERFACE.
 * Setiap turunan menentukan harga dasar, kursi per baris, persen diskon, dan deskripsi pengalaman.
 */
abstract class Studio(
    val id: Int,
    val nama: String,          // nama tipe, contoh "Reguler"
    val kapasitas: Int,
    val ruang: String          // nama ruangan, contoh "Studio 1"
) : Diskon {
    abstract val tipe: TipeStudio
    protected abstract val hargaDasar: Double
    abstract val kursiPerBaris: Int

    abstract fun deskripsiPengalaman(): String

    /** Bonus khas studio (dipakai di e-ticket). Default: tidak ada. */
    open fun bonus(jumlahTiket: Int): String? = null

    open fun hargaTiket(akhirPekan: Boolean) =
        if (akhirPekan) hargaDasar + BIAYA_AKHIR_PEKAN else hargaDasar

    /** Diskon hanya jika membeli minimal 5 tiket; persennya berbeda per tipe studio. */
    override fun hitungDiskon(jumlahTiket: Int, subtotal: Double): Double =
        if (jumlahTiket >= Diskon.MIN_TIKET) Math.round(subtotal * persenDiskon / 100.0).toDouble() else 0.0

    fun jumlahBaris(): Int = (kapasitas + kursiPerBaris - 1) / kursiPerBaris

    /**
     * Membuat denah lengkap untuk disimpan di tabel `kursi`:
     * - baris A, B, C, ... dan nomor 1..kursiPerBaris
     * - setengah baris pertama = section DEPAN (dekat layar), sisanya BELAKANG (jauh dari layar)
     * - setengah nomor pertama di KIRI jalan tengah, sisanya di KANAN
     */
    fun buatDenah(): List<KursiDenah> {
        val barisDepan = jumlahBaris() / 2
        return (0 until kapasitas).map { i ->
            val b = i / kursiPerBaris
            val n = i % kursiPerBaris
            val huruf = ('A' + b).toString()
            KursiDenah(
                studioId = id,
                kode = "$huruf${n + 1}",
                baris = huruf,
                nomor = n + 1,
                section = if (b < barisDepan) Section.DEPAN.name else Section.BELAKANG.name,
                sisi = if (n < kursiPerBaris / 2) Sisi.KIRI.name else Sisi.KANAN.name
            )
        }
    }

    fun semuaKursi(): List<String> = buatDenah().map { it.kode }

    fun kursiValid(kode: String): Boolean = kode in semuaKursi()

    /** Dipanggil saat admin membuat/mengubah studio. */
    fun validasiDenah() {
        if (kapasitas <= 0) throw ValidasiException("Kapasitas harus lebih dari 0.")
        if (kapasitas % kursiPerBaris != 0)
            throw ValidasiException("Kapasitas ${tipe.label} harus kelipatan $kursiPerBaris (satu baris = $kursiPerBaris kursi).")
        if (jumlahBaris() > MAKS_BARIS)
            throw ValidasiException("Kapasitas ${tipe.label} maksimal ${kursiPerBaris * MAKS_BARIS} kursi ($MAKS_BARIS baris x $kursiPerBaris kursi).")
    }

    companion object {
        const val BIAYA_AKHIR_PEKAN = 15_000.0
        const val MAKS_BARIS = 10   // baris A sampai J
    }
}

class Reguler(
    id: Int,
    kapasitas: Int,
    ruang: String = "Studio $id",
    override val hargaDasar: Double = 50_000.0,
    val jenisLayar: String = "2D"
) : Studio(id, "Reguler", kapasitas, ruang) {
    override val tipe = TipeStudio.REGULER
    override val kursiPerBaris = 10          // 5 kiri | jalan tengah | 5 kanan
    override val persenDiskon = 6.0
    override fun deskripsiPengalaman() = "Layar $jenisLayar, kursi standar"
}

class Premiere(
    id: Int,
    kapasitas: Int,
    ruang: String = "Studio $id",
    override val hargaDasar: Double = 90_000.0
) : Studio(id, "Premiere", kapasitas, ruang) {
    override val tipe = TipeStudio.PREMIERE
    override val kursiPerBaris = 8           // 4 kiri | jalan tengah | 4 kanan
    override val persenDiskon = 8.0

    override fun deskripsiPengalaman() = "Kursi recliner yang nyaman dan memanjakan"
}

class Suites(
    id: Int,
    kapasitas: Int,
    ruang: String = "Studio $id",
    override val hargaDasar: Double = 150_000.0
) : Studio(id, "Suites", kapasitas, ruang) {
    override val tipe = TipeStudio.SUITES
    override val kursiPerBaris = 6           // 3 kiri | jalan tengah | 3 kanan
    override val persenDiskon = 10.0
    private val pesananLayanan = mutableListOf<String>()     // atribut khas (ENCAPSULATION)

    fun pesanLayananKursi(item: String) { pesananLayanan.add(item) }
    fun daftarLayanan(): List<String> = pesananLayanan.toList()

    override fun bonus(jumlahTiket: Int) = "Selimut & bantal untuk $jumlahTiket kursi"

    override fun deskripsiPengalaman() = "Suite privat, kursi bed + layanan pelayan pribadi"
}

/** FACTORY: membuat objek Studio yang tepat dari data database. */
object StudioFactory {
    fun buat(
        tipe: TipeStudio,
        id: Int,
        kapasitas: Int,
        ruang: String = "Studio $id",
        hargaDasar: Double? = null
    ): Studio = when (tipe) {
        TipeStudio.REGULER -> if (hargaDasar != null) Reguler(id, kapasitas, ruang, hargaDasar) else Reguler(id, kapasitas, ruang)
        TipeStudio.PREMIERE -> if (hargaDasar != null) Premiere(id, kapasitas, ruang, hargaDasar) else Premiere(id, kapasitas, ruang)
        TipeStudio.SUITES -> if (hargaDasar != null) Suites(id, kapasitas, ruang, hargaDasar) else Suites(id, kapasitas, ruang)
    }
}
