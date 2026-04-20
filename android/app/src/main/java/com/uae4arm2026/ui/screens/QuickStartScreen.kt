package com.uae4arm2026.ui.screens

import android.os.Environment
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uae4arm2026.R
import com.uae4arm2026.data.AppPreferences
import com.uae4arm2026.data.EmulatorLauncher
import com.uae4arm2026.data.FileManager
import com.uae4arm2026.data.FileRepository
import com.uae4arm2026.data.model.AmigaFile
import com.uae4arm2026.data.model.AmigaModel
import com.uae4arm2026.data.model.EmulatorSettings
import com.uae4arm2026.data.model.FileCategory
import com.uae4arm2026.ui.findActivity
import com.uae4arm2026.ui.navigation.Screen
import com.uae4arm2026.ui.viewmodel.QuickStartViewModel
import com.uae4arm2026.ui.viewmodel.SettingsViewModel
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStartScreen(
    navController: androidx.navigation.NavController? = null,
    viewModel: QuickStartViewModel = viewModel(LocalContext.current.findActivity() as androidx.activity.ComponentActivity),
    settingsViewModel: SettingsViewModel = viewModel(LocalContext.current.findActivity() as androidx.activity.ComponentActivity)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings = settingsViewModel.settings
    val model = settings.baseModel
    val roms by viewModel.availableRoms.collectAsState()
    val selectedWhdloadGame = viewModel.selectedWhdload
    val isScanning by FileRepository.getInstance(context).isScanning.collectAsState()
    val hasRoms = roms.isNotEmpty()
    val canStart = settings.romFile.isNotBlank() || hasRoms
    val romRequiredMessage = stringResource(R.string.msg_rom_required_before_start)

    // ── SAF file picker ───────────────────────────────────────────────────
    var pendingFilePickerCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        uri?.let { uriToFilePath(it)?.let { path -> pendingFilePickerCallback?.invoke(path) } }
        pendingFilePickerCallback = null
    }

    // ── SAF folder picker ───────────────────────────────────────────────
    var pendingFolderPickerCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUriToPath(it)?.let { path -> pendingFolderPickerCallback?.invoke(path) } }
        pendingFolderPickerCallback = null
    }

    fun openFilePicker(category: FileCategory, @Suppress("UNUSED_PARAMETER") extensions: List<String>, onPicked: (String) -> Unit) {
        pendingFilePickerCallback = onPicked
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            val dir = FileManager.getEffectiveCategoryDir(context, category)
            if (dir.exists()) {
                pathToInitialUri(dir.absolutePath)?.let {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
                }
            }
        }
        filePickerLauncher.launch(intent)
    }

    fun openFolderPicker(category: FileCategory, onPicked: (String) -> Unit) {
        pendingFolderPickerCallback = onPicked
        val dir = FileManager.getEffectiveCategoryDir(context, category)
        val initialUri = if (dir.exists()) pathToInitialUri(dir.absolutePath) else null
        folderPickerLauncher.launch(initialUri)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!canStart) {
                        scope.launch { snackbarHostState.showSnackbar(romRequiredMessage) }
                        return@ExtendedFloatingActionButton
                    }
                    if (selectedWhdloadGame != null) {
                        EmulatorLauncher.launchWhdload(context, selectedWhdloadGame.path, settings)
                    } else {
                        EmulatorLauncher.launchWithArgs(context, settingsViewModel.generateLaunchArgs())
                    }
                },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                text = { Text(stringResource(R.string.action_start)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Top bar: title + Settings + Configs
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Quick Start",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick        = { navController?.navigate(Screen.Settings.route) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Settings", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick        = { navController?.navigate(Screen.Configurations.route) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Configs", fontSize = 12.sp) }
                }
            }

            // Kickstart row
            KickstartStatusRow(
                settings     = settings,
                onPickFolder = {
                    openFolderPicker(FileCategory.ROMS) { path ->
                        FileManager.setCategoryLibraryPath(context, FileCategory.ROMS, path)
                        viewModel.rescanRoms()
                    }
                }
            )

            // Model hero (full-width, swipeable)
            ModelHeroFull(
                model      = model,
                onPrevious = { settingsViewModel.applyModel(previousModel(model)) },
                onNext     = { settingsViewModel.applyModel(nextModel(model)) }
            )

            ModelVisualStrip(
                selectedModel = model,
                onSelectModel = { settingsViewModel.applyModel(it) }
            )

            // RAM + chip toggles
            RamChipsRow(
                settings         = settings,
                onUpdateSettings = { update -> settingsViewModel.updateSettings(update) }
            )

            // Drive tiles
            if (model.hasFloppy) {
                DriveIconTile(
                    label        = "DF0",
                    art          = R.drawable.featured_drive_df0,
                    currentPath  = settings.floppy0,
                    onEject      = { settingsViewModel.updateSettings { s -> s.copy(floppy0 = "") } },
                    onPickFile = {
                        openFilePicker(FileCategory.FLOPPIES, listOf("adf", "adz", "dms", "ipf", "hfe")) { path ->
                            settingsViewModel.updateSettings { s -> s.copy(floppy0 = path) }
                        }
                    }
                )
                DriveIconTile(
                    label        = "DF1",
                    art          = R.drawable.featured_drive_df_external,
                    currentPath  = settings.floppy1,
                    onEject      = { settingsViewModel.updateSettings { s -> s.copy(floppy1 = "", floppy1Type = -1) } },
                    onPickFile = {
                        openFilePicker(FileCategory.FLOPPIES, listOf("adf", "adz", "dms", "ipf", "hfe")) { path ->
                            settingsViewModel.updateSettings { s -> s.copy(floppy1 = path, floppy1Type = 0) }
                        }
                    }
                )
            }

            if (model.hasCd) {
                DriveIconTile(
                    label        = "CD",
                    art          = R.drawable.featured_cd32,
                    currentPath  = settings.cdImage,
                    onEject      = { settingsViewModel.updateSettings { s -> s.copy(cdImage = "") } },
                    onPickFile = {
                        openFilePicker(FileCategory.CD_IMAGES, listOf("cue", "iso", "ccd", "mds", "chd")) { path ->
                            settingsViewModel.updateSettings { s -> s.copy(cdImage = path) }
                        }
                    }
                )
            }

            WhdIconTile(
                selected     = selectedWhdloadGame,
                onEject      = { viewModel.selectWhdload(null) },
                onPickFile = {
                    openFilePicker(FileCategory.WHDLOAD_GAMES, listOf("lha", "lzx", "lzh")) { path ->
                        val picked = java.io.File(path)
                        val selected = AmigaFile(
                            path = path,
                            name = picked.name,
                            extension = picked.extension.lowercase(),
                            size = picked.length(),
                            lastModified = picked.lastModified(),
                            category = FileCategory.WHDLOAD_GAMES
                        )
                        viewModel.selectWhdload(selected)
                    }
                }
            )

            // Recent launches
            val allRecent by AppPreferences.getInstance(context).recentLaunches
            val recentLaunches = allRecent.filter { entry ->
                when (entry.optString("type")) {
                    "config", "whdload" -> {
                        val path = entry.optString("path")
                        path.isNotEmpty() && java.io.File(path).exists()
                    }
                    "quickstart" -> listOf("df0", "df1", "cd").all { key ->
                        val p = entry.optString(key)
                        p.isEmpty() || java.io.File(p).exists()
                    }
                    else -> false
                }
            }
            if (recentLaunches.isNotEmpty()) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recent", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        recentLaunches.take(5).forEach { entry ->
                            val label = when (entry.optString("type")) {
                                "quickstart" -> {
                                    val m = entry.optString("model", "?")
                                    val df0 = entry.optString("df0").substringAfterLast('/').takeIf { it.isNotBlank() }
                                    val cd  = entry.optString("cd").substringAfterLast('/').takeIf { it.isNotBlank() }
                                    val media = listOfNotNull(df0, cd).joinToString(", ").takeIf { it.isNotBlank() }
                                    if (media != null) "$m � $media" else m
                                }
                                "config"  -> entry.optString("path").substringAfterLast('/').removeSuffix(".uae").ifEmpty { "Config" }
                                "whdload" -> entry.optString("path").substringAfterLast('/').removeSuffix(".lha").removeSuffix(".lzx").ifEmpty { "WHDLoad" }
                                else      -> "?"
                            }
                            TextButton(
                                onClick = {
                                    when (entry.optString("type")) {
                                        "config"  -> EmulatorLauncher.launchWithConfig(context, entry.optString("path"))
                                        "whdload" -> EmulatorLauncher.launchWhdload(context, entry.optString("path"), settings)
                                        "quickstart" -> {
                                            val rm = AmigaModel.entries.firstOrNull { it.cmdArg == entry.optString("model") } ?: AmigaModel.A500
                                            EmulatorLauncher.launchQuickStart(
                                                context,
                                                rm,
                                                floppyPath    = entry.optString("df0").takeIf { it.isNotBlank() },
                                                floppy1Path   = entry.optString("df1").takeIf { it.isNotBlank() },
                                                cdPath        = entry.optString("cd").takeIf { it.isNotBlank() },
                                                hardDrivePath = entry.optString("hd").takeIf { it.isNotBlank() },
                                                romExtFile    = entry.optString("romExtFile").takeIf { it.isNotBlank() }
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// -- Kickstart status row -------------------------------------------------------

@Composable
private fun KickstartStatusRow(
    settings: EmulatorSettings,
    onPickFolder: () -> Unit
) {
    val hasKickstart = settings.romFile.isNotBlank()
    val needsExtRom = settings.baseModel == AmigaModel.CD32 || settings.baseModel == AmigaModel.CDTV
    val hasExtRom   = settings.romExtFile.isNotBlank()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = Color(0x14F57C00)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter            = painterResource(R.drawable.featured_kickstart_check),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = if (hasKickstart) "Kickstart ready" else "Kickstart missing",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (settings.romFile.isNotBlank()) {
                    Text(
                        text     = settings.romFile.substringAfterLast('/'),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (needsExtRom) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text       = if (hasExtRom) "Ext ROM ready" else "Ext ROM missing",
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (hasExtRom) MaterialTheme.colorScheme.onSurface
                                     else Color(0xFFF44336)
                    )
                    if (hasExtRom) {
                        Text(
                            text     = settings.romExtFile.substringAfterLast('/'),
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            TextButton(onClick = onPickFolder) {
                Text(if (hasKickstart) "Change" else "Import")
            }
        }
    }
}

// -- Model hero (full-width, swipeable) -----------------------------------------

@Composable
private fun ModelHeroFull(
    model: AmigaModel,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape    = RoundedCornerShape(16.dp),
        color    = Color(0x22000000)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .pointerInput(model) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, d -> drag += d },
                        onDragEnd = {
                            if (drag > 40f) onPrevious()
                            else if (drag < -40f) onNext()
                            drag = 0f
                        }
                    )
                }
        ) {
            AnimatedContent(
                targetState  = model,
                transitionSpec = {
                    val fromIdx = AmigaModel.entries.indexOf(initialState)
                    val toIdx   = AmigaModel.entries.indexOf(targetState)
                    if (toIdx > fromIdx) {
                        slideInHorizontally { w -> w } + fadeIn() togetherWith
                        slideOutHorizontally { w -> -w } + fadeOut()
                    } else {
                        slideInHorizontally { w -> -w } + fadeIn() togetherWith
                        slideOutHorizontally { w -> w } + fadeOut()
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label    = "ModelHeroArt"
            ) { targetModel ->
                Image(
                    painter            = painterResource(artworkForModel(targetModel)),
                    contentDescription = targetModel.displayName,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
            }
            Text(
                "?", fontSize = 22.sp, color = Color(0x80FFFFFF),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
            )
            Text(
                "?", fontSize = 22.sp, color = Color(0x80FFFFFF),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                shape    = RoundedCornerShape(999.dp),
                color    = Color(0x80000000)
            ) {
                Text(
                    model.displayName, fontSize = 12.sp, color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelVisualStrip(
    selectedModel: AmigaModel,
    onSelectModel: (AmigaModel) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(AmigaModel.entries, key = { it.name }) { model ->
            val isSelected = model == selectedModel
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Color(0x3310B981) else Color(0x18000000),
                modifier = Modifier
                    .width(110.dp)
                    .clickable { onSelectModel(model) }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painter = painterResource(artworkForModel(model)),
                        contentDescription = model.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                    Text(
                        text = model.displayName,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -- RAM dropdowns + JIT / RTG chips -------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RamChipsRow(
    settings: EmulatorSettings,
    onUpdateSettings: ((EmulatorSettings) -> EmulatorSettings) -> Unit
) {
    val jitOn = settings.jitCacheSize > 0
    val rtgOn = settings.useRtg

    // Row 1: Chip + Fast RAM � each gets half the width, no label crowding
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        RamDropdown(
            label    = "Chip RAM",
            options  = EmulatorSettings.chipRamOptions,
            value    = settings.chipRam,
            modifier = Modifier.weight(1f),
            onSelect = { onUpdateSettings { s -> s.copy(chipRam = it) } }
        )
        RamDropdown(
            label    = "Fast RAM",
            options  = EmulatorSettings.fastRamOptions,
            value    = settings.fastRam,
            modifier = Modifier.weight(1f),
            onSelect = { onUpdateSettings { s -> s.copy(fastRam = it) } }
        )
    }

    // Row 2: Z3 RAM (32-bit only) + JIT + RTG chips
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        if (!settings.address24Bit) {
            RamDropdown(
                label    = "Z3 RAM",
                options  = EmulatorSettings.z3RamOptions,
                value    = settings.z3Ram,
                modifier = Modifier.weight(1f),
                onSelect = { onUpdateSettings { s -> s.copy(z3Ram = it) } }
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        FilterChip(
            selected = jitOn,
            onClick  = { onUpdateSettings { s -> s.copy(jitCacheSize = if (jitOn) 0 else 16384) } },
            label    = { Text("JIT", fontSize = 11.sp) }
        )
        FilterChip(
            selected = rtgOn,
            onClick  = { onUpdateSettings { s -> s.copy(useRtg = !rtgOn) } },
            label    = { Text("RTG", fontSize = 11.sp) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RamDropdown(
    label: String,
    options: List<Pair<Int, String>>,
    value: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.firstOrNull { it.first == value }?.second ?: "$value"
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
        modifier         = modifier
    ) {
        OutlinedTextField(
            value         = displayLabel,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label, fontSize = 9.sp) },
            textStyle     = TextStyle(fontSize = 11.sp),
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier      = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (v, lbl) ->
                DropdownMenuItem(
                    text    = { Text(lbl) },
                    onClick = { onSelect(v); expanded = false }
                )
            }
        }
    }
}

// -- Drive icon tile ------------------------------------------------------------

@Composable
private fun DriveIconTile(
    label: String,
    @DrawableRes art: Int,
    currentPath: String,
    onEject: () -> Unit,
    onPickFile: () -> Unit
) {
    val fileName = currentPath.substringAfterLast('/').ifBlank { "Empty" }
    val hasFile  = currentPath.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = if (hasFile) Color(0x1F10B981) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clickable drive icon � directly opens file picker
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22000000))
                    .clickable(enabled = true) { onPickFile() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter            = painterResource(art),
                    contentDescription = label,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text     = fileName,
                style    = MaterialTheme.typography.bodyMedium,
                color    = if (hasFile) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            )
            if (hasFile) {
                IconButton(onClick = onEject, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Eject, "Eject", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// -- WHDLoad icon tile ----------------------------------------------------------

@Composable
private fun WhdIconTile(
    selected: AmigaFile?,
    onEject: () -> Unit,
    onPickFile: () -> Unit
) {
    val displayName = selected?.name?.removeSuffix(".lha")?.removeSuffix(".lzx") ?: "Empty"
    val hasGame = selected != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = if (hasGame) Color(0x1F10B981) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clickable game icon � directly opens file picker
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0x22000000), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = true) { onPickFile() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = null,
                    modifier           = Modifier.size(28.dp),
                    tint               = if (hasGame) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "LHA", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xAA000000), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .padding(horizontal = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text     = displayName,
                style    = MaterialTheme.typography.bodyMedium,
                color    = if (hasGame) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            )
            if (hasGame) {
                IconButton(onClick = onEject, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Eject, "Eject", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// -- Helpers --------------------------------------------------------------------

private fun previousModel(model: AmigaModel): AmigaModel {
    val all = AmigaModel.entries
    return all[(all.indexOf(model) - 1 + all.size) % all.size]
}

private fun nextModel(model: AmigaModel): AmigaModel {
    val all = AmigaModel.entries
    return all[(all.indexOf(model) + 1) % all.size]
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

