package com.uae4arm2026.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uae4arm2026.R
import com.uae4arm2026.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryTab(viewModel: SettingsViewModel) {
	val settings = viewModel.settings
	val noneLabel = stringResource(R.string.settings_option_none)
	val chipOptions = listOf(1, 2, 4, 8, 16).map { value ->
		value to formatMemory(value * 512, noneLabel)
	}
	val slowOptions = listOf(0, 2, 4, 6, 7).map { value ->
		value to if (value == 0) noneLabel else formatMemory(value * 256, noneLabel)
	}
	val z2FastOptions = listOf(0, 1, 2, 4, 8).map { value ->
		value to if (value == 0) noneLabel else stringResource(R.string.settings_memory_value_mb, value)
	}
	val z3FastOptions = listOf(0, 16, 32, 64, 128, 256, 512).map { value ->
		value to if (value == 0) noneLabel else stringResource(R.string.settings_memory_value_mb, value)
	}

	SettingsTabContent {
		SettingsAdaptiveColumns(
			left = {
				OutlinedCard(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(stringResource(R.string.settings_memory_config_title), style = MaterialTheme.typography.titleMedium)

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Box(modifier = Modifier.weight(1f)) {
								MemoryDropdown(
									label = stringResource(R.string.settings_memory_chip_ram),
									options = chipOptions,
									selectedValue = settings.chipRam,
									onValueChange = { viewModel.updateSettings { state -> state.copy(chipRam = it) } }
								)
							}
							Box(modifier = Modifier.weight(1f)) {
								MemoryDropdown(
									label = stringResource(R.string.settings_memory_slow_ram),
									options = slowOptions,
									selectedValue = settings.slowRam,
									onValueChange = { viewModel.updateSettings { state -> state.copy(slowRam = it) } }
								)
							}
						}

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							Box(modifier = Modifier.weight(1f)) {
								MemoryDropdown(
									label = stringResource(R.string.settings_memory_fast_ram),
									options = z2FastOptions,
									selectedValue = settings.fastRam,
									onValueChange = { viewModel.updateSettings { state -> state.copy(fastRam = it) } }
								)
							}
							Box(modifier = Modifier.weight(1f)) {
								if (!settings.address24Bit) {
									MemoryDropdown(
										label = stringResource(R.string.settings_memory_z3_fast_ram),
										options = z3FastOptions,
										selectedValue = settings.z3Ram,
										onValueChange = { viewModel.updateSettings { state -> state.copy(z3Ram = it) } }
									)
								}
							}
						}
					}
				}
			},
			right = {
				OutlinedCard(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(stringResource(R.string.settings_memory_summary_title), style = MaterialTheme.typography.titleMedium)
						Spacer(modifier = Modifier.height(8.dp))

						val chipKB = settings.chipRam * 512
						val slowKB = settings.slowRam * 256
						val fastMB = settings.fastRam
						val z3MB = settings.z3Ram

						Text(
							stringResource(R.string.settings_memory_summary_chip, formatMemory(chipKB, noneLabel)),
							style = MaterialTheme.typography.bodyMedium
						)
						if (slowKB > 0) {
							Text(
								stringResource(R.string.settings_memory_summary_slow, formatMemory(slowKB, noneLabel)),
								style = MaterialTheme.typography.bodyMedium
							)
						}
						if (fastMB > 0) {
							Text(
								stringResource(R.string.settings_memory_summary_z2_fast, fastMB),
								style = MaterialTheme.typography.bodyMedium
							)
						}
						if (z3MB > 0) {
							Text(
								stringResource(R.string.settings_memory_summary_z3_fast, z3MB),
								style = MaterialTheme.typography.bodyMedium
							)
						}

						val totalMB = (chipKB + slowKB) / 1024.0 + fastMB + z3MB
						Spacer(modifier = Modifier.height(4.dp))
						Text(
							stringResource(R.string.settings_memory_summary_total, totalMB),
							style = MaterialTheme.typography.titleSmall,
							color = MaterialTheme.colorScheme.primary
						)
					}
				}
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryDropdown(
	label: String,
	options: List<Pair<Int, String>>,
	selectedValue: Int,
	onValueChange: (Int) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }
	val displayText = options.firstOrNull { it.first == selectedValue }?.second
		?: options.firstOrNull()?.second ?: stringResource(R.string.settings_placeholder_unknown)

	ExposedDropdownMenuBox(
		expanded = expanded,
		onExpandedChange = { expanded = it }
	) {
		OutlinedTextField(
			value = displayText,
			onValueChange = {},
			readOnly = true,
			singleLine = true,
			textStyle = TextStyle(fontSize = 12.sp),
			label = { Text(label, fontSize = 11.sp) },
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			modifier = Modifier
				.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
				.fillMaxWidth()
		)
		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			options.forEach { (value, text) ->
				DropdownMenuItem(
					text = { Text(text) },
					onClick = {
						onValueChange(value)
						expanded = false
					}
				)
			}
		}
	}
}

@Composable
private fun formatMemory(kb: Int, noneLabel: String): String {
	if (kb <= 0) return noneLabel
	return if (kb >= 1024) {
		stringResource(R.string.settings_memory_value_mb, kb / 1024)
	} else {
		stringResource(R.string.settings_memory_value_kb, kb)
	}
}

