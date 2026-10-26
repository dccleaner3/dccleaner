package com.dccleaner.app.ranking

import androidx.collection.MutableIntList
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 디시 시각은 한국 시간이라 기기 시간대와 상관없이 KST로 계산한다. */
internal val KST: ZoneId = ZoneId.of("Asia/Seoul")

enum class GalleryKind(val listPath: String, val commentType: String) {
    MAJOR("board", "G"), MINOR("mgallery/board", "M"), MINI("mini/board", "MI")
}

data class RankingGallery(val id: String, val kind: GalleryKind, val name: String)

/**
 * 하루는 아무 날, 주는 월~일, 달은 1일~말일. [anchor]가 속한 단위로 범위를 만들고 오늘 뒤는 자른다.
 * 오늘이 낀 이번 주·이번 달은 지금까지만 센다.
 */
enum class RankingPeriod(val label: String) {
    DAY("하루"), WEEK("주"), MONTH("달");

    fun range(anchor: LocalDate, today: LocalDate = LocalDate.now(KST)): ClosedRange<LocalDate> {
        val (start, end) = when (this) {
            DAY -> anchor to anchor
            WEEK -> anchor.with(java.time.DayOfWeek.MONDAY).let { it to it.plusDays(6) }
            MONTH -> anchor.withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
        }
        return start..end.coerceAtMost(today)
    }

    /** 기본값: 어제·지난주·지난달. */
    fun defaultAnchor(today: LocalDate = LocalDate.now(KST)): LocalDate = when (this) {
        DAY -> today.minusDays(1)
        WEEK -> today.minusWeeks(1)
        MONTH -> today.minusMonths(1)
    }

    /** ◀ ▶로 한 단위씩 옮긴다. */
    fun shift(anchor: LocalDate, by: Long): LocalDate = when (this) {
        DAY -> anchor.plusDays(by)
        WEEK -> anchor.plusWeeks(by)
        MONTH -> anchor.plusMonths(by)
    }
}

/** 범위에 든 날 수(양끝 포함). */
val ClosedRange<LocalDate>.days: Int get() = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()

/** 하루짜리만 그날의 타임라인·평균 댓글·말버릇을 보여 준다(autoM과 같다). */
val ClosedRange<LocalDate>.isSingleDay: Boolean get() = start == endInclusive

enum class RankingSort(val label: String) { TIME("시간순"), POST("글순"), COMMENT("댓글순") }

enum class NickType { ANON, HALF, FIXED }

data class RankingPost(
    val no: Long,
    val nick: String,
    val uid: String,
    val ip: String,
    val nickType: NickType,
    val time: LocalDateTime,
    val commentCount: Int,
    /** 닉 옆 디시 아이콘(고닉·반고닉·베스트콘) 주소. */
    val iconUrl: String = ""
)

data class RankingComment(
    val nick: String,
    val uid: String,
    val ip: String,
    val nickType: NickType,
    val time: LocalDateTime,
    val content: String,
    val iconUrl: String = ""
)

data class RankedUser(
    val nick: String,
    /** 고닉·반고닉은 아이디, 유동은 IP 앞자리. */
    val idLabel: String,
    /** 기간 전체 활동시간(분). 기간이면 화면에서 하루 평균으로 나눈다. */
    val activityMinutes: Int,
    val posts: Int,
    val comments: Int,
    val postRank: Int,
    val commentRank: Int,
    /** 하루짜리만: 0~1440분 위의 활동 구간 [시작, 끝). */
    val dayBlocks: List<IntRange> = emptyList(),
    /** 하루짜리만: 내 글에 달린 평균 댓글 수. */
    val avgCommentsPerPost: Int? = null,
    /** 하루짜리만: 같은 말을 절반 이상 반복했으면 그 말과 비율(%). */
    val topComment: Pair<String, Int>? = null,
    /** 하루짜리만: 디시콘 댓글 비율이 절반 이상이면 그 비율(%). */
    val dcconPercent: Int? = null,
    val iconUrl: String = "",
    /** [idLabel]이 아이디(고닉·반고닉)면 true. 유동 IP에는 방명록을 보낼 수 없다. */
    val hasUid: Boolean = false
) {
    val displayName: String get() = if (idLabel.isBlank()) nick else "$nick($idLabel)"
}

data class GalleryRankingResult(
    val gallery: RankingGallery,
    val period: RankingPeriod,
    val dates: ClosedRange<LocalDate>,
    val byTime: List<RankedUser>,
    val byPost: List<RankedUser>,
    val byComment: List<RankedUser>,
    val postCount: Int,
    val commentCount: Int,
    /** 아이콘 주소 → 내려받은 이미지 바이트. 이미지로 그릴 때만 쓴다. */
    val icons: Map<String, ByteArray> = emptyMap()
) {
    /** 각 정렬을 [n]위까지만 남긴다. */
    fun top(n: Int): GalleryRankingResult = copy(byTime = byTime.take(n), byPost = byPost.take(n), byComment = byComment.take(n))

    /** 집계된 모든 고닉·반고닉 아이디. 닉을 바꿔 두 줄로 잡힌 사람도 한 번만 넣는다. */
    fun guestbookIds(): List<String> = byTime.asSequence().filter { it.hasUid }.map { it.idLabel }.distinct().toList()

    fun users(sort: RankingSort): List<RankedUser> = when (sort) {
        RankingSort.TIME -> byTime
        RankingSort.POST -> byPost
        RankingSort.COMMENT -> byComment
    }
}

