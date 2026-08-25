package com.ambientcompanion.domain.screen

class SensitiveScreenDetector {
    fun detect(
        snapshot: SanitizedScreenSnapshot,
        category: AppCategory,
        profile: AppProfile? = null,
    ): SensitiveContext {
        if (profile?.sensitiveOverride == false) return SensitiveContext(false)
        val reasons = buildSet {
            if (snapshot.passwordFieldCount > 0) add(SensitiveReason.PASSWORD_FIELD)
            if (snapshot.pinFieldCount > 0) add(SensitiveReason.PIN_FIELD)
            if (category == AppCategory.FINANCE) add(SensitiveReason.FINANCE_APP)
            if (profile?.displayMode == CompanionDisplayMode.PRIVACY || profile?.sensitiveOverride == true) add(SensitiveReason.USER_PROFILE)
            if ((snapshot.passwordFieldCount > 0 || snapshot.pinFieldCount > 0) && snapshot.hasSubmitLikeControl) add(SensitiveReason.AUTHENTICATION_SCREEN)
            if (snapshot.isSecureWindow) add(SensitiveReason.SECURE_WINDOW)
        }
        return SensitiveContext(reasons.isNotEmpty(), reasons)
    }
}
