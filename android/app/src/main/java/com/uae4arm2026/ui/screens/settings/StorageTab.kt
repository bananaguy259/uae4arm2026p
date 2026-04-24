package com.uae4arm2026.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uae4arm2026.R
import com.uae4arm2026.data.FileManager
import com.uae4arm2026.data.model.AmigaFile
import com.uae4arm2026.data.model.FileCategory
import com.uae4arm2026.ui.viewmodel.SettingsViewModel

// ── SAF URI ↔ path helpers (same logic as QuickStartScreen) ──────────────────

private fun docIdToPath(docId: String): String? {
	val idx = docId.indexOf(':')
	if (idx < 0) return null
	val type = docId.substring(0, idx)
	val relative = docId.substring(idx + 1)
	val base = when (type) {
		"primary" -> Environment.getExternalStorageDirectory().absolutePath
		else -> "/storage/$type"
	}
	return if (relative.isEmpty()) base else "$base/$relative"
}

private fun pathToInitialUri(path: String): Uri? {
	val extRoot = Environment.getExternalStorageDirectory().absolutePath
	val docId = if (path.startsWith(extRoot)) {
		"primary:" + path.removePrefix(extRoot).trimStart('/')
	} else {
		val m = Regex("^/storage/([^/]+)(/.*)?$").find(path) ?: return null
		val vol = m.groupValues[1]
		val rel = m.groupValues[2].trimStart('/')
		"$vol:$rel"
	}
	return DocumentsContract.buildDocumentUri(
		"com.android.externalstorage.documents", docId
	)
}

private fun uriToFilePath(uri: Uri): String? = try {
	docIdToPath(DocumentsContract.getDocumentId(uri))
} catch (_: Exception) { null }

private fun treeUriToPath(uri: Uri): String? = try {
	docIdToPath(DocumentsContract.getTreeDocumentId(uri))
} catch (_: Exception) { null }

