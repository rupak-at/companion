package com.ambientcompanion.overlay

import com.ambientcompanion.domain.screen.AvoidZone
import com.ambientcompanion.domain.screen.AvoidZoneSource
import com.ambientcompanion.domain.screen.ObstructionResolver
import com.ambientcompanion.domain.screen.PositionResolution
import com.ambientcompanion.domain.screen.ScreenBounds
import com.ambientcompanion.domain.screen.ScreenContext
import com.ambientcompanion.domain.screen.ScreenType

class SmartPositionController(private val resolver: ObstructionResolver = ObstructionResolver()) {
    fun resolve(current: ScreenBounds, screen: ScreenBounds, context: ScreenContext): PositionResolution {
        val zones = buildList {
            if (context.isKeyboardVisible) add(
                AvoidZone(
                    ScreenBounds(screen.left, screen.top + screen.height * 58 / 100, screen.right, screen.bottom),
                    100,
                    AvoidZoneSource.KEYBOARD,
                ),
            )
            context.importantBounds.forEachIndexed { index, bounds ->
                add(AvoidZone(bounds, if (index == 0 && context.hasFocusedInput) 90 else 70, if (index == 0 && context.hasFocusedInput) AvoidZoneSource.FOCUSED_INPUT else AvoidZoneSource.PRIMARY_ACTION))
            }
            if (context.screenType == ScreenType.DIALOG) add(
                AvoidZone(
                    ScreenBounds(screen.left + screen.width / 8, screen.top + screen.height / 5, screen.right - screen.width / 8, screen.bottom - screen.height / 5),
                    80,
                    AvoidZoneSource.DIALOG,
                ),
            )
            add(AvoidZone(ScreenBounds(screen.left, screen.top, screen.right, screen.top + screen.height / 30), 60, AvoidZoneSource.SYSTEM_BAR))
            add(AvoidZone(ScreenBounds(screen.left, screen.bottom - screen.height / 24, screen.right, screen.bottom), 60, AvoidZoneSource.SYSTEM_BAR))
        }
        return resolver.resolve(current, screen, zones, preferRight = current.left + current.width / 2 >= screen.left + screen.width / 2)
    }
}
