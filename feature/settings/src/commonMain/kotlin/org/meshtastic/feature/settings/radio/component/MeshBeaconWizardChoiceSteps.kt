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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_beacon_wizard_channel_empty
import org.meshtastic.core.resources.mesh_beacon_wizard_channel_key_private
import org.meshtastic.core.resources.mesh_beacon_wizard_channel_question
import org.meshtastic.core.resources.mesh_beacon_wizard_channel_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_destination_question
import org.meshtastic.core.resources.mesh_beacon_wizard_destination_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_message_bytes
import org.meshtastic.core.resources.mesh_beacon_wizard_message_question
import org.meshtastic.core.resources.mesh_beacon_wizard_message_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_channel
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_channel_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_message
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_message_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_preset
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_preset_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_question
import org.meshtastic.core.resources.mesh_beacon_wizard_transmit_limit
import org.meshtastic.core.resources.mesh_beacon_wizard_transmit_question
import org.meshtastic.core.resources.mesh_beacon_wizard_transmit_selected
import org.meshtastic.core.resources.mesh_beacon_wizard_transmit_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_your_message
import org.meshtastic.core.ui.icon.CellTower
import org.meshtastic.core.ui.icon.Channel
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Message
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.core.model.Channel as MeshChannel

@Composable
internal fun PurposeStep(state: MeshBeaconWizardState, onStateChange: (MeshBeaconWizardState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QuestionHeader(title = stringResource(Res.string.mesh_beacon_wizard_purpose_question), summary = null)
        PurposeChoice(
            title = stringResource(Res.string.mesh_beacon_wizard_purpose_message),
            summary = stringResource(Res.string.mesh_beacon_wizard_purpose_message_summary),
            icon = MeshtasticIcons.Message,
            purpose = MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY,
            state = state,
            onStateChange = onStateChange,
        )
        PurposeChoice(
            title = stringResource(Res.string.mesh_beacon_wizard_purpose_preset),
            summary = stringResource(Res.string.mesh_beacon_wizard_purpose_preset_summary),
            icon = MeshtasticIcons.CellTower,
            purpose = MeshBeaconWizardPurpose.PRESET_ANNOUNCEMENT,
            state = state,
            onStateChange = onStateChange,
        )
        PurposeChoice(
            title = stringResource(Res.string.mesh_beacon_wizard_purpose_channel),
            summary = stringResource(Res.string.mesh_beacon_wizard_purpose_channel_summary),
            icon = MeshtasticIcons.Lock,
            purpose = MeshBeaconWizardPurpose.CHANNEL_INVITATION,
            state = state,
            onStateChange = onStateChange,
        )
    }
}

@Composable
private fun PurposeChoice(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    purpose: MeshBeaconWizardPurpose,
    state: MeshBeaconWizardState,
    onStateChange: (MeshBeaconWizardState) -> Unit,
) {
    ChoiceCard(
        title = title,
        summary = summary,
        icon = icon,
        selected = state.purpose == purpose,
        onClick = { onStateChange(state.copy(purpose = purpose, stepIndex = 0)) },
        modifier = Modifier.testTag("mesh_beacon_wizard_purpose_${purpose.name.lowercase()}"),
    )
}

@Composable
internal fun MessageStep(state: MeshBeaconWizardState, onStateChange: (MeshBeaconWizardState) -> Unit) {
    val messageBytes = state.message.encodeToByteArray().size
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionHeader(
            title = stringResource(Res.string.mesh_beacon_wizard_message_question),
            summary = stringResource(Res.string.mesh_beacon_wizard_message_summary),
        )
        OutlinedTextField(
            value = state.message,
            onValueChange = { value ->
                if (value.encodeToByteArray().size <= MESH_BEACON_MESSAGE_MAX_BYTES) {
                    onStateChange(state.copy(message = value))
                }
            },
            label = { Text(stringResource(Res.string.mesh_beacon_wizard_your_message)) },
            supportingText = {
                Text(
                    stringResource(
                        Res.string.mesh_beacon_wizard_message_bytes,
                        messageBytes,
                        MESH_BEACON_MESSAGE_MAX_BYTES,
                    ),
                )
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag(MESH_BEACON_WIZARD_MESSAGE_TAG),
        )
    }
}

@Composable
internal fun DestinationStep(
    state: MeshBeaconWizardState,
    loraConfig: LoRaConfig,
    channels: List<ChannelSettings>,
    availablePresets: List<ModemPreset>,
    onStateChange: (MeshBeaconWizardState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.purpose == MeshBeaconWizardPurpose.CHANNEL_INVITATION) {
            QuestionHeader(
                title = stringResource(Res.string.mesh_beacon_wizard_channel_question),
                summary = stringResource(Res.string.mesh_beacon_wizard_channel_summary),
            )
            if (channels.isEmpty()) {
                Text(
                    text = stringResource(Res.string.mesh_beacon_wizard_channel_empty),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                channels.forEachIndexed { index, channel ->
                    ChoiceCard(
                        title = MeshChannel(channel, loraConfig).name,
                        summary = stringResource(Res.string.mesh_beacon_wizard_channel_key_private),
                        icon = MeshtasticIcons.Channel,
                        selected = state.offeredChannelIndex == index,
                        onClick = { onStateChange(state.copy(offeredChannelIndex = index)) },
                        modifier = Modifier.testTag("mesh_beacon_wizard_channel_$index"),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        QuestionHeader(
            title = stringResource(Res.string.mesh_beacon_wizard_destination_question),
            summary = stringResource(Res.string.mesh_beacon_wizard_destination_summary),
        )
        PresetChips(
            presets = availablePresets,
            selected = listOf(state.advertisedPreset),
            loraConfig = loraConfig,
            onToggle = { onStateChange(state.copy(advertisedPreset = it)) },
            tagPrefix = "mesh_beacon_wizard_destination",
        )
    }
}

@Composable
internal fun TransmitPresetsStep(
    state: MeshBeaconWizardState,
    loraConfig: LoRaConfig,
    availablePresets: List<ModemPreset>,
    onStateChange: (MeshBeaconWizardState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionHeader(
            title = stringResource(Res.string.mesh_beacon_wizard_transmit_question),
            summary = stringResource(Res.string.mesh_beacon_wizard_transmit_summary),
        )
        Text(
            text = stringResource(Res.string.mesh_beacon_wizard_transmit_selected, state.transmitPresets.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        PresetChips(
            presets = availablePresets,
            selected = state.transmitPresets,
            loraConfig = loraConfig,
            onToggle = { preset ->
                val updated =
                    if (preset in state.transmitPresets) {
                        state.transmitPresets - preset
                    } else if (state.transmitPresets.size < MESH_BEACON_MAX_TARGETS) {
                        state.transmitPresets + preset
                    } else {
                        state.transmitPresets
                    }
                onStateChange(state.copy(transmitPresets = updated))
            },
            tagPrefix = "mesh_beacon_wizard_transmit",
        )
        Text(
            text = stringResource(Res.string.mesh_beacon_wizard_transmit_limit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PresetChips(
    presets: List<ModemPreset>,
    selected: List<ModemPreset>,
    loraConfig: LoRaConfig,
    onToggle: (ModemPreset) -> Unit,
    tagPrefix: String,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { preset ->
            val isSelected = preset in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(preset) },
                label = { Text(presetDisplayName(preset, loraConfig)) },
                leadingIcon =
                if (isSelected) {
                    { Icon(MeshtasticIcons.Check, contentDescription = null) }
                } else {
                    null
                },
                modifier = Modifier.testTag("${tagPrefix}_${preset.name.lowercase()}"),
            )
        }
    }
}
