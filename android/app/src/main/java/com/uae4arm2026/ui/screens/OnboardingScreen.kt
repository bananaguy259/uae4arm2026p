package com.uae4arm2026.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.uae4arm2026.R
import com.uae4arm2026.data.AgsDetector
import com.uae4arm2026.data.AppPreferences
import com.uae4arm2026.data.FileManager
import com.uae4arm2026.data.FileRepository
import com.uae4arm2026.data.model.AmigaModel
import com.uae4arm2026.data.model.FileCategory
import com.uae4arm2026.ui.findActivity
import com.uae4arm2026.ui.navigation.Screen
import com.uae4arm2026.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

@get:DrawableRes
private val FileCategory.onboardingDrawable: Int
	get() = when (this) {
		FileCategory.ROMS          -> R.drawable.featured_kickstart_check
		FileCategory.FLOPPIES      -> R.drawable.featured_floppy_inserted
		FileCategory.HARD_DRIVES   -> R.drawable.featured_drive_dh0
		FileCategory.CD_IMAGES     -> R.drawable.featured_cd32
		FileCategory.WHDLOAD_GAMES -> R.drawable.featured_a1200
	}

@DrawableRes
private fun artworkForModel(model: AmigaModel): Int = when (model) {
	AmigaModel.A1200 -> R.drawable.featured_a1200
	AmigaModel.A3000 -> R.drawable.featured_a3000
	AmigaModel.A4000 -> R.drawable.featured_a4000
	AmigaModel.CD32  -> R.drawable.featured_cd32
	AmigaModel.CDTV  -> R.drawable.featured_cd32
	else             -> R.drawable.featured_a500
}

private fun treeUriToPath(context: Context, uri: Uri): String? = try {
	val docId = DocumentsContract.getTreeDocumentId(uri)
	val split = docId.split(":")
	if (split.size >= 2) {
		if (split[0] == "primary") "${Environment.getExternalStorageDirectory()}/${split[1]}"
		else "/storage/${split[0]}/${split[1]}"
	} else {
		Environment.getExternalStorageDirectory().absolutePath
	}
} catch (_: Exception) { null }

// -----------------------------------------------------------------------------
// Root composable � single scrollable page
// -----------------------------------------------------------------------------

