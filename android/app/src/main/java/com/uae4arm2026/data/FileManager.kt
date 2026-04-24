package com.uae4arm2026.data

import android.content.Intent
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
import java.util.zip.ZipInputStream
import java.io.File

object FileManager {

	private const val TAG = "Uae4Arm-FileManager"

	fun getAppStoragePath(context: Context): String {
		return context.getExternalFilesDir(null)?.absolutePath ?: ""
	}

	fun getCategoryDir(context: Context, category: FileCategory): File {
		return File(getAppStoragePath(context), category.dirName)
	}

	fun getScannableExtensions(category: FileCategory): Set<String> {
		// All categories support their native extensions.
		// ZIP is added as a container fallback, but we will handle it strictly in scanning.
		return category.extensions + "zip"
	}

	fun acceptsImportExtension(category: FileCategory, extension: String): Boolean {
		return extension.lowercase() in getScannableExtensions(category)
	}

	fun getEffectiveCategoryDir(context: Context, category: FileCategory): File {
		val configured = getCategoryLibraryPath(context, category)
		if (!configured.isNullOrBlank()) {
			val externalDir = File(configured)
			if (externalDir.isDirectory) {
				return externalDir
			}
		}
		return getCategoryDir(context, category)
	}

	/**
	 * Import a file from a SAF content:// URI into the appropriate app storage directory.
	 */
	fun importFile(context: Context, uri: Uri, category: FileCategory): String? {
		resolveDocumentPath(context, uri)?.let { resolvedPath ->
			val resolvedFile = File(resolvedPath)
			if (resolvedFile.exists() && resolvedFile.isFile && isAppOwnedPath(context, resolvedPath)) {
				resolvedFile.parentFile?.absolutePath?.let { setCategoryLibraryPath(context, category, it) }
				Log.d(TAG, "Using app-owned file in place: $resolvedPath")
				return resolvedPath
			}
		}
		val fileName = getFileName(context, uri) ?: "imported_file"
		val targetDir = getCategoryDir(context, category)
		if (!targetDir.exists()) targetDir.mkdirs()

		val targetFile = File(targetDir, fileName)
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
	fun scanDirectory(
		dir: File,
		extensions: Set<String>,
		recursive: Boolean = false,
		forcedCategory: FileCategory? = null
	): List<AmigaFile> {
		if (!dir.exists() || !dir.isDirectory) return emptyList()

		// Strong hint: If the directory name matches a category dirName, use that category.
		val dirCategory = FileCategory.entries.firstOrNull { it.dirName.equals(dir.name, ignoreCase = true) }
		
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
			// Determine category:
			// 1. Forced (passed by scanForCategory)
			// 2. Directory hint (if in app-owned subfolder)
			// 3. Extension-based
			// 4. Fallback to Floppies
			val fileCategory = forcedCategory 
				?: dirCategory 
				?: FileCategory.fromExtension(file.extension) 
				?: requestedCategory 
				?: FileCategory.FLOPPIES

			val crc = if (fileCategory == FileCategory.ROMS) calculateRomCrc32(file) else null
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
	 */
	fun scanForCategory(context: Context, category: FileCategory): List<AmigaFile> {
		try {
			Log.d(TAG, "Scanning for category: ${category.name}")
			val results = mutableListOf<AmigaFile>()
			val seenPaths = mutableSetOf<String>()
			val extensions = getScannableExtensions(category)

			// 1. Scan the category-specific directory (recursive for hdf/lha subfolders etc)
			val categoryDir = getCategoryDir(context, category)
			Log.d(TAG, "Scanning category dir: ${categoryDir.absolutePath}")
			for (file in scanDirectory(categoryDir, extensions, recursive = true, forcedCategory = category)) {
				if (seenPaths.add(file.path)) results.add(file)
			}

			// 2. Also scan the root app directory for matching files (NON-RECURSIVE)
			// This allows files in the root to be found but avoids cross-category leakage.
			val rootDir = File(getAppStoragePath(context))
			Log.d(TAG, "Scanning root app dir: ${rootDir.absolutePath}")
			for (file in scanDirectory(rootDir, extensions, recursive = false, forcedCategory = category)) {
				if (seenPaths.add(file.path)) results.add(file)
			}

			// 3. whdboot Kickstarts locations
			if (category == FileCategory.ROMS) {
				val kickstartDirs = listOf(
					File(rootDir, "whdboot/game-data/Kickstarts"),
					File(rootDir, "whdboot/save-data/Kickstarts")
				)
				for (dir in kickstartDirs) {
					if (!dir.exists()) continue
					Log.d(TAG, "Scanning kickstart dir: ${dir.absolutePath}")
					for (file in scanDirectory(dir, extensions, recursive = true, forcedCategory = category)) {
						if (seenPaths.add(file.path)) results.add(file)
					}
				}
			}

			val configuredDir = getCategoryLibraryPath(context, category)
			if (!configuredDir.isNullOrBlank()) {
				val externalDir = File(configuredDir)
				val uriString = getCategoryLibraryUri(context, category)

				Log.d(TAG, "Scanning configured external dir: $configuredDir (URI: $uriString)")
				if (!uriString.isNullOrBlank() && !isAppOwnedPath(context, configuredDir)) {
					// Use recursive SAF scan
					for (file in scanDocumentDirectoryRecursive(context, Uri.parse(uriString), extensions, category)) {
						if (seenPaths.add(file.path)) results.add(file)
					}
				} else if (isAppOwnedPath(context, configuredDir)) {
					for (file in scanDirectory(externalDir, extensions, recursive = true, forcedCategory = category)) {
						if (seenPaths.add(file.path)) results.add(file)
					}
				}
			}

			Log.d(TAG, "Found ${results.size} files for ${category.name}")
			return results.sortedBy { it.name.lowercase() }
		} catch (e: Exception) {
			Log.e(TAG, "Error scanning for category ${category.name}", e)
			return emptyList()
		}
	}

	private fun scanDocumentDirectoryRecursive(
		context: Context,
		treeUri: Uri,
		extensions: Set<String>,
		category: FileCategory
	): List<AmigaFile> {
		val results = mutableListOf<AmigaFile>()
		val rootId = try {
			DocumentsContract.getTreeDocumentId(treeUri)
		} catch (_: Exception) {
			DocumentsContract.getDocumentId(treeUri)
		}
		
		scanDocumentDirectoryInternal(context, treeUri, rootId, rootId, extensions, category, results)
		return results
	}

	private fun scanDocumentDirectoryInternal(
		context: Context,
		treeUri: Uri,
		rootId: String,
		parentId: String,
		extensions: Set<String>,
		category: FileCategory,
		results: MutableList<AmigaFile>
	) {
		val resolver = context.contentResolver
		val childrenUri = try {
			DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
		} catch (e: Exception) {
			Log.w(TAG, "Failed to build children URI for $parentId", e)
			return
		}

		val projection = arrayOf(
			DocumentsContract.Document.COLUMN_DOCUMENT_ID,
			DocumentsContract.Document.COLUMN_DISPLAY_NAME,
			DocumentsContract.Document.COLUMN_SIZE,
			DocumentsContract.Document.COLUMN_LAST_MODIFIED,
			DocumentsContract.Document.COLUMN_MIME_TYPE
		)

		try {
			resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
				val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
				val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
				val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
				val modIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
				val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

				while (cursor.moveToNext()) {
					val docId = cursor.getString(idIndex)
					val mime = cursor.getString(mimeIndex)
					val name = cursor.getString(nameIndex) ?: "unknown"

					if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
						if (!name.startsWith(".")) {
							// Skip subdirectories that match OTHER categories
							val otherCat = FileCategory.entries.firstOrNull { 
								it != category && it.dirName.equals(name, ignoreCase = true) 
							}
							if (otherCat == null) {
								scanDocumentDirectoryInternal(context, treeUri, rootId, docId, extensions, category, results)
							}
						}
						continue
					}

					// Only include the file if:
					// 1. We are in the root of the designated library path
					// 2. OR we are inside a folder that matches this category's name
					val currentFolder = parentId.substringAfterLast('/', "")
					val isRoot = parentId == rootId
					val inMatchingFolder = category.dirName.equals(currentFolder, ignoreCase = true)
					
					if (!isRoot && !inMatchingFolder) {
						// We are in some other subfolder, skip
						continue
					}

					val ext = name.substringAfterLast('.', "").lowercase()
					if (ext !in extensions) continue
					
					// ZIP strictness: only if specifically in the matching folder
					if (ext == "zip" && category != FileCategory.FLOPPIES && category != FileCategory.WHDLOAD_GAMES) {
						if (!inMatchingFolder) continue
					}

					val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
					results.add(
						AmigaFile(
							path = docUri.toString(),
							name = name,
							extension = ext,
							size = cursor.getLong(sizeIndex),
							lastModified = cursor.getLong(modIndex),
							category = category,
							crc32 = null
						)
					)
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "Error querying SAF children for $parentId", e)
		}
	}

	fun ensureDirectories(context: Context) {
		val base = context.getExternalFilesDir(null) ?: return
		FileCategory.entries.forEach { category ->
			File(base, category.dirName).let { if (!it.exists()) it.mkdirs() }
		}
		File(base, "conf").let { if (!it.exists()) it.mkdirs() }
	}

	fun getDisplayName(context: Context, uri: Uri): String? = getFileName(context, uri)

	fun getCategoryLibraryPath(context: Context, category: FileCategory): String? {
		val value = AppStorage.openPreferences(context)
			.getString(getLibraryPathKey(category), null)
			?.trim()
		return value?.takeIf { it.isNotEmpty() }
	}

	fun setCategoryLibraryPath(context: Context, category: FileCategory, path: String?, treeUriString: String? = null) {
		val prefs = AppStorage.openPreferences(context)
		prefs.edit().apply {
			val normalized = path?.trim().orEmpty()
			if (normalized.isEmpty()) {
				remove(getLibraryPathKey(category))
				remove(getLibraryUriKey(category))
			} else {
				putString(getLibraryPathKey(category), normalized)
				if (treeUriString != null) {
					putString(getLibraryUriKey(category), treeUriString)
				}
			}
		}.apply()
	}

	fun persistDirectoryAccess(context: Context, uri: Uri) {
		if (!uri.scheme.equals("content", ignoreCase = true)) return
		if (!DocumentsContract.isTreeUri(uri)) return

		val resolver = context.contentResolver
		try {
			resolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			)
		} catch (_: SecurityException) {
			try {
				resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
			} catch (e: SecurityException) {
				Log.w(TAG, "Failed to persist directory access for $uri", e)
			}
		}
	}

