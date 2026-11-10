package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EventKind
import com.example.data.model.KpiStats
import com.example.data.model.RoomType
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetEmerald
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetImaxSky
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextSecondary

@Composable
fun KpiCardsSection(
    kpiStats: KpiStats,
    cinemaRepository: CinemaRepository,
    isLiveDataSource: Boolean = true,
    onInsideAudienceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Inside Now & Leaving in 30m
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = "בקולנוע כרגע",
                value = kpiStats.peopleInside.toString(),
                subtitle = "${kpiStats.activeHallsCount} אולמות פעילים • לחץ לפירוט",
                icon = Icons.Filled.Group,
                accentColor = PlanetRose,
                valueColor = Color.White,
                onClick = onInsideAudienceClick,
                badgeText = if (isLiveDataSource) "נתון אמת" else "דוגמה",
                testTag = "kpi_inside",
                modifier = Modifier.weight(1f)
            )

            KpiCard(
                title = "יוצאים ב-30 דק'",
                value = kpiStats.out30MinPeople.toString(),
                subtitle = "${kpiStats.out30MinHalls} אולמות מתרוקנים",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                accentColor = PlanetAmber,
                valueColor = PlanetGold,
                testTag = "kpi_leaving",
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Arriving in 30m & Next Event
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = "מגיעים ב-30 דק'",
                value = kpiStats.in30MinPeople.toString(),
                subtitle = "${kpiStats.in30MinHalls} הקרנות מתמלאות",
                icon = Icons.AutoMirrored.Filled.Login,
                accentColor = PlanetEmerald,
                valueColor = PlanetEmeraldLight,
                testTag = "kpi_arriving",
                modifier = Modifier.weight(1f)
            )

            val next = kpiStats.nextEvent
            val nextTitle = if (next != null) {
                val kindText = if (next.kind == EventKind.OUT) "יציאה" else "כניסה"
                "$kindText • ${RoomType.formatRoomLabel(next.screening.room)}"
            } else "—"

            val nextSubtitle = if (next != null) {
                val formattedTime = cinemaRepository.formatCinemaMinutes(next.timeMin)
                val relTime = cinemaRepository.relativeTimeSpan(next.minutesUntil)
                "$formattedTime • $relTime"
            } else "אין אירועים קרובים"

            KpiCard(
                title = "האירוע הבא",
                value = nextTitle,
                subtitle = nextSubtitle,
                icon = Icons.Filled.NotificationsActive,
                accentColor = PlanetImaxSky,
                valueColor = Color.White,
                isTextValue = true,
                testTag = "kpi_next_event",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    isTextValue: Boolean = false,
    onClick: (() -> Unit)? = null,
    badgeText: String? = null,
    testTag: String = ""
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PlanetSurface,
                        PlanetCardBg.copy(alpha = 0.85f)
                    )
                )
            )
            .border(1.dp, PlanetBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .then(clickModifier)
            .padding(12.dp)
            .testTag(testTag)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = PlanetTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PlanetEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = PlanetEmeraldLight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isTextValue) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = valueColor
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = PlanetTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
