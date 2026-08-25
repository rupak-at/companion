package com.ambientcompanion.domain.schedule

import java.time.DayOfWeek
import java.time.LocalTime

enum class OutsideHoursBehavior { SLEEP_IN_PLACE, PEEK_FROM_EDGE, HIDE_COMPLETELY }

object SchedulePolicy {
    fun contains(time: LocalTime, startMinutes: Int, endMinutes: Int): Boolean {
        val minute = time.hour * 60 + time.minute
        return if (startMinutes <= endMinutes) minute in startMinutes until endMinutes
        else minute >= startMinutes || minute < endMinutes
    }

    fun isWeekend(day: DayOfWeek, weekendDays: Set<DayOfWeek>) = day in weekendDays
}
