package com.dccleaner.app.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dccleaner.app.model.SavedAccount
import com.dccleaner.app.service.DcCleanerNotifier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageAndNotificationSafetyTest {
    @Test fun failedReadsNeitherDeleteNorOverwriteAccountsAndCanRecover() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        // Keep the user's real saved accounts out of the test, including any attempted reset.
        val context = object : ContextWrapper(base) {
            var unavailable = false
            var deletions = 0
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                check(!unavailable) { "Transient storage failure" }
                return super.getSharedPreferences("safety-test-$name", mode)
            }
            override fun deleteSharedPreferences(name: String): Boolean {
                deletions++
                return super.deleteSharedPreferences("safety-test-$name")
            }
        }
        val name = "saved_accounts_encrypted"
        context.deleteSharedPreferences(name)
        context.deletions = 0
        try {
            val account = SavedAccount("test-user", "test-password", "test")
            assertNotNull(addSavedAccount(context, account))
            context.unavailable = true
            assertNull(getSavedAccounts(context))
            assertNull(addSavedAccount(context, account.copy(id = "other")))
            assertNull(removeSavedAccount(context, account.id))
            assertEquals(0, context.deletions)
            context.unavailable = false
            assertEquals(listOf(account), getSavedAccounts(context))

            val prefs = encryptedPreferences(context, name)
            assertTrue(prefs.edit().putString("accounts", "invalid-json").commit())
            assertNull(getSavedAccounts(context))
            assertNull(addSavedAccount(context, account))
            assertNull(removeSavedAccount(context, account.id))
            assertEquals("invalid-json", prefs.getString("accounts", null))
            assertEquals(0, context.deletions)
        } finally {
            context.unavailable = false
            context.deleteSharedPreferences(name)
        }
    }

    @Test fun resumeIntentOnlyContainsAnActualTaskId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifier = DcCleanerNotifier(context)
        val method = DcCleanerNotifier::class.java.getDeclaredMethod("createResumeIntent", String::class.java)
            .apply { isAccessible = true }
        for (id in listOf(null, "", "  ")) {
            val intent = method.invoke(notifier, id) as Intent
            assertFalse(intent.hasExtra(DcCleanerNotifier.EXTRA_RESUME_TASK_ID))
        }
        val intent = method.invoke(notifier, "saved-task-123") as Intent
        assertEquals("saved-task-123", intent.getStringExtra(DcCleanerNotifier.EXTRA_RESUME_TASK_ID))
    }
}
