package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetElevation
import com.example.ui.theme.PlanetMotion
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    borderColor: Color = PlanetBorderSubtle,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = PlanetRadius.lg,
    gradientColors: List<Color> = listOf(PlanetSurface, PlanetCardBg),
    elevation: Dp = PlanetElevation.subtle,
    contentPadding: PaddingValues = PaddingValues(PlanetSpacing.md),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        shape = shape,
        color = Color.Transparent,
        tonalElevation = elevation,
        shadowElevation = elevation,
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(gradientColors))
            .border(borderWidth, borderColor, shape)
            .then(clickModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    leadingDotColor: Color? = null,
    leadingIcon: ImageVector? = null
) {
    val shape = RoundedCornerShape(PlanetRadius.pill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .padding(horizontal = PlanetSpacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.xs)
    ) {
        if (leadingDotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(leadingDotColor)
            )
        }
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Animates KPI number changes smoothly (vertical slide + fade) without restarting
 * when unrelated state (like the 1-second clock) ticks.
 */
@Composable
fun AnimatedMetricValue(
    value: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(PlanetMotion.standardTween(0, true)) togetherWith
                    fadeOut(PlanetMotion.standardTween(0, true))
            } else {
                (slideInVertically(PlanetMotion.standardTween(PlanetMotion.counterMs)) { it / 3 } +
                    fadeIn(PlanetMotion.standardTween(PlanetMotion.fastMs))) togetherWith
                    (slideOutVertically(PlanetMotion.standardTween(PlanetMotion.fastMs)) { -it / 3 } +
                        fadeOut(PlanetMotion.standardTween(PlanetMotion.fastMs)))
            }
        },
        label = "kpi_metric_value",
        modifier = modifier
    ) { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Triggers a gentle, non-looping accent highlight when [triggerKey] changes after initial composition.
 */
@Composable
fun rememberValueChangeHighlight(
    triggerKey: Any?,
    baseColor: Color,
    highlightColor: Color
): Color {
    val reducedMotion = rememberReducedMotion()
    var isInitialized by remember { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf(false) }

    LaunchedEffect(triggerKey) {
        if (!isInitialized) {
            isInitialized = true
        } else if (!reducedMotion) {
            highlighted = true
            delay(PlanetMotion.pulseHighlightMs.toLong())
            highlighted = false
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = if (highlighted) highlightColor else baseColor,
        animationSpec = PlanetMotion.standardTween(PlanetMotion.mediumMs, reducedMotion),
        label = "value_change_highlight"
    )
    return animatedColor
}

@Composable
fun SmoothProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = PlanetDarkBg,
    height: Dp = 5.dp
) {
    val reducedMotion = rememberReducedMotion()
    val clamped = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = PlanetMotion.standardTween(PlanetMotion.progressMs, reducedMotion),
        label = "smooth_progress"
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(PlanetRadius.pill)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round
    )
}

@Composable
fun SectionHeaderRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = PlanetTextSecondary,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(PlanetRadius.sm))
                        .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PlanetTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = PlanetTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