@Composable
fun OnboardingScreen(navController: NavController) {
	val context = LocalContext.current
	val scope   = rememberCoroutineScope()
	val prefs   = remember { AppPreferences.getInstance(context) }
	val settingsViewModel: SettingsViewModel = viewModel(
		context.findActivity() as ComponentActivity
	)

	// -- State ----------------------------------------------------------------
	var rootPath       by remember { mutableStateOf("") }
	var scanning       by remember { mutableStateOf(false) }
	var scanned        by remember { mutableStateOf(false) }
	val detectedPaths  = remember { mutableStateMapOf<FileCategory, String>() }
	val detectedUris   = remember { mutableStateMapOf<FileCategory, Uri>() }
	val detectedCounts = remember { mutableStateMapOf<FileCategory, Int>() }
	var selectedModel  by remember { mutableStateOf(AmigaModel.A1200) }

	// AGS detection
	var agsInstall     by remember { mutableStateOf<AgsDetector.AgsInstall?>(null) }
	var agsEnabled     by remember { mutableStateOf(true) }
	var agsChecked     by remember { mutableStateOf(false) }

	// Auto-detect AGS on launch
	LaunchedEffect(Unit) {
		val ags = withContext(Dispatchers.IO) { AgsDetector.detect(context) }
		agsInstall = ags
		agsEnabled = ags != null
		agsChecked = true
	}

	// SAF folder picker
	var pickerCallback by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
	val folderPickerLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri != null) pickerCallback?.invoke(uri)
		pickerCallback = null
	}

	fun openPicker(onResult: (Uri) -> Unit) {
		pickerCallback = onResult
		folderPickerLauncher.launch(null)
	}

	// -- Folder scan ----------------------------------------------------------
	fun startScan(uri: Uri) {
		FileManager.persistDirectoryAccess(context, uri)
		val path = treeUriToPath(context, uri)
		rootPath = path ?: ""
		scanning = true
		scanned  = false
		scope.launch {
			val detected = withContext(Dispatchers.IO) {
				FileManager.detectCategoryFolders(context, uri)
			}
			detectedPaths.clear()
			detectedUris.clear()
			detectedCounts.clear()
			detected.forEach { (cat, pair) ->
				val (folderPath, docUri) = pair
				detectedPaths[cat] = folderPath
				detectedUris[cat] = docUri
				val count = withContext(Dispatchers.IO) {
					val dir = File(folderPath)
					dir.listFiles()
						?.count { f -> f.isFile && f.extension.lowercase() in cat.extensions }
						?: 0
				}
				detectedCounts[cat] = count
			}
			// Re-check AGS from the chosen path too
			if (agsInstall == null && path != null) {
				val ags = withContext(Dispatchers.IO) { AgsDetector.detectFromPath(path) }
				if (ags != null) {
					agsInstall = ags
					agsEnabled = true
					agsChecked = true
				}
			}
			scanning = false
			scanned  = true
		}
	}

	// -- Finish onboarding ----------------------------------------------------
	fun finish(model: AmigaModel) {
		detectedPaths.forEach { (cat, path) ->
			if (path.isNotBlank()) {
				FileManager.setCategoryLibraryPath(context, cat, path, detectedUris[cat]?.toString())
			}
		}
		prefs.setHasCompletedSetup(true)
		prefs.setHasSeenWelcome(true)
		settingsViewModel.applyModel(model)

		// Import AGS if user enabled the toggle
		if (agsEnabled && agsInstall != null) {
			val drives = AgsDetector.mountableHardDrives(agsInstall!!).ifEmpty { listOf("") }
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
			// Write the AGS config so it's ready to launch from home screen
			AgsDetector.writeConfig(
				context, agsInstall!!,
				agsInstall!!.romFile,
				AgsDetector.AGS_WIDESCREEN_RTG_WIDTH,
				AgsDetector.AGS_WIDESCREEN_RTG_HEIGHT
			)
		}

		scope.launch { FileRepository.getInstance(context).rescan() }
		navController.navigate(Screen.QuickStart.route) {
			popUpTo(Screen.Onboarding.route) { inclusive = true }
		}
	}

	// -- Model list -----------------------------------------------------------
	val models = listOf(
		AmigaModel.A500,
		AmigaModel.A500_PLUS,
		AmigaModel.A600,
		AmigaModel.A1000,
		AmigaModel.A2000,
		AmigaModel.A1200,
		AmigaModel.A3000,
		AmigaModel.A4000,
		AmigaModel.CD32,
		AmigaModel.CDTV
	)

	// -- UI --------------------------------------------------------------------
	val blueScheme = MaterialTheme.colorScheme.copy(
		primary            = Color(0xFF1565C0),
		onPrimary          = Color(0xFFFFFFFF),
		primaryContainer   = Color(0xFF1E3A6E),
		onPrimaryContainer = Color(0xFFBBDEFB)
	)
	MaterialTheme(colorScheme = blueScheme) {
		Scaffold { padding ->
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(padding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 20.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// -------------------------------------------------------------
				// SECTION � Welcome header
				// -------------------------------------------------------------
				Spacer(Modifier.height(12.dp))

				Image(
					painter            = painterResource(R.drawable.featured_default),
					contentDescription = "UAE4ARM",
					contentScale       = ContentScale.Fit,
					modifier           = Modifier
						.fillMaxWidth()
						.heightIn(min = 120.dp, max = 200.dp)
						.padding(horizontal = 24.dp, vertical = 8.dp)
				)

				Text(
					text       = "UAE4ARM",
					style      = MaterialTheme.typography.headlineMedium,
					fontWeight = FontWeight.Bold,
					textAlign  = TextAlign.Center
				)
				Text(
					text      = "UAE4Arm 2026 app \u2022 Upstream core: Amiberry / WinUAE",
					style     = MaterialTheme.typography.labelLarge,
					textAlign = TextAlign.Center,
					color     = MaterialTheme.colorScheme.primary
				)
				Spacer(Modifier.height(8.dp))
				Text(
					text      = "A full-featured Amiga emulator for Android with AGS, " +
								"WHDLoad, RTG graphics, JIT, and more.",
					style     = MaterialTheme.typography.bodyMedium,
					textAlign = TextAlign.Center,
					color     = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier  = Modifier.padding(horizontal = 12.dp)
				)

				Spacer(Modifier.height(20.dp))
				HorizontalDivider()
				Spacer(Modifier.height(16.dp))


				// -------------------------------------------------------------
				// SECTION � Folder picker
				// -------------------------------------------------------------
				run {
					var step = 1
					if (agsInstall != null) step++
					SectionHeader(step.toString(), "Choose Your Amiga Folder")
				}
				Spacer(Modifier.height(6.dp))
				Text(
					"Pick the folder with your Kickstart ROMs, floppies, HDFs, and WHDLoad games.",
					style     = MaterialTheme.typography.bodySmall,
					textAlign = TextAlign.Center,
					color     = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Spacer(Modifier.height(12.dp))

				if (scanning) {
					CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
					Spacer(Modifier.height(8.dp))
					Text(
						"Scanning\u2026",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				} else {
					OutlinedButton(
						onClick  = { openPicker { uri -> startScan(uri) } },
						modifier = Modifier.fillMaxWidth().height(48.dp)
					) {
						Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(Modifier.width(8.dp))
						Text(if (scanned) "Pick Different Folder" else "Choose Folder")
					}
					if (rootPath.isNotBlank()) {
						Spacer(Modifier.height(4.dp))
						Text(
							rootPath,
							style     = MaterialTheme.typography.labelSmall,
							color     = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
							maxLines  = 1,
							overflow  = TextOverflow.Ellipsis,
							modifier  = Modifier.fillMaxWidth()
						)
					}
				}

				// -------------------------------------------------------------
				// SECTION � Scan results (double-column)
				// -------------------------------------------------------------
				if (scanned) {
					Spacer(Modifier.height(16.dp))
					HorizontalDivider()
					Spacer(Modifier.height(16.dp))

					run {
						var step = 2
						if (agsInstall != null) step++
						SectionHeader(step.toString(), if (detectedPaths.isEmpty()) "Nothing Found" else "Found Files")
					}
					Spacer(Modifier.height(8.dp))

					if (detectedPaths.isEmpty()) {
						OutlinedCard(
							modifier = Modifier.fillMaxWidth(),
							colors   = CardDefaults.outlinedCardColors(
								containerColor = MaterialTheme.colorScheme.errorContainer
							)
						) {
							Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
								Icon(Icons.Default.FolderOff, contentDescription = null,
									tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
								Spacer(Modifier.width(10.dp))
								Text(
									"No Amiga folders detected. Try a parent folder.",
									style = MaterialTheme.typography.bodySmall
								)
							}
						}
					} else {
						// Double-column results grid
						val categories = FileCategory.entries.filter { it in detectedPaths }
						val rows = categories.chunked(2)
						rows.forEach { rowCats ->
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								rowCats.forEach { cat ->
									val count = detectedCounts[cat] ?: 0
									CompactCategoryCard(
										category  = cat,
										fileCount = count,
										modifier  = Modifier.weight(1f).padding(vertical = 4.dp)
									)
								}
								// Fill empty slot if odd number
								if (rowCats.size == 1) {
									Spacer(Modifier.weight(1f))
								}
							}
						}

						// Kickstart warning
						if (FileCategory.ROMS !in detectedPaths) {
							Spacer(Modifier.height(8.dp))
							Text(
								"\u26A0 No Kickstart ROMs found \u2014 you\u2019ll need at least one to run.",
								style     = MaterialTheme.typography.labelSmall,
								color     = MaterialTheme.colorScheme.error,
								textAlign = TextAlign.Center,
								modifier  = Modifier.fillMaxWidth()
							)
						}
					}
				}

				// -------------------------------------------------------------
				// SECTION � AGS import offer (shown as soon as detected,
				//           independent of folder scan)
				// -------------------------------------------------------------
				if (agsChecked && agsInstall != null) {
					Spacer(Modifier.height(16.dp))
					HorizontalDivider()
					Spacer(Modifier.height(16.dp))

					SectionHeader(
						"2",
						"Set Up AGS?"
					)

					Spacer(Modifier.height(8.dp))
					OutlinedCard(modifier = Modifier.fillMaxWidth()) {
						Row(
							modifier          = Modifier.padding(14.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							Icon(
								Icons.Default.SportsEsports,
								contentDescription = null,
								tint               = MaterialTheme.colorScheme.primary,
								modifier           = Modifier.size(32.dp)
							)
							Spacer(Modifier.width(12.dp))
							Column(modifier = Modifier.weight(1f)) {
								Text(
									"Amiga Game Selector found",
									style      = MaterialTheme.typography.bodyMedium,
									fontWeight = FontWeight.SemiBold
								)
								Text(
									agsInstall!!.agsDir.absolutePath,
									style    = MaterialTheme.typography.labelSmall,
									color    = MaterialTheme.colorScheme.onSurfaceVariant,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
							Switch(
								checked         = agsEnabled,
								onCheckedChange = { agsEnabled = it }
							)
						}
					}
					Spacer(Modifier.height(4.dp))
					Text(
						"Enable to import AGS and launch it from the home screen.",
						style     = MaterialTheme.typography.labelSmall,
						color     = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
						modifier  = Modifier.fillMaxWidth()
					)
				}

				Spacer(Modifier.height(16.dp))
				HorizontalDivider()
				Spacer(Modifier.height(16.dp))

				// -------------------------------------------------------------
				// SECTION � Model select (horizontal scroll)
				// -------------------------------------------------------------
				run {
					var step = 2
					if (agsInstall != null) step++
					if (scanned) step++
					SectionHeader(step.toString(), "Choose Your Amiga Model")
				}
				Spacer(Modifier.height(4.dp))
				Text(
					"Swipe to browse. You can change this any time.",
					style     = MaterialTheme.typography.labelSmall,
					color     = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center
				)
				Spacer(Modifier.height(10.dp))

				// Horizontal scrolling model cards
				LazyRow(
					contentPadding        = PaddingValues(horizontal = 4.dp),
					horizontalArrangement = Arrangement.spacedBy(10.dp)
				) {
					items(models) { model ->
						ModelCardCompact(
							model    = model,
							selected = model == selectedModel,
							onClick  = { selectedModel = model }
						)
					}
				}

				Spacer(Modifier.height(24.dp))

				// -------------------------------------------------------------
				// FINISH button
				// -------------------------------------------------------------
				Button(
					onClick  = { finish(selectedModel) },
					modifier = Modifier
						.fillMaxWidth()
						.height(56.dp)
				) {
					Text("Finish Setup", style = MaterialTheme.typography.titleMedium)
				}

				Spacer(Modifier.height(16.dp))
			}
		}
	} // end MaterialTheme
}

// -----------------------------------------------------------------------------
// Section header with step number badge
// -----------------------------------------------------------------------------

@Composable
private fun SectionHeader(number: String, title: String) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier          = Modifier.fillMaxWidth()
	) {
		Surface(
			color    = MaterialTheme.colorScheme.primary,
			shape    = RoundedCornerShape(50),
			modifier = Modifier.size(28.dp)
		) {
			Box(contentAlignment = Alignment.Center) {
				Text(
					number,
					style      = MaterialTheme.typography.labelMedium,
					color      = MaterialTheme.colorScheme.onPrimary,
					fontWeight = FontWeight.Bold
				)
			}
		}
		Spacer(Modifier.width(10.dp))
		Text(
			title,
			style      = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold
		)
	}
}

// -----------------------------------------------------------------------------
// Compact category card (for 2-column results)
// -----------------------------------------------------------------------------

@Composable
private fun CompactCategoryCard(
	category:  FileCategory,
	fileCount: Int,
	modifier:  Modifier = Modifier
) {
	OutlinedCard(modifier = modifier) {
		Row(
			modifier          = Modifier.padding(10.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Image(
				painter            = painterResource(category.onboardingDrawable),
				contentDescription = null,
				contentScale       = ContentScale.Fit,
				modifier           = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
			)
			Spacer(Modifier.width(8.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					category.displayName,
					style      = MaterialTheme.typography.bodySmall,
					fontWeight = FontWeight.SemiBold,
					maxLines   = 1,
					overflow   = TextOverflow.Ellipsis
				)
				Text(
					if (fileCount > 0) "$fileCount files" else "folder set",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			Icon(
				Icons.Default.CheckCircle,
				contentDescription = null,
				tint               = MaterialTheme.colorScheme.primary,
				modifier           = Modifier.size(16.dp)
			)
		}
	}
}

// -----------------------------------------------------------------------------
// Model card (compact, for horizontal scroll)
// -----------------------------------------------------------------------------

@Composable
private fun ModelCardCompact(
	model:    AmigaModel,
	selected: Boolean,
	onClick:  () -> Unit
) {
	val borderColor = if (selected) MaterialTheme.colorScheme.primary
					  else         MaterialTheme.colorScheme.outlineVariant
	val borderWidth = if (selected) 2.dp else 1.dp
	val bgColor     = if (selected) MaterialTheme.colorScheme.primaryContainer
					  else         MaterialTheme.colorScheme.surfaceVariant

	Card(
		modifier = Modifier
			.width(130.dp)
			.border(borderWidth, borderColor, RoundedCornerShape(12.dp))
			.clip(RoundedCornerShape(12.dp))
			.clickable { onClick() },
		colors = CardDefaults.cardColors(containerColor = bgColor),
		shape  = RoundedCornerShape(12.dp)
	) {
		Column(
			modifier            = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 10.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Image(
				painter            = painterResource(artworkForModel(model)),
				contentDescription = model.displayName,
				contentScale       = ContentScale.Fit,
				modifier           = Modifier
					.fillMaxWidth()
					.height(64.dp)
			)
			Spacer(Modifier.height(4.dp))
			Text(
				text       = model.displayName,
				style      = MaterialTheme.typography.bodySmall,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
				textAlign  = TextAlign.Center,
				maxLines   = 1,
				color      = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
							 else         MaterialTheme.colorScheme.onSurfaceVariant
			)
			Text(
				text      = model.chipset,
				style     = MaterialTheme.typography.labelSmall,
				textAlign = TextAlign.Center,
				color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
				maxLines  = 1
			)
			if (selected) {
				Spacer(Modifier.height(2.dp))
				Icon(
					Icons.Default.CheckCircle,
					contentDescription = "Selected",
					tint               = MaterialTheme.colorScheme.primary,
					modifier           = Modifier.size(16.dp)
				)
			}
		}
	}
}
