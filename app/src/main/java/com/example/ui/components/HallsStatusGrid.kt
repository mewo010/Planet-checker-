package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HallCapacityInfo
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetAmberGlow
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetNavy
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseGlow
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary

data class HallState(
    val hallNumber: Int,
    val roomType: RoomType,
    val capacity: Int,
    val capacityInfo: HallCapacityInfo,
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
    val hallStates = remember(allScreenings, currentCinemaMinutes) {
        (1..24).map { hallNum ->
            val type = when {
                hallNum == 5 -> RoomType.IMAX
                hallNum == 19 -> RoomType.SCREEN_X
                hallNum == 20 -> RoomType.FOUR_DX
                hallNum in 21..24 -> RoomType.VIP
                else -> RoomType.TWO_D
            }
            val screeningsForHall = allScreenings.filter { it.roomNumber == hallNum }
            val live = screeningsForHall.firstOrNull { it.status == ScreeningStatus.LIVE }
            val next = screeningsForHall
                .filter { it.status == ScreeningStatus.UPCOMING && it.startMin > currentCinemaMinutes }
                .minByOrNull { it.startMin }
            val capacityInfo = CinemaRepository.getHallCapacityInfo(hallNum)
            val capacity = capacityInfo.usableCapacity
                ?: capacityInfo.originalCapacity
                ?: live?.roomCapacity()
                ?: next?.roomCapacity()
                ?: screeningsForHall.firstNotNullOfOrNull { it.roomCapacity() }
                ?: 0

            HallState(
                hallNumber = hallNum,
                roomType = type,
                capacity = capacity,
                capacityInfo = capacityInfo,
                liveScreening = live,
                nextScreening = next
            )
        }
    }

    val activeCount = remember(hallStates) { hallStates.count { it.liveScreening != null } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md)
    ) {
        SectionHeaderRow(
            title = "מפת 24 האולמות",
            subtitle = "סטטוס פעילות וקיבולת שמישה בזמן אמת",
            icon = Icons.Filled.GridView,
            iconTint = PlanetRoseLight,
            trailing = {
                StatusPill(
                    text = "$activeCount / 24 פעילים",
                    textColor = PlanetRoseLight,
                    containerColor = PlanetRoseGlow,
                    borderColor = PlanetRose.copy(alpha = 0.4f),
                    leadingDotColor = if (activeCount > 0) PlanetRoseLight else null
                )
            }
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 840.dp -> 4
                maxWidth >= 560.dp -> 3
                else -> 2
            }
            val rows = remember(hallStates, columns) { hallStates.chunked(columns) }

            Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
                rows.forEach { rowHalls ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                    ) {
                        rowHalls.forEach { hall ->
                            HallCard(
                                hallState = hall,
                                currentCinemaMinutes = currentCinemaMinutes,
                                cinemaRepository = cinemaRepository,
                                onScreeningClick = onScreeningClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        repeat(columns - rowHalls.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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
    val targetScreening = live ?: hallState.nextScreening
    val isClickable = targetScreening != null
    val capInfo = hallState.capacityInfo

    val borderColor = when {
        isLive -> PlanetRose.copy(alpha = 0.55f)
        !capInfo.isBrokenSeatsKnown -> PlanetAmber.copy(alpha = 0.4f)
        else -> PlanetBorderSubtle
    }
    val gradient = if (isLive) {
        listOf(PlanetNavy, PlanetCardBg)
    } else {
        listOf(PlanetSurface, PlanetDarkBg.copy(alpha = 0.85f))
    }

    val shape = RoundedCornerShape(PlanetRadius.md)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 108.dp)
            .clip(shape)
            .background(Brush.verticalGradient(gradient))
            .border(1.dp, borderColor, shape)
            .then(
                if (isClickable) {
                    Modifier.clickable(onClick = { onScreeningClick(targetScreening!!) })
                } else Modifier
            )
            .padding(10.dp)
            .testTag("hall_card_${hallState.hallNumber}")
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "אולם ${hallState.hallNumber}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = PlanetTextPrimary,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(PlanetRadius.xs))
                            .background(hallState.roomType.containerColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = hallState.roomType.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = hallState.roomType.badgeColor,
                            maxLines = 1
                        )
                    }
                }

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
                                    fontSize = 11.sp
                                ),
                                color = PlanetTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val remaining = live.endMin - currentCinemaMinutes
                        Text(
                            text = "נגמר ${cinemaRepository.relativeTimeSpan(remaining)} (${cinemaRepository.formatCinemaMinutes(live.endMin)})",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = PlanetRoseLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val elapsed = (currentCinemaMinutes - live.startMin).coerceAtLeast(0)
                        val total = (live.endMin - live.startMin).coerceAtLeast(1)
                        val progressFraction = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                        SmoothProgressBar(
                            progress = progressFraction,
                            color = PlanetRose,
                            height = 4.dp
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
                            color = PlanetEmeraldLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = if (!capInfo.isBrokenSeatsKnown) "בשיפוץ / ללא הקרנות" else "פנוי כרגע",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = if (!capInfo.isBrokenSeatsKnown) PlanetGold else PlanetTextMuted,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (capInfo.isBrokenSeatsKnown) {
                Text(
                    text = "${capInfo.usableCapacity} שמישים • ${capInfo.brokenSeats} תקולים (מקור ${capInfo.originalCapacity})",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = PlanetTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PlanetRadius.xs))
                        .background(PlanetAmberGlow)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "מקור ${capInfo.originalCapacity ?: "?"} • תקולים ושמישים: לא ידוע",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                        color = PlanetGold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
