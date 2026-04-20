package com.uae4arm2026.data

import android.content.Context
import com.uae4arm2026.data.model.AmigaModel
import com.uae4arm2026.data.model.EmulatorSettings
import java.io.File

/**
 * Generates .uae config files from EmulatorSettings.
 * Config key names match cfgfile_save_options() format in cfgfile.cpp.
 */
object ConfigGenerator {

	fun generate(settings: EmulatorSettings): String {
		val sb = StringBuilder()
		val onScreenJoystick = settings.onScreenJoystick || settings.joyport1 == "onscreen_joy"
		val onScreenKeyboard = settings.onScreenKeyboard
		val androidJoyport1 = settings.joyport1
		val nativeJoyport1 = if (settings.joyport1 == "onscreen_joy") "joy1" else settings.joyport1
		sb.appendLine(UpstreamConfig.GENERATED_BY_HEADER)
		sb.appendLine()

		// CPU
		sb.appendLine("cpu_model=${settings.cpuModel}")
		sb.appendLine("cpu_compatible=${settings.cpuCompatible.toCfg()}")
		sb.appendLine("cpu_24bit_addressing=${settings.address24Bit.toCfg()}")
		sb.appendLine("cpu_speed=${settings.cpuSpeed}")
		if (settings.fpuModel > 0) {
			sb.appendLine("fpu_model=${settings.fpuModel}")
		}
		if (settings.jitCacheSize > 0) {
			sb.appendLine("cachesize=${settings.jitCacheSize}")
			sb.appendLine("compfpu=${settings.jitFpu.toCfg()}")
		}

		// Chipset
		sb.appendLine("chipset=${settings.chipset}")
		sb.appendLine("immediate_blits=${settings.immediateBlits.toCfg()}")
		sb.appendLine("collision_level=${settings.collisionLevel}")
		sb.appendLine("cycle_exact=${settings.cycleExact.toCfg()}")
		sb.appendLine("ntsc=${settings.ntsc.toCfg()}")
		// Platform-specific chipset extensions
		if (settings.baseModel == AmigaModel.CD32) {
			sb.appendLine("chipset_compatible=CD32")
			sb.appendLine("cs_cd32cd=true")
			sb.appendLine("cs_cd32c2p=true")
			sb.appendLine("cs_cd32nvram=true")
		} else if (settings.baseModel == AmigaModel.CDTV) {
			sb.appendLine("chipset_compatible=CDTV")
			sb.appendLine("cs_cdtv=true")
		}

		// Memory
		sb.appendLine("chipmem_size=${settings.chipRam}")
		sb.appendLine("bogomem_size=${settings.slowRam}")
		sb.appendLine("fastmem_size=${settings.fastRam}")
		if (settings.z3Ram > 0) {
			sb.appendLine("z3mem_size=${settings.z3Ram}")
		}

		// ROM
		if (settings.romFile.isNotEmpty()) {
			sb.appendLine("kickstart_rom_file=${settings.romFile}")
		}
		if (settings.romExtFile.isNotEmpty()) {
			sb.appendLine("kickstart_ext_rom_file=${settings.romExtFile}")
		}

		// Floppy drives
		sb.appendLine("floppy0=${settings.floppy0}")
		sb.appendLine("floppy0type=${settings.floppy0Type}")
		sb.appendLine("floppy1=${settings.floppy1}")
		sb.appendLine("floppy1type=${settings.floppy1Type}")
		sb.appendLine("floppy2=${settings.floppy2}")
		sb.appendLine("floppy2type=${settings.floppy2Type}")
		sb.appendLine("floppy3=${settings.floppy3}")
		sb.appendLine("floppy3type=${settings.floppy3Type}")

		// CD
		if (settings.cdImage.isNotEmpty()) {
			sb.appendLine("cdimage0=${settings.cdImage}")
		}
		// Hard drives
		settings.hardDrives.forEachIndexed { i, path ->
			if (path.isNotEmpty()) {
				sb.appendLine("hardfile2=rw,DH$i:\"$path\",32,1,2,512,0")
			}
		}

		// Sound
		sb.appendLine("sound_output=${settings.soundOutput}")
		sb.appendLine("sound_frequency=${settings.soundFreq}")
		sb.appendLine("sound_channels=${settings.soundChannels}")
		sb.appendLine("sound_stereo_separation=${settings.soundStereoSeparation}")
		sb.appendLine("sound_interpol=${settings.soundInterpolation}")

		// Display
		val activeWidth = if (settings.useRtg) settings.rtgWidth else settings.gfxWidth
		val activeHeight = if (settings.useRtg) settings.rtgHeight else settings.gfxHeight
		sb.appendLine("gfx_width=$activeWidth")
		sb.appendLine("gfx_height=$activeHeight")
		sb.appendLine("gfx_fullscreen_amiga=fullwindow")
		sb.appendLine("gfx_correct_aspect=${settings.correctAspect.toCfg()}")
		sb.appendLine("gfx_auto_crop=${settings.autoCrop.toCfg()}")
		sb.appendLine("show_leds=${settings.showLeds.toCfg()}")
		if (settings.useRtg) {
			sb.appendLine("gfxcard_type=ZorroIII")
			sb.appendLine("gfxcard_size=8")
			sb.appendLine("gfx_fullscreen_picasso=fullwindow")
		} else {
			sb.appendLine("gfxcard_size=0")
		}

		// Input
		sb.appendLine("joyport0=${settings.joyport0}")
		sb.appendLine("joyport1=$nativeJoyport1")
		sb.appendLine("${UpstreamConfig.KEY_ANDROID_JOYPORT1}=$androidJoyport1")

		// Upstream Android compatibility keys that must remain stable for the core.
		sb.appendLine("${UpstreamConfig.KEY_TOUCH_SETTINGS_VERSION}=${UpstreamConfig.TOUCH_SETTINGS_VERSION}")
		sb.appendLine("${UpstreamConfig.KEY_ONSCREEN_JOYSTICK}=${onScreenJoystick.toCfg()}")
		sb.appendLine("${UpstreamConfig.KEY_AMIBERRY_ONSCREEN_JOYSTICK}=${onScreenJoystick.toCfg()}")
		sb.appendLine("${UpstreamConfig.KEY_ONSCREEN_CD32PAD}=${settings.onScreenCd32Pad.toCfg()}")
		sb.appendLine("${UpstreamConfig.KEY_AMIBERRY_ONSCREEN_CD32PAD}=${settings.onScreenCd32Pad.toCfg()}")
		sb.appendLine("${UpstreamConfig.KEY_VIRTUAL_KEYBOARD_ENABLED}=false")
		sb.appendLine("${UpstreamConfig.KEY_DEFAULT_OSK}=false")
		sb.appendLine("${UpstreamConfig.KEY_SHOW_ANDROID_KEYBOARD_BUTTON}=${onScreenKeyboard.toCfg()}")

		// Skip GUI when launched from Android native UI
		sb.appendLine("use_gui=no")

		return sb.toString()
	}

	fun writeConfig(context: Context, settings: EmulatorSettings, filename: String): File {
		val confDir = File(context.getExternalFilesDir(null), "conf")
		if (!confDir.exists()) confDir.mkdirs()
		val file = File(confDir, filename)
		file.writeText(generate(settings))
		return file
	}

	/**
	 * Write config and return the args needed to launch emulation with it.
	 * Uses --model for base hardware, plus --config for overrides.
	 */
	fun generateLaunchArgs(context: Context, settings: EmulatorSettings): Array<String> {
		val configFile = writeConfig(context, settings, ".current_settings.uae")
		return arrayOf(
			"--rescan-roms",
			"--model", settings.baseModel.cmdArg,
			"--config", configFile.absolutePath,
			"-G"
		)
	}

	private fun Boolean.toCfg(): String = if (this) "true" else "false"
}