// ── StorageTab ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageTab(viewModel: SettingsViewModel) {
	val context = LocalContext.current
	val settings = viewModel.settings
	val roms by viewModel.availableRoms.collectAsState()
	val floppies by viewModel.availableFloppies.collectAsState()
	val hardDrives by viewModel.availableHardDrives.collectAsState()
	val cds by viewModel.availableCds.collectAsState()
	val showExtendedRom = settings.baseModel == com.uae4arm2026.data.model.AmigaModel.CD32 ||
		settings.baseModel == com.uae4arm2026.data.model.AmigaModel.CDTV ||
		settings.romExtFile.isNotBlank()

	// Folder picker for category directories
	var pendingCategoryFolderPicker by remember { mutableStateOf<FileCategory?>(null) }
	var pendingDirPickerCallback by remember { mutableStateOf<((String, Uri) -> Unit)?>(null) }
	val folderPickerLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		val category = pendingCategoryFolderPicker
		val callback = pendingDirPickerCallback
		if (uri != null) {
			if (callback != null) {
				treeUriToPath(uri)?.let { path -> callback(path, uri) }
			} else if (category != null) {
				FileManager.persistDirectoryAccess(context, uri)
				treeUriToPath(uri)?.let { path ->
					FileManager.setCategoryLibraryPath(context, category, path, uri.toString())
					viewModel.rescan()
				}
			}
		}
		pendingCategoryFolderPicker = null
		pendingDirPickerCallback = null
	}

	SettingsTabContent {
		SettingsAdaptiveColumns(
			left = {
				OutlinedCard(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(20.dp))
								Spacer(modifier = Modifier.width(8.dp))
								Text(stringResource(R.string.settings_storage_kickstart_rom_title), style = MaterialTheme.typography.titleMedium)
							}
							TextButton(onClick = {
								pendingCategoryFolderPicker = FileCategory.ROMS
								val dir = FileManager.getEffectiveCategoryDir(context, FileCategory.ROMS)
								folderPickerLauncher.launch(if (dir.exists()) pathToInitialUri(dir.absolutePath) else null)
							}) {
								Text("Set Folder")
							}
						}
						Spacer(modifier = Modifier.height(8.dp))

						if (roms.isEmpty()) {
							Text(
								stringResource(R.string.settings_storage_no_roms_found),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.error
							)
						} else {
							FileDropdown(
								label = stringResource(R.string.settings_storage_rom_file_label),
								files = roms,
								selectedPath = settings.romFile,
								category = FileCategory.ROMS,
								context = context,
								onSelect = { viewModel.updateSettings { s -> s.copy(romFile = it) } },
								onClear = { viewModel.updateSettings { s -> s.copy(romFile = "") } }
							)
							if (showExtendedRom) {
								Spacer(modifier = Modifier.height(8.dp))
								FileDropdown(
									label = stringResource(R.string.settings_storage_rom_ext_file_label),
									files = roms,
									selectedPath = settings.romExtFile,
									category = FileCategory.ROMS,
									context = context,
									onSelect = { viewModel.updateSettings { s -> s.copy(romExtFile = it) } },
									onClear = { viewModel.updateSettings { s -> s.copy(romExtFile = "") } }
								)
							}
						}
					}
				}

				OutlinedCard(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
								Spacer(modifier = Modifier.width(8.dp))
								Text(stringResource(R.string.settings_storage_floppy_drives_title), style = MaterialTheme.typography.titleMedium)
							}
							TextButton(onClick = {
								pendingCategoryFolderPicker = FileCategory.FLOPPIES
								val dir = FileManager.getEffectiveCategoryDir(context, FileCategory.FLOPPIES)
								folderPickerLauncher.launch(if (dir.exists()) pathToInitialUri(dir.absolutePath) else null)
							}) {
								Text("Set Folder")
							}
						}
						Spacer(modifier = Modifier.height(8.dp))

						// Compact 2x2 grid for floppy drives
						FloppyDriveCompactRow(
							drives = listOf(
								CompactDrive("DF0", settings.floppy0, settings.floppy0Type) { path, type ->
									viewModel.updateSettings { s -> s.copy(floppy0 = path, floppy0Type = type) }
								},
								CompactDrive("DF1", settings.floppy1, settings.floppy1Type) { path, type ->
									viewModel.updateSettings { s -> s.copy(floppy1 = path, floppy1Type = type) }
								},
								CompactDrive("DF2", settings.floppy2, settings.floppy2Type) { path, type ->
									viewModel.updateSettings { s -> s.copy(floppy2 = path, floppy2Type = type) }
								},
								CompactDrive("DF3", settings.floppy3, settings.floppy3Type) { path, type ->
									viewModel.updateSettings { s -> s.copy(floppy3 = path, floppy3Type = type) }
								}
							),
							files = floppies,
							context = context
						)
					}
				}
			},
			right = {

				OutlinedCard(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(20.dp))
								Spacer(modifier = Modifier.width(8.dp))
								Text(stringResource(R.string.settings_storage_cd_image_title), style = MaterialTheme.typography.titleMedium)
							}
							TextButton(onClick = {
								pendingCategoryFolderPicker = FileCategory.CD_IMAGES
								val dir = FileManager.getEffectiveCategoryDir(context, FileCategory.CD_IMAGES)
								folderPickerLauncher.launch(if (dir.exists()) pathToInitialUri(dir.absolutePath) else null)
							}) {
								Text("Set Folder")
							}
						}
						Spacer(modifier = Modifier.height(8.dp))

						if (cds.isEmpty()) {
							Text(
								stringResource(R.string.settings_storage_no_cd_images_found),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						} else {
							FileDropdown(
								label = stringResource(R.string.settings_storage_cd_image_label),
								files = cds,
								selectedPath = settings.cdImage,
								category = FileCategory.CD_IMAGES,
								context = context,
								onSelect = { viewModel.updateSettings { s -> s.copy(cdImage = it) } },
								onClear = { viewModel.updateSettings { s -> s.copy(cdImage = "") } }
							)
						}
					}
				}

				OutlinedCard(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween
						) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
								Spacer(modifier = Modifier.width(8.dp))
								Text(stringResource(R.string.settings_storage_hard_drives_title), style = MaterialTheme.typography.titleMedium)
							}
							TextButton(onClick = {
								pendingCategoryFolderPicker = FileCategory.HARD_DRIVES
								val dir = FileManager.getEffectiveCategoryDir(context, FileCategory.HARD_DRIVES)
								folderPickerLauncher.launch(if (dir.exists()) pathToInitialUri(dir.absolutePath) else null)
							}) {
								Text("Set Folder")
							}
						}
						Spacer(modifier = Modifier.height(8.dp))
						if (hardDrives.isEmpty()) {
							Text(
								stringResource(R.string.settings_storage_no_hard_drives_found),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						} else {
							settings.hardDrives.forEachIndexed { i, path ->
								if (i > 0) Spacer(modifier = Modifier.height(8.dp))
								FileDropdown(
									label = "DH$i",
									files = hardDrives,
									selectedPath = path,
									category = FileCategory.HARD_DRIVES,
									context = context,
									onSelect = { selected ->
										viewModel.updateSettings { s ->
											val updated = s.hardDrives.toMutableList().apply { this[i] = selected }
											s.copy(hardDrives = updated)
										}
									},
									onClear = {
										viewModel.updateSettings { s ->
											val updated = s.hardDrives.toMutableList().apply { this[i] = "" }
											s.copy(hardDrives = updated)
										}
									},
									onPickDir = {
										pendingDirPickerCallback = { pickedPath, uri ->
											FileManager.persistDirectoryAccess(context, uri)
											viewModel.updateSettings { s ->
												val updated = s.hardDrives.toMutableList().apply { this[i] = pickedPath }
												s.copy(hardDrives = updated)
											}
										}
										val dir = FileManager.getEffectiveCategoryDir(context, FileCategory.HARD_DRIVES)
										folderPickerLauncher.launch(if (dir.exists()) pathToInitialUri(dir.absolutePath) else null)
									}
								)
							}
						}
					}
				}
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDropdown(
	label: String,
	files: List<AmigaFile>,
	selectedPath: String,
	category: FileCategory,
	context: android.content.Context,
	onSelect: (String) -> Unit,
	onClear: () -> Unit,
	onPickDir: (() -> Unit)? = null
) {
	var expanded by remember { mutableStateOf(false) }
	val noneLabel = stringResource(R.string.placeholder_none)
	val selectedName = if (selectedPath.isEmpty()) noneLabel
	else selectedPath.substringAfterLast('/')

	// SAF file picker that opens in the category subfolder
	var pendingImportCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
	val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		val uri = result.data?.data
		uri?.let { uriToFilePath(it)?.let { path -> pendingImportCallback?.invoke(path) } }
		pendingImportCallback = null
	}

	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		ExposedDropdownMenuBox(
			expanded = expanded,
			onExpandedChange = { expanded = it },
			modifier = Modifier.weight(1f)
		) {
			OutlinedTextField(
				value = selectedName,
				onValueChange = {},
				readOnly = true,
					label = { Text(label) },
					trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
					modifier = Modifier
						.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
						.fillMaxWidth()
				)
			ExposedDropdownMenu(
				expanded = expanded,
				onDismissRequest = { expanded = false }
			) {
				DropdownMenuItem(
					text = { Text(noneLabel) },
					onClick = {
						onClear()
						expanded = false
					}
				)
				files.forEach { file ->
					DropdownMenuItem(
						text = { Text(file.name) },
						onClick = {
							onSelect(file.path)
							expanded = false
						}
					)
				}
				if (category != FileCategory.ROMS) {
					// Import from file picker — opens in category subfolder
					Divider()
					DropdownMenuItem(
						text = { Text("Import...") },
						onClick = {
							expanded = false
							pendingImportCallback = onSelect
							val dir = FileManager.getEffectiveCategoryDir(context, category)
							val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
								addCategory(Intent.CATEGORY_OPENABLE)
								type = "*/*"
								if (dir.exists()) {
									pathToInitialUri(dir.absolutePath)?.let {
										putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
									}
								}
							}
							importLauncher.launch(intent)
						}
					)
					if (onPickDir != null) {
						DropdownMenuItem(
							text = { Text("Mount Folder...") },
							onClick = {
								expanded = false
								onPickDir()
							}
						)
					}
				}
			}
		}
		if (selectedPath.isNotEmpty()) {
			IconButton(onClick = onClear) {
				Icon(Icons.Default.Eject, contentDescription = stringResource(R.string.action_eject))
			}
		}
	}
}

