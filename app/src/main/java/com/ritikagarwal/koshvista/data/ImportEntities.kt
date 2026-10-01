package com.ritikagarwal.koshvista.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "source_documents",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [ForeignKey(OwnerEntity::class, ["ownerId"], ["ownerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("ownerId"), Index(value = ["ownerId", "sha256Hex"], unique = true)],
)
data class SourceDocumentEntity(
    val ownerId: String,
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sha256Hex: String,
    val encryptedFileRef: String,
    val kind: String,
    val sizeBytes: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "import_jobs",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [
        ForeignKey(SourceDocumentEntity::class, ["ownerId", "id"], ["ownerId", "documentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["ownerId", "id"], ["ownerId", "accountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["ownerId", "documentId"]), Index(value = ["ownerId", "accountId"])],
)
data class ImportJobEntity(
    val ownerId: String,
    val id: String,
    val documentId: String,
    val accountId: String,
    val status: String,
    val acceptedCount: Int,
    val duplicateCount: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "import_candidates",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [ForeignKey(ImportJobEntity::class, ["ownerId", "id"], ["ownerId", "jobId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["ownerId", "jobId", "decision"])],
)
data class ImportCandidateEntity(
    val ownerId: String,
    val id: String,
    val jobId: String,
    val sourceRow: Int,
    val localDate: String?,
    val description: String,
    val amountMinor: Long?,
    val currencyCode: String,
    val reviewReasons: String,
    val decision: String,
    val fingerprint: String?,
    val linkedTransactionId: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
