package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Quiet, theme-aware background shared by the main screens.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}

/**
 * Theme-aware surface card with a restrained optional accent border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    accentColor: Color? = null,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val fillColor = if (elevated) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    val borderColor = accentColor?.copy(alpha = 0.22f) ?: MaterialTheme.colorScheme.outlineVariant

    val baseModifier = modifier
        .clip(shape)
        .background(color = fillColor, shape = shape)
        .border(width = 1.dp, color = borderColor, shape = shape)
        .let { mod ->
            if (onClick != null) mod.clickable(onClick = onClick) else mod
        }

    Box(
        modifier = baseModifier,
        content = content
    )
}

/**
 * Compact Frosted Glass Pill for chips, badges, and toolbar buttons.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val containerColor = if (selected) {
        accentColor.copy(alpha = 0.20f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val borderStroke = BorderStroke(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = if (selected) {
                listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.35f))
            } else {
                listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant)
            }
        )
    )

    Surface(
        color = containerColor,
        shape = shape,
        border = borderStroke,
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.heightIn(min = 48.dp) else Modifier)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}


/**
 * Theme-aware header with a subtle divider.
 */
@Composable
fun GlassHeaderSurface(
    modifier: Modifier = Modifier,
    elevationAlpha: Float = 0.08f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = Dp.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant
            ),
        content = content
    )
}
