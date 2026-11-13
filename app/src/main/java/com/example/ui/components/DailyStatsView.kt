package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.diagnostics.LiveOccupancyValidationSnapshot
import com.example.data.model.AttendanceKind
import com.example.data.model.DailyScreeningBreakdownItem
import com.example.data.model.DailyStatisticsSummary
import com.example.data.model.DailyTimelinePoint
import com.example.data.model.OccupancyResult
import com.example.data.model.QuickBookSourceStatus
import com.example.data.model.RoomType
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetAmberGlow
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmerald
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetNavy
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary

@Composable
fun DailyStatsView(
    dailyStats: DailyStatisticsSummary?,
    availableDates: List<String>,
    selectedDate: String,
    lastSuccessfulUpdateLabel: String,
    dataFreshnessLabel: String,
    isLiveDataSource: Boolean,
    hasUnverifiedSeatStatuses: Boolean,
    occupancyResult: OccupancyResult = OccupancyResult(),
    quickBookStatus: QuickBookSourceStatus = QuickBookSourceStatus(),
    validationSnapshot: LiveOccupancyValidationSnapshot? = null,
    validationJsonPath: String? = null,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_stats_view"),
        verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md)
    ) {
        LiveDataQualityDashboardCard(
            occupancyResult = occupancyResult,
            quickBookStatus = quickBookStatus,
            lastSuccessfulUpdateLabel = lastSuccessfulUpdateLabel,
            dataFreshnessLabel = dataFreshnessLabel,
            isLiveDataSource = isLiveDataSource,
            validationSnapshot = validationSnapshot,
            validationJsonPath = validationJsonPath
        )

        DateSelectorCard(
            availableDates = availableDates,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate
        )

        if (dailyStats == null) {
            DashboardCard(contentPadding = PaddingValues(PlanetSpacing.xxl)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "טרם נצברו נתוני ניטור לתאריך זה",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlanetTextMuted
                    )
                }
            }
            return
        }

        LiveStatusSummaryBanner(
            dailyStats = dailyStats,
            lastSuccessfulUpdateLabel = lastSuccessfulUpdateLabel,
            dataFreshnessLabel = dataFreshnessLabel,
            isLiveDataSource = isLiveDataSource,
            hasUnverifiedSeatStatuses = hasUnverifiedSeatStatuses
        )

        DailyMetricsGrid(stats = dailyStats)

        OccupancyTimelineChartCard(points = dailyStats.timelinePoints)

        if (dailyStats.monitoringGaps.isNotEmpty() || dailyStats.unknownCount > 0) {
            MonitoringGapsAndAccuracyCard(stats = dailyStats)
        }

        ScreeningBreakdownsCard(breakdowns = dailyStats.screeningBreakdowns)
    }
}

