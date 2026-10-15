package model

import exception.ValidasiException

enum class StatusBayar { MENUNGGU, BERHASIL, GAGAL }

abstract class Pembayaran(val nominal: Double) {
    abstract val metode: String       
    abstract val kode: String            // QRIS / BANK / KREDIT

    open val butuhKonfirmasi: Boolean = true

    var status: StatusBayar = StatusBayar.MENUNGGU
        private set

    open fun biayaAdmin(): Double = 0.0

    fun totalBayar() = nominal + biayaAdmin()

    abstract fun proses(): Boolean

    abstract fun instruksi(): String

    fun bayar(): Boolean {
        status = if (proses()) StatusBayar.BERHASIL else StatusBayar.GAGAL
        return status == StatusBayar.BERHASIL
    }

    init {
        if (nominal <= 0) throw ValidasiException("Nominal pembayaran harus lebih dari 0.")
    }
}

class Qris(nominal: Double, private val referensi: String = System.currentTimeMillis().toString()) : Pembayaran(nominal) {
    override val metode = "QRIS"
    override val kode = "QRIS"

    fun payload() = "CINETIX-QRIS|NOMINAL=${totalBayar().toLong()}|REF=$referensi"

    override fun proses() = true

    override fun instruksi() = "Scan QR di aplikasi e-wallet/m-banking, lalu tekan \"Saya sudah bayar\"."
}

class TransferBank(nominal: Double, val bank: String) : Pembayaran(nominal) {
    override val metode = "Transfer $bank"
    override val kode = "BANK"

    override fun biayaAdmin() = 2_500.0

    override fun proses() = bank.uppercase() in DAFTAR_BANK

    fun nomorVirtualAccount(referensi: Int): String {
        val prefix = when (bank.uppercase()) {
            "BCA" -> "39001"; "BNI" -> "98801"; "BRI" -> "26215"; "MANDIRI" -> "89608"; else -> "00000"
        }
        return prefix + referensi.toString().padStart(11, '0')
    }

    override fun instruksi() = "Transfer ke Virtual Account $bank sesuai nominal, lalu tekan \"Saya sudah bayar\"."

    companion object {
        val DAFTAR_BANK = listOf("BCA", "BNI", "BRI", "MANDIRI")
    }
}

class KartuKredit(nominal: Double, private val noKartu: String) : Pembayaran(nominal) {
    override val metode = "Kartu Kredit"
    override val kode = "KREDIT"
    override val butuhKonfirmasi = false     

    override fun biayaAdmin() = Math.round(nominal * 0.02).toDouble()

    /** 16 digit + lolos algoritma Luhn (kartu uji: 4111111111111111). */
    override fun proses() = noKartu.length == 16 && noKartu.all { it.isDigit() } && lolosLuhn(noKartu)

    fun nomorTersamar() = "**** **** **** " + noKartu.takeLast(4)

    override fun instruksi() = "Kartu ${nomorTersamar()} langsung didebit."

    private fun lolosLuhn(nomor: String): Boolean {
        var jumlah = 0
        nomor.reversed().forEachIndexed { i, c ->
            var d = c.digitToInt()
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            jumlah += d
        }
        return jumlah % 10 == 0
    }
}

object PembayaranFactory {
    fun buat(kode: String?, nominal: Double, bank: String? = null, noKartu: String? = null): Pembayaran =
        when (kode?.uppercase()) {
            "QRIS" -> Qris(nominal)
            "BANK" -> {
                val b = bank?.trim()?.uppercase()
                if (b.isNullOrEmpty()) throw ValidasiException("Pilih bank tujuan transfer.")
                TransferBank(nominal, b)
            }
            "KREDIT" -> {
                val k = noKartu?.filter { it.isDigit() }.orEmpty()
                if (k.isEmpty()) throw ValidasiException("Nomor kartu kredit wajib diisi.")
                KartuKredit(nominal, k)
            }
            else -> throw ValidasiException("Metode pembayaran tidak dikenal. Pilih QRIS, BANK, atau KREDIT.")
        }
}
