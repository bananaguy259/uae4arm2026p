package com.uae4arm2026.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Storage access is handled via SAF (Storage Access Framework) — no special
 * system permission is required. This composable is kept as a no-op so that
 * existing call sites continue to compile without changes.
 */
@Composable
fun StoragePermissionBanner(modifier: Modifier = Modifier) {
	// No-op: SAF does not require any pre-granted permission banner.
}

