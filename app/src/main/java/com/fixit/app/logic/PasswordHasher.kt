package com.fixit.app.logic

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Passwords are never stored in plain text. We store:  hash = SHA-256(salt + password)
 * and the random salt next to it. The salt makes two identical passwords produce
 * different hashes, so a leaked database cannot be attacked with a ready-made table.
 * (A production app would use a slow function such as PBKDF2/bcrypt; SHA-256 keeps this explainable.)
 */
object PasswordHasher {
    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return toHex(bytes)
    }

    fun hash(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return toHex(md.digest((salt + password).toByteArray(Charsets.UTF_8)))
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(password, salt).toByteArray(), expectedHash.toByteArray())

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
