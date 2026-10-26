package com.dccleaner.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dccleaner.app.model.SavedAccount
import com.dccleaner.app.model.dccleanerUiColors
import com.dccleaner.app.ui.card.DeleteProgressCard
import com.dccleaner.app.ui.card.DeleteRunningContent
import com.dccleaner.app.ui.card.LoginCard
import com.dccleaner.app.ui.card.UpdateNotice
import com.dccleaner.app.ui.cleaner.GallerySelectCard
import com.dccleaner.app.ui.theme.DccleanerTheme
import com.dccleaner.app.ui.screen.CommentCleanerTabContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test fun updateNoticeOnlyOffersNewerReleasesAndOpensTheDownloadAction() {
        var latest by mutableStateOf<String?>("2.4.0")
        var checking by mutableStateOf(true)
        var updateClicks = 0
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                UpdateNotice("2.3.1", latest, checking) { updateClicks++ }
            }
        }
        compose.onNodeWithText("업데이트").assertDoesNotExist()
        compose.runOnIdle { checking = false }
        compose.onNodeWithText("새 버전 2.4.0").assertIsDisplayed()
        compose.onNodeWithText("업데이트").performClick()
        compose.runOnIdle { assertEquals(1, updateClicks) }
        for (version in listOf("2.3.1", "2.2.9", "invalid", null)) {
            compose.runOnIdle { latest = version }
            compose.onNodeWithText("업데이트").assertDoesNotExist()
        }
        compose.runOnIdle { latest = "2.10.0" }
        compose.onNodeWithText("업데이트").assertIsDisplayed()
    }

    @Test fun profileFallbackAndBoundedDecoding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fallback = android.graphics.BitmapFactory.decodeResource(context.resources, com.dccleaner.app.R.drawable.default_profile)
        assertEquals(90, fallback.width)
        assertEquals(90, fallback.height)
        assertEquals(null, com.dccleaner.app.ui.screen.decodeProfileImage(byteArrayOf()))
        assertEquals(null, com.dccleaner.app.ui.screen.decodeProfileImage("<html>no image</html>".toByteArray()))
        val large = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream().also { large.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        large.recycle()
        val decoded = com.dccleaner.app.ui.screen.decodeProfileImage(bytes)!!
        assertEquals(256, decoded.width)
        assertEquals(128, decoded.height)
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                com.dccleaner.app.ui.card.UserInfoCard(dccleanerUiColors(false),
                    com.dccleaner.app.model.UserInfo("테스트 계정", "1,234", "567"),
                    com.dccleaner.app.ui.screen.rememberUserProfileImage(""))
            }
        }
        compose.onNodeWithText("테스트 계정").assertIsDisplayed()
        screenshot("ui-profile-fallback")
    }

    @Test fun proxyBenefitsAreVisibleBeforeQuoteInputs() {
        var openedUrl = ""
        var setupOpens = 0
        var showSetup by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides object : androidx.compose.ui.platform.UriHandler {
                override fun openUri(uri: String) { openedUrl = uri }
            }) {
            DccleanerTheme(darkTheme = false) {
                com.dccleaner.app.ui.dialog.ProxyCleanerQuoteDialog(
                    uiColors = dccleanerUiColors(false), initialPostCount = 5000, initialCommentCount = 1000,
                    gallList = mapOf("test" to "테스트 갤러리"), selectedGalleries = listOf("test"),
                    onOpenTwoCaptchaSetup = { setupOpens++; showSetup = true }, onDismiss = {})
                if (showSetup) {
                    com.dccleaner.app.ui.cleaner.TwoCaptchaSetupDialog(
                        uiColors = dccleanerUiColors(false), twocaptchaKey = "",
                        onTwocaptchaKeyChange = {}, isTwocaptchaValid = null,
                        onTwocaptchaValidChange = {}, isCheckingTwocaptcha = false,
                        onIsCheckingTwocaptchaChange = {}, coroutine = rememberCoroutineScope(),
                        snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() },
                        onValidateTwocaptchaKey = { false }, onOpenAutoCaptchaGuide = {},
                        onDismiss = { showSetup = false })
                }
            }
            }
        }
        compose.onNodeWithText("캡차는 알아서 자동 해결").assertIsDisplayed()
        compose.onNodeWithText("앱을 끄고, 삭제는 맡기세요").assertIsDisplayed()
        screenshot("ui-proxy-benefits")
        compose.onNodeWithText("시간·비용 비교하기 ↓").performClick()
        compose.waitForIdle()
        screenshot("ui-proxy-quote")
        compose.onNodeWithText("예상 소요 시간").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3시간 30분").assertIsDisplayed()
        compose.onNodeWithText("결제금액").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4,500원").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("2Captcha") and hasClickAction()).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("3시간 30분").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("결제금액").assertExists()
        compose.onNodeWithText("2Captcha 사용료", substring = true).assertDoesNotExist()
        compose.onNodeWithText("앱을 끄고 진행").performScrollTo().assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "더 유리한 조건"))
        compose.onNodeWithText("필요 없음").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("앱에서 직접 실행").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("진행 방법 선택").performScrollTo().assertIsDisplayed()
        repeat(3) {
            if (compose.onAllNodesWithText("시간·비용 비교하기 ↓").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("시간·비용 비교하기 ↓").performClick()
                compose.waitForIdle()
            }
        }
        screenshot("ui-proxy-before-setup")
        compose.onNodeWithText("2Captcha 설정하기").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, setupOpens); assertEquals("", openedUrl) }
        compose.onNodeWithText("2Captcha 키 얻기").assertIsDisplayed()
        screenshot("ui-two-captcha-setup")
        compose.onNodeWithContentDescription("비교로 돌아가기").performClick()
        compose.onNodeWithText("삭제 방법 비교").assertIsDisplayed()
    }

    @Test fun cleanerActionsFollowSetupAndRemainReachableForBothTypes() {
        var deleteClicks = 0
        var proxyClicks = 0
        var deleteType by mutableStateOf("posting")
        var newestFirst by mutableStateOf(false)
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    com.dccleaner.app.ui.cleaner.DcCleanerTabContent(
                        uiColors = dccleanerUiColors(false),
                        postingGallList = mapOf("test" to "테스트 갤러리"),
                        commentGallList = mapOf("test" to "테스트 갤러리"),
                        deleteType = deleteType,
                        onDeleteTypeChange = { deleteType = it },
                        isTwocaptchaValid = null,
                        onShowDeleteDialog = { deleteClicks++ },
                        selectedGallList = listOf("test"),
                        onSelectedGallListChange = {},
                        minRecommendToKeep = "",
                        onMinRecommendToKeepChange = {},
                        minCommentToKeep = "",
                        onMinCommentToKeepChange = {},
                        minViewToKeep = "",
                        onMinViewToKeepChange = {},
                        recommendFilterEnabled = false,
                        onRecommendFilterEnabledChange = {},
                        commentFilterEnabled = false,
                        onCommentFilterEnabledChange = {},
                        viewFilterEnabled = false,
                        onViewFilterEnabledChange = {},
                        postContentFilterEnabled = false,
                        onPostContentFilterEnabledChange = {},
                        postContentRegex = "",
                        onPostContentRegexChange = {},
                        myPostFilterEnabled = false,
                        onMyPostFilterEnabledChange = {},
                        dcconOnlyFilterEnabled = false,
                        onDcconOnlyFilterEnabledChange = {},
                        commentContentFilterEnabled = false,
                        onCommentContentFilterEnabledChange = {},
                        commentContentRegex = "",
                        onCommentContentRegexChange = {},
                        dateFilterEnabled = false,
                        onDateFilterEnabledChange = {},
                        deleteNewestFirst = newestFirst,
                        onDeleteNewestFirstChange = { newestFirst = it },
                        deleteQuestionPosts = false,
                        onDeleteQuestionPostsChange = {},
                        minPostAgeDaysToDelete = "",
                        onMinPostAgeDaysToDeleteChange = {},
                        recordGuestbookLog = false,
                        onRecordGuestbookLogChange = {},
                        onOpenProxyCleaner = { proxyClicks++ },
                    )
                }
            }
        }
        }
        compose.onNodeWithText("무엇을 지울까요").assertIsDisplayed()
        for (type in listOf("posting", "comment")) {
            compose.runOnIdle { deleteType = type }
            val label = if (type == "posting") "최근 글부터 삭제" else "최근 댓글부터 삭제"
            // 조건은 고른 줄을 누르면 열리는 설정 시트 안에 있다.
            val typeLabel = if (type == "posting") "게시글" else "댓글"
            compose.onNodeWithContentDescription("$typeLabel 설정 열기").performScrollTo().performClick()
            if (compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithText("고급 옵션", substring = true).performScrollTo().performClick()
            }
            compose.onNodeWithText(label).performScrollTo()
            val switch = compose.onNodeWithContentDescription(label)
            switch.performScrollTo().assertIsDisplayed()
            screenshot("ui-cleaner-switch")
            switch.performClick()
            compose.runOnIdle { assertEquals(type == "posting", newestFirst) }
            compose.onNodeWithText("완료").performClick()
            compose.onNodeWithText("삭제 시작").performScrollTo().assertHasClickAction().performClick()
            compose.onNodeWithText("더 빠르고 편하게 · 초고속 삭제").assertDoesNotExist()
            compose.onNodeWithText("캡챠 자동 해결").performScrollTo()
                .assertHasClickAction().performClick()
        }
        compose.runOnIdle { assertEquals(2, deleteClicks); assertEquals(2, proxyClicks) }
        screenshot("ui-cleaner-actions")
    }

    @Test fun twoCaptchaSetupOpensKeyPageAndValidatesInApp() {
        var guideOpens = 0
        var keyText by mutableStateOf("")
        var valid by mutableStateOf<Boolean?>(null)
        var checking by mutableStateOf(false)
        var checkedKey = ""
        var dismissed = 0
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                com.dccleaner.app.ui.cleaner.TwoCaptchaSetupDialog(
                    uiColors = dccleanerUiColors(false), twocaptchaKey = keyText,
                    onTwocaptchaKeyChange = { keyText = it }, isTwocaptchaValid = valid,
                    onTwocaptchaValidChange = { valid = it }, isCheckingTwocaptcha = checking,
                    onIsCheckingTwocaptchaChange = { checking = it },
                    coroutine = rememberCoroutineScope(),
                    snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() },
                    onValidateTwocaptchaKey = { checkedKey = it; true },
                    onOpenAutoCaptchaGuide = { guideOpens++ }, onDismiss = { dismissed++ })
            }
        }
        compose.onNodeWithText("2Captcha 자동 해결 안내").assertDoesNotExist()
        compose.onNodeWithText("2Captcha 키 얻기").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, guideOpens) }
        compose.onNode(hasSetTextAction()).performTextInput("test-key")
        compose.onNodeWithText("키 확인").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("test-key", checkedKey); assertEquals(true, valid) }
        compose.onNodeWithContentDescription("비교로 돌아가기").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun captchaPrioritizesAutomaticResolutionForLargerJobs() {
        var opened = 0
        var resumed = 0
        var proxy = 0
        var totalItems by mutableStateOf(1_001L)
        var shown by mutableStateOf(true)
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    if (shown) {
                        com.dccleaner.app.ui.dialog.CaptchaDialog(dccleanerUiColors(false),
                            onOpenGallog = { opened++ }, onResolveCaptcha = { resumed++ },
                            onOpenProxyCleaner = { proxy++ },
                            totalItemCount = totalItems)
                    }
                }
            }
        }
        compose.onNodeWithText("계정 글·댓글 1,001개를 모두 직접 삭제할 때의 예상치입니다.").assertIsDisplayed()
        // 직접 삭제 속도는 항상 시간당 1,800개로 본다.
        compose.onNodeWithText("앞으로 약 33분 동안 캡챠를 약 6번 풀어야 해요.").assertIsDisplayed()
        compose.onNodeWithText("자동 해결 설정하기").assertIsDisplayed().performClick()
        compose.onNodeWithText("갤로그 열기").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, proxy) }
        compose.onNodeWithContentDescription("닫고 재개").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, resumed) }
        compose.onNodeWithText("직접 해결하기").assertIsDisplayed().performClick()
        compose.onNodeWithText("갤로그 열기").assertIsDisplayed()
        compose.onNodeWithText("자동 해결 설정하기").assertDoesNotExist()
        compose.onNodeWithText("모바일 버전 갤로그에서는 해결할 수 없습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("갤로그 열기").performClick()
        compose.onNodeWithText("해결 후 재개").performClick()
        compose.runOnIdle { assertEquals(1, opened); assertEquals(2, resumed) }
        compose.onNodeWithText("돌아가기").performClick()
        compose.onNodeWithText("자동 해결 설정하기").assertIsDisplayed()
        compose.runOnIdle { totalItems = 1_000L }
        compose.onNodeWithText("계정 글·댓글 1,001개를 모두 직접 삭제할 때의 예상치입니다.").assertIsDisplayed()
        compose.onNodeWithText("갤로그 열기").assertDoesNotExist()
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        compose.runOnIdle { shown = true }
        compose.onNodeWithText("갤로그 열기").assertIsDisplayed()
        compose.onNodeWithText("계정 글·댓글 1,000개를 모두 직접 삭제할 때의 예상치입니다.").assertIsDisplayed()
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        compose.runOnIdle { totalItems = 49_588L; shown = true }
        compose.onNodeWithText("자동 해결 설정하기").assertIsDisplayed()
        compose.onNodeWithText("갤로그 열기").assertDoesNotExist()
        compose.onNodeWithText("계정 글·댓글 49,588개를 모두 직접 삭제할 때의 예상치입니다.").assertIsDisplayed()
        compose.onNodeWithText("앞으로 약 1일 4시간 동안 캡챠를 약 248번 풀어야 해요.").assertIsDisplayed()
        screenshot("ui-captcha-large-text")
    }

    @Test fun guestbookRequiresContentAndConfirmsActualRecipientCount() {
        var recipients by mutableStateOf("")
        var message by mutableStateOf("")
        var confirm by mutableStateOf(false)
        var sends = 0
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    com.dccleaner.app.ui.guestbook.GuestbookTabContent(
                        uiColors = dccleanerUiColors(false), coroutine = rememberCoroutineScope(),
                        userListText = recipients, onUserListTextChange = { recipients = it },
                        messageText = message, onMessageTextChange = { message = it },
                        showConfirmDialog = confirm, onShowConfirmDialogChange = { confirm = it },
                        showResultDialog = false, onShowResultDialogChange = {},
                        isSending = false, onIsSendingChange = {},
                        progressDone = 0, onProgressDoneChange = {}, progressTotal = 0, onProgressTotalChange = {},
                        successCount = 0, onSuccessCountChange = {}, failCount = 0, onFailCountChange = {},
                        cacheEnabled = true, cachedUserCount = 0,
                        onFilterCachedUsers = { com.dccleaner.app.ui.guestbook.GuestbookCacheFilterResult(it, 0) },
                        onClearCachedUsers = { true }, onResolveUserList = { error("No network expected") },
                        onStartGuestbookSend = { ids, body ->
                            assertEquals(listOf("test1", "test2"), ids)
                            assertEquals("테스트 메시지", body)
                            sends++
                        }, onOpenRanking = {})
                }
            }
        }
        compose.onNodeWithText("보내기").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { recipients = "test1\ntest1\ntest2"; message = "테스트 메시지" }
        compose.onNodeWithText("2명에게 보내기").performScrollTo().performClick()
        compose.waitUntil { confirm }
        compose.onNodeWithText("2명에게 보낼까요?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithText("2명에게 보내기").performScrollTo().performClick()
        compose.onNode(hasText("2명에게 보내기") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(1, sends) }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = when {
            name == "ui-two-captcha-setup" -> compose.onAllNodes(isDialog()).onLast()
            name.startsWith("ui-proxy") || name.startsWith("ui-captcha") -> compose.onNode(isDialog())
            else -> compose.onRoot()
        }
        val bitmap = root.captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: context.getExternalFilesDir(null)!!
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun savedAccountSelectionStillReachesLogin() {
        var loginCount = 0
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                var id by remember { mutableStateOf("") }
                var pw by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    LoginCard(
                        uiColors = dccleanerUiColors(false), savedAccounts = listOf(SavedAccount("saved-user", "test-only", "테스트 계정")),
                        id = id, onIdChange = { id = it }, pw = pw, onPwChange = { pw = it },
                        saveLogin = false, onSaveLoginChange = {}, isLoggingIn = false, sessionExpired = false,
                        onSavedAccountClick = { id = it.id; pw = it.password }, onDeleteSavedAccountClick = {},
                        onLoginClick = { loginCount++ }
                    )
                }
            }
        }
        compose.onNodeWithText("아이디").assertDoesNotExist()
        compose.onNodeWithText("saved-user").performClick()
        compose.onAllNodesWithText("saved-user").onFirst().assertIsSelected()
        compose.onNodeWithText("아이디").assertExists()
        compose.onNode(hasText("로그인") and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, loginCount) }
        screenshot("ui-login")
    }

    @Test fun gallerySelectionWorksWithLargeText() {
        var chosenType = "posting"
        compose.setContent {
            DccleanerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                    var type by remember { mutableStateOf(chosenType) }
                    var selected by remember { mutableStateOf(emptyList<String>()) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        GallerySelectCard(
                            dccleanerUiColors(true), mapOf("one" to "첫 번째 갤러리", "two" to "두 번째 갤러리"), type,
                            { type = it; chosenType = it }, selected, { selected = it }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("댓글").performClick()
        compose.runOnIdle { assertEquals("comment", chosenType) }
        compose.onNodeWithContentDescription("갤러리 선택 열기").performClick()
        compose.onNodeWithText("전체 선택", substring = false).performClick()
        compose.onNodeWithContentDescription("닫기").performClick()
        compose.onNodeWithText("2개 선택 · 전체 2개").assertExists()
        screenshot("ui-gallery-large-text")
    }

    @Test fun commentCleanerSettingsShowOnTheScreen() {
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    CommentCleanerTabContent(
                        running = false, onStartCommentCleaner = { _, _, _ -> }, onStopCommentCleaner = {},
                        uiColors = dccleanerUiColors(false), modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        // 시트를 거치지 않고 설정이 화면에 바로 보인다.
        compose.onNodeWithText("댓글 자동 정리 사용").assertIsDisplayed()
        compose.onNodeWithText("차단할 단어").assertIsDisplayed()
        compose.onNodeWithText("감시 설정").assertExists()
        compose.onNodeWithText("백그라운드에서 계속", substring = true).assertExists()
        screenshot("ui-comment-cleaner")
    }

    @Test fun progressLogsAreOptionalAndStopRemainsReachable() {
        var stopped = false
        var totalGalleries by mutableStateOf(0)
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                    DeleteProgressCard(
                        uiColors = dccleanerUiColors(false), deleteType = "posting", isCompleted = false, isDeleting = true,
                        totalPosts = totalGalleries, deletedPosts = 2, currentProgress = .25f, estimatedTimeLeft = 420,
                        currentDeletionSpeedPerHour = 1800, nextCaptchaEstimatedTimeLeft = 60, isTwoCaptchaConfigured = false,
                        deleteLog = listOf("이전 상세 로그", "현재 갤러리 처리 중"),
                        onClose = {}, onComplete = {}, onStop = { stopped = true }, onOpenProxyCleaner = {},
                        currentGallery = "테스트 갤러리"
                    )
                }
            }
        }
        compose.onNodeWithText("갤러리 확인 중").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
            .assertDoesNotExist()
        compose.runOnIdle { totalGalleries = 8 }
        compose.onNodeWithText("2 / 8 갤러리 완료").assertIsDisplayed()
        compose.onNodeWithText("1,234개 삭제").assertIsDisplayed()
        compose.onNodeWithText("56개 제외").assertIsDisplayed()
        screenshot("ui-progress-counts")
        compose.onNodeWithText("이전 상세 로그").assertDoesNotExist()
        compose.onNodeWithText("상세 로그 보기").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("이전 상세 로그"))
        compose.onNodeWithText("이전 상세 로그").assertIsDisplayed()
        compose.onNodeWithText("작업 중단").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, stopped) }
        screenshot("ui-progress-large-text")
    }

    @Test fun twoCaptchaRequestReplacesStaleDeletionProgress() {
        var latestLog by mutableStateOf("[12:00:00] [[DCCLEANER_PROGRESS]]🗑️ 20/100 (20%)")
        compose.setContent {
            DccleanerTheme(darkTheme = false) {
                DeleteRunningContent(
                    uiColors = dccleanerUiColors(false), loginId = "", currentGallery = "테스트 갤러리",
                    progress = .2f, deletedCount = 0, totalCount = 3, latestLog = latestLog,
                    itemCounts = com.dccleaner.app.model.DeleteItemCounts(20, 0), onStop = {}
                )
            }
        }
        compose.onNodeWithText("0/3 갤러리").assertIsDisplayed()
        compose.runOnIdle { latestLog = "[12:00:01] [[DCCLEANER_PROGRESS]]2Captcha 캡챠 해결 중 (1/3)" }
        compose.onNodeWithText("캡챠 해결 중").assertIsDisplayed()
        compose.onNodeWithText("2Captcha 캡챠 해결 중 (1/3)").assertIsDisplayed()
        compose.onNodeWithText("0/3 갤러리").assertDoesNotExist()
        compose.onNodeWithText("20%").assertDoesNotExist()
    }
}
