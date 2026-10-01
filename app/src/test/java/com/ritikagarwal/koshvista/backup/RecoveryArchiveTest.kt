package com.ritikagarwal.koshvista.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryArchiveTest {
    @Test fun roundTripRejectsWrongOwnerPasswordAndTampering() {
        val passphrase = "correct horse battery staple".toCharArray()
        val snapshot = "private vault data".toByteArray()
        val archive = RecoveryArchive.encrypt("owner-a", passphrase, snapshot)
        assertArrayEquals(snapshot, RecoveryArchive.decrypt("owner-a", passphrase, archive))
        assertNotEquals(String(snapshot), String(archive))
        assertThrows(Exception::class.java) { RecoveryArchive.decrypt("owner-b", passphrase, archive) }
        assertThrows(Exception::class.java) { RecoveryArchive.decrypt("owner-a", "wrong passphrase".toCharArray(), archive) }
        archive[archive.lastIndex] = (archive.last() + 1).toByte()
        assertThrows(Exception::class.java) { RecoveryArchive.decrypt("owner-a", passphrase, archive) }
    }
}
