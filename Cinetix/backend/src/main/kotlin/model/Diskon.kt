package model

/**
 * INTERFACE: aturan diskon pembelian banyak tiket.
 * Diimplementasikan oleh Studio; setiap tipe studio punya persen sendiri
 * (Reguler 6%, Premiere 8%, Suites 10%) -> POLYMORPHISM.
 */
interface Diskon {
    val persenDiskon: Double
    fun hitungDiskon(jumlahTiket: Int, subtotal: Double): Double

    companion object {
        /** Diskon berlaku jika membeli minimal 5 tiket dalam satu transaksi. */
        const val MIN_TIKET = 5
    }
}
