package com.ailocal.app.gguf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.ailocal.app.llm.EngineState
import com.ailocal.app.llm.LlamaEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class GgufModelRef(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    /** Absolute path on local storage copied/resolved for native loading (llama.cpp needs a real path, not a content:// URI). */
    val localPath: String
)

/**
 * Lets the user pick any *.gguf file from device storage via the system
 * document picker, remembers that choice between launches, and owns the
 * single [LlamaEngine] instance (only one model resident at a time).
 *
 * The app never assumes a fixed model file name or bundles a model in the
 * APK - the user always picks the file themselves.
 */
class ModelManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("model_manager", Context.MODE_PRIVATE)
    val engine = LlamaEngine()

    private val _selectedModel = MutableStateFlow<GgufModelRef?>(restoreSavedModel())
    val selectedModel: StateFlow<GgufModelRef?> = _selectedModel

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    val engineState: StateFlow<EngineState> = _engineState

    /** Call after the system document picker returns a content:// URI for a *.gguf file. */
    fun onModelPicked(uri: Uri): GgufModelRef? {
        context.contentResolver.takePersistableUriPermission(
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val doc = DocumentFile.fromSingleUri(context, uri) ?: return null
        val name = doc.name ?: queryDisplayName(uri) ?: "model.gguf"
        if (!name.endsWith(".gguf", ignoreCase = true)) return null

        val size = doc.length().takeIf { it > 0 } ?: querySize(uri) ?: 0L

        // llama.cpp's C API needs a filesystem path. We resolve the SAF URI
        // to a real path when possible (e.g. it's already on primary
        // external storage); otherwise we stream-copy it once into the
        // app's private files directory so the native loader can open() it.
        val localPath = resolveOrCopyToLocalPath(uri, name)
            ?: return null

        val ref = GgufModelRef(uri, name, size, localPath)
        _selectedModel.value = ref
        persistModel(ref)
        return ref
    }

    suspend fun loadSelectedModel(
        contextSize: Int,
        threads: Int,
        batchSize: Int,
        seed: Int
    ) {
        val model = _selectedModel.value ?: run {
            _engineState.value = EngineState.Error("No model selected")
            return
        }
        _engineState.value = EngineState.Loading
        val result = engine.load(model.localPath, contextSize, threads, batchSize, seed)
        _engineState.value = result
    }

    fun unloadModel() {
        engine.unload()
        _engineState.value = EngineState.Idle
    }

    fun isModelLoaded(): Boolean = engine.isLoaded()

    private fun resolveOrCopyToLocalPath(uri: Uri, name: String): String? {
        // Fast path: some providers hand back a file:// or a path we can
        // derive directly - try the generic content resolver copy, which
        // is correct in all cases including that one.
        return try {
            val destDir = File(context.filesDir, "models").apply { mkdirs() }
            val destFile = File(destDir, sanitizeFileName(name))

            // Avoid re-copying a multi-GB file every time the same model is
            // reselected: if a same-named, same-size file already exists
            // locally, reuse it.
            val existingSize = if (destFile.exists()) destFile.length() else -1L
            val sourceSize = querySize(uri) ?: -1L
            if (destFile.exists() && existingSize > 0 && existingSize == sourceSize) {
                return destFile.absolutePath
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8 * 1024 * 1024)
                }
            } ?: return null

            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return null
    }

    private fun querySize(uri: Uri): Long? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getLong(idx)
        }
        return null
    }

    private fun persistModel(ref: GgufModelRef) {
        prefs.edit {
            putString(KEY_URI, ref.uri.toString())
            putString(KEY_NAME, ref.displayName)
            putLong(KEY_SIZE, ref.sizeBytes)
            putString(KEY_LOCAL_PATH, ref.localPath)
        }
    }

    private fun restoreSavedModel(): GgufModelRef? {
        val uriStr = prefs.getString(KEY_URI, null) ?: return null
        val localPath = prefs.getString(KEY_LOCAL_PATH, null) ?: return null
        if (!File(localPath).exists()) return null
        return GgufModelRef(
            uri = Uri.parse(uriStr),
            displayName = prefs.getString(KEY_NAME, "model.gguf") ?: "model.gguf",
            sizeBytes = prefs.getLong(KEY_SIZE, 0L),
            localPath = localPath
        )
    }

    companion object {
        private const val KEY_URI = "model_uri"
        private const val KEY_NAME = "model_name"
        private const val KEY_SIZE = "model_size"
        private const val KEY_LOCAL_PATH = "model_local_path"
    }
}
