package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmerald
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
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

    // Pulse animation for LIVE cards
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val cardBorderColor = when {
        isLive -> PlanetRose.copy(alpha = pulseAlpha)
        isRecentlyEnded -> PlanetAmber.copy(alpha = 0.4f)
        isEnded -> PlanetBorder.copy(alpha = 0.3f)
        else -> PlanetBorder.copy(alpha = 0.7f)
    }

    val cardBg = when {
        isLive -> Brush.verticalGradient(
            listOf(PlanetCardBg, PlanetDarkBg.copy(alpha = 0.95f))
        )
        isEnded -> Brush.verticalGradient(
            listOf(PlanetDarkBg, PlanetDarkBg.copy(alpha = 0.85f))
        )
        else -> Brush.verticalGradient(
            listOf(PlanetSurface, PlanetDarkBg.copy(alpha = 0.95f))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(if (isLive) 1.5.dp else 1.dp, cardBorderColor, RoundedCornerShape(18.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .testTag("screening_card_${screening.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Column: Poster Thumbnail
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(2f / 3.2f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, PlanetBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                screening.roomType.badgeColor.copy(alpha = 0.35f),
                                PlanetDarkBg
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (!screening.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = screening.posterUrl,
                        contentDescription = screening.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                Text(
                    text = screening.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }

            // Right Column: Details & Schedule
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Format Badge & Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Format Badge (IMAX, VIP, etc.)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(screening.roomType.containerColor)
                            .border(1.dp, screening.roomType.badgeColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = screening.roomType.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = screening.roomType.badgeColor
                        )
                    }

                    // Status Pill
                    when (screening.status) {
                        ScreeningStatus.LIVE -> {
                            val diff = screening.endMin - currentCinemaMinutes
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(PlanetRose.copy(alpha = 0.2f))
                                    .border(1.dp, PlanetRoseLight.copy(alpha = 0.6f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
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
                                        text = "מוקרן כעת",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = PlanetRoseLight
                                    )
                                }
                            }
                        }
                        ScreeningStatus.UPCOMING -> {
                            val inEntry = currentCinemaMinutes in (screening.entryMin until screening.startMin)
                            if (inEntry) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(PlanetEmerald.copy(alpha = 0.2f))
                                        .border(1.dp, PlanetEmeraldLight.copy(alpha = 0.5f), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "קהל נכנס",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = PlanetEmeraldLight
                                    )
                                }
                            } else {
                                Text(
                                    text = "טרם החל",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = PlanetTextMuted
                                )
                            }
                        }
                        ScreeningStatus.ENDED_RECENTLY -> {
                            Text(
                                text = "הסתיים לאחרונה",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp
                                ),
                                color = PlanetAmber
                            )
                        }
                        ScreeningStatus.ENDED -> {
                            Text(
                                text = "הסתיים",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = PlanetTextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title & Hall
                Text(
                    text = screening.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MeetingRoom,
                        contentDescription = null,
                        tint = PlanetTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = RoomType.formatRoomLabel(screening.room),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = PlanetTextSecondary
                    )
                }

                // Relative time sentence
                val relSentence = when (screening.status) {
                    ScreeningStatus.LIVE -> {
                        val remaining = screening.endMin - currentCinemaMinutes
                        "נגמר ${cinemaRepository.relativeTimeSpan(remaining)}"
                    }
                    ScreeningStatus.UPCOMING -> {
                        val untilStart = screening.startMin - currentCinemaMinutes
                        "מתחיל ${cinemaRepository.relativeTimeSpan(untilStart)}"
                    }
                    ScreeningStatus.ENDED_RECENTLY -> "הסתיים לפני זמן קצר"
                    ScreeningStatus.ENDED -> "הקרנה הסתיימה"
                }

                Text(
                    text = relSentence,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isLive) PlanetRoseLight else PlanetTextMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 3-Column Time Badges: Entry, Start, Exit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TimePill(
                        label = "כניסה",
                        time = cinemaRepository.formatCinemaMinutes(screening.entryMin),
                        labelColor = PlanetEmeraldLight,
                        timeColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    TimePill(
                        label = "התחלה",
                        time = cinemaRepository.formatCinemaMinutes(screening.startMin),
                        labelColor = PlanetTextMuted,
                        timeColor = Color.White,
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                    TimePill(
                        label = "יציאה",
                        time = cinemaRepository.formatCinemaMinutes(screening.endMin),
                        labelColor = PlanetAmber,
                        timeColor = PlanetGold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Crowd Level & Meter
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = screening.estimatedPeople?.toString() ?: "?",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = " / ${screening.capacity ?: "?"} צופים",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = PlanetTextMuted,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        Text(
                            text = screening.crowdLevel.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = screening.crowdLevel.color
                        )
                    }

                    // Linear progress bar for occupancy
                    LinearProgressIndicator(
                        progress = { (screening.occupancyPercentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = screening.crowdLevel.progressColor,
                        trackColor = PlanetDarkBg,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePill(
    label: String,
    time: String,
    labelColor: Color,
    timeColor: Color,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PlanetDarkBg.copy(alpha = 0.7f))
            .border(
                1.dp,
                if (isPrimary) PlanetBorder else PlanetBorder.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = labelColor
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isPrimary) FontWeight.Black else FontWeight.Bold,
                    fontSize = if (isPrimary) 13.sp else 12.sp
                ),
                color = timeColor
            )
        }
    }
}
