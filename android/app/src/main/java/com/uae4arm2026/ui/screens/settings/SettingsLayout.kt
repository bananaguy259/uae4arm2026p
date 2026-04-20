package com.uae4arm2026.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsTabContent(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.verticalScroll(rememberScrollState())
			.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
		content = content
	)
}

@Composable
fun SettingsAdaptiveColumns(
	modifier: Modifier = Modifier,
	left: @Composable ColumnScope.() -> Unit,
	right: (@Composable ColumnScope.() -> Unit)? = null
) {
	BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
		val useTwoColumns = right != null && maxWidth >= 600.dp
		if (useTwoColumns) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(16.dp)
			) {
				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(16.dp),
					content = left
				)
				Column(
					modifier = Modifier.weight(1f),
					verticalArrangement = Arrangement.spacedBy(16.dp),
					content = right!!
				)
			}
		} else {
			Column(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				left()
				right?.invoke(this)
			}
		}
	}
}