	fun detectCategoryFolders(context: Context, treeUri: Uri): Map<FileCategory, Pair<String, Uri>> {
		val rootPath = resolveDocumentPath(context, treeUri) ?: return emptyMap()
		val detectedPaths = detectCategoryFolders(rootPath)
		
		return detectedPaths.mapNotNull { (category, path) ->
			val docId = try {
				val extRoot = Environment.getExternalStorageDirectory().absolutePath
				if (path.startsWith(extRoot)) {
					"primary:" + path.removePrefix(extRoot).trimStart('/')
				} else {
					val m = Regex("^/storage/([^/]+)(/.*)?$").find(path) ?: return@mapNotNull null
					val vol = m.groupValues[1]
					val rel = m.groupValues[2].trimStart('/')
					"$vol:$rel"
				}
			} catch (_: Exception) { return@mapNotNull null }
			
			val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
			category to (path to docUri)
		}.toMap()
	}

	fun detectCategoryFolders(rootPath: String): Map<FileCategory, String> {
		val root = File(rootPath)
		if (!root.isDirectory) return emptyMap()

		val keywords = mapOf(
			FileCategory.ROMS to listOf("kickstart", "kick", "rom", "roms", "bios"),
			FileCategory.FLOPPIES to listOf("floppy", "floppies", "adf", "disk", "disks", "df0", "df1"),
			FileCategory.WHDLOAD_GAMES to listOf("whdload", "whd", "lha", "game", "games"),
			FileCategory.CD_IMAGES to listOf("cd", "cds", "cdrom", "cdroms", "iso", "disc", "discs"),
			FileCategory.HARD_DRIVES to listOf("hdf", "harddrive", "harddrives", "hdd")
		)

		val subdirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyMap()
		val result = mutableMapOf<FileCategory, String>()

		val sortedPairs = keywords.entries
			.flatMap { (cat, kws) -> kws.map { kw -> cat to kw } }
			.sortedByDescending { it.second.length }

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

		for (dir in subdirs) {
			val files = dir.listFiles()?.filter { it.isFile } ?: continue
			for ((category, _) in keywords) {
				if (category in result) continue
				if (files.any { it.extension.lowercase() in category.extensions }) {
					result[category] = dir.absolutePath
				}
			}
		}

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
		if (appRoot.isEmpty()) return false
		if (path.startsWith("/proc/self/fd/", ignoreCase = true)) return true
		return path.startsWith(appRoot, ignoreCase = true)
	}

