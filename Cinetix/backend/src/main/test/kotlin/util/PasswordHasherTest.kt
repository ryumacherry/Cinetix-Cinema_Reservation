package util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {
    @Test
    fun `hash bisa diverifikasi dan tidak menyimpan password asli`() {
        val hash = PasswordHasher.hash("rahasia123")
        assertFalse(hash.contains("rahasia123"))
        assertTrue(PasswordHasher.cocok("rahasia123", hash))
        assertFalse(PasswordHasher.cocok("salah", hash))
    }

    @Test
    fun `salt acak membuat hash selalu berbeda`() {
        assertNotEquals(PasswordHasher.hash("abc123"), PasswordHasher.hash("abc123"))
    }
}
