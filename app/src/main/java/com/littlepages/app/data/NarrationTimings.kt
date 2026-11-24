package com.littlepages.app.data

import org.json.JSONObject

internal data class ApprovedNarrationWord(val start: Int, val end: Int, val startMs: Int, val endMs: Int)

internal data class ApprovedNarrationPage(val page: Int, val text: String, val words: List<ApprovedNarrationWord>) {
    val durationMs: Int = words.last().endMs

    fun wordAt(elapsedMs: Long): ApprovedNarrationWord? {
        if (elapsedMs < 0) return null
        return words.firstOrNull { elapsedMs >= it.startMs && elapsedMs < it.endMs }
            ?: words.lastOrNull()?.takeIf { elapsedMs >= it.endMs }
    }
}

internal data class ApprovedNarrationScript(val pages: List<ApprovedNarrationPage>) {
    fun page(number: Int): ApprovedNarrationPage? = pages.firstOrNull { it.page == number }
}

/** Matches the server narration timing contract. Returns null when the file must not be played. */
internal fun parseNarrationTimings(bytes: ByteArray): ApprovedNarrationScript? {
    if (bytes.size > 5 * 1024 * 1024) return null
    val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull() ?: return null
    if (root.optInt("schemaVersion") != 1) return null
    val pagesJson = root.optJSONArray("pages") ?: return null
    if (pagesJson.length() !in 1..500) return null
    val seen = mutableSetOf<Int>()
    val pages = buildList {
        for (index in 0 until pagesJson.length()) {
            val page = pagesJson.optJSONObject(index) ?: return null
            val number = page.optInt("page")
            val text = page.optString("text")
            val wordsJson = page.optJSONArray("words") ?: return null
            if (number < 1 || !seen.add(number) || text.isEmpty() || text.length > 20_000 || wordsJson.length() !in 1..2000) return null
            var previousEnd = -1
            val words = buildList {
                for (wordIndex in 0 until wordsJson.length()) {
                    val word = wordsJson.optJSONObject(wordIndex) ?: return null
                    val start = word.optInt("start", -1)
                    val end = word.optInt("end", -1)
                    val startMs = word.optInt("startMs", -1)
                    val endMs = word.optInt("endMs", -1)
                    if (start < 0 || end <= start || end > text.length || startMs < previousEnd || endMs <= startMs || endMs > 60 * 60 * 1000) return null
                    previousEnd = endMs
                    add(ApprovedNarrationWord(start, end, startMs, endMs))
                }
            }
            add(ApprovedNarrationPage(number, text, words))
        }
    }
    return ApprovedNarrationScript(pages)
}
