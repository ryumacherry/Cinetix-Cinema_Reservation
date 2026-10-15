package util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash password dengan PBKDF2 (bawaan JDK, tanpa library tambahan).
 * Format simpan: pbkdf2$<iterasi>$<salt base64>$<hash base64>
 */
object PasswordHasher {
    private const val ITERASI = 60_000
    private const val PANJANG_BIT = 256

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hasil = pbkdf2(password, salt, ITERASI)
        val b64 = Base64.getEncoder()
        return "pbkdf2\$$ITERASI\$${b64.encodeToString(salt)}\$${b64.encodeToString(hasil)}"
    }

    fun cocok(password: String, tersimpan: String): Boolean {
        val bagian = tersimpan.split("$")
        if (bagian.size != 4 || bagian[0] != "pbkdf2") return false
        val iterasi = bagian[1].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val salt = b64.decode(bagian[2])
        val harapan = b64.decode(bagian[3])
        return MessageDigest.isEqual(harapan, pbkdf2(password, salt, iterasi))
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterasi: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterasi, PANJANG_BIT)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