internal object GalleryRankingAggregator {
    /** 이 시간(분) 안에 이어서 쓰면 한 번 앉아서 활동한 것으로 묶는다. */
    private const val GAP_MINUTES = 5
    private const val MINUTES_PER_DAY = 24 * 60
    private const val MIN_MINUTES = 1
    private const val REPEAT_THRESHOLD = 50
    private const val MIN_COMMENTS_FOR_HABITS = 10

    fun aggregate(
        gallery: RankingGallery,
        period: RankingPeriod,
        dates: ClosedRange<LocalDate>,
        posts: List<RankingPost>,
        comments: List<RankingComment>,
        topN: Int,
        genericNicks: Set<String>
    ): GalleryRankingResult = Accumulator(dates, genericNicks).apply {
        posts.forEach(::addPost)
        comments.forEachIndexed { i, comment -> addComment(comment, i.toLong()) }
    }.result(gallery, period, topN)

    /**
     * 글·댓글을 받는 대로 사람별 집계에 더하고 원본은 버린다. 한 달짜리 대형 갤러리(글 수십만·댓글 수백만)도
     * 사람마다 활동 시각(분)만 숫자로 남아 메모리에 담긴다. 댓글을 여러 스레드가 동시에 더하므로 잠근다.
     * 댓글은 도착 순서가 실행마다 다르다. 그래서 원래 순서를 함께 받아, 순서에 따라 달라지는 것(동점 순위·첫 아이콘·말버릇)을
     * 모두 원래 순서대로 더한 것과 똑같이 정한다. 글은 늘 댓글보다 먼저, 받은 순서대로 더한다.
     */
    class Accumulator(private val dates: ClosedRange<LocalDate>, genericNicks: Set<String>) {
        private val start = dates.start.atStartOfDay()
        private val end = dates.endInclusive.plusDays(1).atStartOfDay()
        private val generic = genericNicks + "ㅇㅇ"
        // 댓글 내용은 하루짜리 말버릇에만 쓴다. 주·달은 남기지 않는다.
        private val keepContent = dates.isSingleDay
        private val stats = HashMap<String, Stat>()
        private var postCount = 0
        private var commentCount = 0
        // 글의 순서는 음수에서 시작해 어떤 댓글 순서(0 이상)보다도 앞선다.
        private var nextPostOrder = Long.MIN_VALUE

        private class Stat(val nick: String, val idLabel: String, val hasUid: Boolean) {
            var posts = 0
            var comments = 0
            var commentsOnPosts = 0
            var iconUrl = ""
            /** 이 사람이 처음 나온 순서. 같은 수끼리의 순위는 먼저 나온 사람이 앞선다. */
            var firstOrder = Long.MAX_VALUE
            /** [iconUrl]을 준 글·댓글의 순서. 가장 먼저 나온, 비어 있지 않은 아이콘을 쓴다. */
            var iconOrder = Long.MAX_VALUE
            /** 기간 첫날 0시부터 센 분. 날짜마다 목록을 따로 두면 그릇이 숫자보다 커서 한 줄에 모은다. */
            val minutes = MutableIntList()
            /** 하루짜리만: (순서, 댓글 내용). 말버릇이 동점이면 먼저 나온 말을 고르므로 순서를 함께 둔다. */
            val contents = ArrayList<Pair<Long, String>>()

            fun seen(order: Long, icon: String) {
                if (order < firstOrder) firstOrder = order
                if (icon.isNotEmpty() && order < iconOrder) {
                    iconOrder = order
                    iconUrl = icon
                }
            }
        }

        private val startDay = dates.start.toEpochDay()

        private fun Stat.mark(time: LocalDateTime) =
            minutes.add((time.toLocalDate().toEpochDay() - startDay).toInt() * MINUTES_PER_DAY + time.hour * 60 + time.minute)

        private fun counts(time: LocalDateTime, nick: String, nickType: NickType, uid: String, ip: String) =
            !time.isBefore(start) && time.isBefore(end) && !(nickType == NickType.ANON && (uid.isBlank() && ip.isBlank() || generic.any { g ->
                nick == g || (nick.startsWith(g) && nick.length > g.length && nick.substring(g.length).all(Char::isDigit))
            }))

        private fun stat(nick: String, uid: String, ip: String) = uid.ifBlank { ip }.let { id ->
            stats.getOrPut("$nick($id)") { Stat(nick, id, uid.isNotBlank()) }
        }

        /** 글은 목록 순서대로 하나씩 더한다(댓글보다 먼저). */
        @Synchronized fun addPost(post: RankingPost) {
            val order = nextPostOrder++
            if (!counts(post.time, post.nick, post.nickType, post.uid, post.ip)) return
            postCount++
            stat(post.nick, post.uid, post.ip).apply {
                posts++; commentsOnPosts += post.commentCount; mark(post.time); seen(order, post.iconUrl)
            }
        }

