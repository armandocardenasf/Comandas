package com.enverolab.comandas.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Passwords {
    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt)
        return "pbkdf2:120000:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(key)}"
    }

    private fun derive(password: String, salt: ByteArray) =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120000, 256))
            .encoded

    fun verify(password: String, stored: String): Boolean =
        try {
            if (!stored.startsWith("pbkdf2:"))
                MessageDigest.isEqual(password.toByteArray(), stored.toByteArray())
            else {
                val parts = stored.split(':')
                require(parts.size == 4 && parts[1] == "120000")
                MessageDigest.isEqual(
                    derive(password, Base64.getDecoder().decode(parts[2])),
                    Base64.getDecoder().decode(parts[3]),
                )
            }
        } catch (_: Exception) {
            false
        }
}
