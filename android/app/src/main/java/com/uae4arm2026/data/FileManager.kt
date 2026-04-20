package com.uae4arm2026.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.uae4arm2026.data.model.AmigaFile
import com.uae4arm2026.data.model.FileCategory
import java.util.zip.CRC32
import java.io.File

object FileManager {

	private const val TAG = "Uae4Arm-FileManager"

	fun getAppStoragePath(context: Context): String {
		return context.getExternalFilesDir(null)?.absolutePath ?: ""
	}

	fun getCategoryDir(context: Context, category: FileCategory): File {
		return File(getAppStoragePath(context), category.dirName)
	}

	fun getEffectiveCategoryDir(context: Context, category: FileCategory): File {
		val configured = getCategoryLibraryPath(context, category)
		if (!configured.isNullOrBlank()) {
			val externalDir = File(configured)
			if (externalDir.exists() && externalDir.isDirectory) {
				detectCategoryFolders(externalDir.absolutePath)[category]?.let { detectedPath ->
					if (!detectedPath.equals(externalDir.absolutePath, ignoreCase = true)) {
						setCategoryLibraryPath(context, category, detectedPath)
						return File(detectedPath)
					}
				}
				return externalDir
			}
		}
		return getCategoryDir(context, category)
	}

	/**
	 * Import a file from a SAF content:// URI into the appropriate app storage directory.
	 * Returns a usable file path on success, or null on failure.
	 * If the picked document maps to a real filesystem path, it is used in place
	 * and its parent folder is remembered as an external library path.
	 */
	fun importFile(context: Context, uri: Uri, category: FileCategory): String? {
		resolveDocumentPath(context, uri)?.let { resolvedPath ->
			val resolvedFile = File(resolvedPath)
			if (resolvedFile.exists() && resolvedFile.isFile) {
				resolvedFile.parentFile?.absolutePath?.let { setCategoryLibraryPath(context, category, it) }
				Log.d(TAG, "Using external file in place: $resolvedPath")
				return resolvedPath
			}
		}
		val fileName = getFileName(context, uri) ?: "imported_file"
		val targetDir = getCategoryDir(context, category)
		if (!targetDir.exists()) targetDir.mkdirs()

		val targetFile = File(targetDir, fileName)

		// Avoid overwriting: add suffix if file exists
		val finalFile = if (targetFile.exists()) {
			val baseName = targetFile.nameWithoutExtension
			val ext = targetFile.extension
			var counter = 1
			var candidate = File(targetDir, "${baseName}_$counter.$ext")
			while (candidate.exists()) {
				counter++
				candidate = File(targetDir, "${baseName}_$counter.$ext")
			}
			candidate
		} else {
			targetFile
		}

		return try {
			val inputStream = context.contentResolver.openInputStream(uri)
			if (inputStream == null) {
				Log.e(TAG, "Failed to open input stream for URI: $uri")
				return null
			}
			inputStream.use { input ->
				finalFile.outputStream().use { output ->
					input.copyTo(output)
				}
			}
			Log.d(TAG, "Imported ${finalFile.name} to ${finalFile.absolutePath}")
			finalFile.absolutePath
		} catch (e: Exception) {
			Log.e(TAG, "Failed to import file from URI: $uri", e)
			null
		}
	}

	/**
	 * Scan a directory for files matching the given extensions.
	 */
	fun scanDirectory(dir: File, extensions: Set<String>, recursive: Boolean = false): List<AmigaFile> {
		if (!dir.exists() || !dir.isDirectory) return emptyList()

		val category = FileCategory.entries.firstOrNull { it.dirName == dir.name }
		val requestedCategory = FileCategory.entries.firstOrNull { candidate ->
			extensions.isNotEmpty() && extensions.all { it in candidate.extensions }
		}
		val files = if (recursive) {
			dir.walkTopDown()
				.filter { file -> file.isFile && file.extension.lowercase() in extensions }
				.toList()
		} else {
			dir.listFiles()
				?.filter { file -> file.isFile && file.extension.lowercase() in extensions }
				?: emptyList()
		}

		return files.map { file ->
			val fileCategory = category ?: requestedCategory ?: FileCategory.fromExtension(file.extension) ?: FileCategory.FLOPPIES
			val crc = if (fileCategory == FileCategory.ROMS) calculateCrc32(file) else null
			AmigaFile(
				path = file.absolutePath,
				name = file.name,
				extension = file.extension.lowercase(),
				size = file.length(),
				lastModified = file.lastModified(),
				category = fileCategory,
				crc32 = crc
			)
		}.sortedBy { it.name.lowercase() }
	}

