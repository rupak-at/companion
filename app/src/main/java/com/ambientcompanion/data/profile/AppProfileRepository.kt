package com.ambientcompanion.data.profile

import android.content.Context
import com.ambientcompanion.domain.screen.AppCategory
import com.ambientcompanion.domain.screen.AppProfile
import com.ambientcompanion.domain.screen.CompanionDisplayMode

class AppProfileRepository(context: Context) {
    private val store = context.getSharedPreferences("v3_app_profiles", Context.MODE_PRIVATE)

    fun profileFor(packageName: String, category: AppCategory): AppProfile {
        val encoded = store.getString(packageName, null)
        return encoded?.decode(packageName) ?: categoryDefault(packageName, category)
    }

    fun save(profile: AppProfile) {
        store.edit().putString(profile.packageName, profile.encode()).apply()
    }

    fun removeOverride(packageName: String) { store.edit().remove(packageName).apply() }

    fun overrides(): List<AppProfile> = store.all.mapNotNull { (packageName, value) ->
        (value as? String)?.decode(packageName)
    }

    companion object {
        fun categoryDefault(packageName: String, category: AppCategory): AppProfile {
            val mode = when (category) {
                AppCategory.VIDEO -> CompanionDisplayMode.EDGE_PEEK
                AppCategory.GAME -> CompanionDisplayMode.HIDDEN
                AppCategory.MESSAGING -> CompanionDisplayMode.SMALL
                AppCategory.FINANCE -> CompanionDisplayMode.PRIVACY
                AppCategory.SYSTEM -> CompanionDisplayMode.QUIET
                else -> CompanionDisplayMode.NORMAL
            }
            return AppProfile(
                packageName = packageName,
                displayMode = mode,
                allowMessages = mode !in setOf(CompanionDisplayMode.QUIET, CompanionDisplayMode.HIDDEN, CompanionDisplayMode.PRIVACY),
                allowContextActions = mode !in setOf(CompanionDisplayMode.HIDDEN, CompanionDisplayMode.PRIVACY),
                allowWellbeingReactions = mode !in setOf(CompanionDisplayMode.QUIET, CompanionDisplayMode.HIDDEN, CompanionDisplayMode.PRIVACY),
                sensitiveOverride = if (category == AppCategory.FINANCE) true else null,
            )
        }
    }
}

private fun AppProfile.encode() = listOf(
    displayMode.name, allowMessages, allowContextActions, allowWellbeingReactions,
    sensitiveOverride?.toString().orEmpty(), categoryOverride?.name.orEmpty(),
).joinToString("|")

private fun String.decode(packageName: String): AppProfile? = runCatching {
    val values = split('|')
    AppProfile(
        packageName,
        CompanionDisplayMode.valueOf(values[0]),
        values[1].toBooleanStrict(),
        values[2].toBooleanStrict(),
        values[3].toBooleanStrict(),
        values.getOrNull(4)?.takeIf(String::isNotBlank)?.toBooleanStrict(),
        values.getOrNull(5)?.takeIf(String::isNotBlank)?.let(AppCategory::valueOf),
    )
}.getOrNull()
