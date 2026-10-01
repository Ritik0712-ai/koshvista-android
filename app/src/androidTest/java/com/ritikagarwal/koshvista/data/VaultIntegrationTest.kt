package com.ritikagarwal.koshvista.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ritikagarwal.koshvista.core.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultIntegrationTest {
    @Test fun encryptedVaultKeepsOwnersSeparateAndTransferBalanced() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val alice = "test-${UUID.randomUUID()}"
        val bob = "test-${UUID.randomUUID()}"
        try {
            val aliceDatabase = factory.open(alice)
            try {
                val database = aliceDatabase
                val repo = LedgerRepository(alice, database)
                repo.initialiseOwner("Alice")
                val bank = repo.addAccount("bank", "Bank", "INR", Money(10_000, "INR"), LocalDate.now())
                val cash = repo.addAccount("cash", "Cash", "INR", Money(0, "INR"), LocalDate.now())
                repo.transfer(bank, cash, Money(2_500, "INR"), LocalDate.now())
                val balances = repo.balances.first()
                assertEquals(7_500L, balances.single { it.id == bank }.balanceMinor)
                assertEquals(2_500L, balances.single { it.id == cash }.balanceMinor)
            } finally { aliceDatabase.close() }
            val bobDatabase = factory.open(bob)
            try {
                val database = bobDatabase
                val repo = LedgerRepository(bob, database)
                repo.initialiseOwner("Bob")
                assertTrue(repo.balances.first().isEmpty())
            } finally { bobDatabase.close() }
            val reopenedDatabase = factory.open(alice)
            try {
                val database = reopenedDatabase
                assertEquals(2, LedgerRepository(alice, database).balances.first().size)
            } finally { reopenedDatabase.close() }
        } finally {
            factory.delete(alice)
            factory.delete(bob)
        }
    }
}
