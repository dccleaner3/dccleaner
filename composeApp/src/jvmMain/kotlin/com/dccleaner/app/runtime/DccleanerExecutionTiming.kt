package com.dccleaner.app.runtime

import com.dccleaner.app.network.Cleaner

data class DccleanerExecutionTiming(
    val pageRequestDelayMillis: Long = Cleaner.PAGE_REQUEST_DELAY,
    val postRequestDelayMillis: Long = Cleaner.POST_REQUEST_DELAY,
    val captchaRetryDelayMillis: Long = 3_000L,
    val captchaSettleDelayMillis: Long = 2_000L,
    val captchaPollDelayMillis: Long = 1_000L
) {
    companion object {
        val Immediate = DccleanerExecutionTiming(
            pageRequestDelayMillis = 0L,
            postRequestDelayMillis = 0L,
            captchaRetryDelayMillis = 0L,
            captchaSettleDelayMillis = 0L,
            captchaPollDelayMillis = 1L
        )
    }
}
