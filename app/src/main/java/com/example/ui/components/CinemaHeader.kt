package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetAmberGlow
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetEmeraldGlow
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetMotion
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetSkyGlow
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurfaceGlass
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary
import com.example.ui.theme.rememberReducedMotion

@Composable
fun CinemaHeader(
    isLive: Boolean,
    isRefreshing: Boolean,
    seatMapUpdateInProgress: Boolean,
    dataFreshnessLabel: String,
    lastSuccessfulUpdateLabel: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val statusColor = when {
        !isLive -> PlanetAmber
        seatMapUpdateInProgress -> PlanetImaxSkyLight
        dataFreshnessLabel.startsWith("נתון ישן") -> PlanetAmber
        else -> PlanetEmeraldLight
    }
    val statusContainer = when {
        !isLive -> PlanetAmberGlow
        seatMapUpdateInProgress -> PlanetSkyGlow
        dataFreshnessLabel.startsWith("נתון ישן") -> PlanetAmberGlow
        else -> PlanetEmeraldGlow
    }
    val statusText = when {
        seatMapUpdateInProgress -> "מעדכן מושבים…"
        !isLive -> "לא מקוון"
        lastSuccessfulUpdateLabel.isNotBlank() && lastSuccessfulUpdateLabel != "טרם עודכן" ->
            "עודכן $lastSuccessfulUpdateLabel"
        else -> "נתונים חיים"
    }

    Surface(
        color = PlanetSurfaceGlass,
        modifier = modifier
            .fillMaxWidth()
            .testTag("cinema_header")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlanetSpacing.md, vertical = PlanetSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand mark + title (weight(1f) prevents collision on narrow screens)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(PlanetRadius.md))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PlanetRose, PlanetAmber)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = "פלאנט צ'קר",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "פלאנט צ'קר",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = PlanetTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                        }

                        Text(
                            text = if (isLive) "ראשון לציון • נתוני קופות חיים" else "ראשון לציון • נתוני גיבוי מקומיים",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (isLive) PlanetTextSecondary else PlanetAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Connection / Freshness Pill + Accessible Refresh Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.xs)
                ) {
                    AnimatedContent(
                        targetState = statusText,
                        transitionSpec = {
                            fadeIn(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) togetherWith
                                fadeOut(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion))
                        },
                        label = "header_status_pill"
                    ) { targetStatus ->
                        StatusPill(
                            text = targetStatus,
                            textColor = statusColor,
                            containerColor = statusContainer,
                            borderColor = statusColor.copy(alpha = 0.35f),
                            leadingIcon = when {
                                !isLive -> Icons.Filled.CloudOff
                                seatMapUpdateInProgress -> Icons.Filled.Sync
                                else -> null
                            },
                            leadingDotColor = if (isLive && !seatMapUpdateInProgress) statusColor else null
                        )
                    }

                    RefreshActionButton(
                        isRefreshing = isRefreshing,
                        reducedMotion = reducedMotion,
                        onClick = onRefresh
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = PlanetBorderSubtle
            )
        }
    }
}

@Composable
private fun RefreshActionButton(
    isRefreshing: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val rotation = if (isRefreshing && !reducedMotion) {
        val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refresh_rotation"
        )
        angle
    } else {
        0f
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .testTag("refresh_button")
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(PlanetRadius.md))
                .background(PlanetCardBg)
                .border(1.dp, PlanetBorder.copy(alpha = 0.7f), RoundedCornerShape(PlanetRadius.md)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "רענן נתונים",
                tint = PlanetTextPrimary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation)
            )
        }
    }
}
