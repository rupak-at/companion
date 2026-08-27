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
    CompanionArtwork.SPACE_PANDA -> R.drawable.companion_space_panda
    CompanionArtwork.BABY_DRAGON -> R.drawable.companion_baby_dragon
    CompanionArtwork.BLACK_CAT -> R.drawable.companion_black_cat
    CompanionArtwork.TREE_SPIRIT -> R.drawable.companion_tree_spirit
    CompanionArtwork.GOLDEN_DRAGON -> R.drawable.companion_golden_dragon
    CompanionArtwork.GOLDEN_PHOENIX -> R.drawable.companion_golden_phoenix
    CompanionArtwork.GOLDEN_PANTHER -> R.drawable.companion_golden_panther
    CompanionArtwork.PURPLE_PHOENIX -> R.drawable.companion_purple_phoenix
    CompanionArtwork.PURPLE_DRAGON -> R.drawable.companion_purple_dragon
    CompanionArtwork.PURPLE_PANTHER -> R.drawable.companion_purple_panther
    CompanionArtwork.PURPLE_BULL -> R.drawable.companion_purple_bull
    CompanionArtwork.ROBOT_WAVE -> R.drawable.companion_robot_wave
    CompanionArtwork.ROBOT_DARK -> R.drawable.companion_robot_dark
    CompanionArtwork.CYBER_CAT -> R.drawable.companion_cyber_cat
    CompanionArtwork.COSMIC_DRAGON -> R.drawable.companion_cosmic_dragon
    CompanionArtwork.ROBOT_ORBIT -> R.drawable.companion_robot_orbit
}

@DrawableRes
fun CompanionArtwork.thumbnailRes(): Int = when (this) {
    CompanionArtwork.BIRD -> R.drawable.companion_thumb_bird
    CompanionArtwork.BOY_HOODIE -> R.drawable.companion_thumb_boy_hoodie
    CompanionArtwork.BULL_BLACK -> R.drawable.companion_thumb_bull_black
    CompanionArtwork.FOX -> R.drawable.companion_thumb_fox
    CompanionArtwork.EAGLE_FLYING -> R.drawable.companion_thumb_eagle_flying
    CompanionArtwork.EAGLE_AVIATOR -> R.drawable.companion_thumb_eagle_aviator
    CompanionArtwork.GIRL_PORTRAIT -> R.drawable.companion_thumb_girl_portrait
    CompanionArtwork.GIRL_YELLOW_DRESS -> R.drawable.companion_thumb_girl_yellow_dress
    CompanionArtwork.GIRL_MINT_HOODIE -> R.drawable.companion_thumb_girl_mint_hoodie
    CompanionArtwork.PANDA -> R.drawable.companion_thumb_panda
    CompanionArtwork.SPACE_PANDA -> R.drawable.companion_thumb_space_panda
    CompanionArtwork.BABY_DRAGON -> R.drawable.companion_thumb_baby_dragon
    CompanionArtwork.BLACK_CAT -> R.drawable.companion_thumb_black_cat
    CompanionArtwork.TREE_SPIRIT -> R.drawable.companion_thumb_tree_spirit
    CompanionArtwork.GOLDEN_DRAGON -> R.drawable.companion_thumb_golden_dragon
    CompanionArtwork.GOLDEN_PHOENIX -> R.drawable.companion_thumb_golden_phoenix
    CompanionArtwork.GOLDEN_PANTHER -> R.drawable.companion_thumb_golden_panther
    CompanionArtwork.PURPLE_PHOENIX -> R.drawable.companion_thumb_purple_phoenix
    CompanionArtwork.PURPLE_DRAGON -> R.drawable.companion_thumb_purple_dragon
    CompanionArtwork.PURPLE_PANTHER -> R.drawable.companion_thumb_purple_panther
    CompanionArtwork.PURPLE_BULL -> R.drawable.companion_thumb_purple_bull
    CompanionArtwork.ROBOT_WAVE -> R.drawable.companion_thumb_robot_wave
    CompanionArtwork.ROBOT_DARK -> R.drawable.companion_thumb_robot_dark
    CompanionArtwork.CYBER_CAT -> R.drawable.companion_thumb_cyber_cat
    CompanionArtwork.COSMIC_DRAGON -> R.drawable.companion_thumb_cosmic_dragon
    CompanionArtwork.ROBOT_ORBIT -> R.drawable.companion_thumb_robot_orbit
}