	/**
	 * Scan all known directories for files of a given category.
	 * Also checks the root app storage dir for matching files (users may put files there).
	 */
	fun scanForCategory(context: Context, category: FileCategory): List<AmigaFile> {
		val results = mutableListOf<AmigaFile>()
		val seenPaths = mutableSetOf<String>()

		// Scan the category-specific directory
		val categoryDir = getCategoryDir(context, category)
		for (file in scanDirectory(categoryDir, category.extensions)) {
			if (seenPaths.add(file.path)) results.add(file)
		}

		// Also scan the root app directory for matching files
		val rootDir = File(getAppStoragePath(context))
		for (file in scanDirectory(rootDir, category.extensions)) {
			if (seenPaths.add(file.path)) results.add(file)
		}

		// For ROMs, also check whdboot/Kickstarts
		if (category == FileCategory.ROMS) {
			val kickstartsDir = File(rootDir, "whdboot/game-data/Kickstarts")
			for (file in scanDirectory(kickstartsDir, category.extensions)) {
				if (seenPaths.add(file.path)) results.add(file)
			}
		}

		getCategoryLibraryPath(context, category)?.let { configuredDir ->
			val externalDir = File(configuredDir)
			for (file in scanDirectory(externalDir, category.extensions, recursive = true)) {
				if (seenPaths.add(file.path)) results.add(file)
			}
		}

		return results.sortedBy { it.name.lowercase() }
	}

	fun ensureDirectories(context: Context) {
		val base = context.getExternalFilesDir(null) ?: return
		FileCategory.entries.forEach { category ->
			File(base, category.dirName).let { if (!it.exists()) it.mkdirs() }
		}
		File(base, "conf").let { if (!it.exists()) it.mkdirs() }
	}

	/**
	 * Extract the display name from a content:// URI.
	 * Public so callers can validate file extensions before importing.
	 */
	fun getDisplayName(context: Context, uri: Uri): String? = getFileName(context, uri)

	fun getCategoryLibraryPath(context: Context, category: FileCategory): String? {
		val value = AppStorage.openPreferences(context)
			.getString(getLibraryPathKey(category), null)
			?.trim()
		return value?.takeIf { it.isNotEmpty() }
	}

	fun setCategoryLibraryPath(context: Context, category: FileCategory, path: String?) {
		val prefs = AppStorage.openPreferences(context)
		prefs.edit().apply {
			val normalized = path?.trim().orEmpty()
			if (normalized.isEmpty()) remove(getLibraryPathKey(category))
			else putString(getLibraryPathKey(category), normalized)
		}.apply()
	}

