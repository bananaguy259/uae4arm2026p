package com.uae4arm2026.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uae4arm2026.R
import com.uae4arm2026.data.ArchiveCatalog
import com.uae4arm2026.data.ArchiveDownloadItem
import com.uae4arm2026.data.model.FileCategory
import com.uae4arm2026.ui.viewmodel.ArchiveGameGroupUi
import com.uae4arm2026.ui.viewmodel.FileManagerViewModel
import java.util.Locale

private const val MAX_VISIBLE_REMOTE_FILES = 80
private const val MAX_VISIBLE_SEARCH_RESULTS = 120
private const val REMOTE_BATCH_SIZE = 80

@Composable
fun ArchiveDownloadsPane(
	viewModel: FileManagerViewModel,
	modifier: Modifier = Modifier
) {
	val listing by viewModel.remoteListing.collectAsState()
	val isLoading by viewModel.isLoadingRemote.collectAsState()
	val remoteError by viewModel.remoteError.collectAsState()
	val activeDownload by viewModel.activeDownload.collectAsState()
	val downloadedKeys by viewModel.downloadedArchiveKeys.collectAsState()
	val groupedRemoteGames by viewModel.groupedRemoteGames.collectAsState()
	val groupedRemoteCollectionId by viewModel.groupedRemoteCollectionId.collectAsState()
	val isPreparingRemote by viewModel.isPreparingRemote.collectAsState()
	val remotePreparationProgress by viewModel.remotePreparationProgress.collectAsState()

	var selectedSourceIndex by rememberSaveable { mutableIntStateOf(0) }
	var selectedLetter by rememberSaveable { mutableStateOf("A") }
	var searchQuery by rememberSaveable { mutableStateOf("") }
	var selectedGroupTitle by rememberSaveable(selectedSourceIndex, selectedLetter) { mutableStateOf<String?>(null) }
	var visibleItemCount by rememberSaveable(selectedSourceIndex, selectedLetter, searchQuery) {
		mutableIntStateOf(REMOTE_BATCH_SIZE)
	}
	val listState = rememberLazyListState()

	val sourceTabs = listOf(
		stringResource(R.string.downloads_source_kickstarts),
		stringResource(R.string.downloads_source_games)
	)
	val searchPlaceholder = stringResource(R.string.downloads_search_placeholder)

	val selectedLetterChar = selectedLetter.firstOrNull() ?: 'A'
	val selectedCollection = when (selectedSourceIndex) {
		0 -> ArchiveCatalog.kickstartCollection
		else -> ArchiveCatalog.adfCollections.firstOrNull { it.letter == selectedLetterChar }
			?: ArchiveCatalog.adfCollections.first()
	}
	val activeListing = listing?.takeIf { it.collection.id == selectedCollection.id }

	LaunchedEffect(selectedCollection.id) {
		searchQuery = ""
		selectedGroupTitle = null
		visibleItemCount = REMOTE_BATCH_SIZE
		viewModel.selectArchiveCollection(selectedCollection)
	}

	val remoteFiles = activeListing?.files.orEmpty().sortedBy { it.fileName.lowercase(Locale.getDefault()) }
	val groupedRemoteFiles = if (selectedCollection.category == FileCategory.FLOPPIES) {
		if (groupedRemoteCollectionId == selectedCollection.id) groupedRemoteGames else emptyList()
	} else {
		emptyList()
	}
	val filteredGroups = if (selectedCollection.category == FileCategory.FLOPPIES) {
		if (searchQuery.isBlank()) {
			groupedRemoteFiles
		} else {
			groupedRemoteFiles.filter { group ->
				group.title.contains(searchQuery, ignoreCase = true) ||
					group.items.any { it.fileName.contains(searchQuery, ignoreCase = true) }
			}
		}
	} else {
		emptyList()
	}
	val selectedGroup = filteredGroups.firstOrNull { it.title == selectedGroupTitle }
	val useGroupedAdfList =
		selectedCollection.category == FileCategory.FLOPPIES &&
		searchQuery.isBlank() &&
		selectedGroup == null &&
		groupedRemoteFiles.isNotEmpty()
	val filteredFiles = when {
		selectedCollection.category == FileCategory.FLOPPIES && selectedGroup != null -> selectedGroup.items
		selectedCollection.category == FileCategory.FLOPPIES && searchQuery.isNotBlank() ->
			remoteFiles.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
		selectedCollection.category == FileCategory.FLOPPIES && groupedRemoteFiles.isEmpty() -> remoteFiles
		selectedCollection.category == FileCategory.FLOPPIES -> emptyList()
		searchQuery.isBlank() -> remoteFiles
		else -> remoteFiles.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
	}
	val visibleFiles = when {
		useGroupedAdfList ->
			emptyList()
		searchQuery.isBlank() && selectedCollection.category == FileCategory.FLOPPIES ->
			filteredFiles.take(visibleItemCount.coerceAtMost(filteredFiles.size))
		searchQuery.isNotBlank() ->
			filteredFiles.take(visibleItemCount.coerceAtMost(MAX_VISIBLE_SEARCH_RESULTS).coerceAtMost(filteredFiles.size))
		else ->
			filteredFiles.take(visibleItemCount.coerceAtMost(MAX_VISIBLE_REMOTE_FILES).coerceAtMost(filteredFiles.size))
	}
	val visibleGroups = if (useGroupedAdfList) {
		filteredGroups.take(visibleItemCount.coerceAtMost(filteredGroups.size))
	} else {
		emptyList()
	}
	val totalVisibleCount = if (useGroupedAdfList) {
		filteredGroups.size
	} else {
		filteredFiles.size
	}
	val currentVisibleCount = if (useGroupedAdfList) {
		visibleGroups.size
	} else {
		visibleFiles.size
	}
	val isListTrimmed = currentVisibleCount < totalVisibleCount
	val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1

	LaunchedEffect(lastVisibleIndex, currentVisibleCount, totalVisibleCount, searchQuery, selectedGroupTitle) {
		if (!isListTrimmed || currentVisibleCount == 0) return@LaunchedEffect
		val preloadThreshold = 8
		if (lastVisibleIndex >= currentVisibleCount - 1 - preloadThreshold) {
			val maxVisible = if (searchQuery.isBlank()) {
				totalVisibleCount
			} else {
				totalVisibleCount.coerceAtMost(MAX_VISIBLE_SEARCH_RESULTS)
			}
			visibleItemCount = (visibleItemCount + REMOTE_BATCH_SIZE).coerceAtMost(maxVisible)
		}
	}

	Column(
		modifier = modifier.fillMaxSize()
	) {
		if (remoteFiles.size > 12 || searchQuery.isNotBlank() || selectedCollection.category == FileCategory.FLOPPIES) {
			OutlinedTextField(
				value = searchQuery,
				onValueChange = {
					searchQuery = it
					selectedGroupTitle = null
					visibleItemCount = REMOTE_BATCH_SIZE
				},
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 2.dp),
				placeholder = { Text(searchPlaceholder) },
				singleLine = true
			)
		}

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				Icons.Default.CloudDownload,
				contentDescription = null,
				modifier = Modifier.size(18.dp),
				tint = MaterialTheme.colorScheme.primary
			)
			Spacer(modifier = Modifier.width(8.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = if (selectedCollection.category == FileCategory.ROMS) {
						stringResource(R.string.downloads_source_kickstarts)
					} else {
						stringResource(R.string.downloads_source_games)
					},
					style = MaterialTheme.typography.titleSmall
				)
				Text(
					text = activeListing?.archiveTitle ?: selectedCollection.fallbackTitle,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			IconButton(
				onClick = { viewModel.selectArchiveCollection(selectedCollection, forceRefresh = true) },
				modifier = Modifier.size(36.dp)
			) {
				Icon(
					Icons.Default.Refresh,
					contentDescription = stringResource(R.string.action_refresh)
				)
			}
		}

		if (activeDownload != null) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 2.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				Text(
					text = stringResource(
						R.string.downloads_active_download,
						activeDownload!!.fileName
					),
					style = MaterialTheme.typography.labelSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				val fraction = activeDownload!!.fractionComplete
				if (fraction != null) {
					LinearProgressIndicator(
						progress = { fraction.coerceIn(0f, 1f) },
						modifier = Modifier.fillMaxWidth()
					)
				} else {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
			}
		}

		LazyRow(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			contentPadding = PaddingValues(end = 16.dp)
		) {
			items(sourceTabs.size) { index ->
				val title = sourceTabs[index]
				FilterChip(
					selected = selectedSourceIndex == index,
					onClick = { selectedSourceIndex = index },
					label = { Text(title) },
					leadingIcon = {
						Icon(
							if (index == 0) Icons.Default.Memory else Icons.Default.SaveAlt,
							contentDescription = null,
							modifier = Modifier.size(16.dp)
						)
					}
				)
			}
			if (selectedSourceIndex == 1) {
				items(
					items = ArchiveCatalog.adfCollections,
					key = { collection -> collection.id }
				) { collection ->
					val letter = collection.letter?.toString().orEmpty()
					FilterChip(
						selected = selectedLetter == letter,
						onClick = { selectedLetter = letter },
						label = { Text(letter) }
					)
				}
			}
		}

		if (selectedCollection.category == FileCategory.FLOPPIES && selectedGroup != null) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				FilterChip(
					selected = false,
					onClick = {
						selectedGroupTitle = null
						visibleItemCount = REMOTE_BATCH_SIZE
					},
					label = { Text("Back to games") },
					leadingIcon = {
						Icon(
							Icons.Default.ArrowBack,
							contentDescription = null,
							modifier = Modifier.size(16.dp)
						)
					}
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = selectedGroup.title,
					style = MaterialTheme.typography.bodyMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}

		when {
			(isLoading && activeListing == null) || (selectedCollection.category == FileCategory.FLOPPIES && isPreparingRemote && groupedRemoteFiles.isEmpty()) -> {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.padding(32.dp),
					contentAlignment = Alignment.Center
				) {
					Column(horizontalAlignment = Alignment.CenterHorizontally) {
						CircularProgressIndicator()
						Spacer(modifier = Modifier.height(12.dp))
						Text(
							if (selectedCollection.category == FileCategory.FLOPPIES && isPreparingRemote) {
								"Preparing ADF list..."
							} else {
								stringResource(R.string.downloads_loading)
							}
						)
						remotePreparationProgress?.let { progress ->
							Spacer(modifier = Modifier.height(12.dp))
							LinearProgressIndicator(
								progress = { progress.fractionComplete.coerceIn(0f, 1f) },
								modifier = Modifier.fillMaxWidth()
							)
							Spacer(modifier = Modifier.height(8.dp))
							Text(
								text = "Scanned ${progress.processedItems} of ${progress.totalItems}",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
			}

			remoteError != null && remoteFiles.isEmpty() -> {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.padding(24.dp),
					contentAlignment = Alignment.Center
				) {
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(12.dp)
					) {
						Text(
							text = remoteError.orEmpty(),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.error
						)
						Button(onClick = { viewModel.selectArchiveCollection(selectedCollection, forceRefresh = true) }) {
							Text(stringResource(R.string.action_retry))
						}
					}
				}
			}

			useGroupedAdfList && filteredGroups.isEmpty() -> {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.padding(24.dp),
					contentAlignment = Alignment.Center
				) {
					Column(horizontalAlignment = Alignment.CenterHorizontally) {
						Text(
							text = if (searchQuery.isBlank()) {
								stringResource(R.string.downloads_no_files)
							} else {
								stringResource(R.string.downloads_no_search_results)
							},
							style = MaterialTheme.typography.bodyLarge
						)
						Spacer(modifier = Modifier.height(8.dp))
						Text(
							text = activeListing?.archiveTitle ?: selectedCollection.fallbackTitle,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}

			filteredFiles.isEmpty() -> {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.padding(24.dp),
					contentAlignment = Alignment.Center
				) {
					Column(horizontalAlignment = Alignment.CenterHorizontally) {
						Text(
							text = if (searchQuery.isBlank()) {
								stringResource(R.string.downloads_no_files)
							} else {
								stringResource(R.string.downloads_no_search_results)
							},
							style = MaterialTheme.typography.bodyLarge
						)
						if (listing != null) {
							Spacer(modifier = Modifier.height(8.dp))
							Text(
								text = activeListing?.archiveTitle ?: selectedCollection.fallbackTitle,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
			}

			else -> {
				LazyColumn(
					state = listState,
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp)
				) {
					if (isListTrimmed) {
						item(key = "downloads-trimmed-hint") {
							Text(
								text = if (useGroupedAdfList) {
									"Showing first ${visibleGroups.size} of ${filteredGroups.size} games. Scroll for more or search to narrow the list."
								} else if (searchQuery.isBlank()) {
									"Showing first ${visibleFiles.size} of ${filteredFiles.size}. Search to narrow the list."
								} else {
									"Showing first ${visibleFiles.size} matches. Keep typing to narrow the list."
								},
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.padding(vertical = 4.dp)
							)
						}
					}
					if (useGroupedAdfList) {
						items(visibleGroups, key = { it.title }) { group ->
							ArchiveGameGroupRow(
								group = group,
								onOpen = {
									selectedGroupTitle = group.title
									visibleItemCount = REMOTE_BATCH_SIZE
								}
							)
						}
					} else {
						items(visibleFiles, key = { it.id }) { item ->
							ArchiveDownloadItemRow(
								item = item,
								isDownloaded = item.id in downloadedKeys,
								isDownloading = activeDownload?.itemId == item.id,
								onDownload = { viewModel.downloadArchiveItem(item) }
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun ArchiveGameGroupRow(
	group: ArchiveGameGroupUi,
	onOpen: () -> Unit
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onOpen)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				Icons.Default.Folder,
				contentDescription = null,
				modifier = Modifier.size(22.dp),
				tint = MaterialTheme.colorScheme.primary
			)
			Spacer(modifier = Modifier.width(8.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = group.title,
					style = MaterialTheme.typography.bodyMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = buildString {
						append("${group.items.size} files")
						if (group.diskCount > 1) {
							append(" across ${group.diskCount} disks")
						}
					},
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}

@Composable
private fun ArchiveDownloadItemRow(
	item: ArchiveDownloadItem,
	isDownloaded: Boolean,
	isDownloading: Boolean,
	onDownload: () -> Unit
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				if (item.category == FileCategory.ROMS) Icons.Default.Memory else Icons.Default.SaveAlt,
				contentDescription = null,
				modifier = Modifier.size(22.dp),
				tint = MaterialTheme.colorScheme.primary
			)
			Spacer(modifier = Modifier.width(8.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = item.fileName,
					style = MaterialTheme.typography.bodyMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = item.sizeBytes?.let { formatBytes(it) }
						?: stringResource(R.string.downloads_unknown_size),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}

			Button(
				onClick = onDownload,
				enabled = !isDownloading
			) {
				Icon(
					Icons.Default.CloudDownload,
					contentDescription = null,
					modifier = Modifier.size(16.dp)
				)
				Spacer(modifier = Modifier.width(6.dp))
				Text(
					when {
						isDownloading -> stringResource(R.string.downloads_downloading)
						isDownloaded -> stringResource(R.string.downloads_download_again)
						else -> stringResource(R.string.downloads_download)
					}
				)
			}
		}
	}
}

private fun formatBytes(bytes: Long): String {
	return when {
		bytes < 1024L -> "$bytes B"
		bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
		else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
	}
}

private fun formatByteProgress(downloadedBytes: Long, totalBytes: Long?): String {
	val downloaded = formatBytes(downloadedBytes)
	val total = totalBytes?.let { formatBytes(it) }
	return if (total != null) "$downloaded / $total" else downloaded
}
