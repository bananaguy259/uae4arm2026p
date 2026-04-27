package com.uae4arm2026.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

object MediaPathHelper {
	fun normalizeImportedPath(context: Context, uri: Uri): String {
		return FileManager.resolveScopedStoragePath(context, uri) ?: uri.toString()
	}

	fun normalizeLaunchPath(context: Context, path: String): String {
		if (path.isBlank() || !path.startsWith("content://")) return path
		return FileManager.resolveScopedStoragePath(context, Uri.parse(path)) ?: path
	}

	fun canAccessPath(context: Context, path: String): Boolean {
		if (path.isBlank()) return false
		if (path.startsWith("content://") || path.startsWith("/proc/self/fd/")) return true
		if (File(path).exists()) return true
		return FileManager.findContentUriForPath(context, path) != null
	}

	fun isDirectoryPath(context: Context, path: String): Boolean {
		if (path.isBlank()) return false
		if (!path.startsWith("content://") && File(path).isDirectory) return true

		val contentUri = if (path.startsWith("content://")) Uri.parse(path) else FileManager.findContentUriForPath(context, path)
			?: return false

		DocumentFile.fromTreeUri(context, contentUri)?.let { if (it.isDirectory) return true }
		DocumentFile.fromSingleUri(context, contentUri)?.let { if (it.isDirectory) return true }

		return try {
			context.contentResolver.query(
				contentUri,
				arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
				null,
				null,
				null
			)?.use { cursor ->
				if (!cursor.moveToFirst()) return@use false
				val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
				mimeIndex >= 0 && cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR
			} ?: false
		} catch (_: Exception) {
			false
		}
	}
}