package com.dccleaner.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dccleaner.app.util.InAppUriHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.dccleaner.app.ui.screen.DcinsideScreen
import com.dccleaner.app.ui.theme.DccleanerTheme
import com.dccleaner.app.service.DcCleanerNotifier
import com.dccleaner.app.storage.getSavedDarkTheme
import com.dccleaner.app.storage.saveDarkTheme
import com.dccleaner.app.ranking.EXTRA_OPEN_GALLERY_RANKING
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var resumeTaskId by mutableStateOf<String?>(null)
    /** 갤창랭킹 알림으로 들어왔으면 갤창랭킹 화면을 연다. */
    private var openGalleryRanking by mutableStateOf(false)
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission granted")
        } else {
            android.util.Log.w("MainActivity", "Notification permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resumeTaskId = intent.getStringExtra(DcCleanerNotifier.EXTRA_RESUME_TASK_ID)
        // 프로세스가 죽은 뒤 복원되거나 최근 앱 목록에서 다시 열 때는 처음 인텐트의 표시를 다시 읽지 않는다(사용자가 보던 탭을 지킨다).
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            openGalleryRanking = intent.getBooleanExtra(EXTRA_OPEN_GALLERY_RANKING, false)
        }
        enableEdgeToEdge()

        // The app manages saved accounts itself, so keep system autofill services
        // (including Samsung Pass/Wallet) from offering to save the login form.
        findViewById<View>(android.R.id.content).importantForAutofill =
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        // 알림 권한 요청 (Android 13+)
        requestNotificationPermission()
        
        // 배터리 최적화 예외 요청
        requestBatteryOptimizationExemption()

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            var darkTheme by remember {
                mutableStateOf(getSavedDarkTheme(this, systemDarkTheme))
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                    !darkTheme
            }

            val uriHandler = remember { InAppUriHandler(this) { darkTheme } }
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                DccleanerTheme(darkTheme = darkTheme) {
                    DcinsideScreen(
                        resumeTaskId = resumeTaskId,
                        onResumeTaskConsumed = {
                            resumeTaskId = null
                            intent.removeExtra(DcCleanerNotifier.EXTRA_RESUME_TASK_ID)
                        },
                        openGalleryRanking = openGalleryRanking,
                        onOpenGalleryRankingConsumed = {
                            openGalleryRanking = false
                            intent.removeExtra(EXTRA_OPEN_GALLERY_RANKING)
                        },
                        isDarkTheme = darkTheme,
                        onDarkThemeChange = { enabled ->
                            darkTheme = enabled
                            saveDarkTheme(this, enabled)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resumeTaskId = intent.getStringExtra(DcCleanerNotifier.EXTRA_RESUME_TASK_ID)
        openGalleryRanking = intent.getBooleanExtra(EXTRA_OPEN_GALLERY_RANKING, false)
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    android.util.Log.d("MainActivity", "Notification permission already granted")
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
