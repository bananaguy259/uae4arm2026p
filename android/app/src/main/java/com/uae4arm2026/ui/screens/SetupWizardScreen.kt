package com.uae4arm2026.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.uae4arm2026.data.AgsDetector
import com.uae4arm2026.data.AppPreferences
import com.uae4arm2026.data.FileManager
import com.uae4arm2026.data.FileRepository
import com.uae4arm2026.data.model.FileCategory
import com.uae4arm2026.ui.findActivity
import com.uae4arm2026.ui.navigation.Screen
import com.uae4arm2026.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WizardStep { FOLDER_PICK, SCANNING, ASSIGN }

private fun treeUriToWizardPath(uri: Uri): String? = try {
	val docId = DocumentsContract.getTreeDocumentId(uri)
	val split = docId.split(":")
	if (split.size >= 2) {
		if (split[0] == "primary") "${Environment.getExternalStorageDirectory()}/${split[1]}"
		else "/storage/${split[0]}/${split[1]}"
	} else {
		Environment.getExternalStorageDirectory().absolutePath
	}
} catch (_: Exception) { null }

private val FileCategory.icon: ImageVector
	get() = when (this) {
		FileCategory.ROMS          -> Icons.Default.Memory
		FileCategory.FLOPPIES      -> Icons.Default.SaveAlt
		FileCategory.HARD_DRIVES   -> Icons.Default.Storage
		FileCategory.CD_IMAGES     -> Icons.Default.Album
		FileCategory.WHDLOAD_GAMES -> Icons.Default.SportsEsports
	}

@Composable
fun SetupWizardScreen(navController: NavController) {
	val context = LocalContext.current
	val scope   = rememberCoroutineScope()
	val prefs   = remember { AppPreferences.getInstance(context) }
	val settingsViewModel: SettingsViewModel = viewModel(
		context.findActivity() as ComponentActivity
	)

	var step by remember { mutableStateOf(WizardStep.FOLDER_PICK) }

	val assignedPaths = remember { mutableStateMapOf<FileCategory, String>() }
	var rootPath by remember { mutableStateOf("") }
	
	var agsInstall by remember { mutableStateOf<AgsDetector.AgsInstall?>(null) }

	var pickerCallback by remember { mutableStateOf<((Uri, String) -> Unit)?>(null) }
	val folderPickerLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri != null) {
			val path = treeUriToWizardPath(uri) ?: uri.toString()
			pickerCallback?.invoke(uri, path)
		}
		pickerCallback = null
	}

	fun openPicker(onResult: (Uri, String) -> Unit) {
		pickerCallback = onResult
		folderPickerLauncher.launch(null)
	}

	fun finish() {
		try {
			assignedPaths.forEach { (category, path) ->
				if (path.isNotBlank()) FileManager.setCategoryLibraryPath(context, category, path)
			}
			
			if (agsInstall != null) {
				val drives = AgsDetector.mountableHardDrives(context, agsInstall!!).ifEmpty { listOf("") }
				settingsViewModel.applyModel(com.uae4arm2026.data.model.AmigaModel.A1200)
				settingsViewModel.updateSettings { s ->
					s.copy(
						address24Bit = false,
						cpuSpeed     = "max",
						cycleExact   = false,
						fpuModel     = 68882,
						jitCacheSize = 16384,
						z3Ram        = 512,
						useRtg       = true,
						romFile      = agsInstall!!.romFile ?: s.romFile,
						hardDrives   = drives
					)
				}
				AgsDetector.writeConfig(
					context, agsInstall!!,
					agsInstall!!.romFile,
					AgsDetector.AGS_WIDESCREEN_RTG_WIDTH,
					AgsDetector.AGS_WIDESCREEN_RTG_HEIGHT
				)
			}

			prefs.setHasCompletedSetup(true)
			prefs.setHasSeenWelcome(true)
			scope.launch { 
				try {
					FileRepository.getInstance(context).rescan()
				} catch (e: Exception) {
					android.util.Log.e("SetupWizard", "Rescan failed after setup", e)
				}
			}
			navController.navigate(Screen.QuickStart.route) {
				popUpTo(Screen.Setup.route) { inclusive = true }
			}
		} catch (e: Exception) {
			android.util.Log.e("SetupWizard", "Finish setup failed", e)
			navController.navigate(Screen.QuickStart.route) {
				popUpTo(Screen.Setup.route) { inclusive = true }
			}
		}
	}

	fun pickParentFolder() {
		openPicker { uri, path ->
			FileManager.persistDirectoryAccess(context, uri)
			rootPath = path
			step = WizardStep.SCANNING
			scope.launch {
				try {
					android.util.Log.d("SetupWizard", "Starting background scan of $path")
					val detected = withContext(Dispatchers.IO) {
						FileManager.detectCategoryFolders(context, uri)
					}
					android.util.Log.d("SetupWizard", "Scan complete. Detected ${detected.size} categories")
					
					assignedPaths.clear()
					detected.forEach { (category, pair) ->
						val (resolvedPath, docUri) = pair
						assignedPaths[category] = resolvedPath
						FileManager.setCategoryLibraryPath(context, category, resolvedPath, docUri.toString())
					}
					
					val ags = withContext(Dispatchers.IO) { AgsDetector.detectFromPath(path) }
					if (ags != null) {
						agsInstall = ags
					}

					if (detected.isNotEmpty() || agsInstall != null) {
						finish()
					} else {
						step = WizardStep.ASSIGN
					}
				} catch (e: Exception) {
					android.util.Log.e("SetupWizard", "Scan failed", e)
					step = WizardStep.ASSIGN
				}
			}
		}
	}

	Scaffold { padding ->
		when (step) {
			WizardStep.FOLDER_PICK ->
				FolderPickStep(
					modifier     = Modifier.fillMaxSize().padding(padding).padding(24.dp),
					context      = context,
					navController = navController,
					prefs        = prefs,
					onPickFolder = { pickParentFolder() },
					onSkip       = { finish() }
				)
			WizardStep.SCANNING ->
				ScanningStep(modifier = Modifier.fillMaxSize().padding(padding))
			WizardStep.ASSIGN ->
				AssignStep(
					modifier       = Modifier.fillMaxSize().padding(padding),
					context        = context,
					rootPath       = rootPath,
					assignedPaths  = assignedPaths,
					agsInstall     = agsInstall,
					onPickAgs      = {
						openPicker { uri, path ->
							FileManager.persistDirectoryAccess(context, uri)
							scope.launch {
								val ags = withContext(Dispatchers.IO) { AgsDetector.detectFromPath(path) }
								if (ags != null) {
									agsInstall = ags
								}
							}
						}
					},
					onPickCategory = { category ->
						openPicker { uri, path ->
							FileManager.persistDirectoryAccess(context, uri)
							assignedPaths[category] = path
							FileManager.setCategoryLibraryPath(context, category, path, uri.toString())
						}
					},
					onPickParent   = { pickParentFolder() },
					onFinish       = { finish() }
				)
		}
	}
}

