package com.uae4arm2026.ui.screens

import android.os.Environment
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.uae4arm2026.R
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
import com.uae4arm2026.data.AgsDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Section palette (matching UAE4Arm XML drawables) ─────────────────────────
private val BgScreen         = Color(0xFF1a1a2e)
private val BgMedia          = Color(0x1410B981)
private val BorderMedia      = Color(0x3310B981)
private val BgModel          = Color(0x142196F3)
private val BorderModel      = Color(0x332196F3)
private val BgKick           = Color(0x1429B6F6)
private val BorderKick       = Color(0x3329B6F6)
private val TextPrimary      = Color(0xFFFFFFFF)
private val TextSecondary    = Color(0xFFAAAAAA)
private val TextOrange       = Color(0xFFFF8A65)
private val GreenAccent      = Color(0xFF10B981)
private val BtnGreenStart    = Color(0xFF1565C0)
private val BtnGrey          = Color(0xFF37474F)
private val CardRadius       = RoundedCornerShape(10.dp)
private val TransparentBg    = Color(0x00000000)
private val BorderSubtle     = Color(0x44FFFFFF)

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionBox(
    bgColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(bgColor, CardRadius)
            .border(1.dp, borderColor, CardRadius)
            .padding(6.dp),
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

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

private fun treeUriToPath(uri: Uri): String? = try {
    docIdToPath(DocumentsContract.getTreeDocumentId(uri))
} catch (_: Exception) { null }

private enum class PlayMode { FLOPPY, HDF, WHDLOAD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Uae4ArmHomeScreen(
    navController: NavController? = null,
    viewModel: QuickStartViewModel = viewModel(
        LocalContext.current.findActivity() as androidx.activity.ComponentActivity
    ),
    settingsViewModel: SettingsViewModel = viewModel(
        LocalContext.current.findActivity() as androidx.activity.ComponentActivity
    )
) {
    val context  = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = settingsViewModel.settings
    val model    = settings.baseModel
    val isCdConsoleModel = model == AmigaModel.CD32 || model == AmigaModel.CDTV

    val roms         by viewModel.availableRoms.collectAsState()
    val selectedWhd  = viewModel.selectedWhdload
    val isScanning   by FileRepository.getInstance(context).isScanning.collectAsState()
    val canStart     = settings.romFile.isNotBlank() || roms.isNotEmpty()

    // ── SAF file picker ───────────────────────────────────────────────────
    var pendingFileCategory by remember { mutableStateOf(FileCategory.FLOPPIES) }
    var pendingFilePickerCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val callback = pendingFilePickerCallback
        val category = pendingFileCategory
        pendingFilePickerCallback = null
        if (uri != null && callback != null) {
            scope.launch(Dispatchers.IO) {
                val path = FileManager.importFile(context, uri, category)
                if (path != null) withContext(Dispatchers.Main) { callback(path) }
            }
        }
    }

    // ── SAF directory picker ──────────────────────────────────────────────
    var pendingDirPickerCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUriToPath(it)?.let { path -> pendingDirPickerCallback?.invoke(path) } }
        pendingDirPickerCallback = null
    }

    fun openFilePicker(category: FileCategory, @Suppress("UNUSED_PARAMETER") extensions: List<String>, onPicked: (String) -> Unit) {
        pendingFileCategory = category
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

    fun openPicker(category: FileCategory, onPicked: (String) -> Unit) {
        pendingDirPickerCallback = onPicked
        val dir = FileManager.getEffectiveCategoryDir(context, category)
        val initialUri = if (dir.exists()) pathToInitialUri(dir.absolutePath) else null
        dirPickerLauncher.launch(initialUri)
    }

    // ── AGS auto-detect ─────────────────────────────────────────────────
    var agsInstall by remember { mutableStateOf<AgsDetector.AgsInstall?>(null) }
    var agsDismissed by remember { mutableStateOf(false) }
    var agsBusy by remember { mutableStateOf(false) }
    var agsStatusText by remember { mutableStateOf<String?>(null) }
    var agsNeedsBrowse by remember { mutableStateOf(false) }
    LaunchedEffect(settings.hardDrives) {
        val found = withContext(Dispatchers.IO) {
            AgsDetector.detect(context) ?: AgsDetector.detectFromMountedDrives(settings.hardDrives)
        }
        agsInstall = found
    }

    var playMode by remember(isCdConsoleModel) {
        mutableStateOf(if (isCdConsoleModel) PlayMode.WHDLOAD else PlayMode.FLOPPY)
    }
    val agsMountedPaths = agsInstall?.let { AgsDetector.mountableHardDrives(it) }
    val isAgsMountedInHdfTab = playMode == PlayMode.HDF &&
        agsInstall != null &&
        agsMountedPaths != null &&
        settings.hardDrives == agsMountedPaths

    fun resetAgsSetup() {
        val mountedPaths = agsMountedPaths
        if (mountedPaths != null && settings.hardDrives == mountedPaths) {
            settingsViewModel.updateSettings { s -> s.copy(hardDrives = listOf("")) }
        }
        agsInstall = null
        agsNeedsBrowse = true
        agsStatusText = "AGS reset. Tap Browse to choose the folder again"
        agsDismissed = true
    }

    fun resetAllMedia() {
        viewModel.selectWhdload(null)
        settingsViewModel.updateSettings { s ->
            s.copy(
                floppy0 = "",
                floppy1 = "",
                floppy2 = "",
                floppy3 = "",
                cdImage = "",
                hardDrives = listOf("")
            )
        }
        agsInstall = null
        agsDismissed = true
        agsNeedsBrowse = true
        agsStatusText = "Media reset. AGS will require Browse again"
    }

    // ── Layout ────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GreenAccent)
            }

            // ── AGS auto-detect banner ────────────────────────────────
            if (agsInstall != null && !agsDismissed) {
                AgsBanner(
                    install = agsInstall!!,
                    hasKickstart = agsInstall!!.romFile != null || settings.romFile.isNotBlank(),
                    onLaunch = {
                        val configPath = AgsDetector.writeConfig(
                            context,
                            agsInstall!!,
                            settings.romFile.ifBlank { null },
                            AgsDetector.AGS_WIDESCREEN_RTG_WIDTH,
                            AgsDetector.AGS_WIDESCREEN_RTG_HEIGHT,
                        )
                        EmulatorLauncher.launchWithConfig(context, configPath)
                    },
                    onDismiss = { agsDismissed = true }
                )
            }

            // ── Kickstart row ─────────────────────────────────────────
            KickstartRow(
                settings      = settings,
                onPickRomFile = {
                    openFilePicker(FileCategory.ROMS, listOf("rom", "bin", "kick")) { path ->
                        settingsViewModel.updateSettings { s -> s.copy(romFile = path) }
                    }
                },
                onOpenConfigs        = { navController?.navigate(Screen.Configurations.route) },
                onOpenSettings       = { navController?.navigate(Screen.Settings.route) },
                onRerunWalkthrough   = { navController?.navigate(Screen.Onboarding.route) }
            )

            // ── Model area ────────────────────────────────────────────
            SectionBox(
                bgColor     = BgMedia,
                borderColor = BorderMedia,
                modifier    = Modifier.fillMaxWidth().weight(1f)
            ) {
                Row(
                    modifier          = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.Top
                ) {
                    LeftControlsColumn(
                        modifier         = Modifier.weight(1f).fillMaxHeight(),
                        settings         = settings,
                        onUpdateSettings = { settingsViewModel.updateSettings(it) }
                    )
                    ModelImageBox(
                        modifier   = Modifier.weight(1.5f).fillMaxHeight(),
                        model      = model,
                        onPrevious = { settingsViewModel.applyModel(prevModel(model)) },
                        onNext     = { settingsViewModel.applyModel(nextModel(model)) }
                    )
                }
            }

            if (isCdConsoleModel) {
                // ── CD console: disc card (same style as floppy/HDF) ──
                SectionBox(bgColor = BgKick, borderColor = BorderKick, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DriveIconCard(
                            label       = "CD0",
                            driveArt    = R.drawable.featured_drive_dh0,
                            currentPath = settings.cdImage,
                            onEject     = { settingsViewModel.updateSettings { s -> s.copy(cdImage = "") } },
                            onPickFile  = {
                                openFilePicker(FileCategory.CD_IMAGES, listOf("cue", "iso", "chd", "nrg", "mds")) { path ->
                                    settingsViewModel.updateSettings { s -> s.copy(cdImage = path) }
                                }
                            }
                        )
                    }
                }
            } else {
                // ── Mode selector tabs ────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ModeTab("💾  Floppy",  playMode == PlayMode.FLOPPY,  Modifier.weight(1f)) { playMode = PlayMode.FLOPPY }
                    ModeTab("🖥  HDF",     playMode == PlayMode.HDF,     Modifier.weight(1f)) { playMode = PlayMode.HDF }
                    ModeTab("📦  WHDLoad", playMode == PlayMode.WHDLOAD, Modifier.weight(1f)) { playMode = PlayMode.WHDLOAD }
                }

                // ── Media content for selected mode ───────────────────
                SectionBox(bgColor = BgKick, borderColor = BorderKick, modifier = Modifier.fillMaxWidth()) {
                    when (playMode) {
                        PlayMode.FLOPPY -> {
                            val activeFloppyCount = when {
                                settings.floppy3Type != -1 -> 4
                                settings.floppy2Type != -1 -> 3
                                settings.floppy1Type != -1 -> 2
                                else -> 1
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                DriveIconCard(
                                    label = "DF0", driveArt = R.drawable.featured_drive_df0,
                                    currentPath = settings.floppy0,
                                    onEject    = { settingsViewModel.updateSettings { s -> s.copy(floppy0 = "") } },
                                    onPickFile = {
                                        openFilePicker(FileCategory.FLOPPIES, listOf("adf", "adz", "dms", "ipf", "hfe")) { path ->
                                            settingsViewModel.updateSettings { s -> s.copy(floppy0 = path) }
                                        }
                                    }
                                )
                                if (activeFloppyCount >= 2) DriveIconCard(
                                    label = "DF1", driveArt = R.drawable.featured_drive_df_external,
                                    currentPath = settings.floppy1,
                                    onEject    = { settingsViewModel.updateSettings { s -> s.copy(floppy1 = "", floppy1Type = -1) } },
                                    onPickFile = {
                                        openFilePicker(FileCategory.FLOPPIES, listOf("adf", "adz", "dms", "ipf", "hfe")) { path ->
                                            settingsViewModel.updateSettings { s -> s.copy(floppy1 = path, floppy1Type = 0) }
                                        }
                                    }
                                )
                                if (activeFloppyCount >= 3) DriveIconCard(
                                    label = "DF2", driveArt = R.drawable.featured_drive_df_external,
                                    currentPath = settings.floppy2,
                                    onEject    = { settingsViewModel.updateSettings { s -> s.copy(floppy2 = "", floppy2Type = -1) } },
                                    onPickFile = {
                                        openFilePicker(FileCategory.FLOPPIES, listOf("adf", "adz", "dms", "ipf", "hfe")) { path ->
                                            settingsViewModel.updateSettings { s -> s.copy(floppy2 = path, floppy2Type = 0) }
                                        }
                                    }
                                )
                                if (activeFloppyCount >= 4) DriveIconCard(
                                    label = "DF3", driveArt = R.drawable.featured_drive_df_external,
                                    currentPath = settings.floppy3,
                                    onEject    = { settingsViewModel.updateSettings { s -> s.copy(floppy3 = "", floppy3Type = -1) } },
                                    onPickFile = {
                                        openFilePicker(FileCategory.FLOPPIES, listOf("adf", "adz", "dms", "ipf", "hfe")) { path ->
                                            settingsViewModel.updateSettings { s -> s.copy(floppy3 = path, floppy3Type = 0) }
                                        }
                                    }
                                )
                                if (activeFloppyCount < 4) AddDriveCard {
                                    settingsViewModel.updateSettings { s ->
                                        when (activeFloppyCount) {
                                            1 -> s.copy(floppy1Type = 0)
                                            2 -> s.copy(floppy2Type = 0)
                                            3 -> s.copy(floppy3Type = 0)
                                            else -> s
                                        }
                                    }
                                }
                            }
                        }

                        PlayMode.HDF -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    settings.hardDrives.forEachIndexed { i, path ->
                                        DriveIconCard(
                                            label    = "DH$i",
                                            driveArt = R.drawable.featured_drive_dh0,
                                            currentPath = path,
                                            onEject = {
                                                settingsViewModel.updateSettings { s ->
                                                    val updated = s.hardDrives.toMutableList()
                                                    if (i == updated.lastIndex && updated.size > 1) updated.removeAt(i)
                                                    else updated[i] = ""
                                                    s.copy(hardDrives = updated)
                                                }
                                            },
                                            onPickFile = {
                                                openFilePicker(FileCategory.HARD_DRIVES, listOf("hdf", "hdi", "vhd")) { picked ->
                                                    settingsViewModel.updateSettings { s ->
                                                        val updated = s.hardDrives.toMutableList().apply { this[i] = picked }
                                                        s.copy(hardDrives = updated)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    if (settings.hardDrives.size < 10) AddDriveCard {
                                        settingsViewModel.updateSettings { s -> s.copy(hardDrives = s.hardDrives + "") }
                                    }
                                    AgsDriveCard(
                                        detected = agsInstall != null,
                                        busy = agsBusy,
                                        onMount = {
                                            val mountInstall: (AgsDetector.AgsInstall?) -> Unit = { install ->
                                                scope.launch {
                                                    agsBusy = true
                                                    agsStatusText = null
                                                    agsInstall = install
                                                    if (install != null) {
                                                        val drives = AgsDetector.mountableHardDrives(install).ifEmpty { listOf("") }
                                                        settingsViewModel.applyModel(AmigaModel.A1200)
                                                        settingsViewModel.updateSettings { s ->
                                                            s.copy(
                                                                address24Bit = false,
                                                                cpuSpeed = "max",
                                                                cycleExact = false,
                                                                fpuModel = 68882,
                                                                jitCacheSize = 16384,
                                                                z3Ram = 512,
                                                                useRtg = true,
                                                                romFile = install.romFile ?: s.romFile,
                                                                hardDrives = drives
                                                            )
                                                        }
                                                        agsStatusText = "Mounted ${drives.size} AGS hard drives"
                                                    } else {
                                                        agsStatusText = "Selected folder is not a valid AGS_UAE install"
                                                    }
                                                    agsBusy = false
                                                }
                                            }

                                            if (agsInstall != null && !agsNeedsBrowse) {
                                                mountInstall(agsInstall)
                                            } else {
                                                openPicker(FileCategory.HARD_DRIVES) { pickedPath ->
                                                    scope.launch {
                                                        agsBusy = true
                                                        agsStatusText = "Checking $pickedPath"
                                                        val install = withContext(Dispatchers.IO) {
                                                            AgsDetector.detectFromPath(pickedPath)
                                                        }
                                                        agsBusy = false
                                                        if (install != null) {
                                                            agsNeedsBrowse = false
                                                            agsDismissed = false
                                                        }
                                                        mountInstall(install)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    agsStatusText?.let { status ->
                                        Text(
                                            text = status,
                                            style = TextStyle(
                                                fontSize = 8.sp,
                                                color = if (agsInstall != null) GreenAccent else TextOrange,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    if (agsInstall != null || isAgsMountedInHdfTab || agsNeedsBrowse) {
                                        TextButton(
                                            onClick = { resetAgsSetup() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("Reset AGS", fontSize = 9.sp, color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }

                        PlayMode.WHDLOAD -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier          = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = {
                                        openFilePicker(FileCategory.WHDLOAD_GAMES, listOf("lha", "lzx", "lzh")) { path ->
                                            val f = java.io.File(path)
                                            viewModel.selectWhdload(AmigaFile(
                                                path         = path,
                                                name         = f.name,
                                                extension    = f.extension.lowercase(),
                                                size         = f.length(),
                                                lastModified = f.lastModified(),
                                                category     = FileCategory.WHDLOAD_GAMES
                                            ))
                                        }
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = "Select WHDLoad",
                                        tint = GreenAccent, modifier = Modifier.size(28.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = selectedWhd?.name
                                        ?.removeSuffix(".lha")?.removeSuffix(".lzx")?.removeSuffix(".lzh")
                                        ?: "Tap to select WHDLoad game (.lha)",
                                    style    = TextStyle(fontSize = 12.sp, color = if (selectedWhd != null) TextPrimary else TextSecondary),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selectedWhd != null) {
                                    IconButton(onClick = { viewModel.selectWhdload(null) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Eject, contentDescription = "Eject",
                                            tint = TextSecondary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }

            }

            // ── START always at bottom ───────────────────────────────
            val startEnabled = if (isCdConsoleModel) {
                canStart
            } else {
                when (playMode) {
                    PlayMode.FLOPPY  -> canStart
                    PlayMode.HDF     -> canStart && settings.hardDrives.any { it.isNotBlank() }
                    PlayMode.WHDLOAD -> selectedWhd != null
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { resetAllMedia() },
                    colors = ButtonDefaults.buttonColors(containerColor = BtnGrey),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text("Reset Media", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Button(
                    onClick = {
                        if (isCdConsoleModel) {
                            EmulatorLauncher.launchWithArgs(context, settingsViewModel.generateLaunchArgs())
                        } else {
                            when (playMode) {
                                PlayMode.WHDLOAD -> selectedWhd?.let {
                                    EmulatorLauncher.launchWhdload(context, it.path, settings)
                                }
                                PlayMode.HDF -> {
                                    if (isAgsMountedInHdfTab && agsInstall != null) {
                                        val configPath = AgsDetector.writeConfig(
                                            context,
                                            agsInstall!!,
                                            settings.romFile.ifBlank { null },
                                            AgsDetector.AGS_WIDESCREEN_RTG_WIDTH,
                                            AgsDetector.AGS_WIDESCREEN_RTG_HEIGHT,
                                        )
                                        EmulatorLauncher.launchWithConfig(context, configPath)
                                    } else {
                                        EmulatorLauncher.launchWithArgs(context, settingsViewModel.generateLaunchArgs())
                                    }
                                }
                                else -> EmulatorLauncher.launchWithArgs(context, settingsViewModel.generateLaunchArgs())
                            }
                        }
                    },
                    enabled  = startEnabled,
                    colors   = ButtonDefaults.buttonColors(containerColor = BtnGreenStart),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text("▶  START", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kickstart status row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KickstartRow(
    settings: EmulatorSettings,
    onPickRomFile: () -> Unit,
    onOpenConfigs: () -> Unit,
    onOpenSettings: () -> Unit,
    onRerunWalkthrough: () -> Unit
) {
    SectionBox(
        bgColor     = BgKick,
        borderColor = BorderKick,
        modifier    = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            Image(
                painter            = painterResource(R.drawable.featured_kickstart_check),
                contentDescription = "Kickstart",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = if (settings.romFile.isNotBlank())
                        "KS: " + settings.romFile.substringAfterLast('/')
                    else
                        "Kickstart: (not selected)",
                    color    = if (settings.romFile.isNotBlank()) TextPrimary else TextOrange,
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }
            TextButton(onClick = onPickRomFile, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("ROM", fontSize = 10.sp, color = TextSecondary)
            }
            Button(
                onClick          = onOpenConfigs,
                colors           = ButtonDefaults.buttonColors(containerColor = BtnGrey),
                modifier         = Modifier.height(34.dp),
                contentPadding   = PaddingValues(horizontal = 10.dp)
            ) {
                Text("Configs", fontSize = 11.sp, color = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick          = onOpenSettings,
                colors           = ButtonDefaults.buttonColors(containerColor = BtnGrey),
                modifier         = Modifier.height(34.dp),
                contentPadding   = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null,
                    tint = TextPrimary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Settings", fontSize = 11.sp, color = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick  = onRerunWalkthrough,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.Replay,
                    contentDescription = "Run setup wizard",
                    tint     = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Left controls column (memory + JIT)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeftControlsColumn(
    modifier: Modifier = Modifier,
    settings: EmulatorSettings,
    onUpdateSettings: ((EmulatorSettings) -> EmulatorSettings) -> Unit
) {
    Column(
        modifier  = modifier.padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Memory row — 2-row grid so each dropdown gets half the width
        SectionBox(
            bgColor     = BgModel,
            borderColor = BorderModel,
            modifier    = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chip", fontSize = 9.sp, color = TextSecondary, modifier = Modifier.padding(end = 2.dp))
                RamSpinner(
                    value    = settings.chipRam,
                    options  = EmulatorSettings.chipRamOptions,
                    modifier = Modifier.weight(1f),
                    onSelect = { onUpdateSettings { s -> s.copy(chipRam = it) } }
                )
                Text("Fast", fontSize = 9.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, end = 2.dp))
                RamSpinner(
                    value    = settings.fastRam,
                    options  = EmulatorSettings.fastRamOptions,
                    modifier = Modifier.weight(1f),
                    onSelect = { onUpdateSettings { s -> s.copy(fastRam = it) } }
                )
                if (settings.useRtg) {
                    Text("Z3", fontSize = 9.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, end = 2.dp))
                    RamSpinner(
                        value    = settings.z3Ram,
                        options  = EmulatorSettings.z3RamOptions,
                        modifier = Modifier.weight(1f),
                        onSelect = { onUpdateSettings { s -> s.copy(z3Ram = it) } }
                    )
                }
            }
        }

        val jitOn = settings.jitCacheSize > 0
        val rtgOn = settings.useRtg
        val ledsOn = settings.showLeds
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected  = jitOn,
                onClick   = { onUpdateSettings { s -> s.copy(jitCacheSize = if (jitOn) 0 else 16384) } },
                label     = { Text("JIT", fontSize = 10.sp) },
                modifier  = Modifier.weight(1f),
                colors    = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenAccent,
                    selectedLabelColor     = TextPrimary,
                    containerColor         = BtnGrey,
                    labelColor             = TextSecondary
                )
            )
            FilterChip(
                selected  = rtgOn,
                onClick   = {
                    onUpdateSettings { s ->
                        if (rtgOn) {
                            s.copy(useRtg = false)
                        } else {
                            s.copy(useRtg = true, z3Ram = if (s.z3Ram == 0) 16 else s.z3Ram)
                        }
                    }
                },
                label     = { Text("RTG", fontSize = 10.sp) },
                modifier  = Modifier.weight(1f),
                colors    = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenAccent,
                    selectedLabelColor     = TextPrimary,
                    containerColor         = BtnGrey,
                    labelColor             = TextSecondary
                )
            )
            FilterChip(
                selected  = ledsOn,
                onClick   = { onUpdateSettings { s -> s.copy(showLeds = !ledsOn) } },
                label     = { Text("LEDs", fontSize = 10.sp) },
                modifier  = Modifier.weight(1f),
                colors    = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenAccent,
                    selectedLabelColor     = TextPrimary,
                    containerColor         = BtnGrey,
                    labelColor             = TextSecondary
                )
            )
        }
        Text(
            "Swipe or tap arrows to change model",
            fontSize = 8.sp,
            color    = TextSecondary,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RamSpinner(
    value: Int,
    options: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: "$value"

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
        modifier         = modifier
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .border(1.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
                .background(Color(0xFF111111), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text     = label,
                fontSize = 11.sp,
                color    = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded, Modifier.size(16.dp))
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (v, lbl) ->
                DropdownMenuItem(
                    text    = { Text(lbl, fontSize = 12.sp) },
                    onClick = { onSelect(v); expanded = false }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Model image (swipeable)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModelImageBox(
    modifier: Modifier = Modifier,
    model: AmigaModel,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val all = AmigaModel.entries
    Box(
        modifier = modifier
            .pointerInput(model) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart      = { drag = 0f },
                    onHorizontalDrag = { _, d -> drag += d },
                    onDragEnd        = {
                        when {
                            drag >  24f -> onPrevious()
                            drag < -24f -> onNext()
                        }
                        drag = 0f
                    }
                )
            }
    ) {
        // Slide the artwork in/out based on direction
        AnimatedContent(
            targetState    = model,
            transitionSpec = {
                val iIdx = all.indexOf(initialState)
                val tIdx = all.indexOf(targetState)
                val goForward = when {
                    iIdx == all.lastIndex && tIdx == 0 -> true
                    iIdx == 0 && tIdx == all.lastIndex -> false
                    else -> tIdx > iIdx
                }
                if (goForward)
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                else
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
            },
            label    = "model_slide",
            modifier = Modifier.fillMaxSize()
        ) { m ->
            Image(
                painter            = painterResource(artworkFor(m)),
                contentDescription = m.displayName,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize()
            )
        }
        // Invisible tap regions
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onPrevious))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onNext))
        }
        // Arrow overlays
        Surface(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).clickable(onClick = onPrevious),
            shape    = RoundedCornerShape(999.dp),
            color    = Color(0x80000000)
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous model",
                tint = TextPrimary, modifier = Modifier.padding(8.dp).size(22.dp))
        }
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).clickable(onClick = onNext),
            shape    = RoundedCornerShape(999.dp),
            color    = Color(0x80000000)
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next model",
                tint = TextPrimary, modifier = Modifier.padding(8.dp).size(22.dp))
        }
        // Model name pill
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter),
            shape    = RoundedCornerShape(999.dp),
            color    = Color(0x80000000)
        ) {
            Text(model.displayName, fontSize = 11.sp, color = TextPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Drive icon card (vertical: image-top, label + filename below)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DriveIconCard(
    label: String,
    @DrawableRes driveArt: Int,
    currentPath: String,
    onEject: () -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileName = currentPath.substringAfterLast('/').ifBlank { "" }
    val hasFile  = currentPath.isNotBlank()

    Box(modifier = modifier.width(80.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (hasFile) BorderKick else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                .background(if (hasFile) BgKick else Color(0x0AFFFFFF), RoundedCornerShape(8.dp))
                .clickable(onClick = onPickFile)
                .padding(6.dp)
        ) {
            Image(
                painter            = painterResource(driveArt),
                contentDescription = label,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.size(56.dp, 42.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = label,
                style = TextStyle(fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text      = if (hasFile) fileName else "empty",
                style     = TextStyle(fontSize = 8.sp, color = if (hasFile) TextPrimary else Color(0x55FFFFFF)),
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }
        if (hasFile) {
            IconButton(
                onClick  = onEject,
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Eject $label",
                    tint     = TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AddDriveCard(onAdd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onAdd)
            .padding(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(56.dp, 42.dp)
                .background(Color(0x1410B981), RoundedCornerShape(4.dp))
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add drive", tint = GreenAccent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("Add", style = TextStyle(fontSize = 10.sp, color = GreenAccent, fontWeight = FontWeight.Bold), maxLines = 1)
        // Spacer to match card height of DriveIconCard
        Text("", style = TextStyle(fontSize = 8.sp, color = Color.Transparent), maxLines = 1)
    }
}

@Composable
private fun AgsDriveCard(
    detected: Boolean,
    busy: Boolean,
    onMount: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .border(1.dp, if (detected) BorderMedia else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .background(if (detected) BgMedia else Color(0x0AFFFFFF), RoundedCornerShape(8.dp))
            .clickable(enabled = !busy, onClick = onMount)
            .padding(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp, 42.dp)
                .background(Color(0x1410B981), RoundedCornerShape(4.dp))
        ) {
            Image(
                painter = painterResource(R.drawable.featured_a1200),
                contentDescription = "Mount AGS",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(52.dp, 38.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("AGS", style = TextStyle(fontSize = 10.sp, color = GreenAccent, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(
            when {
                busy -> "Scanning"
                detected -> "Mount"
                else -> "Browse"
            },
            style = TextStyle(fontSize = 8.sp, color = TextSecondary),
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WHDLoad row
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhdloadRow(
    selected: AmigaFile?,
    canStart: Boolean,
    onEject: () -> Unit,
    onPickFile: () -> Unit,
    onStart: () -> Unit
) {
    val displayName = selected?.name
        ?.removeSuffix(".lha")?.removeSuffix(".lzx")?.removeSuffix(".lzh")
        ?: "WHDLoad: tap icon to select"

    SectionBox(
        bgColor     = BgMedia,
        borderColor = BorderMedia,
        modifier    = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Archive, contentDescription = null,
                tint     = GreenAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onPickFile, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = "Select WHDLoad",
                    tint = GreenAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "LHA",
                style = TextStyle(fontSize = 9.sp, color = GreenAccent, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = displayName,
                style = TextStyle(fontSize = 10.sp, color = if (selected != null) TextPrimary else TextSecondary),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected != null) {
                IconButton(onClick = onEject, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Eject, contentDescription = "Eject",
                        tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = BtnGreenStart),
                modifier = Modifier.height(34.dp).width(88.dp),
                enabled = canStart
            ) {
                Text(
                    text = "START",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CD Launch row (CD32 / CDTV)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CdLaunchRow(
    currentPath: String,
    canStart: Boolean,
    onEject: () -> Unit,
    onPickFile: () -> Unit,
    onStart: () -> Unit
) {
    val displayName = currentPath.substringAfterLast('/').ifBlank { "CD: tap icon to insert disc" }
    val hasFile = currentPath.isNotBlank()

    SectionBox(
        bgColor     = BgMedia,
        borderColor = BorderMedia,
        modifier    = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPickFile, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = "Select CD image",
                    tint = GreenAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text  = "CD",
                style = TextStyle(fontSize = 9.sp, color = GreenAccent, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text     = displayName,
                style    = TextStyle(fontSize = 10.sp, color = if (hasFile) TextPrimary else TextSecondary),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasFile) {
                IconButton(onClick = onEject, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Eject, contentDescription = "Eject",
                        tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick  = onStart,
                colors   = ButtonDefaults.buttonColors(containerColor = BtnGreenStart),
                modifier = Modifier.height(34.dp).width(88.dp),
                enabled  = canStart
            ) {
                Text(
                    text       = "START",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    maxLines   = 1
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun prevModel(m: AmigaModel): AmigaModel {
    val all = AmigaModel.entries
    return all[(all.indexOf(m) - 1 + all.size) % all.size]
}

private fun nextModel(m: AmigaModel): AmigaModel {
    val all = AmigaModel.entries
    return all[(all.indexOf(m) + 1) % all.size]
}

@DrawableRes
private fun artworkFor(model: AmigaModel): Int = when (model) {
    AmigaModel.A1200 -> R.drawable.featured_a1200
    AmigaModel.A3000 -> R.drawable.featured_a3000
    AmigaModel.A4000 -> R.drawable.featured_a4000
    AmigaModel.CD32,
    AmigaModel.CDTV  -> R.drawable.featured_cd32
    else             -> R.drawable.featured_a500
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode tab button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick        = onClick,
        colors         = ButtonDefaults.buttonColors(
            containerColor = if (selected) GreenAccent else BtnGrey,
            contentColor   = TextPrimary
        ),
        modifier       = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Text(
            label,
            fontSize   = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Storage permission banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgsBanner(
    install: AgsDetector.AgsInstall,
    hasKickstart: Boolean,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A237E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x664FC3F7), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Image(
            painter             = painterResource(R.drawable.featured_a1200),
            contentDescription  = null,
            contentScale        = ContentScale.Fit,
            modifier            = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "AGS Amiga Game System detected",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Text(
                install.agsDir.absolutePath,
                style    = TextStyle(fontSize = 9.sp, color = TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!hasKickstart) {
                Text(
                    "Kickstart ROM not configured — select an A1200 Kickstart in the Kickstart row or Settings.",
                    style = TextStyle(fontSize = 9.sp, color = Color(0xFFFF8A65))
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Button(
            onClick        = onLaunch,
            enabled        = hasKickstart,
            colors         = ButtonDefaults.buttonColors(containerColor = BtnGreenStart),
            modifier       = Modifier.height(30.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("LAUNCH", fontSize = 11.sp, color = TextPrimary)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint     = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
