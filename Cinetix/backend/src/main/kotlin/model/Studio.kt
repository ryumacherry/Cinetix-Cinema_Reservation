package model

import exception.ValidasiException

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

enum class Section(val label: String) {
    DEPAN("Dekat layar"),
    BELAKANG("Jauh dari layar")
}

enum class Sisi { KIRI, KANAN }

data class KursiDenah(
    val id: Int = 0,
    val studioId: Int,
    val kode: String,       // A1, A2, ...
    val baris: String,      // A, B, ...
    val nomor: Int,         // 1, 2, ...
    val section: String,    // DEPAN / BELAKANG
    val sisi: String        // KIRI / KANAN
)

abstract class Studio(
    val id: Int,
    val nama: String,         
    val kapasitas: Int,
    val ruang: String          
) : Diskon {
    abstract val tipe: TipeStudio
    protected abstract val hargaDasar: Double
    abstract val kursiPerBaris: Int

    abstract fun deskripsiPengalaman(): String

    open fun bonus(jumlahTiket: Int): String? = null

    open fun hargaTiket(akhirPekan: Boolean) =
        if (akhirPekan) hargaDasar + BIAYA_AKHIR_PEKAN else hargaDasar

    override fun hitungDiskon(jumlahTiket: Int, subtotal: Double): Double =
        if (jumlahTiket >= Diskon.MIN_TIKET) Math.round(subtotal * persenDiskon / 100.0).toDouble() else 0.0

    fun jumlahBaris(): Int = (kapasitas + kursiPerBaris - 1) / kursiPerBaris

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
    override val kursiPerBaris = 10         
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
    override val kursiPerBaris = 8           
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
    override val kursiPerBaris = 6          
    override val persenDiskon = 10.0
    private val pesananLayanan = mutableListOf<String>()     

    fun pesanLayananKursi(item: String) { pesananLayanan.add(item) }
    fun daftarLayanan(): List<String> = pesananLayanan.toList()

    override fun bonus(jumlahTiket: Int) = "Selimut & bantal untuk $jumlahTiket kursi"

    override fun deskripsiPengalaman() = "Suite privat, kursi bed + layanan pelayan pribadi"
}

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
