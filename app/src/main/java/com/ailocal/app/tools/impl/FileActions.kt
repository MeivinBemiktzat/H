package com.ailocal.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ailocal.app.storage.PickedFilesStore
import com.ailocal.app.tools.ToolResult

/**
 * All file access goes through the Android Storage Access Framework
 * (ACTION_OPEN_DOCUMENT / ACTION_OPEN_DOCUMENT_TREE, persisted URI
 * permissions). The app never reads or writes raw filesystem paths
 * outside its own sandbox, and never requests broad storage permissions.
 */
class FileActions(private val context: Context) {

    private val pickedFilesStore = PickedFilesStore(context)

    fun openFileManager(): ToolResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", context.filesDir
                ), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened file manager")
        } catch (e: Exception) {
            // Many devices lack a generic "open folder" handler; fall back to the SAF document tree UI.
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.Success("Opened document picker")
            } catch (e2: Exception) {
                ToolResult.NotSupported("No file manager app available on this device")
            }
        }
    }

    fun listPickedFiles(): ToolResult {
        val files = pickedFilesStore.getAll()
        if (files.isEmpty()) return ToolResult.Success("No files picked yet", mapOf("count" to "0"))
        val summary = files.joinToString("; ") { it.displayName }
        return ToolResult.Success(summary, mapOf("count" to files.size.toString()))
    }

    fun openFile(uriString: String?): ToolResult {
        if (uriString.isNullOrBlank()) return ToolResult.Failure("Missing file reference")
        return try {
            val uri = Uri.parse(uriString)
            val doc = DocumentFile.fromSingleUri(context, uri)
            val mime = doc?.type ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened file")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open file: ${e.message}")
        }
    }

    fun createTextFile(name: String?, content: String?): ToolResult {
        if (name.isNullOrBlank()) return ToolResult.Failure("Missing file name")
        val treeUri = pickedFilesStore.getWritableFolder()
            ?: return ToolResult.Failure("No writable folder selected yet. Pick a folder first in the Files screen.")

        return try {
            val folder = DocumentFile.fromTreeUri(context, treeUri)
                ?: return ToolResult.Failure("Saved folder is no longer accessible")
            val file = folder.createFile("text/plain", name)
                ?: return ToolResult.Failure("Could not create file")
            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write((content ?: "").toByteArray(Charsets.UTF_8))
            }
            pickedFilesStore.add(file.uri, name)
            ToolResult.Success("Created $name")
        } catch (e: Exception) {
            ToolResult.Failure("Could not create file: ${e.message}")
        }
    }

    fun deleteFile(uriString: String?): ToolResult {
        if (uriString.isNullOrBlank()) return ToolResult.Failure("Missing file reference")
        return try {
            val uri = Uri.parse(uriString)
            val doc = DocumentFile.fromSingleUri(context, uri)
            val deleted = doc?.delete() ?: false
            if (deleted) {
                pickedFilesStore.remove(uri)
                ToolResult.Success("File deleted")
            } else {
                ToolResult.Failure("Could not delete file")
            }
        } catch (e: Exception) {
            ToolResult.Failure("Could not delete file: ${e.message}")
        }
    }

    fun searchFiles(query: String?): ToolResult {
        if (query.isNullOrBlank()) return ToolResult.Failure("Missing search query")
        val matches = pickedFilesStore.getAll().filter { it.displayName.contains(query, ignoreCase = true) }
        if (matches.isEmpty()) return ToolResult.Success("No matching files found among files you've shared with the app")
        return ToolResult.Success(matches.joinToString("; ") { it.displayName })
    }
}
