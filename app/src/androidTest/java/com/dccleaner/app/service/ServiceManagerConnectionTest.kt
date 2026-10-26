package com.dccleaner.app.service

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dccleaner.app.network.Cleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceManagerConnectionTest {
    @Test fun failedBindingDoesNotReportDeletionStarted() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int) = false
        }
        val manager = ServiceManager(context)
        try {
            assertFalse(manager.startDeletion(Cleaner(), listOf("123"), "posting", mapOf("123" to "test")))
            assertNotNull(manager.errorMessage.value)
            assertFalse(manager.isDeleting.value)
            runBlocking { manager.stopForLogout() }
        } finally {
            manager.unbindService()
        }
    }

    @Test fun logoutWaitsForInitialServiceConnection() = runBlocking {
        val manager = ServiceManager(InstrumentationRegistry.getInstrumentation().targetContext)
        withContext(Dispatchers.Main) {
            try {
                manager.stopForLogout()
                assertTrue(manager.isServiceConnected.value)
            } finally {
                manager.unbindService()
            }
        }
    }
}
