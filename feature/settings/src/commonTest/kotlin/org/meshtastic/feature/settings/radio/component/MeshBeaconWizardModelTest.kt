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

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshBeaconWizardModelTest {
    private val loraConfig = LoRaConfig(use_preset = true, modem_preset = ModemPreset.LONG_FAST, region = RegionCode.US)

    @Test
    fun `message of the day uses primary channel and clears an old offer`() {
        val listenFlag = MeshBeaconConfig.Flags.FLAG_LISTEN_ENABLED.value
        val legacyFlag = MeshBeaconConfig.Flags.FLAG_LEGACY_SPLIT.value
        val unknownFlag = 0x80
        val existing =
            MeshBeaconConfig(
                flags = listenFlag or legacyFlag or unknownFlag,
                broadcast_offer_channel = ChannelSettings(name = "Old mesh"),
                broadcast_offer_region = RegionCode.EU_868,
                broadcast_offer_preset = ModemPreset.NARROW_SLOW,
                broadcast_targets =
                listOf(
                    MeshBeaconConfig.BroadcastTarget(preset = ModemPreset.NARROW_SLOW, region = RegionCode.EU_868),
                ),
            )
        val wizard =
            MeshBeaconWizardState(
                purpose = MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY,
                message = "Breakfast is ready at 08:00",
                intervalSecs = TWELVE_HOURS_SECS,
            )

        val result = existing.applyWizard(wizard, loraConfig, channels = emptyList())

        assertTrue(result.flags.hasFlag(MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value))
        assertTrue(result.flags.hasFlag(listenFlag))
        assertTrue(result.flags.hasFlag(legacyFlag))
        assertTrue(result.flags.hasFlag(unknownFlag))
        assertEquals("Breakfast is ready at 08:00", result.broadcast_message)
        assertEquals(TWELVE_HOURS_SECS, result.broadcast_interval_secs)
        assertEquals(loraConfig.region, result.broadcast_on_region)
        assertEquals(loraConfig.modem_preset, result.broadcast_on_preset)
        assertNull(result.broadcast_on_channel)
        assertNull(result.broadcast_offer_channel)
        assertEquals(RegionCode.UNSET, result.broadcast_offer_region)
        assertNull(result.broadcast_offer_preset)
        assertTrue(result.broadcast_targets.isEmpty())
    }

    @Test
    fun `preset announcement creates one target per selected preset without offering a key`() {
        val wizard =
            MeshBeaconWizardState(
                purpose = MeshBeaconWizardPurpose.PRESET_ANNOUNCEMENT,
                message = "All the fun is happening on NarrowSlow",
                advertisedPreset = ModemPreset.NARROW_SLOW,
                transmitPresets = listOf(ModemPreset.LONG_FAST, ModemPreset.LONG_SLOW, ModemPreset.NARROW_FAST),
                intervalSecs = SIX_HOURS_SECS,
            )

        val result = MeshBeaconConfig().applyWizard(wizard, loraConfig, channels = emptyList())

        assertNull(result.broadcast_offer_channel)
        assertEquals(RegionCode.US, result.broadcast_offer_region)
        assertEquals(ModemPreset.NARROW_SLOW, result.broadcast_offer_preset)
        assertEquals(wizard.transmitPresets, result.broadcast_targets.map { it.preset })
        assertTrue(result.broadcast_targets.all { it.region == RegionCode.US && it.channel_index == null })
    }

    @Test
    fun `channel invitation copies the selected key and gives an unnamed channel its effective preset name`() {
        val psk = ByteArray(32) { it.toByte() }.toByteString()
        val channels = listOf(ChannelSettings(name = "", psk = psk), ChannelSettings(name = "Ops"))
        val wizard =
            MeshBeaconWizardState(
                purpose = MeshBeaconWizardPurpose.CHANNEL_INVITATION,
                message = "Join us",
                advertisedPreset = ModemPreset.NARROW_SLOW,
                transmitPresets = listOf(ModemPreset.LONG_FAST, ModemPreset.NARROW_SLOW),
                offeredChannelIndex = 0,
                intervalSecs = SIX_HOURS_SECS,
            )

        val result = MeshBeaconConfig().applyWizard(wizard, loraConfig, channels)

        assertEquals("NarrowSlow", result.broadcast_offer_channel?.name)
        assertEquals(psk, result.broadcast_offer_channel?.psk)
        assertEquals(ModemPreset.NARROW_SLOW, result.broadcast_offer_preset)
    }

    @Test
    fun `builder never writes more than four broadcast targets`() {
        val wizard =
            MeshBeaconWizardState(
                purpose = MeshBeaconWizardPurpose.PRESET_ANNOUNCEMENT,
                message = "Find us",
                advertisedPreset = ModemPreset.LONG_FAST,
                transmitPresets =
                listOf(
                    ModemPreset.LONG_FAST,
                    ModemPreset.LONG_SLOW,
                    ModemPreset.MEDIUM_FAST,
                    ModemPreset.SHORT_FAST,
                    ModemPreset.SHORT_SLOW,
                ),
                intervalSecs = SIX_HOURS_SECS,
            )

        val result = MeshBeaconConfig().applyWizard(wizard, loraConfig, channels = emptyList())

        assertEquals(MESH_BEACON_MAX_TARGETS, result.broadcast_targets.size)
        assertEquals(wizard.transmitPresets.take(MESH_BEACON_MAX_TARGETS), result.broadcast_targets.map { it.preset })
    }

    @Test
    fun `message path skips destination while invitation requires a channel`() {
        val message = MeshBeaconWizardState(purpose = MeshBeaconWizardPurpose.MESSAGE_OF_THE_DAY, message = "Hello")
        val invitation =
            MeshBeaconWizardState(
                purpose = MeshBeaconWizardPurpose.CHANNEL_INVITATION,
                message = "Join us",
                transmitPresets = listOf(ModemPreset.LONG_FAST),
                offeredChannelIndex = 0,
            )

        assertEquals(
            listOf(
                MeshBeaconWizardStep.PURPOSE,
                MeshBeaconWizardStep.MESSAGE,
                MeshBeaconWizardStep.SCHEDULE,
                MeshBeaconWizardStep.REVIEW,
            ),
            message.steps,
        )
        assertFalse(invitation.isComplete(channelCount = 0))
        assertTrue(invitation.isComplete(channelCount = 1))
    }
}
