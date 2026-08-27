/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.back
import org.meshtastic.core.resources.mesh_beacon_wizard_get_started
import org.meshtastic.core.resources.mesh_beacon_wizard_next
import org.meshtastic.core.resources.mesh_beacon_wizard_save
import org.meshtastic.core.resources.mesh_beacon_wizard_start
import org.meshtastic.core.resources.mesh_beacon_wizard_start_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_step
import org.meshtastic.core.resources.mesh_beacon_wizard_title
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Tune
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig

internal const val MESH_BEACON_WIZARD_START_TAG = "mesh_beacon_wizard_start"
internal const val MESH_BEACON_WIZARD_NEXT_TAG = "mesh_beacon_wizard_next"
internal const val MESH_BEACON_WIZARD_SAVE_TAG = "mesh_beacon_wizard_save"
internal const val MESH_BEACON_WIZARD_MESSAGE_TAG = "mesh_beacon_wizard_message"

private val WizardStateSaver =
    listSaver<MeshBeaconWizardState, String>(
        save = { state ->
            listOf(
                state.purpose?.name.orEmpty(),
                state.message,
                state.advertisedPreset.name,
                state.transmitPresets.joinToString(separator = ",", transform = ModemPreset::name),
                state.offeredChannelIndex.toString(),
                state.intervalSecs.toString(),
                state.stepIndex.toString(),
            )
        },
        restore = { saved ->
            MeshBeaconWizardState(
                purpose = saved[0].takeIf(String::isNotEmpty)?.let(MeshBeaconWizardPurpose::valueOf),
                message = saved[1],
                advertisedPreset = ModemPreset.valueOf(saved[2]),
                transmitPresets = saved[3].split(',').filter(String::isNotEmpty).map(ModemPreset::valueOf),
                offeredChannelIndex = saved[4].toInt(),
                intervalSecs = saved[5].toInt(),
                stepIndex = saved[6].toInt(),
            )
        },
    )

@Composable
internal fun MeshBeaconWizardButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(MESH_BEACON_WIZARD_START_TAG),
        ) {
            Icon(MeshtasticIcons.Tune, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.mesh_beacon_wizard_start))
        }
        Text(
            text = stringResource(Res.string.mesh_beacon_wizard_start_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun MeshBeaconWizardScreen(
    initialConfig: MeshBeaconConfig,
    loraConfig: LoRaConfig,
    channels: List<ChannelSettings>,
    availablePresets: List<ModemPreset>,
    onClose: () -> Unit,
    onSave: (MeshBeaconConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wizardState by rememberWizardState(initialConfig, loraConfig)
    val backAction = {
        if (wizardState.stepIndex > 0) {
            wizardState = wizardState.copy(stepIndex = wizardState.stepIndex - 1)
        } else {
            onClose()
        }
    }
    val backHandlerState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = backHandlerState, isBackEnabled = true, onBackCompleted = backAction)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.mesh_beacon_wizard_title),
                canNavigateUp = true,
                onNavigateUp = backAction,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
        bottomBar = {
            WizardNavigationBar(
                state = wizardState,
                channelCount = channels.size,
                onBack = backAction,
                onNext = { wizardState = wizardState.copy(stepIndex = wizardState.stepIndex + 1) },
                onSave = { onSave(initialConfig.applyWizard(wizardState, loraConfig, channels)) },
            )
        },
    ) { innerPadding ->
        WizardScaffoldContent(
            state = wizardState,
            loraConfig = loraConfig,
            channels = channels,
            availablePresets = availablePresets,
            onStateChange = { wizardState = it },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun rememberWizardState(
    initialConfig: MeshBeaconConfig,
    loraConfig: LoRaConfig,
): MutableState<MeshBeaconWizardState> {
    val initialState =
        MeshBeaconWizardState(
            message = initialConfig.broadcast_message,
            advertisedPreset = initialConfig.broadcast_offer_preset ?: loraConfig.modem_preset,
            transmitPresets =
            initialConfig.broadcast_targets
                .mapNotNull(MeshBeaconConfig.BroadcastTarget::preset)
                .takeIf(List<ModemPreset>::isNotEmpty) ?: listOf(loraConfig.modem_preset),
            offeredChannelIndex = 0,
            intervalSecs =
            initialConfig.broadcast_interval_secs.takeIf {
                it == SIX_HOURS_SECS || it == TWELVE_HOURS_SECS || it == TWENTY_FOUR_HOURS_SECS
            } ?: SIX_HOURS_SECS,
        )
    return rememberSaveable(stateSaver = WizardStateSaver) { mutableStateOf(initialState) }
}

@Composable
private fun WizardScaffoldContent(
    state: MeshBeaconWizardState,
    loraConfig: LoRaConfig,
    channels: List<ChannelSettings>,
    availablePresets: List<ModemPreset>,
    onStateChange: (MeshBeaconWizardState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.currentStep) { scrollState.scrollTo(0) }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
            Modifier.fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WizardProgress(state = state)
            WizardStepContent(
                state = state,
                loraConfig = loraConfig,
                channels = channels,
                availablePresets = availablePresets,
                onStateChange = onStateChange,
            )
        }
    }
}

@Composable
private fun WizardProgress(state: MeshBeaconWizardState) {
    val current = state.stepIndex.coerceIn(0, state.steps.lastIndex) + 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text =
            if (state.purpose == null) {
                stringResource(Res.string.mesh_beacon_wizard_get_started)
            } else {
                stringResource(Res.string.mesh_beacon_wizard_step, current, state.steps.size)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.purpose != null) {
            LinearProgressIndicator(
                progress = { current.toFloat() / state.steps.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun WizardStepContent(
    state: MeshBeaconWizardState,
    loraConfig: LoRaConfig,
    channels: List<ChannelSettings>,
    availablePresets: List<ModemPreset>,
    onStateChange: (MeshBeaconWizardState) -> Unit,
) {
    when (state.currentStep) {
        MeshBeaconWizardStep.PURPOSE -> PurposeStep(state, onStateChange)

        MeshBeaconWizardStep.MESSAGE -> MessageStep(state, onStateChange)

        MeshBeaconWizardStep.DESTINATION ->
            DestinationStep(state, loraConfig, channels, availablePresets, onStateChange)

        MeshBeaconWizardStep.TRANSMIT_PRESETS -> TransmitPresetsStep(state, loraConfig, availablePresets, onStateChange)

        MeshBeaconWizardStep.SCHEDULE -> ScheduleStep(state, onStateChange)

        MeshBeaconWizardStep.REVIEW -> ReviewStep(state, loraConfig, channels)
    }
}

@Composable
private fun WizardNavigationBar(
    state: MeshBeaconWizardState,
    channelCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.stepIndex > 0) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.back))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (state.currentStep == MeshBeaconWizardStep.REVIEW) {
                Button(
                    onClick = onSave,
                    enabled = state.canContinue(channelCount),
                    modifier = Modifier.weight(1f).testTag(MESH_BEACON_WIZARD_SAVE_TAG),
                ) {
                    Text(stringResource(Res.string.mesh_beacon_wizard_save))
                }
            } else {
                Button(
                    onClick = onNext,
                    enabled = state.canContinue(channelCount),
                    modifier = Modifier.weight(1f).testTag(MESH_BEACON_WIZARD_NEXT_TAG),
                ) {
                    Text(stringResource(Res.string.mesh_beacon_wizard_next))
                }
            }
        }
    }
}
