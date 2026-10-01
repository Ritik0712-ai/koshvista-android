package com.ritikagarwal.koshvista.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Wraps a random vault key with a non-exportable Android Keystore key. */
class VaultKeys(context: Context) {
    private val prefs = context.getSharedPreferences("vault_key_wrappers", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun getOrCreate(ownerId: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
        val handle = digest.joinToString("") { "%02x".format(it) }
        val alias = "koshvista-vault-$handle"
        val key = keyStore.getKey(alias, null) as? SecretKey ?: createWrappingKey(alias)
        val stored = prefs.getString(handle, null)
        if (stored != null) {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            require(packed.size >= 29) { "Vault key wrapper is damaged" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            cipher.updateAAD(ownerId.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(packed.copyOfRange(12, packed.size))
        }
        val vaultKey = ByteArray(32).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(ownerId.toByteArray(Charsets.UTF_8))
        val wrapped = cipher.iv + cipher.doFinal(vaultKey)
        check(prefs.edit().putString(handle, Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit())
        return vaultKey
    }

    fun delete(ownerId: String) {
        val digest = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
        val handle = digest.joinToString("") { "%02x".format(it) }
        prefs.edit().remove(handle).commit()
        keyStore.deleteEntry("koshvista-vault-$handle")
    }

    private fun createWrappingKey(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