	private fun getFileName(context: Context, uri: Uri): String? {
		context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
			val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
			if (nameIndex >= 0 && cursor.moveToFirst()) {
				val name = cursor.getString(nameIndex)
				if (!name.isNullOrBlank()) return name
			}
		}
		return uri.lastPathSegment?.substringAfterLast('/')
	}

	fun getCategoryLibraryUri(context: Context, category: FileCategory): String? {
		return AppStorage.openPreferences(context)
			.getString(getLibraryUriKey(category), null)
	}

	fun setCategoryLibraryUri(context: Context, category: FileCategory, uri: String?) {
		AppStorage.openPreferences(context).edit().apply {
			if (uri.isNullOrBlank()) remove(getLibraryUriKey(category))
			else putString(getLibraryUriKey(category), uri)
		}.apply()
	}

	private fun getLibraryPathKey(category: FileCategory): String = "library_path_${category.dirName}"
	private fun getLibraryUriKey(category: FileCategory): String = "library_uri_${category.dirName}"

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

	private fun calculateRomCrc32(file: File): Long? {
		return if (file.extension.equals("zip", ignoreCase = true)) {
			calculateZipEntryCrc32(file, FileCategory.ROMS.extensions)
		} else {
			calculateCrc32(file)
		}
	}

	private fun calculateZipEntryCrc32(file: File, allowedExtensions: Set<String>): Long? {
		return try {
			var entryCount = 0
			var matchedCrc: Long? = null

			ZipInputStream(file.inputStream().buffered()).use { zip ->
				while (true) {
					val entry = zip.nextEntry ?: break
					if (entry.isDirectory) continue

					val ext = entry.name.substringAfterLast('.', "").lowercase()
					if (ext !in allowedExtensions) continue

					entryCount++
					if (entryCount > 1) {
						return null
					}

					val crc = CRC32()
					val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
					while (true) {
						val read = zip.read(buffer)
						if (read <= 0) break
						crc.update(buffer, 0, read)
					}
					matchedCrc = crc.value and 0xffffffffL
				}
			}

			matchedCrc
		} catch (e: Exception) {
			Log.w(TAG, "Failed to calculate ROM CRC32 inside zip ${file.absolutePath}", e)
			null
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

	fun findContentUriForPath(context: Context, path: String): Uri? {
		if (path.isBlank() || path.startsWith("content://") || path.startsWith("/proc/")) return null

		var bestMatchCategory: FileCategory? = null
		var bestMatchLength = -1

		FileCategory.entries.forEach { category ->
			val libPath = getCategoryLibraryPath(context, category) ?: return@forEach
			val treeUriString = getCategoryLibraryUri(context, category) ?: return@forEach

			if (path.startsWith(libPath, ignoreCase = true)) {
				if (libPath.length > bestMatchLength) {
					bestMatchLength = libPath.length
					bestMatchCategory = category
				}
			}
		}

		if (bestMatchCategory != null) {
			val category = bestMatchCategory!!
			val libPath = getCategoryLibraryPath(context, category)!!
			val treeUriString = getCategoryLibraryUri(context, category)!!
			
			val treeUri = Uri.parse(treeUriString)
			val rootId = DocumentsContract.getTreeDocumentId(treeUri)
			
			val libFile = File(libPath)
			val targetFile = File(path)
			val libAbs = libFile.absolutePath
			val targetAbs = targetFile.absolutePath
			
			val relativePath = if (libAbs.equals(targetAbs, ignoreCase = true)) "" 
							   else if (targetAbs.startsWith(libAbs, ignoreCase = true) && targetAbs.length > libAbs.length)
								   targetAbs.substring(libAbs.length).removePrefix("/").removePrefix("\\")
							   else null

			if (relativePath == null) return null
			if (relativePath.isEmpty()) return treeUri

			if (treeUri.authority == "com.android.externalstorage.documents") {
				val parts = rootId.split(":")
				if (parts.size >= 2) {
					val newId = "${parts[0]}:${parts[1]}/$relativePath"
					return DocumentsContract.buildDocumentUriUsingTree(treeUri, newId)
				}
			}

			val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
			var current: DocumentFile? = root
			val parts = relativePath.split(File.separator, "/")
			for (part in parts) {
				if (part.isEmpty()) continue
				current = current?.findFile(part)
				if (current == null) break
			}
			if (current != null) return current.uri
		}
		return null
	}
}
