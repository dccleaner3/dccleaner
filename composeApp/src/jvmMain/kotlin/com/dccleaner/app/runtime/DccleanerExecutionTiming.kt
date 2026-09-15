package com.dccleaner.app.runtime

import com.dccleaner.app.network.Cleaner

data class DccleanerExecutionTiming(
    val pageRequestDelayMillis: Long = Cleaner.PAGE_REQUEST_DELAY,
    val postRequestDelayMillis: Long = Cleaner.POST_REQUEST_DELAY,
    val captchaRetryDelayMillis: Long = 3_000L,
    val captchaSettleDelayMillis: Long = 2_000L,
    val captchaPollDelayMillis: Long = 1_000L,
    val daewangconPostIntervalDelayMillis: Long = 30_000L,
    val daewangconCommentIntervalDelayMillis: Long = 0L,
    val daewangconPostBatchDelayMillis: Long = 0L,
    val daewangconCommentBatchDelayMillis: Long = 90_000L
) {
    companion object {
        val Immediate = DccleanerExecutionTiming(
            pageRequestDelayMillis = 0L,
            postRequestDelayMillis = 0L,
            captchaRetryDelayMillis = 0L,
            captchaSettleDelayMillis = 0L,
            captchaPollDelayMillis = 1L,
            daewangconPostIntervalDelayMillis = 0L,
            daewangconCommentIntervalDelayMillis = 0L,
            daewangconPostBatchDelayMillis = 0L,
            daewangconCommentBatchDelayMillis = 0L
        )
    }
}
