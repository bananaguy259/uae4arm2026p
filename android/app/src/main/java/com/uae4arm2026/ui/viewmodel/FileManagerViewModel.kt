package com.uae4arm2026.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uae4arm2026.data.ArchiveCollection
import com.uae4arm2026.data.ArchiveCollectionListing
import com.uae4arm2026.data.ArchiveDownloadProgress
import com.uae4arm2026.data.ArchiveDownloadItem
import com.uae4arm2026.data.ArchiveRepository
import com.uae4arm2026.R
import com.uae4arm2026.data.FileManager
import com.uae4arm2026.data.FileRepository
import com.uae4arm2026.data.model.AmigaFile
import com.uae4arm2026.data.model.FileCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArchiveGameGroupUi(
	val title: String,
	val items: List<ArchiveDownloadItem>,
	val diskCount: Int
)

data class RemotePreparationProgress(
	val processedItems: Int,
	val totalItems: Int
) {
	val fractionComplete: Float
		get() = if (totalItems <= 0) 0f else processedItems.toFloat() / totalItems.toFloat()
}

private data class CachedGroupedRemoteGames(
	val signature: Int,
	val groups: List<ArchiveGameGroupUi>
)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {
	private val maxDownloadedKeyChecks = 250
	private val groupProgressStep = 100

	private val repository = FileRepository.getInstance(application)

	val roms: StateFlow<List<AmigaFile>> = repository.roms
	val floppies: StateFlow<List<AmigaFile>> = repository.floppies
	val hardDrives: StateFlow<List<AmigaFile>> = repository.hardDrives
	val cdImages: StateFlow<List<AmigaFile>> = repository.cdImages
	val whdloadGames: StateFlow<List<AmigaFile>> = repository.whdloadGames
	val isScanning: StateFlow<Boolean> = repository.isScanning

	private val _importResult = MutableStateFlow<String?>(null)
	val importResult: StateFlow<String?> = _importResult.asStateFlow()

	private val _isImporting = MutableStateFlow(false)
	val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

	private val _remoteListing = MutableStateFlow<ArchiveCollectionListing?>(null)
	val remoteListing: StateFlow<ArchiveCollectionListing?> = _remoteListing.asStateFlow()

	private val _isLoadingRemote = MutableStateFlow(false)
	val isLoadingRemote: StateFlow<Boolean> = _isLoadingRemote.asStateFlow()

	private val _remoteError = MutableStateFlow<String?>(null)
	val remoteError: StateFlow<String?> = _remoteError.asStateFlow()

	private val _activeDownload = MutableStateFlow<ArchiveDownloadProgress?>(null)
	val activeDownload: StateFlow<ArchiveDownloadProgress?> = _activeDownload.asStateFlow()

	private val _downloadedArchiveKeys = MutableStateFlow<Set<String>>(emptySet())
	val downloadedArchiveKeys: StateFlow<Set<String>> = _downloadedArchiveKeys.asStateFlow()

	private val _groupedRemoteGames = MutableStateFlow<List<ArchiveGameGroupUi>>(emptyList())
	val groupedRemoteGames: StateFlow<List<ArchiveGameGroupUi>> = _groupedRemoteGames.asStateFlow()

	private val _groupedRemoteCollectionId = MutableStateFlow<String?>(null)
	val groupedRemoteCollectionId: StateFlow<String?> = _groupedRemoteCollectionId.asStateFlow()

	private val _isPreparingRemote = MutableStateFlow(false)
	val isPreparingRemote: StateFlow<Boolean> = _isPreparingRemote.asStateFlow()

	private val _remotePreparationProgress = MutableStateFlow<RemotePreparationProgress?>(null)
	val remotePreparationProgress: StateFlow<RemotePreparationProgress?> = _remotePreparationProgress.asStateFlow()

	private var importJob: Job? = null
	private var remoteLoadJob: Job? = null
	private var remotePrepareJob: Job? = null
	private var activeRemoteCollectionId: String? = null
	private val cachedGroupedGames = mutableMapOf<String, CachedGroupedRemoteGames>()
	@Volatile private var hasPrimedAdfGroups = false

	init {
		rescan()
	}

	fun rescan() {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				repository.rescan()
			} catch (e: Exception) {
				Log.e(TAG, "Failed to rescan files", e)
			}
		}
	}

	fun importFiles(uris: List<Uri>, category: FileCategory) {
		if (uris.isEmpty()) return
		importJob?.cancel()
		importJob = viewModelScope.launch(Dispatchers.IO) {
			_isImporting.value = true
			try {
				val app = getApplication<Application>()
				var successCount = 0
				val totalCount = uris.size

				for (uri in uris) {
					// Validate file extension before importing
					val fileName = FileManager.getDisplayName(app, uri)
					if (fileName != null) {
						val ext = fileName.substringAfterLast('.', "").lowercase()
						if (ext.isNotEmpty() && !FileManager.acceptsImportExtension(category, ext)) {
							continue
						}
					}

					val result = FileManager.importFile(app, uri, category)
					if (result != null) {
						successCount++
					}
				}

				_importResult.value = if (totalCount == 1) {
					if (successCount == 1) {
						app.getString(R.string.msg_imported_successfully)
					} else {
						app.getString(R.string.msg_import_failed)
					}
				} else {
					app.getString(R.string.msg_imported_multiple, successCount, totalCount)
				}

				if (successCount > 0) {
					repository.rescanCategory(category)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Failed to import files", e)
				_importResult.value = getApplication<Application>().getString(R.string.msg_import_failed)
			} finally {
				_isImporting.value = false
			}
		}
	}

	fun deleteFile(file: AmigaFile) {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val app = getApplication<Application>()
				if (!FileManager.isAppOwnedPath(app, file.path)) {
					_importResult.value = "External library files are used in place and can't be deleted from here."
					return@launch
				}
				val deleted = java.io.File(file.path).delete()
				if (deleted) {
					_importResult.value = app.getString(R.string.msg_deleted_file, file.name)
					repository.rescanCategory(file.category)
				} else {
					_importResult.value = app.getString(R.string.msg_failed_delete_file, file.name)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Failed to delete file: ${file.path}", e)
				_importResult.value = getApplication<Application>().getString(R.string.msg_failed_delete_file, file.name)
			}
		}
	}

	fun clearImportResult() {
		_importResult.value = null
	}

	fun getStoragePath(): String {
		return FileManager.getAppStoragePath(getApplication())
	}

	fun getCategoryLibraryPath(category: FileCategory): String? {
		return FileManager.getCategoryLibraryPath(getApplication(), category)
	}

	fun setCategoryLibraryPath(category: FileCategory, uri: Uri?) {
		if (uri == null) return
		viewModelScope.launch(Dispatchers.IO) {
			val app = getApplication<Application>()
			FileManager.persistDirectoryAccess(app, uri)
			val detected = FileManager.detectCategoryFolders(app, uri)
			if (detected.isNotEmpty()) {
				detected.forEach { (detectedCategory, path) ->
					FileManager.setCategoryLibraryPath(app, detectedCategory, path, uri.toString())
				}
				repository.rescan()
				_importResult.value = "Mapped ${detected.size} library folders from selected parent"
				return@launch
			}
			val resolved = FileManager.resolveDirectoryPath(app, uri)
			if (resolved == null) {
				_importResult.value = "Couldn't use that folder. Pick a normal device storage folder."
				return@launch
			}
			FileManager.setCategoryLibraryPath(app, category, resolved, uri.toString())
			repository.rescanCategory(category)
			_importResult.value = "Using $resolved"
		}
	}

	fun clearCategoryLibraryPath(category: FileCategory) {
		viewModelScope.launch(Dispatchers.IO) {
			val app = getApplication<Application>()
			FileManager.setCategoryLibraryPath(app, category, null)
			repository.rescanCategory(category)
			_importResult.value = "Library folder cleared for ${category.displayName}"
		}
	}

	fun clearAllCategoryLibraryPaths() {
		viewModelScope.launch(Dispatchers.IO) {
			val app = getApplication<Application>()
			FileCategory.entries.forEach { category ->
				FileManager.setCategoryLibraryPath(app, category, null)
			}
			repository.rescan()
			_importResult.value = "Library parent folder reset"
		}
	}

	fun primeAdfGroupsOnFirstDownloadsVisit() {
		if (hasPrimedAdfGroups) return
		hasPrimedAdfGroups = true

		val collection = com.uae4arm2026.data.ArchiveCatalog.adfCollections.firstOrNull() ?: return
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val app = getApplication<Application>()
				val listing = ArchiveRepository.getCachedCollection(app, collection)
					?: ArchiveRepository.loadCollection(app, collection)
				val signature = listing.files.contentSignature()
				val existing = cachedGroupedGames[collection.id]
				if (existing?.signature == signature) return@launch

				val groups = buildGroupedGames(listing.files)
				cachedGroupedGames[collection.id] = CachedGroupedRemoteGames(
					signature = signature,
					groups = groups
				)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.w(TAG, "ADF pre-sync failed on first downloads visit", e)
			}
		}
	}

	fun selectArchiveCollection(collection: ArchiveCollection, forceRefresh: Boolean = false) {
		activeRemoteCollectionId = collection.id
		val app = getApplication<Application>()
		if (forceRefresh) {
			cachedGroupedGames.remove(collection.id)
		}
		if (collection.category != FileCategory.FLOPPIES) {
			remotePrepareJob?.cancel()
			_groupedRemoteGames.value = emptyList()
			_groupedRemoteCollectionId.value = null
			_isPreparingRemote.value = false
			_remotePreparationProgress.value = null
		}
		if (!forceRefresh) {
			ArchiveRepository.getCachedCollection(app, collection)?.let { cached ->
				_remoteListing.value = cached
				_remoteError.value = null
				refreshDownloadedArchiveKeys(cached.files)
				prepareRemoteGroupsIfNeeded(collection, cached.files)
				return
			}
		}

		_remoteError.value = null
		_remoteListing.value = null
		if (collection.category == FileCategory.FLOPPIES) {
			_groupedRemoteGames.value = emptyList()
			_groupedRemoteCollectionId.value = null
			_remotePreparationProgress.value = null
		}
		remoteLoadJob?.cancel()
		remoteLoadJob = viewModelScope.launch(Dispatchers.IO) {
			_isLoadingRemote.value = true
			try {
				val listing = if (forceRefresh) {
					ArchiveRepository.refreshCollection(app, collection)
				} else {
					ArchiveRepository.loadCollection(app, collection)
				}
				if (activeRemoteCollectionId == collection.id) {
					_remoteListing.value = listing
					refreshDownloadedArchiveKeys(listing.files)
					prepareRemoteGroupsIfNeeded(collection, listing.files)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Failed to load archive collection ${collection.identifier}", e)
				if (activeRemoteCollectionId == collection.id) {
					_remoteError.value = "Couldn't load ${collection.fallbackTitle}. Check the connection and try again."
					_downloadedArchiveKeys.value = emptySet()
					_groupedRemoteGames.value = emptyList()
					_groupedRemoteCollectionId.value = null
					_isPreparingRemote.value = false
					_remotePreparationProgress.value = null
				}
			} finally {
				if (activeRemoteCollectionId == collection.id) {
					_isLoadingRemote.value = false
				}
			}
		}
	}

	private fun prepareRemoteGroupsIfNeeded(
		collection: ArchiveCollection,
		files: List<ArchiveDownloadItem>
	) {
		if (collection.category != FileCategory.FLOPPIES) return
		val fileSignature = files.contentSignature()

		cachedGroupedGames[collection.id]?.let { cached ->
			if (cached.signature == fileSignature && (files.isEmpty() || cached.groups.isNotEmpty())) {
				_groupedRemoteGames.value = cached.groups
				_groupedRemoteCollectionId.value = collection.id
				_isPreparingRemote.value = false
				_remotePreparationProgress.value = null
				return
			}
		}

		remotePrepareJob?.cancel()
		remotePrepareJob = viewModelScope.launch(Dispatchers.Default) {
			_isPreparingRemote.value = true
			_remotePreparationProgress.value = RemotePreparationProgress(0, files.size)

			try {
				val prepared = buildGroupedGames(files) { processed, total ->
					if (processed % groupProgressStep == 0 || processed == total) {
						_remotePreparationProgress.value = RemotePreparationProgress(processed, total)
					}
				}

				cachedGroupedGames[collection.id] = CachedGroupedRemoteGames(
					signature = fileSignature,
					groups = prepared
				)
				if (activeRemoteCollectionId == collection.id) {
					_groupedRemoteGames.value = prepared
					_groupedRemoteCollectionId.value = collection.id
				}
			} catch (e: CancellationException) {
				throw e
			} finally {
				if (activeRemoteCollectionId == collection.id) {
					_isPreparingRemote.value = false
					_remotePreparationProgress.value = null
				}
			}
		}
	}

	fun downloadArchiveItem(item: ArchiveDownloadItem) {
		if (_activeDownload.value != null) {
			_importResult.value = "Wait for the current download to finish."
			return
		}

		viewModelScope.launch(Dispatchers.IO) {
			try {
				val app = getApplication<Application>()
				_activeDownload.value = ArchiveDownloadProgress(
					itemId = item.id,
					fileName = item.fileName,
					category = item.category,
					downloadedBytes = 0L,
					totalBytes = item.sizeBytes
				)

				val downloadedFile = ArchiveRepository.downloadItem(app, item) { downloadedBytes, totalBytes ->
					_activeDownload.value = ArchiveDownloadProgress(
						itemId = item.id,
						fileName = item.fileName,
						category = item.category,
						downloadedBytes = downloadedBytes,
						totalBytes = totalBytes
					)
				}

				repository.rescanCategory(item.category)
				refreshDownloadedArchiveKeys(_remoteListing.value?.files.orEmpty())
				_importResult.value = "Downloaded ${downloadedFile.name} to ${item.category.dirName}"
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Failed to download archive item ${item.fileName}", e)
				_importResult.value = "Couldn't download ${item.fileName}"
			} finally {
				_activeDownload.value = null
			}
		}
	}

	private fun refreshDownloadedArchiveKeys(files: List<ArchiveDownloadItem>) {
		if (files.size > maxDownloadedKeyChecks) {
			_downloadedArchiveKeys.value = emptySet()
			return
		}

		val app = getApplication<Application>()
		_downloadedArchiveKeys.value = files.mapNotNull { item ->
			val exactMatch = java.io.File(FileManager.getEffectiveCategoryDir(app, item.category), item.fileName)
			item.id.takeIf { exactMatch.exists() }
		}.toSet()
	}

	companion object {
		private const val TAG = "Uae4Arm-FileManagerVM"
	}
}

