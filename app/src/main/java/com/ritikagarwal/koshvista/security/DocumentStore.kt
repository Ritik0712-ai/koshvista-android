package com.ritikagarwal.koshvista.security

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** App-private source files are encrypted before touching persistent storage. */
class DocumentStore(private val context: Context) {
    private val vaultKeys = VaultKeys(context)
    private val random = SecureRandom()

    fun store(ownerId: String, content: ByteArray): String {
        require(content.isNotEmpty() && content.size <= 20 * 1024 * 1024) { "File must be between 1 byte and 20 MB" }
        val key = documentKey(ownerId)
        try {
            val nonce = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(ownerId.toByteArray(Charsets.UTF_8))
            val encrypted = nonce + cipher.doFinal(content)
            val fileName = "${UUID.randomUUID()}.enc"
            val directory = ownerDirectory(ownerId)
            check(directory.mkdirs() || directory.isDirectory)
            val temporary = File(directory, "$fileName.tmp")
            temporary.writeBytes(encrypted)
            check(temporary.renameTo(File(directory, fileName))) { "Could not preserve source file" }
            return fileName
        } finally { key.fill(0) }
    }

    fun read(ownerId: String, fileName: String): ByteArray {
        require(Regex("^[0-9a-f-]{36}\\.enc$").matches(fileName)) { "Invalid source reference" }
        val bytes = File(ownerDirectory(ownerId), fileName).readBytes()
        require(bytes.size >= 29) { "Source file is damaged" }
        val key = documentKey(ownerId)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(ownerId.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        } finally { key.fill(0) }
    }

    fun delete(ownerId: String, fileName: String) {
        require(Regex("^[0-9a-f-]{36}\\.enc$").matches(fileName))
        File(ownerDirectory(ownerId), fileName).delete()
    }

    fun deleteAll(ownerId: String) {
        val directory = ownerDirectory(ownerId)
        check(!directory.exists() || directory.deleteRecursively()) { "Could not remove encrypted source files" }
    }

    private fun documentKey(ownerId: String): ByteArray {
        val vaultKey = vaultKeys.getOrCreate(ownerId)
        return try {
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(vaultKey, "HmacSHA256")) }
                .doFinal("KoshVista document key v1".toByteArray(Charsets.UTF_8))
        } finally { vaultKey.fill(0) }
    }

    private fun ownerDirectory(ownerId: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "sources/$hash")
    }
}
