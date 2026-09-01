package com.dawood.orbit.core.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * An in-memory list backed by a file.
 *
 * Reads are synchronous off a [StateFlow] so the UI never waits; writes go to
 * disk on a background scope. Notes, tasks and bookmarks all use this, which is
 * why they behave identically when the app is killed mid-edit.
 */
abstract class EntityRepository<T>(private val store: JsonFileStore<T>) {

    protected abstract fun idOf(item: T): String

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow(store.load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    fun get(id: String): T? = _items.value.firstOrNull { idOf(it) == id }

    fun add(item: T) {
        _items.value = _items.value + item
        persist()
    }

    /** Replaces the row with the same id, or appends when it is new. */
    fun upsert(item: T) {
        val id = idOf(item)
        val existing = _items.value
        _items.value = if (existing.any { idOf(it) == id }) {
            existing.map { if (idOf(it) == id) item else it }
        } else {
            existing + item
        }
        persist()
    }

    fun update(id: String, transform: (T) -> T) {
        var changed = false
        _items.value = _items.value.map { item ->
            if (idOf(item) == id) {
                changed = true
                transform(item)
            } else {
                item
            }
        }
        if (changed) persist()
    }

    fun remove(id: String) {
        val before = _items.value
        val after = before.filterNot { idOf(it) == id }
        if (after.size != before.size) {
            _items.value = after
            persist()
        }
    }

    fun removeAll(predicate: (T) -> Boolean) {
        val before = _items.value
        val after = before.filterNot(predicate)
        if (after.size != before.size) {
            _items.value = after
            persist()
        }
    }

    fun replaceAll(items: List<T>) {
        _items.value = items
        persist()
    }

    private fun persist() {
        val snapshot = _items.value
        scope.launch { store.save(snapshot) }
    }
}
