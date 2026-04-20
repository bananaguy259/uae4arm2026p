package com.uae4arm2026.data

import android.content.Context
import android.content.Intent
import com.uae4arm2026.Uae4ArmEmulatorActivity
import com.uae4arm2026.data.model.EmulatorSettings
import com.uae4arm2026.data.model.AmigaModel
import com.uae4arm2026.data.model.FileCategory
import org.json.JSONObject
import java.io.File

object EmulatorLauncher {

	/**
	 * Launch emulation with a Quick Start model and optional media.
	 * Uses --model + -0/-s for floppy/CD, plus -G to skip ImGui GUI.
	 */
	fun launchQuickStart(
		context: Context,
		model: AmigaModel,
		floppyPath: String? = null,
		floppy1Path: String? = null,
		cdPath: String? = null,
		hardDrivePath: String? = null,
		useRtg: Boolean = false,
		romExtFile: String? = null
	) {
		val args = mutableListOf("--rescan-roms", "--model", model.cmdArg)

		if (floppyPath != null && model.hasFloppy) {
			args.addAll(listOf("-0", floppyPath))
		}
		if (floppy1Path != null && model.hasFloppy) {
			args.addAll(listOf("-1", floppy1Path))
		}
		if (cdPath != null && model.hasCd) {
			args.addAll(listOf("-s", "cdimage0=$cdPath"))
		}
		if (!romExtFile.isNullOrBlank() && model.hasCd) {
			args.addAll(listOf("-s", "kickstart_ext_rom_file=$romExtFile"))
		}
		if (hardDrivePath != null) {
			// Mount as DH0 using new-style hardfile syntax: rw,DEV:PATH,sectors,surfaces,reserved,blocksize,bootpri
			args.addAll(listOf("-s", "hardfile2=rw,DH0:\"$hardDrivePath\",32,1,2,512,0"))
		}
		if (useRtg) {
			args.addAll(listOf("-s", "gfxcard_type=ZorroIII"))
			args.addAll(listOf("-s", "gfxcard_size=8"))
		}

		args.add("-G") // Skip ImGui GUI, start emulation directly

		// Track recent launch
		AppPreferences.getInstance(context).addRecentLaunch(JSONObject().apply {
			put("type", "quickstart")
			put("model", model.cmdArg)
			put("df0", floppyPath ?: "")
			put("df1", floppy1Path ?: "")
			put("cd", cdPath ?: "")
			put("hd", hardDrivePath ?: "")
			put("romExtFile", romExtFile ?: "")
		})

		launchSdlActivity(context, args.toTypedArray())
	}

	/**
	 * Launch emulation from a saved .uae config file.
	 */
	fun launchWithConfig(context: Context, configPath: String, skipGui: Boolean = true) {
		val args = mutableListOf("--rescan-roms", "--config", configPath)
		if (skipGui) args.add("-G")

		AppPreferences.getInstance(context).addRecentLaunch(JSONObject().apply {
			put("type", "config")
			put("path", configPath)
		})
		launchSdlActivity(context, args.toTypedArray())
	}

	/**
	 * Launch a WHDLoad game via the --autoload mechanism.
	 * The emulator auto-configures based on its XML database match.
	 */
	fun launchWhdload(context: Context, lhaPath: String) {
		val args = arrayOf("--rescan-roms", "--autoload", lhaPath, "-G")
		AppPreferences.getInstance(context).addRecentLaunch(JSONObject().apply {
			put("type", "whdload")
			put("path", lhaPath)
		})
		launchSdlActivity(context, args)
	}

	fun launchWhdload(context: Context, lhaPath: String, settings: EmulatorSettings) {
		val configFile = ConfigGenerator.writeConfig(context, settings, ".current_settings.uae")
		val args = arrayOf("--rescan-roms", "--config", configFile.absolutePath, "--autoload", lhaPath, "-G")
		AppPreferences.getInstance(context).addRecentLaunch(JSONObject().apply {
			put("type", "whdload")
			put("path", lhaPath)
		})
		launchSdlActivity(context, args)
	}

