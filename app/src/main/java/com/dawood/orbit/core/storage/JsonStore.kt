package com.dawood.orbit.core.storage

import java.io.File

/**
 * Turns a list of entities into text and back.
 *
 * Kept separate from the file handling so it stays a pure function of strings,
 * which is what lets the round trip be unit tested without an Android device.
 */
interface JsonCodec<T> {
    fun encode(items: List<T>): String
    fun decode(text: String): List<T>
}

/**
 * A small on-disk list.
 *
 * Deliberately a JSON file rather than a database: the collections here are
 * hundreds of rows, not millions, and a file has no schema migrations to get
 * wrong. Everything goes through this one class, so swapping in Room later is
 * a change to this file and the repositories' constructor, nothing else.
 */
class JsonFileStore<T>(
    private val file: File,
    private val codec: JsonCodec<T>,
) {

    @Synchronized
    fun load(): List<T> {
        if (!file.exists()) return emptyList()
        return runCatching { codec.decode(file.readText()) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(items: List<T>) {
        runCatching {
            file.parentFile?.mkdirs()
            // Write to a sibling first: a crash mid-write must not be able to
            // leave the file half-written and unreadable on next launch.
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(codec.encode(items))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }
}
