package model

interface Diskon {
    val persenDiskon: Double
    fun hitungDiskon(jumlahTiket: Int, subtotal: Double): Double

    companion object {
        const val MIN_TIKET = 5
    }
}
