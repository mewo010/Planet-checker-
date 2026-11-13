package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.AvailabilitySource
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetAmberGlow
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetCardElevated
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmeraldGlow
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetNavy
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseGlow
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSkyGlow
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary

@Composable
fun ScreeningTicketCard(
    screening: Screening,
    currentCinemaMinutes: Int,
    cinemaRepository: CinemaRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = screening.status == ScreeningStatus.LIVE
    val isRecentlyEnded = screening.status == ScreeningStatus.ENDED_RECENTLY
    val isEnded = screening.status == ScreeningStatus.ENDED

    val cardBorderColor = when {
        isLive -> PlanetRose.copy(alpha = 0.65f)
        isRecentlyEnded -> PlanetAmber.copy(alpha = 0.45f)
        else -> PlanetBorderSubtle
    }

    val cardGradient = when {
        isLive -> listOf(PlanetNavy, PlanetCardElevated)
        isEnded -> listOf(PlanetDarkBg.copy(alpha = 0.85f), PlanetSurface.copy(alpha = 0.7f))
        else -> listOf(PlanetSurface, PlanetCardBg)
    }

    val shape = RoundedCornerShape(PlanetRadius.lg)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 128.dp)
            .clip(shape)
            .background(Brush.verticalGradient(cardGradient))
            .border(if (isLive) 1.25.dp else 1.dp, cardBorderColor, shape)
            .clickable(onClick = onClick)
            .alpha(if (isEnded) 0.78f else 1f)
            .padding(PlanetSpacing.md)
            .testTag("screening_card_${screening.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.md)
        ) {
            // Clean Poster Thumbnail (no duplicated title overlay)
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(PlanetRadius.md))
                    .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
                    .background(PlanetCardBg)
            ) {
                SubcomposeAsyncImage(
                    model = screening.posterUrl,
                    contentDescription = screening.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PlanetCardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(screening.roomType.badgeColor.copy(alpha = 0.45f), PlanetDarkBg)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                )
            }

            // Main Content Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Format pill + Screening status pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(PlanetRadius.xs))
                            .background(screening.roomType.containerColor)
                            .border(
                                1.dp,
                                screening.roomType.badgeColor.copy(alpha = 0.45f),
                                RoundedCornerShape(PlanetRadius.xs)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = screening.roomType.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = screening.roomType.badgeColor,
                            maxLines = 1
                        )
                    }

                    ScreeningStatusPill(
                        status = screening.status,
                        inEntry = screening.status == ScreeningStatus.UPCOMING &&
                            currentCinemaMinutes in (screening.entryMin until screening.startMin)
                    )
                }

                // Row 2: Movie Title (up to 2 lines so long titles never break layout)
                Text(
                    text = screening.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    ),
                    color = PlanetTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Row 3: Hall & relative countdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MeetingRoom,
                        contentDescription = null,
                        tint = PlanetTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = RoomType.formatRoomLabel(screening.room),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PlanetTextSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = PlanetTextMuted
                    )

                    val relSentence = when (screening.status) {
                        ScreeningStatus.LIVE -> {
                            val remaining = screening.endMin - currentCinemaMinutes
                            "נגמר ${cinemaRepository.relativeTimeSpan(remaining)}"
                        }
                        ScreeningStatus.UPCOMING -> {
                            val untilStart = screening.startMin - currentCinemaMinutes
                            "מתחיל ${cinemaRepository.relativeTimeSpan(untilStart)}"
                        }
                        ScreeningStatus.ENDED_RECENTLY -> "מפנים אולם"
                        ScreeningStatus.ENDED -> "הסתיים"
                    }

                    Text(
                        text = relSentence,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isLive) PlanetRoseLight else PlanetTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Row 4: Clean 3-Slot Time Strip (Entry / Start / Exit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    TimeSlotChip(
                        label = "כניסה",
                        time = cinemaRepository.formatCinemaMinutes(screening.entryMin),
                        labelColor = PlanetEmeraldLight,
                        timeColor = PlanetTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TimeSlotChip(
                        label = "התחלה",
                        time = cinemaRepository.formatCinemaMinutes(screening.startMin),
                        labelColor = PlanetTextSecondary,
                        timeColor = PlanetTextPrimary,
                        isHighlighted = true,
                        modifier = Modifier.weight(1f)
                    )
                    TimeSlotChip(
                        label = "יציאה",
                        time = cinemaRepository.formatCinemaMinutes(screening.endMin),
                        labelColor = PlanetAmber,
                        timeColor = PlanetGold,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 5: Occupancy numbers + verification quality + smooth progress bar
                val origCap = screening.originalHallCapacity()
                val brokenCount = screening.brokenSeatsCount()
                val usableCap = screening.usableHallCapacity()
                val capacityDisplay = usableCap ?: screening.roomCapacity()
                val occupiedDisplay = screening.estimatedOccupiedSeats()
                val isUnknownAvailability = screening.availabilityRatio == null || occupiedDisplay == null
                val isVerifiedSeatMap = !isUnknownAvailability &&
                    screening.availabilitySource == AvailabilitySource.LIVE_SEAT_MAP

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = occupiedDisplay?.toString() ?: "?",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = PlanetTextPrimary
                            )
                            Text(
                                text = "/ ${capacityDisplay ?: "?"} צופים",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = PlanetTextMuted,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }

                        val qualityLabel = when {
                            screening.seatSummary?.hasUnverifiedStatuses == true && isUnknownAvailability ->
                                "מושבים לא מאומתים"
                            isUnknownAvailability -> "זמינות לא ידועה"
                            isVerifiedSeatMap -> "${screening.occupancyPercentage}% • מאומת"
                            else -> "${screening.occupancyPercentage}% • אומדן"
                        }
                        val qualityColor = when {
                            isUnknownAvailability -> PlanetTextMuted
                            isVerifiedSeatMap -> PlanetEmeraldLight
                            else -> screening.crowdLevel.color
                        }
                        val qualityBg = when {
                            isUnknownAvailability -> PlanetDarkBg.copy(alpha = 0.7f)
                            isVerifiedSeatMap -> PlanetEmeraldGlow
                            else -> PlanetSkyGlow
                        }

                        StatusPill(
                            text = qualityLabel,
                            textColor = qualityColor,
                            containerColor = qualityBg,
                            leadingIcon = when {
                                isUnknownAvailability -> null
                                isVerifiedSeatMap -> Icons.Filled.CheckCircle
                                else -> Icons.Filled.QueryStats
                            }
                        )
                    }

                    SmoothProgressBar(
                        progress = if (isUnknownAvailability) 0f
                        else (screening.occupancyPercentage / 100f).coerceIn(0f, 1f),
                        color = if (isUnknownAvailability) PlanetBorder else screening.crowdLevel.progressColor,
                        height = 5.dp
                    )

                    if (!screening.isBrokenSeatsKnown()) {
                        Text(
                            text = "קיבולת מקור: ${origCap ?: "?"} • מושבים תקולים: לא ידוע",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlanetAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if ((brokenCount ?: 0) > 0) {
                        Text(
                            text = "קיבולת שמישה ${usableCap ?: "?"} (מקור ${origCap ?: "?"} פחות $brokenCount תקולים)",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlanetTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreeningStatusPill(
    status: ScreeningStatus,
    inEntry: Boolean
) {
    when (status) {
        ScreeningStatus.LIVE -> {
            StatusPill(
                text = "מוקרן כעת",
                textColor = PlanetRoseLight,
                containerColor = PlanetRoseGlow,
                borderColor = PlanetRoseLight.copy(alpha = 0.55f),
                leadingDotColor = PlanetRoseLight
            )
        }
        ScreeningStatus.UPCOMING -> {
            if (inEntry) {
                StatusPill(
                    text = "קהל נכנס",
                    textColor = PlanetEmeraldLight,
                    containerColor = PlanetEmeraldGlow,
                    borderColor = PlanetEmeraldLight.copy(alpha = 0.5f),
                    leadingDotColor = PlanetEmeraldLight
                )
            } else {
                StatusPill(
                    text = "טרם החל",
                    textColor = PlanetTextMuted,
                    containerColor = PlanetDarkBg.copy(alpha = 0.7f)
                )
            }
        }
        ScreeningStatus.ENDED_RECENTLY -> {
            StatusPill(
                text = "סיום / פינוי",
                textColor = PlanetAmber,
                containerColor = PlanetAmberGlow
            )
        }
        ScreeningStatus.ENDED -> {
            StatusPill(
                text = "הסתיים",
                textColor = PlanetTextMuted,
                containerColor = PlanetDarkBg.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TimeSlotChip(
    label: String,
    time: String,
    labelColor: Color,
    timeColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(PlanetRadius.sm))
            .background(if (isHighlighted) PlanetCardBg else PlanetDarkBg.copy(alpha = 0.85f))
            .border(
                1.dp,
                if (isHighlighted) PlanetBorder.copy(alpha = 0.75f) else PlanetBorderSubtle,
                RoundedCornerShape(PlanetRadius.sm)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = labelColor,
            maxLines = 1
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = timeColor,
            maxLines = 1
        )
    }
}
