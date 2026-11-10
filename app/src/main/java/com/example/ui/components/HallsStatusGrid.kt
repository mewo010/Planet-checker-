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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextSecondary

data class HallState(
    val hallNumber: Int,
    val roomType: RoomType,
    val capacity: Int,
    val liveScreening: Screening?,
    val nextScreening: Screening?
)

@Composable
fun HallsStatusGrid(
    allScreenings: List<Screening>,
    currentCinemaMinutes: Int,
    cinemaRepository: CinemaRepository,
    onScreeningClick: (Screening) -> Unit,
    modifier: Modifier = Modifier
) {
    val hallStates = (1..24).map { hallNum ->
        val type = when {
            hallNum == 5 -> RoomType.IMAX
            hallNum == 19 -> RoomType.SCREEN_X
            hallNum == 20 -> RoomType.FOUR_DX
            hallNum in 21..24 -> RoomType.VIP
            else -> RoomType.TWO_D
        }
        val capacity = CinemaRepository.HALL_CAPACITIES[hallNum] ?: 120

        val screeningsForHall = allScreenings.filter { it.roomNumber == hallNum }
        val live = screeningsForHall.firstOrNull { it.status == ScreeningStatus.LIVE }
        val next = screeningsForHall
            .filter { it.status == ScreeningStatus.UPCOMING && it.startMin > currentCinemaMinutes }
            .minByOrNull { it.startMin }

        HallState(
            hallNumber = hallNum,
            roomType = type,
            capacity = capacity,
            liveScreening = live,
            nextScreening = next
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "מפת 24 האולמות",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "סטטוס פעילות חי בזמן אמת",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlanetTextSecondary
                )
            }

            val activeCount = hallStates.count { it.liveScreening != null }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$activeCount / 24 פעילים כעת",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = PlanetRoseLight
                )
            }
        }

        // Responsive grid of hall cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val chunked = hallStates.chunked(2)
            chunked.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { hall ->
                        HallCard(
                            hallState = hall,
                            currentCinemaMinutes = currentCinemaMinutes,
                            cinemaRepository = cinemaRepository,
                            onScreeningClick = onScreeningClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HallCard(
    hallState: HallState,
    currentCinemaMinutes: Int,
    cinemaRepository: CinemaRepository,
    onScreeningClick: (Screening) -> Unit,
    modifier: Modifier = Modifier
) {
    val live = hallState.liveScreening
    val isLive = live != null

    val borderColor = if (isLive) PlanetRose.copy(alpha = 0.6f) else PlanetBorder.copy(alpha = 0.5f)
    val bgColor = if (isLive) PlanetSurface else PlanetDarkBg.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = live != null || hallState.nextScreening != null) {
                val target = live ?: hallState.nextScreening
                if (target != null) onScreeningClick(target)
            }
            .padding(10.dp)
            .testTag("hall_card_${hallState.hallNumber}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: Hall number & format badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MeetingRoom,
                        contentDescription = null,
                        tint = if (isLive) PlanetRoseLight else PlanetTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "אולם ${hallState.hallNumber}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(hallState.roomType.containerColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = hallState.roomType.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = hallState.roomType.badgeColor
                    )
                }
            }

            // Body: Live Movie or Next
            if (live != null) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PlanetRoseLight)
                        )
                        Text(
                            text = live.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val remaining = live.endMin - currentCinemaMinutes
                    Text(
                        text = "מסתיים ${cinemaRepository.relativeTimeSpan(remaining)} (${cinemaRepository.formatCinemaMinutes(live.endMin)})",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = PlanetRoseLight
                    )

                    // Progress bar
                    val elapsed = (currentCinemaMinutes - live.startMin).coerceAtLeast(0)
                    val total = (live.endMin - live.startMin).coerceAtLeast(1)
                    val progressFraction = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = PlanetRose,
                        trackColor = PlanetDarkBg,
                        strokeCap = StrokeCap.Round
                    )
                }
            } else if (hallState.nextScreening != null) {
                val next = hallState.nextScreening
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "הבא: ${next.title}",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = PlanetTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ב-${cinemaRepository.formatCinemaMinutes(next.startMin)} • ${cinemaRepository.relativeTimeSpan(next.startMin - currentCinemaMinutes)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = PlanetEmeraldLight
                    )
                }
            } else {
                Text(
                    text = "פנוי כרגע (אין הקרנה מתוכננת)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = PlanetTextMuted
                )
            }

            // Hall capacity footer
            Text(
                text = "${hallState.capacity} מקומות ישיבה",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = PlanetTextMuted
            )
        }
    }
}