	/**
	 * Detect category folders from a SAF tree URI using the DocumentFile API.
	 * Works without MANAGE_EXTERNAL_STORAGE. Returns a map of category → resolved
	 * filesystem path. Categories whose path cannot be resolved are omitted.
	 */
	fun detectCategoryFolders(context: Context, treeUri: Uri): Map<FileCategory, String> {
		val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyMap()
		if (!root.isDirectory) return emptyMap()

		val keywords = mapOf(
			FileCategory.ROMS to listOf("kickstart", "kick", "rom", "roms", "bios"),
			FileCategory.FLOPPIES to listOf("floppy", "floppies", "adf", "disk", "disks", "df0", "df1"),
			FileCategory.WHDLOAD_GAMES to listOf("whdload", "whd", "lha", "game", "games"),
			FileCategory.CD_IMAGES to listOf("cd", "cds", "cdrom", "cdroms", "iso", "disc", "discs"),
			FileCategory.HARD_DRIVES to listOf("hdf", "harddrive", "harddrives", "hdd")
		)

		val subdirs = root.listFiles()
			?.filter { it.isDirectory && it.name?.startsWith(".") == false }
			?: return emptyMap()

		val sortedPairs = keywords.entries
			.flatMap { (cat, kws) -> kws.map { kw -> cat to kw } }
			.sortedByDescending { it.second.length }

		val resultDocs = mutableMapOf<FileCategory, DocumentFile>()

		// Pass 1: name-based matching
		for (dir in subdirs) {
			val nameLower = dir.name?.lowercase() ?: continue
			val nameNorm = nameLower.replace(Regex("[-_ ]+"), "")
			for ((category, kw) in sortedPairs) {
				if (category !in resultDocs && (nameLower.contains(kw) || nameNorm.contains(kw))) {
					resultDocs[category] = dir
					break
				}
			}
		}

		// Pass 2: extension-based detection for unmatched categories
		for (dir in subdirs) {
			val files = dir.listFiles()?.filter { it.isFile } ?: continue
			for ((category, _) in keywords) {
				if (category in resultDocs) continue
				if (files.any { f -> f.name?.substringAfterLast('.', "")?.lowercase() in category.extensions }) {
					resultDocs[category] = dir
				}
			}
		}

		// Also check root itself for kickstarts
		if (FileCategory.ROMS !in resultDocs) {
			val rootFiles = root.listFiles()?.filter { it.isFile } ?: emptyList()
			if (rootFiles.any { f -> f.name?.substringAfterLast('.', "")?.lowercase() in FileCategory.ROMS.extensions }) {
				resultDocs[FileCategory.ROMS] = root
			}
		}

		// Resolve DocumentFiles to filesystem paths via content URI
		return resultDocs.mapNotNull { (category, docFile) ->
			val path = resolveDocumentPath(context, docFile.uri)
			if (path != null) category to path else null
		}.toMap()
	}

	/**
	 * Detect category folders from a parent directory path.
	 * Requires filesystem access to that path (works for app-owned storage and,
	 * on Android 10 with legacy storage, for external storage).
	 */
	fun detectCategoryFolders(rootPath: String): Map<FileCategory, String> {
		val root = File(rootPath)
		if (!root.isDirectory) return emptyMap()

		// Priority keyword lists per category (all lowercase)
		val keywords = mapOf(
			FileCategory.ROMS to listOf("kickstart", "kick", "rom", "roms", "bios"),
			FileCategory.FLOPPIES to listOf("floppy", "floppies", "adf", "disk", "disks", "df0", "df1"),
			FileCategory.WHDLOAD_GAMES to listOf("whdload", "whd", "lha", "game", "games"),
			FileCategory.CD_IMAGES to listOf("cd", "cds", "cdrom", "cdroms", "iso", "disc", "discs"),
			FileCategory.HARD_DRIVES to listOf("hdf", "harddrive", "harddrives", "hdd")
		)

		val subdirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyMap()
		val result = mutableMapOf<FileCategory, String>()

		// Flatten to (category, keyword) pairs sorted longest-first so that a specific
		// keyword like "cdrom" always wins over a shorter one like "rom".
		val sortedPairs = keywords.entries
			.flatMap { (cat, kws) -> kws.map { kw -> cat to kw } }
			.sortedByDescending { it.second.length }

		// Pass 1: name-based matching — longer/more-specific keywords take priority.
		// Normalize separators (hyphen, underscore, space) out of the folder name so that
		// "CD-ROMs", "CD_ROMs" and "CD ROMs" all match the "cdroms" keyword instead of
		// falling through to the shorter "rom" keyword and landing in ROMS.
		for (dir in subdirs) {
			val nameLower = dir.name.lowercase()
			val nameNorm = nameLower.replace(Regex("[-_ ]+"), "")
			for ((category, kw) in sortedPairs) {
				if (category !in result && (nameLower.contains(kw) || nameNorm.contains(kw))) {
					result[category] = dir.absolutePath
					break
				}
			}
		}

		// Pass 2: extension-based detection for unmatched categories
		for (dir in subdirs) {
			val files = dir.listFiles()?.filter { it.isFile } ?: continue
			for ((category, _) in keywords) {
				if (category in result) continue
				if (files.any { it.extension.lowercase() in category.extensions }) {
					result[category] = dir.absolutePath
				}
			}
		}

		// Also check root itself for kickstarts (common pattern: all files in one folder)
		if (FileCategory.ROMS !in result) {
			val rootFiles = root.listFiles()?.filter { it.isFile } ?: emptyList()
			if (rootFiles.any { it.extension.lowercase() in FileCategory.ROMS.extensions }) {
				result[FileCategory.ROMS] = rootPath
			}
		}

		return result
	}