private fun buildGroupedGames(
	files: List<ArchiveDownloadItem>,
	onProgress: ((processed: Int, total: Int) -> Unit)? = null
): List<ArchiveGameGroupUi> {
	val groupedMap = linkedMapOf<String, MutableList<ArchiveDownloadItem>>()
	files.forEachIndexed { index, item ->
		val key = extractArchiveGroupTitle(item.fileName)
		groupedMap.getOrPut(key) { mutableListOf() }.add(item)
		onProgress?.invoke(index + 1, files.size)
	}

	return groupedMap.entries
		.map { (title, groupedItems) ->
			ArchiveGameGroupUi(
				title = title,
				items = groupedItems.sortedBy { it.fileName.lowercase() },
				diskCount = groupedItems.mapNotNull { extractDiskNumber(it.fileName) }.distinct().size
			)
		}
		.sortedBy { it.title.lowercase() }
}

private fun extractArchiveGroupTitle(fileName: String): String {
	val withoutDiskMarker = fileName.replace(Regex("\\(Disk\\s+\\d+\\s+of\\s+\\d+\\)", RegexOption.IGNORE_CASE), "")
	return withoutDiskMarker.substringBefore('(')
		.substringBefore('[')
		.substringBeforeLast('.')
		.trim()
		.ifBlank { withoutDiskMarker.substringBeforeLast('.').trim().ifBlank { fileName } }
}

private fun extractDiskNumber(fileName: String): Int? {
	val match = Regex("\\(Disk\\s+(\\d+)\\s+of\\s+\\d+\\)", RegexOption.IGNORE_CASE).find(fileName)
	return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}

private fun List<ArchiveDownloadItem>.contentSignature(): Int {
	var hash = 1
	for (item in this) {
		hash = 31 * hash + item.id.hashCode()
		hash = 31 * hash + item.fileName.hashCode()
		hash = 31 * hash + (item.sizeBytes?.hashCode() ?: 0)
	}
	return hash
}