	/**
	 * Launch emulation with pre-built args (used by SettingsScreen).
	 */
	fun launchWithArgs(context: Context, args: Array<String>) {
		launchSdlActivity(context, args)
	}

	private const val SESSION_MARKER = ".emulator_session"
	private const val CLEAN_EXIT_MARKER = ".clean_exit"
	private const val PENDING_LAUNCH_MARKER = ".pending_emulator_launch"

	/**
	 * @param trackSession If true, writes a crash-detection marker and marks the
	 *                     launch for review-prompt tracking. Set to false for
	 *                     GUI-only sessions (Advanced/ImGui) where process exit
	 *                     is expected and should not trigger the crash dialog.
	 */
	private fun launchSdlActivity(context: Context, args: Array<String>, trackSession: Boolean = true) {
		if (trackSession) {
			writeSessionMarker(context)
			writePendingLaunchMarker(context)
			(context as? com.uae4arm2026.ui.MainActivity)?.markEmulatorLaunched()
		}

		// Replace --rescan-roms with a no-op if ROM files haven't changed since last scan
		val filteredArgs = if (needsRomRescan(context)) {
			args
		} else {
			args.filter { it != "--rescan-roms" }.toTypedArray()
		}
		val finalArgs = buildManagedPathOverrides(context) + filteredArgs

		val intent = Intent(context, Uae4ArmEmulatorActivity::class.java)
		intent.putExtra("SDL_ARGS", finalArgs)
		context.startActivity(intent)
	}

	private fun buildManagedPathOverrides(context: Context): Array<String> {
		val appRoot = FileManager.getAppStoragePath(context)
		if (appRoot.isBlank()) return emptyArray()

		val overrides = linkedMapOf<String, String>()
		overrides["config_path"] = appRoot
		overrides["controllers_path"] = File(appRoot, "controllers").absolutePath
		overrides["whdboot_path"] = File(appRoot, "whdboot").absolutePath
		overrides["whdload_arch_path"] = FileManager.getEffectiveCategoryDir(context, FileCategory.WHDLOAD_GAMES).absolutePath
		overrides["floppy_path"] = FileManager.getEffectiveCategoryDir(context, FileCategory.FLOPPIES).absolutePath
		overrides["harddrive_path"] = FileManager.getEffectiveCategoryDir(context, FileCategory.HARD_DRIVES).absolutePath
		overrides["cdrom_path"] = FileManager.getEffectiveCategoryDir(context, FileCategory.CD_IMAGES).absolutePath
		overrides["rom_path"] = FileManager.getEffectiveCategoryDir(context, FileCategory.ROMS).absolutePath

		return overrides
			.filterValues { it.isNotBlank() }
			.flatMap { (key, value) -> listOf("-o", "$key=$value") }
			.toTypedArray()
	}

	/**
	 * Check if ROM files have changed since the last launch.
	 * Checks all directories that FileManager.scanForCategory(ROMS) scans:
	 * roms/, root app dir, and whdboot/game-data/Kickstarts/.
	 */
	private fun needsRomRescan(context: Context): Boolean {
		val prefs = AppPreferences.getInstance(context)
		val base = FileManager.getAppStoragePath(context)
		val dirs = mutableListOf(
			FileManager.getCategoryDir(context, FileCategory.ROMS),
			File(base),
			File(base, "whdboot/game-data/Kickstarts")
		)
		FileManager.getCategoryLibraryPath(context, FileCategory.ROMS)?.let { dirs.add(File(it)) }
		val currentFingerprint = dirs.joinToString("|") { computeDirFingerprint(it) }

		if (currentFingerprint == prefs.lastRomFingerprint) return false

		prefs.lastRomFingerprint = currentFingerprint
		return true
	}

