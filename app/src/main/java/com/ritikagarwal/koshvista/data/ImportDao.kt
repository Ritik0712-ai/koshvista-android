package com.ritikagarwal.koshvista.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Insert suspend fun insertDocument(document: SourceDocumentEntity)
    @Query("SELECT * FROM source_documents WHERE ownerId = :ownerId AND sha256Hex = :sha256Hex")
    suspend fun documentByHash(ownerId: String, sha256Hex: String): SourceDocumentEntity?
    @Query("SELECT * FROM source_documents WHERE ownerId = :ownerId AND id = :id")
    suspend fun document(ownerId: String, id: String): SourceDocumentEntity?
    @Query("SELECT * FROM import_jobs WHERE ownerId = :ownerId AND documentId = :documentId LIMIT 1")
    suspend fun jobByDocument(ownerId: String, documentId: String): ImportJobEntity?

    @Insert suspend fun insertJob(job: ImportJobEntity)
    @Update suspend fun updateJob(job: ImportJobEntity)
    @Query("SELECT * FROM import_jobs WHERE ownerId = :ownerId AND id = :id")
    suspend fun job(ownerId: String, id: String): ImportJobEntity?
    @Query("SELECT * FROM import_jobs WHERE ownerId = :ownerId ORDER BY createdAtMs DESC")
    fun jobs(ownerId: String): Flow<List<ImportJobEntity>>

    @Insert suspend fun insertCandidate(candidate: ImportCandidateEntity)
    @Update suspend fun updateCandidate(candidate: ImportCandidateEntity)
    @Query("SELECT * FROM import_candidates WHERE ownerId = :ownerId AND jobId = :jobId ORDER BY sourceRow")
    fun candidates(ownerId: String, jobId: String): Flow<List<ImportCandidateEntity>>
    @Query("SELECT * FROM import_candidates WHERE ownerId = :ownerId AND jobId = :jobId ORDER BY sourceRow")
    suspend fun candidatesOnce(ownerId: String, jobId: String): List<ImportCandidateEntity>
    @Query("SELECT * FROM import_candidates WHERE ownerId = :ownerId AND id = :id")
    suspend fun candidate(ownerId: String, id: String): ImportCandidateEntity?
}
