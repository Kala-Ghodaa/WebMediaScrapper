package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceWhite
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.VividViolet

/**
 * Ambient Aurora Mesh background that gives translucent Glass surfaces
 * subtle cyan, violet, and emerald light refraction without visual clutter.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Top-right Electric Cyan ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.14f),
                        ElectricCyan.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.08f),
                    radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f,
                center = Offset(size.width * 0.88f, size.height * 0.08f)
            )

            // Center-left Vivid Violet ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VividViolet.copy(alpha = 0.13f),
                        VividViolet.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.12f, size.height * 0.36f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.12f, size.height * 0.36f)
            )

            // Bottom-right subtle Emerald/Cyan glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        EmeraldSuccess.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.86f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.82f, size.height * 0.86f)
            )
        }
        content()
    }
}

/**
 * Translucent Frosted Glass Card with directional specular border highlight.
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
    val fillBrush = Brush.verticalGradient(
        colors = if (elevated) {
            listOf(
                Color.White.copy(alpha = 0.13f),
                Color.White.copy(alpha = 0.06f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.09f),
                Color.White.copy(alpha = 0.04f)
            )
        }
    )

    val borderBrush = Brush.linearGradient(
        colors = if (accentColor != null) {
            listOf(
                accentColor.copy(alpha = 0.55f),
                GlassBorderLight,
                accentColor.copy(alpha = 0.18f)
            )
        } else {
            listOf(
                GlassBorderLight,
                GlassBorderSubtle,
                Color.White.copy(alpha = 0.04f)
            )
        }
    )

    val baseModifier = modifier
        .clip(shape)
        .background(brush = fillBrush, shape = shape)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
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
    accentColor: Color = ElectricCyan,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val containerColor = if (selected) {
        accentColor.copy(alpha = 0.20f)
    } else {
        GlassSurfaceWhite
    }

    val borderStroke = BorderStroke(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = if (selected) {
                listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.35f))
            } else {
                listOf(GlassBorderLight, GlassBorderSubtle)
            }
        )
    )

    Surface(
        color = containerColor,
        shape = shape,
        border = borderStroke,
        modifier = modifier
            .clip(shape)
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
 * Translucent Glass Top Header Surface with subtle bottom specular divider.
 */
@Composable
fun GlassHeaderSurface(
    modifier: Modifier = Modifier,
    elevationAlpha: Float = 0.08f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111A2E).copy(alpha = 0.88f),
                        Color(0xFF0B1120).copy(alpha = 0.78f)
                    )
                )
            )
            .border(
                width = Dp.Hairline,
                color = GlassBorderLight
            ),
        content = content
    )
}
