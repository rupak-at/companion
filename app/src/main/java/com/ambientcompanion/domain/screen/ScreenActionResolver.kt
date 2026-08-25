package com.ambientcompanion.domain.screen

interface ScreenActionResolver { fun resolve(context: ScreenContext): Set<ScreenAction> }

class DefaultScreenActionResolver : ScreenActionResolver {
    override fun resolve(context: ScreenContext): Set<ScreenAction> {
        if (context.isSensitive) return setOf(ScreenAction.BACK, ScreenAction.HOME, ScreenAction.HIDE)
        val generic = setOf(ScreenAction.BACK, ScreenAction.HOME, ScreenAction.HIDE, ScreenAction.REFRESH)
        if (context.confidence == ContextConfidence.LOW) return generic
        return when (context.screenType) {
            ScreenType.ARTICLE -> generic + setOf(ScreenAction.SCROLL_TOP, ScreenAction.SCROLL_BOTTOM, ScreenAction.QUIET_30_MINUTES)
            ScreenType.FORM, ScreenType.LOGIN -> generic + setOf(ScreenAction.PREVIOUS_FIELD, ScreenAction.NEXT_FIELD, ScreenAction.HIDE_KEYBOARD)
            ScreenType.CHAT -> generic + setOf(ScreenAction.HIDE_KEYBOARD, ScreenAction.QUIET_30_MINUTES)
            ScreenType.MEDIA -> generic + setOf(ScreenAction.EDGE_PEEK, ScreenAction.SCREENSHOT)
            else -> generic
        }
    }
}
