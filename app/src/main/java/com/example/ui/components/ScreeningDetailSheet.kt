package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextSecondary

import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.PlanetBorderSubtle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreeningDetailSheet(
    screening: Screening,
    currentCinemaMinutes: Int,
    cinemaRepository: CinemaRepository,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onFilterByMovie: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PlanetSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PlanetBorder)
            )
        },
        modifier = Modifier.testTag("screening_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(screening.roomType.containerColor)
                            .border(1.dp, screening.roomType.badgeColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = screening.roomType.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = screening.roomType.badgeColor
                        )
                    }

                    Text(
                        text = RoomType.formatRoomLabel(screening.room),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "סגור",
                        tint = PlanetTextSecondary
                    )
                }
            }

            // Hero Details (Poster + Titles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(12.dp))
                        .background(PlanetCardBg),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = screening.posterUrl,
                        contentDescription = screening.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
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
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(screening.roomType.badgeColor.copy(alpha = 0.4f), PlanetDarkBg)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = screening.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White
                    )

                    val formatDescription = when (screening.roomType) {
                        RoomType.IMAX -> "מסך ענק פנורמי וסאונד לייזר מתקדם (אולם 5)"
                        RoomType.VIP -> "לאונג' אירוח יוקרתי וכורסאות עור חשמליות (אולמות 21-24)"
                        RoomType.FOUR_DX -> "מושבים נעים ואפקטי רוח, מים וריח (אולם 20)"
                        RoomType.SCREEN_X -> "חוויית הקרנה ב-270 מעלות על 3 קירות (אולם 19)"
                        RoomType.TWO_D -> "הקרנה דיגיטלית באיכות קריסטל וסאונד היקפי"
                    }

                    Text(
                        text = formatDescription,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = PlanetTextSecondary
                    )

                    // Room specs pill
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.EventSeat,
                                contentDescription = null,
                                tint = PlanetGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val usableHero = screening.usableHallCapacity()
                            val origHero = screening.originalHallCapacity() ?: screening.capacity
                            val heroCapLabel = if (usableHero != null) {
                                "$usableHero שמישים (מקור ${origHero ?: "?"})"
                            } else {
                                "${origHero ?: "?"} מקור • שמיש לא ידוע"
                            }
                            Text(
                                text = heroCapLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = PlanetGold
                            )
                        }

                        Text(
                            text = "•",
                            color = PlanetTextMuted
                        )

                        Text(
                            text = "רמת עומס: ${screening.crowdLevel.label}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = screening.crowdLevel.color
                        )
                    }
                }
            }

            // Crowd & Occupancy Box
            val origHallCap = screening.originalHallCapacity()
            val brokenSeats = screening.brokenSeatsCount()
            val usableCap = screening.usableHallCapacity()
            val brokenKnown = screening.isBrokenSeatsKnown()
            val effectiveCapacity = usableCap ?: screening.roomCapacity()
            val occupiedSeats = screening.estimatedOccupiedSeats()
            val hasKnownRatio = screening.availabilityRatio != null && occupiedSeats != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "תפוסת קהל וזמינות מושבים",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = if (hasKnownRatio) {
                                "$occupiedSeats / ${effectiveCapacity ?: "?"} (${screening.occupancyPercentage}%)"
                            } else {
                                "זמינות לא ידועה"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (hasKnownRatio) screening.crowdLevel.color else PlanetTextMuted
                        )
                    }

                    LinearProgressIndicator(
                        progress = {
                            if (hasKnownRatio) (screening.occupancyPercentage / 100f).coerceIn(0f, 1f)
                            else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = screening.crowdLevel.progressColor,
                        trackColor = PlanetDarkBg,
                        strokeCap = StrokeCap.Round
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "מקור: ${screening.availabilitySource.hebrewLabel}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = PlanetTextSecondary
                        )
                        val idLabel = buildString {
                            append(screening.presentationId?.let { "מזהה: $it" } ?: "ללא presentationId")
                            screening.presentationCode?.let { append(" • קוד $it") }
                            screening.seatApiHttpCode?.let { append(" • HTTP $it") }
                        }
                        Text(
                            text = idLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = PlanetTextMuted
                        )
                    }

                    if (screening.seatSummary == null && screening.seatFetchError != null) {
                        Text(
                            text = "סטטוס קריאת מושבים: ${screening.seatFetchState.hebrewLabel} (${screening.seatFetchError})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = PlanetAmber
                        )
                    }

                    // Hall Capacity Breakdown (Original vs Manual Broken vs Usable + Known/Unknown)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(PlanetDarkBg.copy(alpha = 0.7f))
                            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("detail_capacity_breakdown"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "קיבולת אולם ומושבים תקולים",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = if (brokenKnown) "דיווח ידוע" else "סטטוס לא ידוע",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (brokenKnown) PlanetEmeraldLight else PlanetAmber
                            )
                        }
                        Text(
                            text = "קיבולת אולם מקורית: ${origHallCap ?: "לא ידוע"} מקומות",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = PlanetTextSecondary
                        )
                        Text(
                            text = "מושבים תקולים / לא שמישים (דיווח ידני): ${brokenSeats?.toString() ?: "לא ידוע"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (brokenKnown) PlanetTextSecondary else PlanetAmber
                        )
                        Text(
                            text = "קיבולת שמישה בפועל: ${usableCap?.let { "$it מקומות" } ?: "לא ידוע (נתון חלקי)"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (usableCap != null) PlanetGold else PlanetAmber
                        )
                        if (screening.brokenSeatsExcludedByLiveMap()) {
                            Text(
                                text = "מפת המושבים החיה כבר מחריגה מושבים תקולים/חסומים — לא בוצעה הפחתה כפולה.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = PlanetEmeraldLight
                            )
                        }
                    }

                    screening.seatSummary?.let { summary ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PlanetDarkBg.copy(alpha = 0.7f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "פירוט מפת מושבים (seats-statusV2)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "סה\"כ אובייקטי מושב: ${summary.totalRawSeatObjects} • מאומתים: ${summary.totalValidSeats} • לא ידועים: ${summary.unknownSeats}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = PlanetTextSecondary
                            )
                            Text(
                                text = "פנויים: ${summary.availableSeats} | תפוסים: ${summary.occupiedSeats} | שמורים: ${summary.reservedSeats} | חסומים: ${summary.blockedSeats}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = PlanetTextMuted
                            )
                            if (summary.observedPropertyKeys.isNotEmpty()) {
                                Text(
                                    text = "שדות JSON גולמיים: ${summary.observedPropertyKeys.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                                    color = PlanetTextMuted
                                )
                            }
                            if (summary.hasUnverifiedStatuses) {
                                Text(
                                    text = "שדות סטטוס גולמיים (טרם אומתו מול צבעי האתר): ${summary.rawStatusHistogram.entries.joinToString { "${it.key} (${it.value})" }}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = PlanetAmber
                                )
                            }
                        }
                    }
                }
            }

            // Complete Shift Timeline Breakdown
            Text(
                text = "לוח זמנים מפורט למשמרת",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = Color.White
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimelineStep(
                    time = cinemaRepository.formatCinemaMinutes(screening.entryMin),
                    title = "פתיחת דלתות וכניסת קהל",
                    desc = "20 דקות לפני תחילת ההקרנה. בדיקת כרטיסים ועמדות סדרנים.",
                    color = PlanetEmeraldLight
                )

                TimelineStep(
                    time = cinemaRepository.formatCinemaMinutes(screening.startMin),
                    title = "תחילת ההקרנה ופרומואים",
                    desc = "החשכת אורות אולם, פרסומות ותחילת הסרט.",
                    color = Color.White
                )

                TimelineStep(
                    time = cinemaRepository.formatCinemaMinutes(screening.endMin - CinemaRepository.EXTRA_END),
                    title = "כתוביות סיום",
                    desc = "הדלקת תאורת מדרגות עמומה, תחילת יציאת צופים ראשונים.",
                    color = PlanetAmber
                )

                TimelineStep(
                    time = cinemaRepository.formatCinemaMinutes(screening.endMin),
                    title = "סיום פינוי ופתיחת דלתות לניקיון",
                    desc = "15 דקות תוספת לסיום מוחלט של יציאת הקהל והכשרת האולם להקרנה הבאה.",
                    color = PlanetGold
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onFilterByMovie(screening.title)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PlanetRose),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "סנן לפי סרט זה",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(
                        text = "סגור",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimelineStep(
    time: String,
    title: String,
    desc: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(PlanetDarkBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = color
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = Color.White
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = PlanetTextMuted
            )
        }
    }
}
