package com.dccleaner.app.desktop

import com.dccleaner.app.model.SavedAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.UUID

class DesktopCredentialStoreTest {
    private class FakeBackend : CredentialBackend {
        val values = mutableMapOf<String, String>()
        var failReads = false

        override fun read(name: String): String? {
            if (failReads) error("read failed")
            return values[name]
        }

        override fun write(name: String, value: String) { values[name] = value }
        override fun delete(name: String) { values.remove(name) }
    }

    @Test
    fun accountsAndCaptchaKeySurviveStoreRecreation() {
        val backend = FakeBackend()
        val store = DesktopCredentialStore(backend)
        store.addAccount(SavedAccount("first", "old", "one"))
        store.addAccount(SavedAccount("second", "pw", "two"))
        store.addAccount(SavedAccount("first", "new", "one"))
        store.saveTwoCaptchaKey("test-key")

        val reopened = DesktopCredentialStore(backend)
        assertEquals(listOf("first", "second"), reopened.getAccounts().map { it.id })
        assertEquals("new", reopened.getAccounts().first().password)
        assertEquals("test-key", reopened.getTwoCaptchaKey())
        reopened.removeAccount("first")
        reopened.removeTwoCaptchaKey()
        assertEquals(listOf("second"), store.getAccounts().map { it.id })
        assertEquals("", store.getTwoCaptchaKey())
    }

    @Test
    fun readFailureNeverOverwritesExistingAccounts() {
        val backend = FakeBackend()
        val store = DesktopCredentialStore(backend)
        store.addAccount(SavedAccount("first", "pw"))
        val original = backend.values.toMap()
        backend.failReads = true
        assertFailsWith<IllegalStateException> { store.addAccount(SavedAccount("second", "pw")) }
        assertEquals(original, backend.values)
    }

    @Test
    fun windowsCredentialFieldsAreAccessibleToJna() {
        assertTrue(WindowsCredential().size() > 0)
    }

    @Test
    fun macKeychainRoundTripWhenExplicitlyEnabled() {
        if (System.getenv("DCCLEANER_TEST_KEYCHAIN") != "1" ||
            !System.getProperty("os.name").lowercase().contains("mac")) return
        val backend = MacKeychainBackend()
        val name = "test-${UUID.randomUUID()}"
        try {
            backend.write(name, "한글-test")
            assertEquals("한글-test", backend.read(name))
        } finally {
            backend.delete(name)
        }
        assertEquals(null, backend.read(name))
    }
}