@Composable
private fun FolderPickStep(
	modifier: Modifier = Modifier,
	context: Context,
	navController: NavController,
	prefs: AppPreferences,
	onPickFolder: () -> Unit,
	onSkip: () -> Unit
) {
	LaunchedEffect(Unit) { onPickFolder() }

	Column(
		modifier = modifier.verticalScroll(rememberScrollState()),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Spacer(Modifier.height(24.dp))
		Icon(
			Icons.Default.FolderOpen, contentDescription = null,
			modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary
		)
		Spacer(Modifier.height(12.dp))
		Text(
			"Pick your Amiga files folder",
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
		)
		Spacer(Modifier.height(10.dp))
		Text(
			"Pick the parent folder that contains your Amiga files. " +
				"The wizard scans its sub-folders and assigns each to the right category.",
			style = MaterialTheme.typography.bodyMedium,
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Spacer(Modifier.height(12.dp))
		OutlinedCard(modifier = Modifier.fillMaxWidth()) {
			Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
				TreeLine("📁 Amiga/",          bold = true)
				TreeLine("  ├─ 📁 Kickstarts/")
				TreeLine("  ├─ 📁 Games/")
				TreeLine("  ├─ 📁 Floppies/")
				TreeLine("  ├─ 📁 CDs/")
				TreeLine("  └─ 📁 HardDrives/")
			}
		}
		Spacer(Modifier.height(20.dp))
		Button(
			onClick = onPickFolder,
			modifier = Modifier.fillMaxWidth()
		) {
			Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text("Browse & Pick Amiga Folder")
		}
		
		Spacer(Modifier.height(8.dp))
		TextButton(
			onClick = {
				prefs.clearAll()
				navController.navigate(Screen.Setup.route) {
					popUpTo(0) { inclusive = true }
				}
			},
			modifier = Modifier.fillMaxWidth(),
			colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
		) {
			Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text("Reset App Data & Start Over")
		}

		TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
			Text("Skip for now")
		}
		Spacer(Modifier.height(16.dp))
	}
}

@Composable
private fun TreeLine(text: String, bold: Boolean = false) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodySmall.copy(
			fontFamily = FontFamily.Monospace,
			fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
		),
		modifier = Modifier.padding(vertical = 1.dp)
	)
}

