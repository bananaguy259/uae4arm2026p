package com.uae4arm2026.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.uae4arm2026.data.model.EmulatorSettings
import com.uae4arm2026.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundTab(viewModel: SettingsViewModel) {
	val settings = viewModel.settings
	val soundOutputLabels = mapOf(
		"none" to stringResource(R.string.settings_sound_output_disabled),
		"interrupts" to stringResource(R.string.settings_sound_output_emulated_no_output),
		"normal" to stringResource(R.string.settings_sound_output_normal),
		"exact" to stringResource(R.string.settings_sound_output_exact)
	)

	val freqOptions = listOf(
		22050 to stringResource(R.string.settings_sound_frequency_hz, 22050),
		44100 to stringResource(R.string.settings_sound_frequency_hz, 44100),
		48000 to stringResource(R.string.settings_sound_frequency_hz, 48000)
	)

	val channelOptions = listOf(
		"mono" to stringResource(R.string.settings_sound_channels_mono),
		"stereo" to stringResource(R.string.settings_sound_channels_stereo),
		"mixed" to stringResource(R.string.settings_sound_channels_mixed)
	)
	val interpolationOptions = listOf(
		"none" to stringResource(R.string.settings_sound_interpolation_none),
		"anti" to stringResource(R.string.settings_sound_interpolation_anti),
		"sinc" to stringResource(R.string.settings_sound_interpolation_sinc),
		"rh" to stringResource(R.string.settings_sound_interpolation_rh),
		"crux" to stringResource(R.string.settings_sound_interpolation_crux)
	)
	val outputSummary = soundOutputLabels[settings.soundOutput] ?: settings.soundOutput
	val channelSummary = channelOptions.firstOrNull { it.first == settings.soundChannels }?.second ?: settings.soundChannels
	val frequencySummary = freqOptions.firstOrNull { it.first == settings.soundFreq }?.second
		?: stringResource(R.string.settings_sound_frequency_hz, settings.soundFreq)
	val interpolationSummary = interpolationOptions.firstOrNull { it.first == settings.soundInterpolation }?.second
		?: settings.soundInterpolation

	SettingsTabContent {
		SettingsAdaptiveColumns(
			left = {
				OutlinedCard(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(stringResource(R.string.settings_sound_title), style = MaterialTheme.typography.titleMedium)
						Spacer(modifier = Modifier.height(8.dp))

						var outputExpanded by remember { mutableStateOf(false) }
						val outputLabel = EmulatorSettings.soundOutputOptions
							.firstOrNull { it.first == settings.soundOutput }?.let { (value, fallback) ->
								soundOutputLabels[value] ?: fallback
							} ?: settings.soundOutput

						ExposedDropdownMenuBox(
							expanded = outputExpanded,
							onExpandedChange = { outputExpanded = it }
						) {
							OutlinedTextField(
								value = outputLabel,
								onValueChange = {},
								readOnly = true,
								label = { Text(stringResource(R.string.settings_sound_output_label)) },
								trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = outputExpanded) },
								modifier = Modifier
									.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
									.fillMaxWidth()
							)
							ExposedDropdownMenu(
								expanded = outputExpanded,
								onDismissRequest = { outputExpanded = false }
							) {
								EmulatorSettings.soundOutputOptions.forEach { (value, fallbackLabel) ->
									DropdownMenuItem(
										text = { Text(soundOutputLabels[value] ?: fallbackLabel) },
										onClick = {
											viewModel.updateSettings { s -> s.copy(soundOutput = value) }
											outputExpanded = false
										}
									)
								}
							}
						}

						Spacer(modifier = Modifier.height(8.dp))

						var freqExpanded by remember { mutableStateOf(false) }
						val freqLabel = freqOptions.firstOrNull { it.first == settings.soundFreq }?.second
							?: stringResource(R.string.settings_sound_frequency_hz, settings.soundFreq)

						ExposedDropdownMenuBox(
							expanded = freqExpanded,
							onExpandedChange = { freqExpanded = it }
						) {
							OutlinedTextField(
								value = freqLabel,
								onValueChange = {},
								readOnly = true,
								label = { Text(stringResource(R.string.settings_sound_sample_rate_label)) },
								trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
								modifier = Modifier
									.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
									.fillMaxWidth()
							)
							ExposedDropdownMenu(
								expanded = freqExpanded,
								onDismissRequest = { freqExpanded = false }
							) {
								freqOptions.forEach { (value, label) ->
									DropdownMenuItem(
										text = { Text(label) },
										onClick = {
											viewModel.updateSettings { s -> s.copy(soundFreq = value) }
											freqExpanded = false
										}
									)
								}
							}
						}

						Spacer(modifier = Modifier.height(8.dp))

						var channelExpanded by remember { mutableStateOf(false) }
						val channelLabel = channelOptions.firstOrNull { it.first == settings.soundChannels }?.second
							?: settings.soundChannels

						ExposedDropdownMenuBox(
							expanded = channelExpanded,
							onExpandedChange = { channelExpanded = it }
						) {
							OutlinedTextField(
								value = channelLabel,
								onValueChange = {},
								readOnly = true,
								label = { Text(stringResource(R.string.settings_sound_stereo_mode_label)) },
								trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelExpanded) },
								modifier = Modifier
									.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
									.fillMaxWidth()
							)
							ExposedDropdownMenu(
								expanded = channelExpanded,
								onDismissRequest = { channelExpanded = false }
							) {
								channelOptions.forEach { (value, label) ->
									DropdownMenuItem(
										text = { Text(label) },
										onClick = {
											viewModel.updateSettings { s -> s.copy(soundChannels = value) }
											channelExpanded = false
										}
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
						Text(stringResource(R.string.settings_sound_summary_title), style = MaterialTheme.typography.titleMedium)
						Spacer(modifier = Modifier.height(8.dp))
						Text(
							stringResource(R.string.settings_sound_summary_output, outputSummary),
							style = MaterialTheme.typography.bodyMedium
						)
						Text(
							stringResource(R.string.settings_sound_summary_frequency, frequencySummary),
							style = MaterialTheme.typography.bodyMedium
						)
						Text(
							stringResource(R.string.settings_sound_summary_channels, channelSummary),
							style = MaterialTheme.typography.bodyMedium
						)
						Text(
							stringResource(R.string.settings_sound_summary_interpolation, interpolationSummary),
							style = MaterialTheme.typography.bodyMedium
						)
						Spacer(modifier = Modifier.height(12.dp))

						var interpolationExpanded by remember { mutableStateOf(false) }
						ExposedDropdownMenuBox(
							expanded = interpolationExpanded,
							onExpandedChange = { interpolationExpanded = it }
						) {
							OutlinedTextField(
								value = interpolationSummary,
								onValueChange = {},
								readOnly = true,
								label = { Text(stringResource(R.string.settings_sound_interpolation_label)) },
								trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = interpolationExpanded) },
								modifier = Modifier
									.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
									.fillMaxWidth()
							)
							ExposedDropdownMenu(
								expanded = interpolationExpanded,
								onDismissRequest = { interpolationExpanded = false }
							) {
								interpolationOptions.forEach { (value, label) ->
									DropdownMenuItem(
										text = { Text(label) },
										onClick = {
											viewModel.updateSettings { state -> state.copy(soundInterpolation = value) }
											interpolationExpanded = false
										}
									)
								}
							}
						}

						Spacer(modifier = Modifier.height(12.dp))
						Text(stringResource(R.string.settings_sound_stereo_separation_label), style = MaterialTheme.typography.bodyMedium)
						Slider(
							value = settings.soundStereoSeparation.toFloat(),
							onValueChange = { value ->
								viewModel.updateSettings { state -> state.copy(soundStereoSeparation = value.toInt()) }
							},
							valueRange = 0f..10f,
							steps = 9
						)
						Text(
							stringResource(R.string.settings_sound_stereo_separation_value, settings.soundStereoSeparation),
							style = MaterialTheme.typography.bodySmall
						)
					}
				}
			}
		)
	}
}

