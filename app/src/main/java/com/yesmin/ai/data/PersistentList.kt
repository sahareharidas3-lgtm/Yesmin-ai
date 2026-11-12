package com.yesmin.ai.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tiny JSON-file-backed list exposed as a StateFlow. Stored in app-private storage. */
class PersistentList<T>(
    private val file: File,
    private val enc: (T) -> JSONObject,
    private val dec: (JSONObject) -> T?,
    private val max: Int = 500
) {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items

    private fun load(): List<T> = try {
        if (!file.exists()) emptyList() else {
            val a = JSONArray(file.readText())
            (0 until a.length()).mapNotNull { dec(a.getJSONObject(it)) }
        }
    } catch (e: Exception) { emptyList() }

    @Synchronized fun add(item: T, persist: Boolean = true) {
        _items.value = (_items.value + item).takeLast(max)
        if (persist) save()
    }

    @Synchronized fun remove(pred: (T) -> Boolean) {
        _items.value = _items.value.filterNot(pred)
        save()
    }

    @Synchronized fun clear() {
        _items.value = emptyList()
        runCatching { file.delete() }
    }

    private fun save() {
        runCatching {
            val a = JSONArray()
            _items.value.forEach { a.put(enc(it)) }
            file.writeText(a.toString())
        }
    }
}
