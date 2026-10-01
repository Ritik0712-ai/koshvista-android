package com.ritikagarwal.koshvista.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned authenticated envelope for a complete vault snapshot. */
object RecoveryArchive {
    private const val MAGIC = 0x4b565431 // KVT1
    private const val VERSION = 1
    private const val KDF_ITERATIONS = 310_000
    private val random = SecureRandom()

    fun encrypt(ownerId: String, passphrase: CharArray, snapshot: ByteArray): ByteArray {
        require(ownerId.isNotBlank())
        require(passphrase.size >= 12) { "Recovery passphrase is too short" }
        val salt = ByteArray(16).also(random::nextBytes)
        val dataKey = ByteArray(32).also(random::nextBytes)
        val wrappingKey = derive(passphrase, salt)
        try {
            val wrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val wrapNonce = ByteArray(12).also(random::nextBytes)
            wrapCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, wrapNonce))
            wrapCipher.updateAAD(ownerId.toByteArray(Charsets.UTF_8))
            val wrappedKey = wrapCipher.doFinal(dataKey)

            val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val payloadNonce = ByteArray(12).also(random::nextBytes)
            payloadCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(128, payloadNonce))
            payloadCipher.updateAAD(ownerId.toByteArray(Charsets.UTF_8))
            payloadCipher.updateAAD(byteArrayOf(VERSION.toByte()))
            val ciphertext = payloadCipher.doFinal(snapshot)

            return ByteArrayOutputStream().use { output ->
                DataOutputStream(output).use { archive ->
                    archive.writeInt(MAGIC)
                    archive.writeInt(VERSION)
                    archive.write(salt)
                    archive.write(wrapNonce)
                    archive.writeInt(wrappedKey.size)
                    archive.write(wrappedKey)
                    archive.write(payloadNonce)
                    archive.writeInt(ciphertext.size)
                    archive.write(ciphertext)
                }
                output.toByteArray()
            }
        } finally {
            dataKey.fill(0)
            wrappingKey.fill(0)
        }
    }

    fun decrypt(ownerId: String, passphrase: CharArray, archiveBytes: ByteArray): ByteArray {
        require(archiveBytes.size >= 96) { "Backup is incomplete" }
        DataInputStream(ByteArrayInputStream(archiveBytes)).use { archive ->
            require(archive.readInt() == MAGIC) { "Unknown backup format" }
            require(archive.readInt() == VERSION) { "Unsupported backup version" }
            val salt = ByteArray(16).also(archive::readFully)
            val wrapNonce = ByteArray(12).also(archive::readFully)
            val wrappedSize = archive.readInt()
            require(wrappedSize == 48) { "Backup key is damaged" }
            val wrappedKey = ByteArray(wrappedSize).also(archive::readFully)
            val payloadNonce = ByteArray(12).also(archive::readFully)
            val payloadSize = archive.readInt()
            require(payloadSize >= 16 && payloadSize <= archive.available()) { "Backup payload is damaged" }
            val payload = ByteArray(payloadSize).also(archive::readFully)
            require(archive.available() == 0) { "Unexpected backup data" }
            val wrappingKey = derive(passphrase, salt)
            val dataKey = try {
                val unwrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
                unwrapCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, wrapNonce))
                unwrapCipher.updateAAD(ownerId.toByteArray(Charsets.UTF_8))
                unwrapCipher.doFinal(wrappedKey)
            } finally { wrappingKey.fill(0) }
            try {
                val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
                payloadCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(128, payloadNonce))
                payloadCipher.updateAAD(ownerId.toByteArray(Charsets.UTF_8))
                payloadCipher.updateAAD(byteArrayOf(VERSION.toByte()))
                return payloadCipher.doFinal(payload)
            } finally { dataKey.fill(0) }
        }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, KDF_ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
