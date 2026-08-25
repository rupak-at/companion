package com.ambientcompanion.renderer

import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.overlay.CompanionView

class AnimatedAssetRenderer(private val view: CompanionView) : CompanionRenderer {
    override fun setState(state: CompanionState) = view.setState(state)
    override fun play(animation: AnimationId) = view.play(animation)
    override fun setAccessory(accessory: AccessoryId?) = view.setAccessory(accessory)
    override fun setOpacity(value: Float) = view.setOpacity(value)
    override fun pause() = view.pause()
    override fun resume() = view.resume()
}

class EmojiRenderer(private val view: CompanionView) : CompanionRenderer {
    override fun setState(state: CompanionState) = view.setState(state)
    override fun play(animation: AnimationId) = view.play(animation)
    override fun setAccessory(accessory: AccessoryId?) = view.setAccessory(null)
    override fun setOpacity(value: Float) = view.setOpacity(value)
    override fun pause() = view.pause()
    override fun resume() = view.resume()
}