	private fun computeDirFingerprint(dir: File): String {
		if (!dir.exists()) return "0:0:0"
		val romFiles = dir.listFiles { f ->
			f.isFile && f.extension.lowercase() in FileCategory.ROMS.extensions
		} ?: return "0:0:0"
		val count = romFiles.size
		val totalSize = romFiles.sumOf { it.length() }
		val latestModified = romFiles.maxOfOrNull { it.lastModified() } ?: 0L
		return "$count:$totalSize:$latestModified"
	}

	private fun writeSessionMarker(context: Context) {
		try {
			val marker = File(context.getExternalFilesDir(null), SESSION_MARKER)
			marker.writeText(System.currentTimeMillis().toString())
		} catch (_: Exception) {
			// Non-critical — crash detection is best-effort
		}
	}

	private fun writePendingLaunchMarker(context: Context) {
		try {
			File(context.getExternalFilesDir(null), PENDING_LAUNCH_MARKER).writeText(System.currentTimeMillis().toString())
		} catch (_: Exception) {
			// Best-effort
		}
	}

	/**
	 * Delete the session marker. Called by Uae4ArmEmulatorActivity on normal exit.
	 */
	fun clearSessionMarker(context: Context) {
		try {
			File(context.getExternalFilesDir(null), SESSION_MARKER).delete()
			File(context.getExternalFilesDir(null), PENDING_LAUNCH_MARKER).delete()
		} catch (_: Exception) {
			// Ignore
		}
	}

	/**
	 * Write a clean-exit marker so the main process knows this was a
	 * user-initiated quit (e.g., from the pause menu), not a crash.
	 * Called from the :sdl process before finish().
	 */
	fun writeCleanExitMarker(context: Context) {
		try {
			File(context.getExternalFilesDir(null), CLEAN_EXIT_MARKER).writeText("1")
		} catch (_: Exception) {
			// Best-effort
		}
	}

	/**
	 * Check and consume the clean-exit marker. Returns true if present
	 * (meaning the user quit intentionally).
	 */
	fun checkAndClearCleanExit(context: Context): Boolean {
		val marker = File(context.getExternalFilesDir(null), CLEAN_EXIT_MARKER)
		if (marker.exists()) {
			marker.delete()
			return true
		}
		return false
	}

	fun checkAndClearPendingLaunchMarker(context: Context): Boolean {
		val marker = File(context.getExternalFilesDir(null), PENDING_LAUNCH_MARKER)
		if (!marker.exists()) return false
		marker.delete()
		return true
	}

	/**
	 * Check if a previous emulator session crashed (marker file still present).
	 * Returns true if a crash was detected, and cleans up the marker.
	 *
	 * Includes a staleness check: if the marker is older than [STALE_MARKER_THRESHOLD_MS],
	 * it is silently discarded. This prevents false-positive crash dialogs when the system
	 * OOM-kills the :sdl process or the user force-stops the app from Android settings,
	 * since neither path triggers onDestroy(isFinishing=true).
	 */
	fun checkAndClearCrashMarker(context: Context): Boolean {
		val marker = File(context.getExternalFilesDir(null), SESSION_MARKER)
		if (!marker.exists()) return false

		val isCrash = try {
			val startTime = marker.readText().trim().toLongOrNull()
			if (startTime == null) {
				// Corrupt marker — treat as crash to be safe
				true
			} else {
				val elapsed = System.currentTimeMillis() - startTime
				elapsed in 1 until STALE_MARKER_THRESHOLD_MS
			}
		} catch (_: Exception) {
			// Unreadable marker (I/O error) — treat as crash to be safe
			true
		}

		marker.delete()
		return isCrash
	}

	/**
	 * Markers older than this are considered stale (force-stop / OOM) rather than crashes.
	 * 12 hours covers virtually all real emulator sessions.
	 */
	private const val STALE_MARKER_THRESHOLD_MS = 12 * 60 * 60 * 1000L
}

