package model

import kotlin.test.Test
import kotlin.test.assertEquals

class DiskonTest {

    @Test
    fun `persen diskon berbeda per tipe studio (polymorphism lewat interface Diskon)`() {
        val aturan: List<Diskon> = listOf(Reguler(1, 100), Premiere(2, 40), Suites(3, 24))
        assertEquals(listOf(6.0, 8.0, 10.0), aturan.map { it.persenDiskon })
    }

    @Test
    fun `diskon hanya berlaku mulai 5 tiket`() {
        val reguler = Reguler(1, 100)
        assertEquals(0.0, reguler.hitungDiskon(4, 200_000.0))
        assertEquals(15_000.0, reguler.hitungDiskon(5, 250_000.0))     // 6%
        assertEquals(18_000.0, reguler.hitungDiskon(6, 300_000.0))
    }

    @Test
    fun `Premiere 8 persen dan Suites 10 persen`() {
        assertEquals(36_000.0, Premiere(1, 40).hitungDiskon(5, 450_000.0))
        assertEquals(75_000.0, Suites(1, 24).hitungDiskon(5, 750_000.0))
        assertEquals(0.0, Suites(1, 24).hitungDiskon(2, 300_000.0))
    }
}
