package com.ambientcompanion.renderer

import com.ambientcompanion.domain.model.CompanionState

enum class AnimationId {
    IDLE, BLINK, LOOK_LEFT, LOOK_RIGHT, TAP_HAPPY, DOUBLE_TAP_SURPRISED, DRAG, EDGE_LAND,
    SLEEP, WAKE_UP, CHARGING, BATTERY_LOW, BATTERY_FULL, HEADPHONES,
    NETWORK_LOST, NETWORK_RESTORED, RAIN, COLD, HOT, WEEKEND,
    MESSAGE_SHOW, MESSAGE_HIDE, STATE_TRANSITION, WAVE, PEEK, SPIN, TINY_JUMP, STRETCH,
}

enum class AccessoryId { SCARF, UMBRELLA, SLEEP_CAP, HEADPHONES, SUNGLASSES, CHARGING_SPARK }

interface CompanionRenderer {
    fun setState(state: CompanionState)
    fun play(animation: AnimationId)
    fun setAccessory(accessory: AccessoryId?)
    fun setOpacity(value: Float)
    fun pause()
    fun resume()
}
