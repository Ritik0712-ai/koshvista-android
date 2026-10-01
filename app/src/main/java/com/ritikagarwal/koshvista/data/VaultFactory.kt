package com.ritikagarwal.koshvista.data

import android.content.Context
import androidx.room.Room
import com.ritikagarwal.koshvista.security.VaultKeys
import java.security.MessageDigest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class VaultFactory(private val context: Context) {
    private val keys = VaultKeys(context)

    fun open(ownerId: String): VaultDatabase {
        require(ownerId.isNotBlank())
        val handle = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val key = keys.getOrCreate(ownerId)
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, VaultDatabase::class.java, "vault-$handle.db")
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
    }

    fun delete(ownerId: String) {
        val handle = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        context.deleteDatabase("vault-$handle.db")
        keys.delete(ownerId)
    }
}
