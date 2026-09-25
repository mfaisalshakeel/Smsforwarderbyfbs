package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Single source of truth for spacing and corner radii, so screens stay visually consistent. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp

    /** Horizontal page gutter. */
    val gutter = 16.dp

    /** Content is capped at this width so it does not stretch across a tablet. */
    val maxContentWidth = 720.dp

    /** Minimum height for anything tappable. */
    val minTouchTarget = 48.dp
}

object Shapes {
    val card = RoundedCornerShape(18.dp)
    val cardCompact = RoundedCornerShape(14.dp)
    val field = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(10.dp)
    val dialog = RoundedCornerShape(24.dp)
}
