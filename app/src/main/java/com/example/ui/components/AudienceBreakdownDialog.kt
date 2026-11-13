package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OccupancyResult
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.model.deduplicateScreenings
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetAmberGlow
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudienceBreakdownDialog(
    allScreenings: List<Screening>,
    totalAudience: Int,
    isLiveDataSource: Boolean,
    occupancyResult: OccupancyResult = OccupancyResult(),
    lastUpdatedLabel: String = "",
    onDismiss: () -> Unit
) {
    val liveScreenings = remember(allScreenings) {
        deduplicateScreenings(allScreenings).filter { it.status == ScreeningStatus.LIVE }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PlanetRadius.xl))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorder, RoundedCornerShape(PlanetRadius.xl))
            .testTag("audience_breakdown_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PlanetSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "פירוט צופים חי באולמות",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = PlanetTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (occupancyResult.isComplete) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                            contentDescription = "סטטוס נתונים",
                            tint = if (occupancyResult.isComplete) PlanetEmeraldLight else PlanetAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    val subHeader = buildString {
                        append(if (isLiveDataSource) "נתוני אמת מהקופות" else "נתוני דוגמה (לא מקוון)")
                        if (lastUpdatedLabel.isNotBlank() && lastUpdatedLabel != "טרם עודכן") {
                            append(" • עודכן $lastUpdatedLabel")
                        }
                    }
                    Text(
                        text = subHeader,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isLiveDataSource) PlanetEmeraldLight else PlanetAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "סגור",
                        tint = PlanetTextSecondary
                    )
                }
            }

            // Summary Card
            DashboardCard(
                gradientColors = listOf(PlanetNavy, PlanetCardBg),
                borderColor = PlanetBorder,
                contentPadding = PaddingValues(PlanetSpacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "סה\"כ צופים פעילים",
                                style = MaterialTheme.typography.labelMedium,
                                color = PlanetTextSecondary
                            )
                            Text(
                                text = if (occupancyResult.unknownOccupancy) "לא ידוע" else "$totalAudience צופים",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = PlanetRoseLight
                            )
                        }

                        StatusPill(
                            text = "${liveScreenings.size} אולמות • ${occupancyResult.completenessPercentage}%",
                            textColor = PlanetRoseLight,
                            containerColor = PlanetRoseGlow,
                            borderColor = PlanetRose.copy(alpha = 0.4f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.xs)
                    ) {
                        StatusPill(
                            text = "מאומתים: ${occupancyResult.verifiedOccupiedSeats}",
                            textColor = PlanetEmeraldLight,
                            containerColor = PlanetEmeraldGlow,
                            leadingIcon = Icons.Filled.CheckCircle
                        )
                        StatusPill(
                            text = "אומדן יחס: ${occupancyResult.estimatedOccupiedSeats}",
                            textColor = PlanetImaxSkyLight,
                            containerColor = PlanetSkyGlow,
                            leadingIcon = Icons.Filled.QueryStats
                        )
                    }

                    if (occupancyResult.activeScreenings > 0 && !occupancyResult.isComplete) {
                        Text(
                            text = "נספרו ${occupancyResult.countedScreenings}/${occupancyResult.activeScreenings} | חסר PID: ${occupancyResult.missingPresentationIdScreenings} | לא מאומת: ${occupancyResult.unverifiedSeatStatusScreenings} | נכשלו: ${occupancyResult.failedScreenings}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlanetGold
                        )
                    }
                }
            }

            if (!occupancyResult.isComplete && occupancyResult.activeScreenings > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PlanetRadius.sm))
                        .background(PlanetAmberGlow)
                        .border(1.dp, PlanetAmber.copy(alpha = 0.4f), RoundedCornerShape(PlanetRadius.sm))
                        .padding(PlanetSpacing.sm)
                ) {
                    Text(
                        text = "חלקי: קיימות הקרנות פעילות ללא נתוני מושבים מאומתים (${occupancyResult.missingScreenings} חסרות, ${occupancyResult.failedScreenings} נכשלו). הקרנות אלו מסומנות כ-'?' ואינן נספרות באפס שקרי.",
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
                        color = PlanetGold
                    )
                }
            }

            Text(
                text = "אולמות פעילים ברגע זה",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = PlanetTextPrimary
            )

            if (liveScreenings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PlanetRadius.md))
                        .background(PlanetDarkBg.copy(alpha = 0.65f))
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "אין אולמות המקרינים ברגע זה",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlanetTextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                ) {
                    items(liveScreenings, key = { it.id }) { screening ->
                        ActiveRoomRow(screening = screening)
                    }
                }
            }

            Text(
                text = "האפליקציה מפרידה בין מושבים מאומתים ממפת המושבים החיה (תפוסים + שמורים) לבין אומדני יחס קיבולת שמישה.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = PlanetTextMuted
            )
        }
    }
}

@Composable
private fun ActiveRoomRow(screening: Screening) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PlanetRadius.md))
            .background(PlanetCardBg.copy(alpha = 0.72f))
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
            .padding(PlanetSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(PlanetRoseLight)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = RoomType.formatRoomLabel(screening.room),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = PlanetTextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(screening.roomType.containerColor)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = screening.roomType.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                        color = screening.roomType.badgeColor
                    )
                }
            }

            Text(
                text = "${screening.title} • ${screening.startStr}-${screening.endStr}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = PlanetTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val origCap = screening.originalHallCapacity()
            val broken = screening.brokenSeatsCount()
            val usable = screening.usableHallCapacity()
            val diagNote = buildString {
                append(screening.presentationId?.let { "PID: $it" } ?: "ללא PID")
                append(" • מקור ${origCap ?: "?"} | תקולים ${broken ?: "לא ידוע"} | שמיש ${usable ?: "לא ידוע"}")
                screening.seatApiHttpCode?.let { append(" • HTTP $it") }
                screening.seatSummary?.let { s ->
                    append(" • מאומת ${s.totalValidSeats}/לא ידוע ${s.unknownSeats}")
                }
            }
            Text(
                text = diagNote,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = PlanetTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        val occupied = screening.estimatedOccupiedSeats()
        val kind = screening.occupancyCountKind()
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (occupied != null) "$occupied צופים" else "? צופים",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = if (occupied != null) PlanetRoseLight else PlanetTextMuted
            )
            val subStatus = if (occupied != null) {
                "${screening.occupancyPercentage}% • ${kind.hebrewLabel}"
            } else {
                screening.seatFetchState.hebrewLabel
            }
            Text(
                text = subStatus,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (occupied != null) screening.crowdLevel.color else PlanetAmber
            )
        }
    }
}
