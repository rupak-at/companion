package com.ambientcompanion.data.preferences

import org.junit.Assert.*
import org.junit.Test

class SettingsMigrationTest {
    @Test fun `v1 values survive migration and v2 defaults are applied`() {
        val old = UserSettings(schemaVersion = 1, companionEnabled = true, companionSizeDp = 104, idleOpacity = .6f, weatherEnabled = false)
        val migrated = SettingsMigration.migrate(old)
        assertEquals(2, migrated.schemaVersion)
        assertTrue(migrated.companionEnabled)
        assertEquals(104, migrated.companionSizeDp)
        assertEquals(.6f, migrated.idleOpacity)
        assertFalse(migrated.weatherEnabled)
        assertTrue(migrated.batteryReactions)
        assertFalse(migrated.connectivityReactions)
        assertEquals(CompanionArtwork.BIRD, migrated.selectedArtwork)
    }
}