        /** [order]는 댓글끼리의 원래 순서(0 이상)다. 어떤 순서로 불러도 결과는 순서대로 더한 것과 같다. */
        @Synchronized fun addComment(comment: RankingComment, order: Long) {
            if (!counts(comment.time, comment.nick, comment.nickType, comment.uid, comment.ip)) return
            commentCount++
            stat(comment.nick, comment.uid, comment.ip).apply {
                comments++; if (keepContent) contents += order to comment.content; mark(comment.time); seen(order, comment.iconUrl)
            }
        }

        @Synchronized fun result(gallery: RankingGallery, period: RankingPeriod, topN: Int): GalleryRankingResult {
            // 처음 나온 순서로 세운 뒤 순위를 매겨, 같은 수일 때 먼저 나온 사람이 앞서게 한다.
            val ordered = stats.values.sortedBy { it.firstOrder }
            val postRanks = rankOf(ordered.filter { it.posts > 0 }.sortedByDescending { it.posts })
            val commentRanks = rankOf(ordered.filter { it.comments > 0 }.sortedByDescending { it.comments })
            val daily = dates.isSingleDay
            val users = ordered.map { s ->
                val contents = if (daily) s.contents.sortedBy { it.first }.map { it.second } else emptyList()
                // 활동 구간은 날짜마다 따로 묶는다. 자정을 넘겨 이어진 활동도 날짜별로 나뉜다.
                val minutes = IntArray(s.minutes.size, s.minutes::get).apply { sort() }
                val blocksByDay = minutes.asList().groupBy { it / MINUTES_PER_DAY }.values
                    .map { day -> mergeBlocks(day.map { it % MINUTES_PER_DAY }) }
                RankedUser(
                    nick = s.nick,
                    idLabel = s.idLabel,
                    activityMinutes = blocksByDay.sumOf { blocks -> blocks.sumOf { it.last - it.first + 1 } },
                    posts = s.posts,
                    comments = s.comments,
                    postRank = postRanks[s] ?: 0,
                    commentRank = commentRanks[s] ?: 0,
                    dayBlocks = if (daily) blocksByDay.flatten() else emptyList(),
                    avgCommentsPerPost = if (daily && s.posts > 0) Math.round(s.commentsOnPosts.toDouble() / s.posts).toInt() else null,
                    topComment = if (daily) repeatedComment(contents) else null,
                    dcconPercent = if (daily) dcconPercent(contents) else null,
                    iconUrl = s.iconUrl,
                    hasUid = s.hasUid
                )
            }
            val byTime = compareByDescending<RankedUser> { it.activityMinutes }
                .thenByDescending { it.posts }.thenByDescending { it.comments }.thenBy { it.displayName }
            val n = topN.coerceAtLeast(1)
            return GalleryRankingResult(
                gallery = gallery,
                period = period,
                dates = dates,
                byTime = users.sortedWith(byTime).take(n),
                byPost = users.filter { it.posts > 0 }.sortedWith(compareByDescending<RankedUser> { it.posts }.then(byTime)).take(n),
                byComment = users.filter { it.comments > 0 }.sortedWith(compareByDescending<RankedUser> { it.comments }.then(byTime)).take(n),
                postCount = postCount,
                commentCount = commentCount
            )
        }
    }

    private fun <T> rankOf(sorted: List<T>): Map<T, Int> = sorted.withIndex().associate { (i, v) -> v to i + 1 }

    /** 분 단위 시각을 1분 이상, 5분 안에 이어지면 하나로 묶은 구간으로 만든다(끝 포함 IntRange). */
    internal fun mergeBlocks(minutes: List<Int>): List<IntRange> {
        if (minutes.isEmpty()) return emptyList()
        val sorted = minutes.sorted()
        val blocks = ArrayList<IntRange>()
        var start = sorted[0]
        var end = sorted[0] + MIN_MINUTES
        for (t in sorted.drop(1)) {
            if (t <= end + GAP_MINUTES) end = t + MIN_MINUTES
            else {
                blocks += start until end
                start = t
                end = t + MIN_MINUTES
            }
        }
        blocks += start until end
        return blocks
    }

    private fun repeatedComment(contents: List<String>): Pair<String, Int>? {
        if (contents.size <= MIN_COMMENTS_FOR_HABITS) return null
        val (text, count) = contents.asSequence().filterNot { "dccon" in it }
            .map { it.replace(Regex("<[^>]+>"), "").trim() }.filter(String::isNotEmpty)
            .groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        val percent = count * 100 / contents.size
        return if (percent >= REPEAT_THRESHOLD) text.replace(Regex("\\s+"), " ") to percent else null
    }

    private fun dcconPercent(contents: List<String>): Int? {
        if (contents.size <= MIN_COMMENTS_FOR_HABITS) return null
        val percent = contents.count { "dccon" in it } * 100 / contents.size
        return percent.takeIf { it >= REPEAT_THRESHOLD }
    }
}
