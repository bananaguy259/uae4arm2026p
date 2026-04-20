package com.uae4arm2026.data

import android.content.Context
import android.content.SharedPreferences

object AppStorage {
	const val PREFS_NAME = "uae4arm_prefs"
	const val LEGACY_PREFS_NAME = "amiberry_prefs"

	private const val KEY_LEGACY_PREFS_MIGRATED = "_legacy_amiberry_prefs_migrated"

	fun openPreferences(context: Context): SharedPreferences {
		val appContext = context.applicationContext
		val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		if (!prefs.getBoolean(KEY_LEGACY_PREFS_MIGRATED, false)) {
			migrateLegacyPreferences(appContext, prefs)
		}
		return prefs
	}

	private fun migrateLegacyPreferences(context: Context, prefs: SharedPreferences) {
		val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
		val editor = prefs.edit()

		for ((key, value) in legacyPrefs.all) {
			if (prefs.contains(key)) continue
			when (value) {
				is Boolean -> editor.putBoolean(key, value)
				is Float -> editor.putFloat(key, value)
				is Int -> editor.putInt(key, value)
				is Long -> editor.putLong(key, value)
				is String -> editor.putString(key, value)
				is Set<*> -> {
					@Suppress("UNCHECKED_CAST")
					editor.putStringSet(key, value as Set<String>)
				}
			}
		}

		editor.putBoolean(KEY_LEGACY_PREFS_MIGRATED, true)
		editor.apply()
	}
}