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

import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.constraintFor
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig

internal const val MESH_BEACON_MESSAGE_MAX_BYTES = 100
internal const val MESH_BEACON_CHANNEL_NAME_MAX_BYTES = 11
internal const val MESH_BEACON_MIN_INTERVAL_SECS = 3600
internal const val MESH_BEACON_MAX_TARGETS = 4
internal const val SIX_HOURS_SECS = 6 * 60 * 60
internal const val TWELVE_HOURS_SECS = 12 * 60 * 60
internal const val TWENTY_FOUR_HOURS_SECS = 24 * 60 * 60

internal enum class MeshBeaconWizardPurpose {
    MESSAGE_OF_THE_DAY,
    PRESET_ANNOUNCEMENT,
    CHANNEL_INVITATION,
}

internal enum class MeshBeaconWizardStep {
    PURPOSE,
    MESSAGE,
    DESTINATION,
    TRANSMIT_PRESETS,
    SCHEDULE,
    REVIEW,
}

internal data class MeshBeaconWizardState(
    val purpose: MeshBeaconWizardPurpose? = null,
    val message: String = "",
    val advertisedPreset: ModemPreset = ModemPreset.LONG_FAST,
    val transmitPresets: List<ModemPreset> = listOf(ModemPreset.LONG_FAST),
    val offeredChannelIndex: Int = 0,
    val intervalSecs: Int = SIX_HOURS_SECS,
    val stepIndex: Int = 0,
) {
    val steps: List<MeshBeaconWizardStep>
        get() = stepsFor(purpose)

    val currentStep: MeshBeaconWizardStep
        get() = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    fun canContinue(channelCount: Int): Boolean = when (currentStep) {
        MeshBeaconWizardStep.PURPOSE -> purpose != null

        MeshBeaconWizardStep.MESSAGE ->
            message.isNotBlank() && message.encodeToByteArray().size <= MESH_BEACON_MESSAGE_MAX_BYTES

        MeshBeaconWizardStep.DESTINATION ->
            purpose != MeshBeaconWizardPurpose.CHANNEL_INVITATION || offeredChannelIndex in 0 until channelCount

        MeshBeaconWizardStep.TRANSMIT_PRESETS ->
            transmitPresets.isNotEmpty() && transmitPresets.size <= MESH_BEACON_MAX_TARGETS

        MeshBeaconWizardStep.SCHEDULE -> intervalSecs >= MESH_BEACON_MIN_INTERVAL_SECS

        MeshBeaconWizardStep.REVIEW -> isComplete(channelCount)
    }

    fun isComplete(channelCount: Int): Boolean = purpose != null &&
        message.isNotBlank() &&
        message.encodeToByteArray().size <= MESH_BEACON_MESSAGE_MAX_BYTES &&
        intervalSecs >= MESH_BEACON_MIN_INTERVAL_SECS &&
        (purpose == MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY || transmitPresets.isNotEmpty()) &&
        (purpose != MeshBeaconWizardPurpose.CHANNEL_INVITATION || offeredChannelIndex in 0 until channelCount)
}

internal fun stepsFor(purpose: MeshBeaconWizardPurpose?): List<MeshBeaconWizardStep> = when (purpose) {
    MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY ->
        listOf(
            MeshBeaconWizardStep.PURPOSE,
            MeshBeaconWizardStep.MESSAGE,
            MeshBeaconWizardStep.SCHEDULE,
            MeshBeaconWizardStep.REVIEW,
        )

    MeshBeaconWizardPurpose.PRESET_ANNOUNCEMENT,
    MeshBeaconWizardPurpose.CHANNEL_INVITATION,
    ->
        listOf(
            MeshBeaconWizardStep.PURPOSE,
            MeshBeaconWizardStep.MESSAGE,
            MeshBeaconWizardStep.DESTINATION,
            MeshBeaconWizardStep.TRANSMIT_PRESETS,
            MeshBeaconWizardStep.SCHEDULE,
            MeshBeaconWizardStep.REVIEW,
        )

    null -> listOf(MeshBeaconWizardStep.PURPOSE)
}

internal fun Int.withFlag(flag: Int, on: Boolean): Int = if (on) this or flag else this and flag.inv()

internal fun Int.hasFlag(flag: Int): Boolean = (this and flag) != 0

/** Builds the one complete config written by the final wizard action. */
internal fun MeshBeaconConfig.applyWizard(
    wizardState: MeshBeaconWizardState,
    loraConfig: LoRaConfig,
    channels: List<ChannelSettings>,
): MeshBeaconConfig {
    require(wizardState.isComplete(channels.size)) { "Mesh Beacon wizard state is incomplete" }

    val broadcastFlag = MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value
    val common =
        copy(
            flags = flags.withFlag(broadcastFlag, true),
            broadcast_message = wizardState.message,
            broadcast_interval_secs = wizardState.intervalSecs,
            broadcast_on_channel = null,
            broadcast_on_region = loraConfig.region,
            broadcast_on_preset = loraConfig.modem_preset,
        )

    return when (wizardState.purpose) {
        MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY ->
            common.copy(
                broadcast_offer_channel = null,
                broadcast_offer_region = RegionCode.UNSET,
                broadcast_offer_preset = null,
                broadcast_targets = emptyList(),
            )

        MeshBeaconWizardPurpose.PRESET_ANNOUNCEMENT ->
            common.copy(
                broadcast_offer_channel = null,
                broadcast_offer_region = loraConfig.region,
                broadcast_offer_preset = wizardState.advertisedPreset,
                broadcast_targets = wizardState.toBroadcastTargets(loraConfig.region),
            )

        MeshBeaconWizardPurpose.CHANNEL_INVITATION -> {
            val channel = channels[wizardState.offeredChannelIndex]
            val offeredLora = loraConfig.copy(use_preset = true, modem_preset = wizardState.advertisedPreset)
            val offeredChannel = channel.copy(name = Channel(settings = channel, loraConfig = offeredLora).name)
            common.copy(
                broadcast_offer_channel = offeredChannel,
                broadcast_offer_region = loraConfig.region,
                broadcast_offer_preset = wizardState.advertisedPreset,
                broadcast_targets = wizardState.toBroadcastTargets(loraConfig.region),
            )
        }

        null -> error("Mesh Beacon wizard purpose is required")
    }
}

private fun MeshBeaconWizardState.toBroadcastTargets(region: RegionCode): List<MeshBeaconConfig.BroadcastTarget> =
    transmitPresets.distinct().take(MESH_BEACON_MAX_TARGETS).map { preset ->
        MeshBeaconConfig.BroadcastTarget(preset = preset, region = region)
    }

internal fun availableMeshBeaconPresets(
    firmwareVersion: String?,
    currentPreset: ModemPreset,
    region: RegionCode,
    regionPresetMap: LoRaRegionPresetMap?,
    isLicensed: Boolean,
): List<ModemPreset> {
    val capabilities = Capabilities(firmwareVersion)
    val constraint =
        region
            .takeIf { it != RegionCode.UNSET && capabilities.supportsLoraRegionPresetMap }
            ?.let { regionPresetMap.constraintFor(it) }
    val presetsGated = constraint?.isGated(isLicensed) == true
    val selectable =
        ChannelOption.entries
            .filter(capabilities::supportsPreset)
            .filter { constraint == null || it.modemPreset in constraint.presets }
            .filter { !presetsGated || it.modemPreset == currentPreset }
            .map(ChannelOption::modemPreset)
    return (selectable + currentPreset).distinct()
}
