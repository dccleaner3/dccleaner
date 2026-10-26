package com.dccleaner.app.runtime

fun String.toDeletionRegex(enabled: Boolean, label: String): Regex? {
    if (!enabled) return null
    if (isBlank()) throw IllegalArgumentException("$label 정규식을 입력해 주세요.")
    return runCatching {
        val match = Regex("^/(.+)/([a-zA-Z]*)$").find(this)
        if (match == null) return@runCatching Regex(this)
        val (pattern, flags) = match.destructured
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        Regex(pattern, options)
    }.getOrElse { throw IllegalArgumentException("$label 정규식이 올바르지 않습니다.") }
}
