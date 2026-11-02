package com.littlepages.app.data

import android.content.Context

/** Local-first progress cache. A signed-in repository can sync these values later. */
class ReaderProgressStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyBookTitles()
    }

    fun page(childId: String, bookId: String): Int = preferences.getInt(pageKey(childId, bookId), 1).coerceAtLeast(1)

    fun savePage(childId: String, bookId: String, page: Int) {
        preferences.edit().putInt(pageKey(childId, bookId), page.coerceAtLeast(1)).apply()
    }

    fun isBookmarked(childId: String, bookId: String, page: Int): Boolean = page in bookmarkedPages(childId, bookId)

    fun toggleBookmark(childId: String, bookId: String, page: Int): Boolean {
        val pages = bookmarkedPages(childId, bookId).toMutableSet()
        val added = if (page in pages) pages.remove(page) else pages.add(page)
        preferences.edit().putStringSet(bookmarkKey(childId, bookId), pages.map(Int::toString).toSet()).apply()
        return added
    }

    private fun bookmarkedPages(childId: String, bookId: String): Set<Int> = preferences
        .getStringSet(bookmarkKey(childId, bookId), emptySet())
        .orEmpty()
        .mapNotNull(String::toIntOrNull)
        .toSet()

    private fun pageKey(childId: String, bookId: String) = "page:$childId:$bookId"
    private fun bookmarkKey(childId: String, bookId: String) = "bookmarks:$childId:$bookId"

    private fun migrateLegacyBookTitles() {
        val idsByTitle = mapOf(
            "The Brave Little Fox" to "brave-little-fox",
            "A Day at the Lake" to "day-at-the-lake",
            "The Kind Giant" to "kind-giant",
            "The Star Keeper" to "star-keeper"
        )
        val editor = preferences.edit()
        var changed = false
        preferences.all.forEach { (key, value) ->
            val separator = key.indexOf(':')
            if (separator < 0) return@forEach
            val prefix = key.substringBefore(':')
            if (prefix != "page" && prefix != "bookmarks") return@forEach
            val childAndTitle = key.substring(separator + 1)
            val childSeparator = childAndTitle.indexOf(':')
            if (childSeparator < 0) return@forEach
            val childId = childAndTitle.substringBefore(':')
            val title = childAndTitle.substring(childSeparator + 1)
            val productId = idsByTitle[title] ?: return@forEach
            val migratedKey = if (prefix == "page") pageKey(childId, productId) else bookmarkKey(childId, productId)
            if (!preferences.contains(migratedKey)) {
                when (value) {
                    is Int -> editor.putInt(migratedKey, value)
                    is Set<*> -> editor.putStringSet(migratedKey, value.filterIsInstance<String>().toSet())
                }
            }
            editor.remove(key)
            changed = true
        }
        if (changed) editor.apply()
    }

    private companion object {
        const val FILE_NAME = "reader_progress_v1"
    }
}
