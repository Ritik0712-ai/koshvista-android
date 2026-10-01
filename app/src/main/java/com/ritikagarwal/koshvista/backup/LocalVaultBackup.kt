package com.ritikagarwal.koshvista.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import com.ritikagarwal.koshvista.data.VaultDatabase
import com.ritikagarwal.koshvista.security.DocumentStore
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** A logical, owner-bound vault snapshot. Plaintext exists only in memory before envelope encryption. */
class LocalVaultBackup(private val context: Context) {
    private val files = DocumentStore(context)
    private val tables = listOf("owners", "accounts", "categories", "source_documents", "transactions",
        "transaction_splits", "import_jobs", "import_candidates", "fixed_deposits", "budgets",
        "instruments", "investment_trades", "balance_observations")
    private val maxArchiveBytes = 100 * 1024 * 1024

    suspend fun create(ownerId: String, database: VaultDatabase, passphrase: CharArray): ByteArray {
        val snapshot = database.withTransaction {
            JSONObject().apply {
                put("format", 1)
                put("owner", ownerId)
                put("schema", 6)
                val data = JSONObject()
                tables.forEach { table -> data.put(table, rows(database, table, ownerId)) }
                put("tables", data)
                val documents = JSONObject()
                val sourceRows = data.getJSONArray("source_documents")
                for (index in 0 until sourceRows.length()) {
                    val row = sourceRows.getJSONObject(index)
                    val bytes = files.read(ownerId, row.getString("encryptedFileRef"))
                    require(bytes.size <= 20 * 1024 * 1024)
                    require(sha256(bytes) == row.getString("sha256Hex")) { "Source document integrity check failed" }
                    documents.put(row.getString("id"), Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
                put("documents", documents)
            }
        }.toString().toByteArray(Charsets.UTF_8)
        require(snapshot.size <= maxArchiveBytes) { "Vault is too large for local backup" }
        return RecoveryArchive.encrypt(ownerId, passphrase, snapshot)
    }

    /** Restores only into an empty vault. A failed insert rolls back every database record. */
    suspend fun restore(ownerId: String, database: VaultDatabase, passphrase: CharArray, encrypted: ByteArray) {
        require(encrypted.size in 1..maxArchiveBytes) { "Backup file is too large" }
        val plaintext = RecoveryArchive.decrypt(ownerId, passphrase, encrypted)
        require(plaintext.size <= maxArchiveBytes) { "Backup payload is too large" }
        val snapshot = JSONObject(String(plaintext, Charsets.UTF_8))
        plaintext.fill(0)
        require(snapshot.getInt("format") == 1 && snapshot.getInt("schema") in 4..6)
        require(snapshot.getString("owner") == ownerId) { "Backup belongs to a different owner" }
        val data = snapshot.getJSONObject("tables")
        val documentBytes = snapshot.getJSONObject("documents")
        val writtenRefs = mutableListOf<String>()
        val replacementRefs = mutableMapOf<String, String>()
        try {
            val sourceRows = data.getJSONArray("source_documents")
            for (index in 0 until sourceRows.length()) {
                val row = sourceRows.getJSONObject(index)
                require(row.getString("ownerId") == ownerId)
                val id = row.getString("id")
                val bytes = Base64.decode(documentBytes.getString(id), Base64.DEFAULT)
                require(bytes.size.toLong() == row.getLong("sizeBytes"))
                require(sha256(bytes) == row.getString("sha256Hex")) { "Source document integrity check failed" }
                val ref = files.store(ownerId, bytes)
                writtenRefs += ref
                replacementRefs[id] = ref
                bytes.fill(0)
            }
            database.withTransaction {
                val sql = database.openHelper.writableDatabase
                tables.filter { it != "owners" && it != "categories" }.forEach { table ->
                    sql.query("SELECT COUNT(*) FROM $table WHERE ownerId = ?", arrayOf(ownerId)).use { cursor ->
                        check(cursor.moveToFirst() && cursor.getLong(0) == 0L) { "Restore requires an empty vault" }
                    }
                }
                sql.execSQL("DELETE FROM categories WHERE ownerId = ?", arrayOf(ownerId))
                val ownerRows = data.getJSONArray("owners")
                require(ownerRows.length() == 1 && ownerRows.getJSONObject(0).getString("ownerId") == ownerId)
                sql.query("SELECT COUNT(*) FROM owners WHERE ownerId = ?", arrayOf(ownerId)).use { cursor ->
                    if (cursor.moveToFirst() && cursor.getLong(0) == 0L) {
                        insertRows(database, "owners", ownerRows, ownerId, replacementRefs)
                    }
                }
                tables.filter { it != "owners" }.forEach { table ->
                    insertRows(database, table, data.optJSONArray(table) ?: JSONArray(), ownerId, replacementRefs)
                }
            }
        } catch (error: Exception) {
            writtenRefs.forEach { files.delete(ownerId, it) }
            throw error
        }
    }

    private fun rows(database: VaultDatabase, table: String, ownerId: String): JSONArray {
        require(table in tables)
        val result = JSONArray()
        database.openHelper.readableDatabase.query("SELECT * FROM $table WHERE ownerId = ?", arrayOf(ownerId)).use { cursor ->
            while (cursor.moveToNext()) result.put(cursorRow(cursor))
        }
        return result
    }

    private fun cursorRow(cursor: Cursor) = JSONObject().apply {
        cursor.columnNames.forEachIndexed { index, name ->
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> put(name, cursor.getLong(index))
                Cursor.FIELD_TYPE_STRING -> put(name, cursor.getString(index))
                Cursor.FIELD_TYPE_BLOB -> put(name, JSONObject().put("blob", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP)))
                else -> error("Unsupported database column type")
            }
        }
    }

    private fun insertRows(database: VaultDatabase, table: String, rows: JSONArray, ownerId: String,
        documentRefs: Map<String, String>) {
        require(table in tables)
        val sql = database.openHelper.writableDatabase
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            require(row.getString("ownerId") == ownerId)
            val values = ContentValues()
            row.keys().forEach { column ->
                require(Regex("^[A-Za-z][A-Za-z0-9]*$").matches(column))
                when (val value = row.get(column)) {
                    JSONObject.NULL -> values.putNull(column)
                    is String -> values.put(column, if (table == "source_documents" && column == "encryptedFileRef")
                        documentRefs[row.getString("id")] ?: error("Missing source document") else value)
                    is Number -> values.put(column, value.toLong())
                    is JSONObject -> values.put(column, Base64.decode(value.getString("blob"), Base64.DEFAULT))
                    else -> error("Unsupported backup field")
                }
            }
            sql.insert(table, 0, values)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