data class CompactDrive(
	val label: String,
	val path: String,
	val type: Int,
	val onChange: (path: String, type: Int) -> Unit
)

@Composable
private fun FloppyDriveCompactRow(
	drives: List<CompactDrive>,
	files: List<AmigaFile>,
	context: android.content.Context
) {
	val noneLabel = stringResource(R.string.placeholder_none)
	
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(4.dp)
	) {
		drives.forEach { drive ->
			CompactFloppyDrive(drive.label, drive.path, drive.type, files, drive.onChange, noneLabel, context)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactFloppyDrive(
	label: String,
	selectedPath: String,
	driveType: Int,
	files: List<AmigaFile>,
	onChange: (path: String, type: Int) -> Unit,
	noneLabel: String,
	context: android.content.Context
) {
	var expanded by remember { mutableStateOf(false) }
	val typeLabel = when (driveType) {
		0 -> "DD"
		1 -> "HD"
		else -> "Off"
	}
	val selectedName = if (selectedPath.isEmpty()) noneLabel else selectedPath.substringAfterLast('/')
	val displayText = if (driveType < 0) label else "$label $selectedName"

	// SAF file picker that opens in the floppies subfolder
	val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		val uri = result.data?.data
		uri?.let {
			val path = uriToFilePath(it)
			if (path != null) onChange(path, driveType)
		}
	}

	ExposedDropdownMenuBox(
		expanded = expanded,
		onExpandedChange = { expanded = it }
	) {
		OutlinedTextField(
			value = displayText,
			onValueChange = {},
			readOnly = true,
			textStyle = MaterialTheme.typography.bodySmall,
			singleLine = true,
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			modifier = Modifier
				.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
				.fillMaxWidth()
		)
		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			// None option
			DropdownMenuItem(
				text = { Text("$label -", style = MaterialTheme.typography.bodySmall) },
				onClick = {
					onChange("", driveType)
					expanded = false
				}
			)
			// File options
			files.take(8).forEach { file ->
				DropdownMenuItem(
					text = { Text(file.name, style = MaterialTheme.typography.bodySmall) },
					onClick = {
						onChange(file.path, driveType)
						expanded = false
					}
				)
			}
			// Type options
			if (driveType >= 0) {
				Divider()
				listOf("DD" to 0, "HD" to 1, "Off" to -1).forEach { (name, value) ->
					DropdownMenuItem(
						text = { Text("Type: $name", style = MaterialTheme.typography.bodySmall) },
						onClick = {
							onChange(selectedPath, value)
							expanded = false
						}
					)
				}
			}
			// Import option — opens in floppies subfolder
			Divider()
			DropdownMenuItem(
				text = { Text("Import...", style = MaterialTheme.typography.bodySmall) },
				onClick = {
					expanded = false
					val dir = FileManager.getEffectiveCategoryDir(context, FileCategory.FLOPPIES)
					val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
						addCategory(Intent.CATEGORY_OPENABLE)
						type = "*/*"
						if (dir.exists()) {
							pathToInitialUri(dir.absolutePath)?.let {
								putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
							}
						}
					}
					importLauncher.launch(intent)
				}
			)
		}
	}
}