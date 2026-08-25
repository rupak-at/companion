package com.ambientcompanion.ui

import androidx.annotation.DrawableRes
import com.ambientcompanion.R
import com.ambientcompanion.data.preferences.CompanionArtwork

@DrawableRes
fun CompanionArtwork.drawableRes(): Int = when (this) {
    CompanionArtwork.BIRD -> R.drawable.companion_bird
    CompanionArtwork.BOY_HOODIE -> R.drawable.companion_boy_hoodie
    CompanionArtwork.BULL_BLACK -> R.drawable.companion_bull_black
    CompanionArtwork.FOX -> R.drawable.companion_fox
    CompanionArtwork.EAGLE_FLYING -> R.drawable.companion_eagle_flying
    CompanionArtwork.EAGLE_AVIATOR -> R.drawable.companion_eagle_aviator
    CompanionArtwork.GIRL_PORTRAIT -> R.drawable.companion_girl_portrait
    CompanionArtwork.GIRL_YELLOW_DRESS -> R.drawable.companion_girl_yellow_dress
    CompanionArtwork.GIRL_MINT_HOODIE -> R.drawable.companion_girl_mint_hoodie
    CompanionArtwork.PANDA -> R.drawable.companion_panda
}
