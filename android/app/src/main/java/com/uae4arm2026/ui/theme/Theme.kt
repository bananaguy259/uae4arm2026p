package com.uae4arm2026.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.uae4arm2026.data.AppPreferences

private val Uae4ArmBlue = Color(0xFF3366CC)
private val Uae4ArmBlueDark = Color(0xFF264D99)

private val DarkColorScheme = darkColorScheme(
	primary = Uae4ArmBlue,
	secondary = Uae4ArmBlue,
	tertiary = Color(0xFF66BB6A)
)

private val LightColorScheme = lightColorScheme(
	primary = Uae4ArmBlueDark,
	secondary = Uae4ArmBlue,
	tertiary = Color(0xFF388E3C)
)

@Composable
fun Uae4ArmTheme(
	content: @Composable () -> Unit
) {
	val context = LocalContext.current
	val prefs = AppPreferences.getInstance(context)
	val useDynamicColor by prefs.useDynamicColor
	val themeMode by prefs.themeMode
	val dynamicColor = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

	val darkTheme = when (themeMode) {
		"dark" -> true
		"light" -> false
		else -> isSystemInDarkTheme()
	}

	val colorScheme = when {
		dynamicColor -> {
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		}
		darkTheme -> DarkColorScheme
		else -> LightColorScheme
	}

	MaterialTheme(
		colorScheme = colorScheme,
		content = content
	)
}

