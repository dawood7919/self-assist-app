package com.dawood.orbit.tools.notes

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonFileStore
import java.io.File

/** The one place notes live, shared by every screen that shows them. */
class NotesRepository private constructor(context: Context) :
    EntityRepository<Note>(
        JsonFileStore(File(context.filesDir, "notes.json"), NoteCodec),
    ) {

    override fun idOf(item: Note): String = item.id

    /** Creates an empty note in [notebook] and returns it. */
    fun create(notebook: String = Note.DEFAULT_NOTEBOOK, body: String = ""): Note {
        val note = Note(notebook = notebook, body = body)
        add(note)
        return note
    }

    fun save(note: Note) = upsert(note.copy(updatedAt = System.currentTimeMillis()))

    fun togglePinned(id: String) = update(id) { it.copy(pinned = !it.pinned) }

    companion object {
        @Volatile
        private var instance: NotesRepository? = null

        fun get(context: Context): NotesRepository =
            instance ?: synchronized(this) {
                instance ?: NotesRepository(context.applicationContext).also { instance = it }
            }
    }
}
