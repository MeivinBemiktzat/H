package com.ailocal.app.storage

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class PickedFile(val uri: Uri, val displayName: String)

/**
 * Tracks files and folders the user has explicitly granted access to via
 * the system document picker (Storage Access Framework). The app never
 * sees or requests anything outside what the user selected here.
 */
class PickedFilesStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("picked_files_store", Context.MODE_PRIVATE)

    fun add(uri: Uri, displayName: String) {
        val files = getAll().toMutableList()
        files.removeAll { it.uri == uri }
        files.add(PickedFile(uri, displayName))
        persist(files)
    }

    fun remove(uri: Uri) {
        persist(getAll().filterNot { it.uri == uri })
    }

    fun getAll(): List<PickedFile> {
        val raw = prefs.getString(KEY_FILES, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            PickedFile(Uri.parse(obj.getString("uri")), obj.getString("name"))
        }
    }

    fun setWritableFolder(uri: Uri) {
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun getWritableFolder(): Uri? = prefs.getString(KEY_FOLDER, null)?.let { Uri.parse(it) }

    private fun persist(files: List<PickedFile>) {
        val arr = JSONArray()
        files.forEach {
            arr.put(JSONObject().apply {
                put("uri", it.uri.toString())
                put("name", it.displayName)
            })
        }
        prefs.edit().putString(KEY_FILES, arr.toString()).apply()
    }

    companion object {
        private const val KEY_FILES = "files_json"
        private const val KEY_FOLDER = "writable_folder_uri"
    }
}