	fun resolveDirectoryPath(context: Context, uri: Uri): String? {
		val resolved = resolveDocumentPath(context, uri) ?: return null
		return resolved.takeIf { File(it).exists() && File(it).isDirectory }
	}

	fun isAppOwnedPath(context: Context, path: String): Boolean {
		val appRoot = getAppStoragePath(context).trim()
		return appRoot.isNotEmpty() && path.startsWith(appRoot, ignoreCase = true)
	}

	private fun getFileName(context: Context, uri: Uri): String? {
		// Try ContentResolver query first (works for most SAF URIs)
		context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
			val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
			if (nameIndex >= 0 && cursor.moveToFirst()) {
				val name = cursor.getString(nameIndex)
				if (!name.isNullOrBlank()) return name
			}
		}
		// Fallback: extract from URI path
		return uri.lastPathSegment?.substringAfterLast('/')
	}

	private fun getLibraryPathKey(category: FileCategory): String = "library_path_${category.dirName}"

	private fun resolveDocumentPath(context: Context, uri: Uri): String? {
		if (uri.scheme.equals("file", ignoreCase = true)) {
			return uri.path?.let { File(it).absolutePath }
		}
		if (!uri.scheme.equals("content", ignoreCase = true)) return null

		val docId = resolveExternalStorageDocId(uri) ?: return null

		if (uri.authority != "com.android.externalstorage.documents") return null

		val parts = docId.split(':', limit = 2)
		if (parts.isEmpty()) return null
		val volume = parts[0]
		val relative = parts.getOrNull(1).orEmpty()
		val base = if (volume.equals("primary", ignoreCase = true)) {
			Environment.getExternalStorageDirectory().absolutePath
		} else {
			"/storage/$volume"
		}
		return if (relative.isBlank()) base else File(base, relative).absolutePath
	}

	private fun resolveExternalStorageDocId(uri: Uri): String? {
		// For child folders discovered under a picked SAF tree, the URI usually contains
		// both the original tree id and the concrete document id. Prefer the document id
		// so category folders resolve to their actual subdirectory instead of the tree root.
		return try {
			DocumentsContract.getDocumentId(uri)
		} catch (_: IllegalArgumentException) {
			try {
				DocumentsContract.getTreeDocumentId(uri)
			} catch (_: IllegalArgumentException) {
				null
			}
		}
	}

	private fun calculateCrc32(file: File): Long? {
		return try {
			val crc = CRC32()
			file.inputStream().use { input ->
				val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
				while (true) {
					val read = input.read(buffer)
					if (read <= 0) break
					crc.update(buffer, 0, read)
				}
			}
			crc.value and 0xffffffffL
		} catch (e: Exception) {
			Log.w(TAG, "Failed to calculate CRC32 for ${file.absolutePath}", e)
			null
		}
	}
}

