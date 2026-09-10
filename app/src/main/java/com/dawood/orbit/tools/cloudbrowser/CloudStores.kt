package com.dawood.orbit.tools.cloudbrowser

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonFileStore
import java.io.File

/**
 * Persisted Cloud Browser collections (Workstream A).
 *
 * One thin [EntityRepository] subclass per collection, exactly like
 * NotesRepository: reads are synchronous off a StateFlow, writes go to disk
 * on a background scope. Only ever the key *path* string is stored, never
 * key file contents.
 */

/** Saved VPS connections, backed by cloud_servers.json. */
class ServerStore private constructor(context: Context) :
    EntityRepository<SavedServer>(
        JsonFileStore(File(context.filesDir, "cloud_servers.json"), CloudServerCodec),
    ) {

    override fun idOf(item: SavedServer): String = item.id

    companion object {
        @Volatile
        private var instance: ServerStore? = null

        fun get(context: Context): ServerStore =
            instance ?: synchronized(this) {
                instance ?: ServerStore(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * User preferences, backed by cloud_settings.json.
 *
 * Holds 0..1 row: every row shares the singleton id, so saving replaces the
 * previous settings instead of appending. Reads fall back to [CloudSettings]
 * defaults when nothing was saved yet.
 */
class SettingsStore private constructor(context: Context) :
    EntityRepository<CloudSettings>(
        JsonFileStore(File(context.filesDir, "cloud_settings.json"), CloudSettingsCodec),
    ) {

    override fun idOf(item: CloudSettings): String = SINGLETON_ID

    fun loadOrDefaults(): CloudSettings = items.value.firstOrNull() ?: CloudSettings()

    fun save(settings: CloudSettings) = upsert(settings)

    companion object {
        const val SINGLETON_ID = "cloud-settings"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
