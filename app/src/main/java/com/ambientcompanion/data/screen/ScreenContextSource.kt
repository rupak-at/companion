package com.ambientcompanion.data.screen

import com.ambientcompanion.data.profile.AppCategoryResolver
import com.ambientcompanion.data.profile.AppProfileRepository
import com.ambientcompanion.domain.screen.DefaultScreenActionResolver
import com.ambientcompanion.domain.screen.DeterministicScreenClassifier
import com.ambientcompanion.domain.screen.SanitizedScreenSnapshot
import com.ambientcompanion.domain.screen.ScreenContext
import com.ambientcompanion.domain.screen.SensitiveScreenDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenContextSource(
    private val categoryResolver: AppCategoryResolver,
    private val profileRepository: AppProfileRepository,
) {
    private val classifier = DeterministicScreenClassifier()
    private val sensitiveDetector = SensitiveScreenDetector()
    private val actionResolver = DefaultScreenActionResolver()
    private val mutableState = MutableStateFlow(ScreenContext.EMPTY)
    val state: StateFlow<ScreenContext> = mutableState.asStateFlow()

    fun update(snapshot: SanitizedScreenSnapshot, sensitiveModeEnabled: Boolean): ScreenContext {
        val packageName = snapshot.packageName
        val category = categoryResolver.resolve(packageName)
        val profile = packageName?.let { profileRepository.profileFor(it, category) }
        val effectiveCategory = categoryResolver.resolve(packageName, profile?.categoryOverride)
        val classification = classifier.classify(snapshot)
        val sensitive = if (sensitiveModeEnabled) sensitiveDetector.detect(snapshot, effectiveCategory, profile)
        else com.ambientcompanion.domain.screen.SensitiveContext(false)
        val base = ScreenContext(
            packageName = packageName,
            appCategory = effectiveCategory,
            screenType = classification.type,
            isKeyboardVisible = snapshot.isKeyboardVisible,
            hasFocusedInput = snapshot.hasFocusedInput,
            isScrollable = snapshot.scrollableCount > 0,
            isFullScreen = snapshot.isFullScreen,
            isSensitive = sensitive.isSensitive,
            orientation = snapshot.orientation,
            confidence = classification.confidence,
            importantBounds = snapshot.importantBounds,
            sensitiveReasons = sensitive.reasons,
        )
        return base.copy(availableActions = actionResolver.resolve(base)).also { mutableState.value = it }
    }

    fun packageOnly(packageName: String?): ScreenContext {
        val context = ScreenContext(packageName = packageName, appCategory = categoryResolver.resolve(packageName))
        mutableState.value = context
        return context
    }

    fun clear() { mutableState.value = ScreenContext.EMPTY }
}
