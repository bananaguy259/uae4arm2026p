package com.uae4arm2026.data

import android.content.Context
import com.uae4arm2026.data.model.FileCategory
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object ArchiveRepository {
	private const val CONNECT_TIMEOUT_MS = 15_000
	private const val READ_TIMEOUT_MS = 30_000
	private const val CACHE_DIR_NAME = "archive-listings"
	private const val CACHE_VERSION = 3
	private const val PROGRESS_UPDATE_INTERVAL_MS = 1_000L
	private const val PROGRESS_UPDATE_STEP_BYTES = 1_024 * 1_024L
	private val cachedCollections = mutableMapOf<String, ArchiveCollectionListing>()

	private val kickstartExtensions = setOf("rom", "bin", "zip")
	private val floppyExtensions = setOf("adf", "adz", "zip", "dms", "ipf", "gz")

	fun getCachedCollection(context: Context, collection: ArchiveCollection): ArchiveCollectionListing? {
		cachedCollections[collection.id]?.let { return it }
		return readCachedCollection(context, collection)?.also {
			cachedCollections[collection.id] = it
		}
	}

	fun loadCollection(context: Context, collection: ArchiveCollection): ArchiveCollectionListing {
		getCachedCollection(context, collection)?.let { return it }
		val listing = fetchCollection(collection)
		writeCachedCollection(context, listing)
		return listing.also { cachedCollections[collection.id] = it }
	}

	fun refreshCollection(context: Context, collection: ArchiveCollection): ArchiveCollectionListing {
		cachedCollections.remove(collection.id)
		cacheFile(context, collection).delete()
		val listing = fetchCollection(collection)
		writeCachedCollection(context, listing)
		return listing.also { cachedCollections[collection.id] = it }
	}

	private fun fetchCollection(collection: ArchiveCollection): ArchiveCollectionListing {
		val response = openConnection(ArchiveCatalog.metadataUrl(collection)).useConnection { connection ->
			connection.inputStream.bufferedReader().use { it.readText() }
		}
		val root = JSONObject(response)
		val archiveTitle = root.optJSONObject("metadata")
			?.optString("title")
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?: collection.fallbackTitle

		val allowedExtensions = when (collection.category) {
			FileCategory.ROMS -> kickstartExtensions
			FileCategory.FLOPPIES -> floppyExtensions
			else -> collection.category.extensions
		}

		val files = buildList {
			val filesArray = root.optJSONArray("files") ?: return@buildList
			for (index in 0 until filesArray.length()) {
				val fileJson = filesArray.optJSONObject(index) ?: continue
				val fileName = fileJson.optString("name").trim()
				if (fileName.isEmpty()) continue
				val source = fileJson.optString("source").trim()
				if (source.isNotEmpty() && source != "original") continue

				val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
				if (ext !in allowedExtensions) continue

				val sizeBytes = fileJson.optString("size")
					.toLongOrNull()
					?: fileJson.optLong("size").takeIf { it > 0L }

				add(
					ArchiveDownloadItem(
						collectionId = collection.id,
						fileName = fileName,
						sizeBytes = sizeBytes,
						downloadUrl = ArchiveCatalog.fileDownloadUrl(collection, fileName),
						category = collection.category
					)
				)
			}
		}.sortedBy { it.fileName.lowercase(Locale.ROOT) }

		return ArchiveCollectionListing(
			collection = collection,
			archiveTitle = archiveTitle,
			files = files
		)
	}

	fun downloadItem(
		context: Context,
		item: ArchiveDownloadItem,
		onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
	): File {
		val targetDir = FileManager.getEffectiveCategoryDir(context, item.category).also { it.mkdirs() }
		val finalFile = createUniqueTargetFile(targetDir, item.fileName)
		val tempFile = File(finalFile.absolutePath + ".part")

		if (tempFile.exists()) {
			tempFile.delete()
		}

		try {
			var finalDownloadedBytes = 0L
			var finalTotalBytes: Long? = item.sizeBytes
			openConnection(item.downloadUrl).useConnection { connection ->
				val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: item.sizeBytes
				var downloadedBytes = 0L
				var lastReportedBytes = 0L
				var lastReportAtMs = 0L
				finalTotalBytes = totalBytes
				onProgress(0L, totalBytes)

				connection.inputStream.buffered().use { input ->
					tempFile.outputStream().buffered().use { output ->
						val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
						while (true) {
							val read = input.read(buffer)
							if (read <= 0) break
							output.write(buffer, 0, read)
							downloadedBytes += read
							val nowMs = System.currentTimeMillis()
							val shouldReport = downloadedBytes == totalBytes ||
								downloadedBytes - lastReportedBytes >= PROGRESS_UPDATE_STEP_BYTES ||
								nowMs - lastReportAtMs >= PROGRESS_UPDATE_INTERVAL_MS
							if (shouldReport) {
								lastReportedBytes = downloadedBytes
								lastReportAtMs = nowMs
								onProgress(downloadedBytes, totalBytes)
							}
						}
						finalDownloadedBytes = downloadedBytes
					}
				}
			}

			if (!tempFile.renameTo(finalFile)) {
				tempFile.copyTo(finalFile, overwrite = true)
				tempFile.delete()
			}

			onProgress(finalDownloadedBytes, finalTotalBytes)
			return finalFile
		} catch (e: Exception) {
			tempFile.delete()
			throw e
		}
	}

	private fun readCachedCollection(
		context: Context,
		collection: ArchiveCollection
	): ArchiveCollectionListing? {
		val file = cacheFile(context, collection)
		if (!file.exists()) return null

		return try {
			val root = JSONObject(file.readText())
			if (root.optInt("cacheVersion", 0) != CACHE_VERSION) {
				file.delete()
				return null
			}
			val cachedIdentifier = root.optString("identifier").trim()
			if (cachedIdentifier.isNotEmpty() && cachedIdentifier != collection.identifier) {
				file.delete()
				return null
			}
			val cachedFiles = buildList {
				val filesArray = root.optJSONArray("files") ?: JSONArray()
				for (index in 0 until filesArray.length()) {
					val fileJson = filesArray.optJSONObject(index) ?: continue
					val fileName = fileJson.optString("fileName").trim()
					if (fileName.isEmpty()) continue

					add(
						ArchiveDownloadItem(
							collectionId = collection.id,
							fileName = fileName,
							sizeBytes = fileJson.optLong("sizeBytes").takeIf { it > 0L },
							downloadUrl = ArchiveCatalog.fileDownloadUrl(collection, fileName),
							category = collection.category
						)
					)
				}
			}

			ArchiveCollectionListing(
				collection = collection,
				archiveTitle = root.optString("archiveTitle").ifBlank { collection.fallbackTitle },
				files = cachedFiles
			)
		} catch (_: Exception) {
			file.delete()
			null
		}
	}

	private fun writeCachedCollection(
		context: Context,
		listing: ArchiveCollectionListing
	) {
		val file = cacheFile(context, listing.collection)
		file.parentFile?.mkdirs()

		val root = JSONObject().apply {
			put("cacheVersion", CACHE_VERSION)
			put("collectionId", listing.collection.id)
			put("identifier", listing.collection.identifier)
			put("archiveTitle", listing.archiveTitle)
			put(
				"files",
				JSONArray().apply {
					listing.files.forEach { item ->
						put(
							JSONObject().apply {
								put("fileName", item.fileName)
								item.sizeBytes?.let { put("sizeBytes", it) }
								put("downloadUrl", item.downloadUrl)
							}
						)
					}
				}
			)
		}

		file.writeText(root.toString())
	}

	private fun cacheFile(context: Context, collection: ArchiveCollection): File {
		return File(File(context.cacheDir, CACHE_DIR_NAME), "${collection.id}.json")
	}

	private fun createUniqueTargetFile(targetDir: File, fileName: String): File {
		val initial = File(targetDir, fileName)
		if (!initial.exists()) return initial

		val baseName = initial.nameWithoutExtension
		val extension = initial.extension
		var counter = 1

		while (true) {
			val candidateName = if (extension.isBlank()) {
				"${baseName}_$counter"
			} else {
				"${baseName}_$counter.$extension"
			}
			val candidate = File(targetDir, candidateName)
			if (!candidate.exists()) return candidate
			counter++
		}
	}

	private fun openConnection(url: String): HttpURLConnection {
		return (URL(url).openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			instanceFollowRedirects = true
			connectTimeout = CONNECT_TIMEOUT_MS
			readTimeout = READ_TIMEOUT_MS
			setRequestProperty("Accept", "application/json,text/plain,*/*")
		}
	}

	private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
		return try {
			connect()
			if (responseCode >= 400) {
				val body = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
				throw IOException("HTTP $responseCode ${responseMessage.orEmpty()} $body".trim())
			}
			block(this)
		} finally {
			disconnect()
		}
	}
}
