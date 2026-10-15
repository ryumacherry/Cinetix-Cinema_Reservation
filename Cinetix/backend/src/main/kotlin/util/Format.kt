package util

import java.util.Locale

/** 50000.0 -> "Rp50.000" */
fun Double.rupiah(): String = "Rp" + String.format(Locale.US, "%,d", Math.round(this)).replace(',', '.')
