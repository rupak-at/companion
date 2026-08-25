package com.ambientcompanion.data.profile

import com.ambientcompanion.domain.screen.AppCategory

class AppCategoryResolver {
    fun resolve(packageName: String?, override: AppCategory? = null): AppCategory {
        if (override != null) return override
        val name = packageName?.lowercase() ?: return AppCategory.OTHER
        return when {
            name in financePackages || listOf("bank", "wallet", "finance", "payment").any(name::contains) -> AppCategory.FINANCE
            listOf("youtube", "netflix", "video", "twitch").any(name::contains) -> AppCategory.VIDEO
            listOf("spotify", "music", "soundcloud").any(name::contains) -> AppCategory.MUSIC
            listOf("whatsapp", "telegram", "messenger", "signal", "chat").any(name::contains) -> AppCategory.MESSAGING
            listOf("facebook", "instagram", "twitter", "tiktok", "reddit", "social").any(name::contains) -> AppCategory.SOCIAL
            listOf("chrome", "firefox", "browser", "edge").any(name::contains) -> AppCategory.BROWSER
            listOf("kindle", "reader", "books").any(name::contains) -> AppCategory.READING
            listOf("game", "gaming").any(name::contains) -> AppCategory.GAME
            listOf("shop", "amazon", "store").any(name::contains) -> AppCategory.SHOPPING
            name.startsWith("com.android.") || name.startsWith("com.google.android.settings") -> AppCategory.SYSTEM
            listOf("docs", "office", "notion", "productivity").any(name::contains) -> AppCategory.PRODUCTIVITY
            else -> AppCategory.OTHER
        }
    }

    private val financePackages = setOf("com.google.android.apps.walletnfcrel", "com.paypal.android.p2pmobile")
}
