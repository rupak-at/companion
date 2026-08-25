package com.ambientcompanion.domain.screen

enum class AvoidZoneSource { KEYBOARD, FOCUSED_INPUT, DIALOG, PRIMARY_ACTION, CUTOUT, SYSTEM_BAR }
data class AvoidZone(val bounds: ScreenBounds, val priority: Int, val source: AvoidZoneSource)

data class PositionResolution(val x: Int, val y: Int, val moved: Boolean, val reason: AvoidZoneSource? = null)

class ObstructionResolver(private val gapPx: Int = 12) {
    fun resolve(
        current: ScreenBounds,
        screen: ScreenBounds,
        zones: List<AvoidZone>,
        preferRight: Boolean,
    ): PositionResolution {
        val blockers = zones.sortedByDescending(AvoidZone::priority)
        if (blockers.none { current.intersects(it.bounds) }) return PositionResolution(current.left, current.top, false)
        val width = current.width
        val height = current.height
        val xs = if (preferRight) listOf(screen.right - width, screen.left) else listOf(screen.left, screen.right - width)
        val ys = buildList {
            add(current.top.coerceIn(screen.top, (screen.bottom - height).coerceAtLeast(screen.top)))
            blockers.forEach { zone ->
                add(zone.bounds.top - height - gapPx)
                add(zone.bounds.bottom + gapPx)
            }
            add(screen.top)
            add(screen.bottom - height)
        }
        val candidates = xs.flatMap { x -> ys.map { y -> ScreenBounds(x, y, x + width, y + height) } }
            .map { it.copy(left = it.left.coerceIn(screen.left, screen.right - width), top = it.top.coerceIn(screen.top, screen.bottom - height)).let { b -> ScreenBounds(b.left, b.top, b.left + width, b.top + height) } }
        val best = candidates.filter { candidate -> blockers.none { candidate.intersects(it.bounds) } }
            .minByOrNull { candidate -> kotlin.math.abs(candidate.left - current.left) + kotlin.math.abs(candidate.top - current.top) }
            ?: candidates.minByOrNull { candidate -> blockers.filter { candidate.intersects(it.bounds) }.sumOf(AvoidZone::priority) }
            ?: current
        return PositionResolution(best.left, best.top, best.left != current.left || best.top != current.top, blockers.firstOrNull { current.intersects(it.bounds) }?.source)
    }
}