@Composable
private fun ScanningStep(modifier: Modifier = Modifier) {
	Box(modifier = modifier, contentAlignment = Alignment.Center) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			CircularProgressIndicator(modifier = Modifier.size(56.dp))
			Spacer(Modifier.height(20.dp))
			Text("Scanning sub-folders…", style = MaterialTheme.typography.titleMedium)
			Spacer(Modifier.height(8.dp))
			Text(
				"Matching Kickstarts, games, floppies and more.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(horizontal = 32.dp)
			)
		}
	}
}

@Composable
private fun AssignStep(
	modifier: Modifier = Modifier,
	context: Context,
	rootPath: String,
	assignedPaths: Map<FileCategory, String>,
	agsInstall: AgsDetector.AgsInstall?,
	onPickAgs: () -> Unit,
	onPickCategory: (FileCategory) -> Unit,
	onPickParent: () -> Unit,
	onFinish: () -> Unit
) {
	Column(
		modifier = modifier
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 20.dp, vertical = 16.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp)
	) {
		Text(
			"Assign library folders",
			style = MaterialTheme.typography.headlineSmall,
			fontWeight = FontWeight.Bold
		)
		if (rootPath.isNotBlank()) {
			Text(
				rootPath,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2, overflow = TextOverflow.Ellipsis
			)
		}
		Text(
			"Each row shows which folder is used for that file type. " +
				"Tap any row to change its folder.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		HorizontalDivider()

		AgsAssignRow(
			install = agsInstall,
			onPick = onPickAgs
		)
		
		if (agsInstall != null) {
			TextButton(
				onClick = {
					FileManager.setCategoryLibraryPath(context, FileCategory.WHDLOAD_GAMES, null)
					// Trigger a UI refresh by simulating a pick result of null
					onPickCategory(FileCategory.WHDLOAD_GAMES) 
				},
				modifier = Modifier.padding(start = 8.dp)
			) {
				Text("Clear AGS Folder", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
			}
		}

		FileCategory.entries.forEach { category ->
			CategoryAssignRow(
				category   = category,
				folderPath = assignedPaths[category] ?: "",
				onPick     = { onPickCategory(category) }
			)
		}

		HorizontalDivider()
		TextButton(onClick = onPickParent, modifier = Modifier.fillMaxWidth()) {
			Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(6.dp))
			Text("Pick a different parent folder")
		}
		Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp)) {
			Text("Finish Setup", style = MaterialTheme.typography.titleMedium)
		}
		Spacer(Modifier.height(16.dp))
	}
}

@Composable
private fun AgsAssignRow(
	install: AgsDetector.AgsInstall?,
	onPick: () -> Unit
) {
	val assigned = install != null
	OutlinedCard(
		modifier = Modifier.fillMaxWidth(),
		onClick = onPick,
		colors = if (assigned) CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
				 else CardDefaults.outlinedCardColors()
	) {
		Row(
			modifier = Modifier.padding(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				Icons.Default.SportsEsports, contentDescription = null,
				modifier = Modifier.size(28.dp),
				tint = if (assigned) MaterialTheme.colorScheme.primary
					   else MaterialTheme.colorScheme.onSurfaceVariant
			)
			Spacer(Modifier.width(12.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					"AGS Amiga Game System",
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.SemiBold
				)
				Text(
					if (assigned) install!!.agsDir.absolutePath
					else "Not found — tap to point to AGS_UAE folder",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1, overflow = TextOverflow.Ellipsis
				)
			}
			Icon(
				if (assigned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
				contentDescription = null,
				modifier = Modifier.size(20.dp),
				tint = if (assigned) MaterialTheme.colorScheme.primary
					   else MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

@Composable
private fun CategoryAssignRow(
	category: FileCategory,
	folderPath: String,
	onPick: () -> Unit
) {
	val assigned = folderPath.isNotBlank()
	OutlinedCard(
		modifier = Modifier.fillMaxWidth(),
		onClick = onPick
	) {
		Row(
			modifier = Modifier.padding(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Icon(
				category.icon, contentDescription = null,
				modifier = Modifier.size(28.dp),
				tint = if (assigned) MaterialTheme.colorScheme.primary
					   else MaterialTheme.colorScheme.onSurfaceVariant
			)
			Spacer(Modifier.width(12.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					category.displayName,
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.SemiBold
				)
				Text(
					if (assigned) folderPath.substringAfterLast('/').ifBlank { folderPath }
					else "Not assigned — tap to set",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1, overflow = TextOverflow.Ellipsis
				)
			}
			Icon(
				if (assigned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
				contentDescription = null,
				modifier = Modifier.size(20.dp),
				tint = if (assigned) MaterialTheme.colorScheme.primary
					   else MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
