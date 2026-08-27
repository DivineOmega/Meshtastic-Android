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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_beacon_wizard_broadcast_on
import org.meshtastic.core.resources.mesh_beacon_wizard_every_hours
import org.meshtastic.core.resources.mesh_beacon_wizard_interval_12_hours
import org.meshtastic.core.resources.mesh_beacon_wizard_interval_24_hours
import org.meshtastic.core.resources.mesh_beacon_wizard_interval_6_hours
import org.meshtastic.core.resources.mesh_beacon_wizard_offer_channel
import org.meshtastic.core.resources.mesh_beacon_wizard_offer_preset
import org.meshtastic.core.resources.mesh_beacon_wizard_primary_channel
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_channel
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_message
import org.meshtastic.core.resources.mesh_beacon_wizard_purpose_preset
import org.meshtastic.core.resources.mesh_beacon_wizard_region
import org.meshtastic.core.resources.mesh_beacon_wizard_review_airtime
import org.meshtastic.core.resources.mesh_beacon_wizard_review_question
import org.meshtastic.core.resources.mesh_beacon_wizard_review_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_schedule_question
import org.meshtastic.core.resources.mesh_beacon_wizard_schedule_summary
import org.meshtastic.core.resources.mesh_beacon_wizard_your_message
import org.meshtastic.core.ui.icon.CalendarMonth
import org.meshtastic.core.ui.icon.CheckCircle
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset

@Composable
internal fun ScheduleStep(state: MeshBeaconWizardState, onStateChange: (MeshBeaconWizardState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QuestionHeader(
            title = stringResource(Res.string.mesh_beacon_wizard_schedule_question),
            summary = stringResource(Res.string.mesh_beacon_wizard_schedule_summary),
        )
        ScheduleChoice(SIX_HOURS_SECS, state, onStateChange)
        ScheduleChoice(TWELVE_HOURS_SECS, state, onStateChange)
        ScheduleChoice(TWENTY_FOUR_HOURS_SECS, state, onStateChange)
    }
}

@Composable
private fun ScheduleChoice(
    intervalSecs: Int,
    state: MeshBeaconWizardState,
    onStateChange: (MeshBeaconWizardState) -> Unit,
) {
    ChoiceCard(
        title = intervalLabel(intervalSecs),
        summary = null,
        icon = MeshtasticIcons.CalendarMonth,
        selected = state.intervalSecs == intervalSecs,
        onClick = { onStateChange(state.copy(intervalSecs = intervalSecs)) },
    )
}

@Composable
internal fun ReviewStep(state: MeshBeaconWizardState, loraConfig: LoRaConfig, channels: List<ChannelSettings>) {
    val purposeTitle =
        when (state.purpose) {
            MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY -> stringResource(Res.string.mesh_beacon_wizard_purpose_message)
            MeshBeaconWizardPurpose.PRESET_ANNOUNCEMENT -> stringResource(Res.string.mesh_beacon_wizard_purpose_preset)
            MeshBeaconWizardPurpose.CHANNEL_INVITATION -> stringResource(Res.string.mesh_beacon_wizard_purpose_channel)
            null -> ""
        }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionHeader(
            title = stringResource(Res.string.mesh_beacon_wizard_review_question),
            summary = stringResource(Res.string.mesh_beacon_wizard_review_summary),
        )
        ReviewSummaryCard(state, loraConfig, channels, purposeTitle)
        if (state.purpose != MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY) {
            Text(
                text = stringResource(Res.string.mesh_beacon_wizard_review_airtime, state.transmitPresets.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewSummaryCard(
    state: MeshBeaconWizardState,
    loraConfig: LoRaConfig,
    channels: List<ChannelSettings>,
    purposeTitle: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = purposeTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            ReviewLine(stringResource(Res.string.mesh_beacon_wizard_your_message), state.message)
            ReviewLine(stringResource(Res.string.mesh_beacon_wizard_every_hours), intervalLabel(state.intervalSecs))
            ReviewLine(stringResource(Res.string.mesh_beacon_wizard_broadcast_on), broadcastOnLabel(state, loraConfig))
            if (state.purpose != MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY) {
                ReviewLine(
                    stringResource(Res.string.mesh_beacon_wizard_offer_preset),
                    presetDisplayName(state.advertisedPreset, loraConfig),
                )
                ReviewLine(
                    stringResource(Res.string.mesh_beacon_wizard_region),
                    loraConfig.region.name.replace('_', ' '),
                )
            }
            if (state.purpose == MeshBeaconWizardPurpose.CHANNEL_INVITATION) {
                channels.getOrNull(state.offeredChannelIndex)?.let { channel ->
                    val offeredLora = loraConfig.copy(use_preset = true, modem_preset = state.advertisedPreset)
                    ReviewLine(
                        stringResource(Res.string.mesh_beacon_wizard_offer_channel),
                        Channel(channel, offeredLora).name,
                    )
                }
            }
        }
    }
}

@Composable
private fun broadcastOnLabel(state: MeshBeaconWizardState, loraConfig: LoRaConfig): String =
    if (state.purpose == MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY) {
        stringResource(Res.string.mesh_beacon_wizard_primary_channel)
    } else {
        state.transmitPresets.joinToString { presetDisplayName(it, loraConfig) }
    }

@Composable
private fun ReviewLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
internal fun QuestionHeader(title: String, summary: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ChoiceCard(
    title: String,
    summary: String?,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    val border =
        if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoiceIcon(icon = icon, selected = selected)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = MeshtasticIcons.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ChoiceIcon(icon: ImageVector, selected: Boolean) {
    val containerColor: Color
    val contentColor: Color
    if (selected) {
        containerColor = MaterialTheme.colorScheme.primary
        contentColor = MaterialTheme.colorScheme.onPrimary
    } else {
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.medium, color = containerColor) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        }
    }
}

@Composable
internal fun intervalLabel(intervalSecs: Int): String = when (intervalSecs) {
    TWELVE_HOURS_SECS -> stringResource(Res.string.mesh_beacon_wizard_interval_12_hours)
    TWENTY_FOUR_HOURS_SECS -> stringResource(Res.string.mesh_beacon_wizard_interval_24_hours)
    else -> stringResource(Res.string.mesh_beacon_wizard_interval_6_hours)
}

internal fun presetDisplayName(preset: ModemPreset, loraConfig: LoRaConfig): String =
    Channel(settings = ChannelSettings(), loraConfig = loraConfig.copy(use_preset = true, modem_preset = preset)).name
