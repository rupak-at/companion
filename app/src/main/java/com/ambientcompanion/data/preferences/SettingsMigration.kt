package com.ambientcompanion.data.preferences

object SettingsMigration {
    const val CURRENT_SCHEMA_VERSION = 3
    fun migrate(settings: UserSettings): UserSettings = if (settings.schemaVersion >= CURRENT_SCHEMA_VERSION) settings
    else settings.copy(schemaVersion = CURRENT_SCHEMA_VERSION)
}