@Composable
private fun DateSelectorCard(
    availableDates: List<String>,
    selectedDate: String,
    onSelectDate: (String) -> Unit
) {
    DashboardCard(contentPadding = PaddingValues(PlanetSpacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = PlanetRoseLight,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "בחר תאריך לצפייה בסטטיסטיקה והיסטוריה",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PlanetTextPrimary
                )
            }

            if (availableDates.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(availableDates, key = { it }) { dateStr ->
                        val selected = dateStr == selectedDate
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(PlanetRadius.md))
                                .background(if (selected) PlanetRose else PlanetDarkBg.copy(alpha = 0.8f))
                                .border(
                                    1.dp,
                                    if (selected) PlanetRoseLight else PlanetBorderSubtle,
                                    RoundedCornerShape(PlanetRadius.md)
                                )
                                .clickable(onClick = { onSelectDate(dateStr) })
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("history_date_$dateStr"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (selected) Color.White else PlanetTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStatusSummaryBanner(
    dailyStats: DailyStatisticsSummary,
    lastSuccessfulUpdateLabel: String,
    dataFreshnessLabel: String,
    isLiveDataSource: Boolean,
    hasUnverifiedSeatStatuses: Boolean
) {
    DashboardCard(
        gradientColors = listOf(PlanetNavy, PlanetCardBg),
        contentPadding = PaddingValues(PlanetSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (dailyStats.isToday) "תפוסת קולנוע נוכחית (היום)" else "סיכום היסטורי לתאריך ${dailyStats.date}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = PlanetTextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = PlanetTextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "עדכון אחרון: $lastSuccessfulUpdateLabel • $dataFreshnessLabel",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (isLiveDataSource) PlanetEmeraldLight else PlanetAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (dailyStats.isToday) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${dailyStats.estimatedCurrentViewers}",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = PlanetRoseLight
                        )
                        val incompleteNote = if (dailyStats.unknownCount > 0) {
                            "${dailyStats.currentlyRunningScreenings} פועלות (${dailyStats.unknownCount} לא ידוע)"
                        } else {
                            "${dailyStats.currentlyRunningScreenings} הקרנות פעילות"
                        }
                        Text(
                            text = incompleteNote,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (dailyStats.unknownCount > 0) PlanetGold else PlanetTextSecondary
                        )
                    }
                }
            }

            if (hasUnverifiedSeatStatuses) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PlanetRadius.sm))
                        .background(PlanetAmberGlow)
                        .border(1.dp, PlanetAmber.copy(alpha = 0.4f), RoundedCornerShape(PlanetRadius.sm))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "שדות סטטוס מושבים מה-API החי (seats-statusV2) נשמרו במצב גולמי ומוגדרים 'לא ידוע' עד לאימות מול צבעי האתר.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                        color = PlanetGold
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyMetricsGrid(stats: DailyStatisticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
        ) {
            StatMiniCard(
                label = "סה\"כ הקרנות ביום",
                value = stats.totalScreeningsToday.toString(),
                subtitle = "${stats.currentlyRunningScreenings} פועלות • ${stats.completedScreenings} הסתיימו",
                valueColor = PlanetTextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StatMiniCard(
                label = "אומדן כניסות בהקרנות שהסתיימו",
                value = stats.estimatedCompletedAttendance?.toString() ?: "לא ידוע",
                subtitle = "ידוע: ${stats.completedWithKnownAttendance} | לא ידוע: ${stats.completedWithUnknownAttendance}",
                valueColor = PlanetGold,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
        ) {
            StatMiniCard(
                label = "אומדן כניסות להקרנות (כל היום)",
                value = stats.estimatedTotalAttendanceToday?.toString() ?: "לא ידוע",
                subtitle = "כולל כפל צופים אפשרי (לא מבקרים ייחודיים)",
                valueColor = PlanetRoseLight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StatMiniCard(
                label = "מושבים ידועים (תפוס / פנוי)",
                value = "${stats.totalKnownOccupiedSeats ?: "?"} / ${stats.totalKnownAvailableSeats ?: "?"}",
                subtitle = "מאומתים: ${stats.confirmedCount} | אומדנים: ${stats.estimatedCount} | לא ידוע: ${stats.unknownCount}",
                valueColor = PlanetImaxSkyLight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    DashboardCard(
        contentPadding = PaddingValues(PlanetSpacing.md),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = PlanetTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(PlanetSpacing.xs))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = valueColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(PlanetSpacing.xxs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                color = PlanetTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OccupancyTimelineChartCard(points: List<DailyTimelinePoint>) {
    DashboardCard(contentPadding = PaddingValues(PlanetSpacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md)) {
            SectionHeaderRow(
                title = "גרף תפוסה לאורך היום",
                subtitle = "דגימות מוקלטות לאורך שעות הפעילות",
                icon = Icons.Filled.BarChart,
                iconTint = PlanetRoseLight,
                trailing = {
                    StatusPill(
                        text = "${points.size} נקודות",
                        textColor = PlanetTextSecondary,
                        containerColor = PlanetDarkBg.copy(alpha = 0.8f)
                    )
                }
            )

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(PlanetRadius.md))
                        .background(PlanetDarkBg.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "טרם נאספו דגימות ציר זמן ליום זה",
                        style = MaterialTheme.typography.bodySmall,
                        color = PlanetTextMuted
                    )
                }
            } else {
                val maxViewers = (points.maxOfOrNull { it.totalCurrentViewers } ?: 1).coerceAtLeast(1)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(PlanetRadius.md))
                        .background(PlanetDarkBg.copy(alpha = 0.85f))
                        .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                            val y = h * frac
                            drawLine(
                                color = Color(0x28475569),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        if (points.size == 1) {
                            val y = h - ((points[0].totalCurrentViewers.toFloat() / maxViewers) * h * 0.85f)
                            drawCircle(
                                color = PlanetRoseLight,
                                radius = 5.dp.toPx(),
                                center = Offset(w / 2f, y)
                            )
                        } else {
                            val stepX = w / (points.size - 1).coerceAtLeast(1)
                            val linePath = Path()
                            val areaPath = Path()
                            points.forEachIndexed { idx, pt ->
                                val x = idx * stepX
                                val y = h - ((pt.totalCurrentViewers.toFloat() / maxViewers) * h * 0.85f)
                                if (idx == 0) {
                                    linePath.moveTo(x, y)
                                    areaPath.moveTo(x, h)
                                    areaPath.lineTo(x, y)
                                } else {
                                    linePath.lineTo(x, y)
                                    areaPath.lineTo(x, y)
                                }
                            }
                            areaPath.lineTo((points.size - 1) * stepX, h)
                            areaPath.close()

                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        PlanetRose.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                )
                            )

                            drawPath(
                                path = linePath,
                                color = PlanetRose,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            points.forEachIndexed { idx, pt ->
                                val x = idx * stepX
                                val y = h - ((pt.totalCurrentViewers.toFloat() / maxViewers) * h * 0.85f)
                                drawCircle(
                                    color = PlanetRoseLight,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "התחלה: ${points.first().labelTime}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = PlanetTextMuted
                    )
                    Text(
                        text = "שיא נצפה: $maxViewers צופים",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PlanetRoseLight
                    )
                    Text(
                        text = "אחרון: ${points.last().labelTime}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = PlanetTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitoringGapsAndAccuracyCard(stats: DailyStatisticsSummary) {
    DashboardCard(
        borderColor = PlanetAmber.copy(alpha = 0.4f),
        gradientColors = listOf(PlanetCardBg.copy(alpha = 0.8f), PlanetSurface),
        contentPadding = PaddingValues(PlanetSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = PlanetAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "שקיפות נתונים ופערים בניטור",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PlanetGold
                )
            }

            if (stats.unknownCount > 0) {
                Text(
                    text = "• ${stats.unknownCount} הקרנות מסומנות כ'לא ידוע' (הסתיימו לפני תחילת הניטור או ללא נתון מושבים מאומת) ואינן נספרות כאפס או כמלאות.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = PlanetTextSecondary
                )
            }

            stats.monitoringGaps.forEach { gap ->
                val reasonLabel = when (gap.reason) {
                    "NETWORK_UNAVAILABLE" -> "ללא חיבור רשת"
                    else -> "האפליקציה הייתה כבויה / מוגבלת ברקע"
                }
                Text(
                    text = "• פער ניטור מתועד: ${gap.startLabel} – ${gap.endLabel} ($reasonLabel)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = PlanetAmber
                )
            }
        }
    }
}

@Composable
private fun ScreeningBreakdownsCard(breakdowns: List<DailyScreeningBreakdownItem>) {
    DashboardCard(contentPadding = PaddingValues(PlanetSpacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "פירוט לפי הקרנה (${breakdowns.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PlanetTextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = PlanetTextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "רשומה אחת לכל הקרנה",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = PlanetTextMuted
                    )
                }
            }

            if (breakdowns.isEmpty()) {
                Text(
                    text = "אין רשומות הקרנה שמורות לתאריך זה.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlanetTextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    breakdowns.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(PlanetRadius.md))
                                .background(PlanetDarkBg.copy(alpha = 0.72f))
                                .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = PlanetTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${RoomType.formatRoomLabel(item.room)} • ${item.startTime}-${item.endTime} • ${item.availabilitySource.hebrewLabel}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = PlanetTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                val countText = item.estimatedOccupiedSeats?.let { "$it צופים" } ?: "לא ידוע"
                                Text(
                                    text = countText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = when (item.attendanceKind) {
                                        AttendanceKind.CONFIRMED -> PlanetEmeraldLight
                                        AttendanceKind.ESTIMATED -> PlanetRoseLight
                                        AttendanceKind.UNKNOWN -> PlanetTextMuted
                                    }
                                )
                                val badge = buildString {
                                    append(if (item.isFinal) "דגימה סופית" else "דגימה זמנית")
                                    if (item.hasCompleted) append(" • הסתיים")
                                }
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = if (item.isFinal) PlanetEmerald else PlanetTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
