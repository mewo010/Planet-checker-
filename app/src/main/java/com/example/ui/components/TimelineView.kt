package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CinemaEvent
import com.example.data.model.EventKind
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetAmberGlow
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmeraldGlow
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary

@Composable
fun TimelineView(
    events: List<CinemaEvent>,
    currentCinemaMinutes: Int,
    cinemaRepository: CinemaRepository,
    onScreeningClick: (Screening) -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardCard(
        contentPadding = PaddingValues(PlanetSpacing.lg),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md)) {
            SectionHeaderRow(
                title = "ציר עומסי קהל",
                subtitle = "אירועי כניסה ויציאה ב-2 השעות הקרובות",
                icon = Icons.Filled.Timeline,
                iconTint = PlanetImaxSkyLight,
                trailing = {
                    StatusPill(
                        text = "${events.size} אירועים",
                        textColor = PlanetGold,
                        containerColor = PlanetAmberGlow,
                        borderColor = PlanetAmber.copy(alpha = 0.35f)
                    )
                }
            )

            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PlanetRadius.md))
                        .background(PlanetDarkBg.copy(alpha = 0.65f))
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bedtime,
                            contentDescription = null,
                            tint = PlanetTextMuted,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "שקט ב-2 השעות הקרובות",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlanetTextSecondary
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    events.forEach { event ->
                        TimelineEventRow(
                            event = event,
                            currentCinemaMinutes = currentCinemaMinutes,
                            cinemaRepository = cinemaRepository,
                            onClick = { onScreeningClick(event.screening) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PlanetRadius.sm))
                    .background(PlanetDarkBg.copy(alpha = 0.8f))
                    .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.sm))
                    .padding(PlanetSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = PlanetTextMuted,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(top = 2.dp)
                )
                Text(
                    text = "כניסה = 20 דק' לפני תחילת ההקרנה. יציאה = שעת סיום + 15 דק' לפינוי וניקיון. נתוני הצופים מבוססים על הנתונים הזמינים מהקופות.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = PlanetTextMuted
                )
            }
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: CinemaEvent,
    currentCinemaMinutes: Int,
    cinemaRepository: CinemaRepository,
    onClick: () -> Unit
) {
    val isOut = event.kind == EventKind.OUT
    val isUrgent = event.isUrgent
    val nowEntry = !isOut && event.screening.entryMin <= currentCinemaMinutes

    val borderColor = if (isUrgent) PlanetAmber.copy(alpha = 0.65f) else PlanetBorderSubtle
    val bgColor = if (isUrgent) PlanetAmberGlow else PlanetCardBg.copy(alpha = 0.65f)
    val iconBg = if (isOut) PlanetAmberGlow else PlanetEmeraldGlow
    val iconColor = if (isOut) PlanetGold else PlanetEmeraldLight
    val icon = if (isOut) Icons.AutoMirrored.Filled.ExitToApp else Icons.AutoMirrored.Filled.Login

    val mainText = when {
        isOut -> "קהל יוצא • ${RoomType.formatRoomLabel(event.screening.room)}"
        nowEntry -> "קהל נכנס כעת • ${RoomType.formatRoomLabel(event.screening.room)}"
        else -> "כניסה קרובה • ${RoomType.formatRoomLabel(event.screening.room)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clip(RoundedCornerShape(PlanetRadius.md))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(PlanetRadius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = PlanetSpacing.md, vertical = 10.dp)
            .testTag("timeline_event_${event.screening.id}"),
        horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(PlanetRadius.sm))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = PlanetTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isUrgent) {
                    StatusPill(
                        text = "דחוף",
                        textColor = PlanetGold,
                        containerColor = PlanetAmber.copy(alpha = 0.2f)
                    )
                }
            }

            Text(
                text = event.screening.title,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = PlanetTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = cinemaRepository.formatCinemaMinutes(event.timeMin),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = if (isUrgent) PlanetGold else PlanetTextPrimary
            )

            val countText = event.screening.estimatedPeople?.let { " • ~$it צופים" } ?: ""
            Text(
                text = "${cinemaRepository.relativeTimeSpan(event.minutesUntil)}$countText",
                style = MaterialTheme.typography.labelSmall,
                color = if (isUrgent) PlanetAmber else PlanetTextMuted,
                maxLines = 1
            )
        }
    }
}
