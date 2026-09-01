package com.dawood.orbit.tools.bookmarks

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonFileStore
import java.io.File

class BookmarksRepository private constructor(context: Context) :
    EntityRepository<Bookmark>(
        JsonFileStore(File(context.filesDir, "bookmarks.json"), BookmarkCodec),
    ) {

    override fun idOf(item: Bookmark): String = item.id

    fun create(url: String, title: String, description: String, tags: List<String>): Bookmark {
        val bookmark = Bookmark(
            url = BookmarkQueries.normaliseUrl(url),
            title = title.trim(),
            description = description.trim(),
            tags = tags,
        )
        add(bookmark)
        return bookmark
    }

    companion object {
        @Volatile
        private var instance: BookmarksRepository? = null

        fun get(context: Context): BookmarksRepository =
            instance ?: synchronized(this) {
                instance ?: BookmarksRepository(context.applicationContext).also { instance = it }
            }
    }
}
