package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmerald
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextSecondary

@Composable
fun TimelineView(
    events: List<CinemaEvent>,
    currentCinemaMinutes: Int,
    cinemaRepository: CinemaRepository,
    onScreeningClick: (Screening) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "הכנה לעומסים",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "אירועי כניסה ויציאה קרובים",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlanetTextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "שעתיים קדימה",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = PlanetAmber
                )
            }
        }

        // Events list or empty state
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bedtime,
                        contentDescription = null,
                        tint = PlanetTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "שקט ב-2 השעות הקרובות",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlanetTextMuted
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                events.take(12).forEach { event ->
                    TimelineEventRow(
                        event = event,
                        currentCinemaMinutes = currentCinemaMinutes,
                        cinemaRepository = cinemaRepository,
                        onClick = { onScreeningClick(event.screening) }
                    )
                }
            }
        }

        // Helpful guidance footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PlanetCardBg.copy(alpha = 0.5f))
                .padding(8.dp),
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
                text = "כניסה = כ-20 דק' לפני תחילת ההקרנה. יציאה = שעת הסיום כולל 15 דק' תוספת. מספרי הצופים הם הערכה לפי תפוסת האולם.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = PlanetTextMuted
            )
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

    val borderColor = when {
        isUrgent -> PlanetAmber.copy(alpha = 0.6f)
        else -> PlanetBorder.copy(alpha = 0.5f)
    }

    val bgColor = when {
        isUrgent -> PlanetAmber.copy(alpha = 0.10f)
        else -> PlanetCardBg.copy(alpha = 0.6f)
    }

    val iconBg = if (isOut) PlanetAmber.copy(alpha = 0.15f) else PlanetEmerald.copy(alpha = 0.15f)
    val iconColor = if (isOut) PlanetAmber else PlanetEmeraldLight
    val icon = if (isOut) Icons.AutoMirrored.Filled.ExitToApp else Icons.AutoMirrored.Filled.Login

    val mainText = when {
        isOut -> "קהל יוצא • ${RoomType.formatRoomLabel(event.screening.room)}"
        nowEntry -> "קהל נכנס עכשיו • ${RoomType.formatRoomLabel(event.screening.room)}"
        else -> "קהל מתחיל להגיע • ${RoomType.formatRoomLabel(event.screening.room)}"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("timeline_event_${event.screening.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mainText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = event.screening.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = PlanetTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Time & Countdown
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = cinemaRepository.formatCinemaMinutes(event.timeMin),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = if (isUrgent) PlanetGold else Color.White
                )

                val countText = event.screening.estimatedPeople?.let { " • ~$it" } ?: ""
                Text(
                    text = "${cinemaRepository.relativeTimeSpan(event.minutesUntil)}$countText",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = if (isUrgent) PlanetAmber else PlanetTextMuted
                )
            }
        }
    }
}
