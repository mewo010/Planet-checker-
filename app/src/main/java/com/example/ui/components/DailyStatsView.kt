package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.diagnostics.LiveValidationSnapshotBuilder
import com.example.data.diagnostics.SourceFreshnessInfo
import com.example.data.diagnostics.SourceFreshnessState
import com.example.data.model.AttendanceKind
import com.example.data.model.DailyScreeningBreakdownItem
import com.example.data.model.DailyStatisticsSummary
import com.example.data.model.DailyTimelinePoint
import com.example.data.model.OccupancyCountKind
import com.example.data.model.OccupancyResult
import com.example.data.model.QuickBookSourceStatus
import com.example.data.model.RoomType
import com.example.data.model.ScreeningOccupancyDiagnostic
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
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live Data Quality Dashboard (Transparency & Completeness)
        LiveDataQualityDashboardCard(
            occupancyResult = occupancyResult,
            quickBookStatus = quickBookStatus,
            lastSuccessfulUpdateLabel = lastSuccessfulUpdateLabel,
            dataFreshnessLabel = dataFreshnessLabel,
            isLiveDataSource = isLiveDataSource,
            validationSnapshot = validationSnapshot,
            validationJsonPath = validationJsonPath
        )

        // Date Picker Strip
        DateSelectorCard(
            availableDates = availableDates,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate
        )

        if (dailyStats == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PlanetSurface)
                    .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "טרם נצברו נתוני ניטור לתאריך זה",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlanetTextMuted
                )
            }
            return
        }

        // Data Freshness & Live Status Banner
        LiveStatusSummaryBanner(
            dailyStats = dailyStats,
            lastSuccessfulUpdateLabel = lastSuccessfulUpdateLabel,
            dataFreshnessLabel = dataFreshnessLabel,
            isLiveDataSource = isLiveDataSource,
            hasUnverifiedSeatStatuses = hasUnverifiedSeatStatuses
        )

        // Daily Metrics Grid
        DailyMetricsGrid(stats = dailyStats)

        // Occupancy Timeline Chart
        OccupancyTimelineChartCard(points = dailyStats.timelinePoints)

        // Monitoring Gaps & Epistemic Accuracy Transparency
        if (dailyStats.monitoringGaps.isNotEmpty() || dailyStats.unknownCount > 0) {
            MonitoringGapsAndAccuracyCard(stats = dailyStats)
        }

        // Per-screening historical / current breakdown
        ScreeningBreakdownsCard(breakdowns = dailyStats.screeningBreakdowns)
    }
}

@Composable
private fun LiveDataQualityDashboardCard(
    occupancyResult: OccupancyResult,
    quickBookStatus: QuickBookSourceStatus,
    lastSuccessfulUpdateLabel: String,
    dataFreshnessLabel: String,
    isLiveDataSource: Boolean,
    validationSnapshot: LiveOccupancyValidationSnapshot?,
    validationJsonPath: String?
) {
    var showSnapshotJson by remember { mutableStateOf(false) }
    val completeness = occupancyResult.completenessPercentage
    val completenessColor = when {
        !isLiveDataSource -> PlanetAmber
        completeness >= 90 -> PlanetEmeraldLight
        completeness >= 50 -> PlanetGold
        else -> PlanetRoseLight
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("data_quality_dashboard_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = "לוח איכות נתונים",
                    tint = completenessColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "לוח בקרת איכות נתונים (Live)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(completenessColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$completeness% שלמות",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = completenessColor
                )
            }
        }

        LinearProgressIndicator(
            progress = { (completeness / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = completenessColor,
            trackColor = PlanetDarkBg,
            strokeCap = StrokeCap.Round
        )

        // Metrics Grid Rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DataQualityMetricTile(
                label = "הקרנות פעילות / נספרו",
                value = "${occupancyResult.countedScreenings} / ${occupancyResult.activeScreenings}",
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            DataQualityMetricTile(
                label = "מושבים מאומתים",
                value = "${occupancyResult.verifiedOccupiedSeats}",
                valueColor = PlanetEmeraldLight,
                modifier = Modifier.weight(1f)
            )
            DataQualityMetricTile(
                label = "אומדן יחס תפוסה",
                value = "${occupancyResult.estimatedOccupiedSeats}",
                valueColor = PlanetImaxSkyLight,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DataQualityMetricTile(
                label = "חסר PID",
                value = "${occupancyResult.missingPresentationIdScreenings}",
                valueColor = if (occupancyResult.missingPresentationIdScreenings > 0) PlanetAmber else PlanetTextSecondary,
                modifier = Modifier.weight(1f)
            )
            DataQualityMetricTile(
                label = "בקשות שנכשלו",
                value = "${occupancyResult.failedScreenings}",
                valueColor = if (occupancyResult.failedScreenings > 0) PlanetRoseLight else PlanetTextSecondary,
                modifier = Modifier.weight(1f)
            )
            DataQualityMetricTile(
                label = "סטטוס לא מאומת",
                value = "${occupancyResult.unverifiedSeatStatusScreenings}",
                valueColor = if (occupancyResult.unverifiedSeatStatusScreenings > 0) PlanetGold else PlanetTextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        // Source & Timestamp footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PlanetDarkBg.copy(alpha = 0.7f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "רענון מוצלח אחרון: $lastSuccessfulUpdateLabel • $dataFreshnessLabel",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = PlanetTextSecondary
            )
            val qbLine = buildString {
                append("QuickBook רשמי: ${quickBookStatus.stateLabel}")
                if (quickBookStatus.recoveredMissingScreenings > 0 || quickBookStatus.recoveredPresentationIds > 0) {
                    append(" (+${quickBookStatus.recoveredMissingScreenings} הקרנות, +${quickBookStatus.recoveredPresentationIds} מזהים)")
                }
            }
            Text(
                text = qbLine,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (quickBookStatus.isReachable) PlanetEmeraldLight else PlanetAmber
            )
        }

        // Incomplete / Excluded Active Screenings Transparency List
        val incompleteOrNotable = occupancyResult.screeningDiagnostics.filter { diag ->
            !diag.includedInOccupancyTotal || (diag.unknownSeats ?: 0) > 0 || diag.occupancyKind == OccupancyCountKind.RATIO_ESTIMATE
        }
        if (incompleteOrNotable.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "אבחון הקרנות פעילות (${incompleteOrNotable.size} מוערכות / מוחרגות):",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = PlanetGold
                )
                incompleteOrNotable.take(8).forEach { diag ->
                    DiagnosticScreeningRow(diag = diag)
                }
            }
        }

        // Validation Snapshot: Freshness, 6 Counting Invariants, Disagreements & JSON Export
        if (validationSnapshot != null) {
            ValidationSnapshotSummarySection(
                snapshot = validationSnapshot,
                validationJsonPath = validationJsonPath,
                showJsonPreview = showSnapshotJson,
                onToggleJsonPreview = { showSnapshotJson = !showSnapshotJson }
            )
        }
    }
}

@Composable
private fun ValidationSnapshotSummarySection(
    snapshot: LiveOccupancyValidationSnapshot,
    validationJsonPath: String?,
    showJsonPreview: Boolean,
    onToggleJsonPreview: () -> Unit
) {
    val verification = snapshot.countingAccuracyVerification
    val allOk = verification.allInvariantsPassed
    val disagreementItems = snapshot.activeScreenings.filter { it.disagreements.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PlanetDarkBg.copy(alpha = 0.78f))
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag("validation_snapshot_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    imageVector = if (allOk) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = "אימות ספירה חי",
                    tint = if (allOk) PlanetEmeraldLight else PlanetRoseLight,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (allOk) "בדיקות אימות ספירה: 6/6 תקינות" else "נמצאה חריגה בבדיקות אימות ספירה",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = if (allOk) PlanetEmeraldLight else PlanetRoseLight
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder, RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleJsonPreview)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("toggle_validation_json_button")
            ) {
                Text(
                    text = if (showJsonPreview) "הסתר JSON" else "הצג Snapshot JSON",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PlanetImaxSkyLight
                )
            }
        }

        // Source freshness strip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(snapshot.sourceFreshness, key = { it.sourceName }) { src ->
                SourceFreshnessBadge(info = src)
            }
        }

        // Explicit disagreement log (if any source disagreed)
        if (disagreementItems.isNotEmpty()) {
            val totalDisagreements = disagreementItems.sumOf { it.disagreements.size }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "פערים בין מקורות ($totalDisagreements ב-${disagreementItems.size} הקרנות):",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = PlanetGold
                )
                disagreementItems.take(5).forEach { entry ->
                    entry.disagreements.take(2).forEach { dis ->
                        Text(
                            text = "• ${RoomType.formatRoomLabel(entry.room)} (${entry.startTime}) [${dis.field}]: נבחר ${dis.trustedSource}=${dis.trustedValue ?: "?"} — ${dis.reason}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 13.sp),
                            color = PlanetTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Text(
            text = "שים לב: הנתונים משקפים תפוסת הקרנות מוערכת/מוזמנת מלוח הזמנים ומפות המושבים, ולא ספירה פיזית מאומתת של אנשים בתוך המתחם.",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 13.sp),
            color = PlanetTextMuted
        )

        if (!validationJsonPath.isNullOrBlank()) {
            Text(
                text = "קובץ JSON נשמר מקומית: latest_validation_snapshot.json",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                color = PlanetTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showJsonPreview) {
            val previewJson = remember(snapshot) {
                LiveValidationSnapshotBuilder.toJson(snapshot).take(6000)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF090D16))
                    .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = previewJson,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = PlanetImaxSkyLight
                )
            }
        }
    }
}

@Composable
private fun SourceFreshnessBadge(info: SourceFreshnessInfo) {
    val stateColor = when (info.freshnessState) {
        SourceFreshnessState.CURRENT -> PlanetEmeraldLight
        SourceFreshnessState.CACHED -> PlanetImaxSkyLight
        SourceFreshnessState.EXPIRED -> PlanetGold
        SourceFreshnessState.CLOUDFLARE_BLOCKED -> PlanetRoseLight
        SourceFreshnessState.FAILED -> PlanetRoseLight
        SourceFreshnessState.NOT_QUERIED -> PlanetTextMuted
    }
    val ageText = info.ageSeconds?.let { "${it}s" } ?: "—"
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PlanetCardBg.copy(alpha = 0.7f))
            .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = info.sourceName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White
        )
        Text(
            text = "${info.freshnessState.name} • $ageText",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = stateColor
        )
    }
}

@Composable
private fun DataQualityMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PlanetCardBg.copy(alpha = 0.75f))
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            ),
            color = valueColor,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = PlanetTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiagnosticScreeningRow(diag: ScreeningOccupancyDiagnostic) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PlanetDarkBg.copy(alpha = 0.65f))
            .border(
                1.dp,
                if (diag.includedInOccupancyTotal) PlanetBorderSubtle else PlanetAmber.copy(alpha = 0.45f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${RoomType.formatRoomLabel(diag.room)} • ${diag.title} (${diag.startTime})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = diag.countedOccupiedSeats?.let { "$it צופים (${diag.occupancyKind.hebrewLabel})" } ?: "הוחרג (?)",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = if (diag.includedInOccupancyTotal) PlanetImaxSkyLight else PlanetRoseLight
            )
        }
        val detail = buildString {
            append(diag.presentationId?.let { "PID=$it" } ?: "ללא PID")
            diag.presentationCode?.let { append(" • Code=$it") }
            diag.seatApiHttpCode?.let { append(" • HTTP $it") }
            diag.exclusionReason?.let { append(" • $it") }
            if (diag.observedPropertyKeys.isNotEmpty()) {
                append(" • keys=${diag.observedPropertyKeys.joinToString(",")}")
            }
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
            color = PlanetTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DateSelectorCard(
    availableDates: List<String>,
    selectedDate: String,
    onSelectDate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                color = Color.White
            )
        }

        if (availableDates.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(availableDates, key = { it }) { dateStr ->
                    val selected = dateStr == selectedDate
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) PlanetRose else PlanetCardBg)
                            .border(
                                1.dp,
                                if (selected) PlanetRoseLight else PlanetBorderSubtle,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(onClick = { onSelectDate(dateStr) })
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("history_date_$dateStr")
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

@Composable
private fun LiveStatusSummaryBanner(
    dailyStats: DailyStatisticsSummary,
    lastSuccessfulUpdateLabel: String,
    dataFreshnessLabel: String,
    isLiveDataSource: Boolean,
    hasUnverifiedSeatStatuses: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(listOf(PlanetSurface, PlanetCardBg))
            )
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (dailyStats.isToday) "תפוסת קולנוע נוכחית (היום)" else "סיכום היסטורי לתאריך ${dailyStats.date}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White
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
                        color = if (isLiveDataSource) PlanetEmeraldLight else PlanetAmber
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlanetAmberGlow)
                    .border(1.dp, PlanetAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
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

@Composable
private fun DailyMetricsGrid(stats: DailyStatisticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatMiniCard(
                label = "סה\"כ הקרנות ביום",
                value = stats.totalScreeningsToday.toString(),
                subtitle = "${stats.currentlyRunningScreenings} פועלות • ${stats.completedScreenings} הסתיימו",
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            StatMiniCard(
                label = "אומדן כניסות בהקרנות שהסתיימו",
                value = stats.estimatedCompletedAttendance?.toString() ?: "לא ידוע",
                subtitle = "ידוע: ${stats.completedWithKnownAttendance} | לא ידוע: ${stats.completedWithUnknownAttendance}",
                valueColor = PlanetGold,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatMiniCard(
                label = "אומדן כניסות להקרנות (כל היום)",
                value = stats.estimatedTotalAttendanceToday?.toString() ?: "לא ידוע",
                subtitle = "כולל כפל צופים אפשרי (לא מבקרים ייחודיים)",
                valueColor = PlanetRoseLight,
                modifier = Modifier.weight(1f)
            )
            StatMiniCard(
                label = "מושבים ידועים (תפוס / פנוי)",
                value = "${stats.totalKnownOccupiedSeats ?: "?"} / ${stats.totalKnownAvailableSeats ?: "?"}",
                subtitle = "מאומתים: ${stats.confirmedCount} | אומדנים: ${stats.estimatedCount} | לא ידוע: ${stats.unknownCount}",
                valueColor = PlanetImaxSkyLight,
                modifier = Modifier.weight(1f)
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = PlanetTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = valueColor,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                color = PlanetTextMuted,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun OccupancyTimelineChartCard(points: List<DailyTimelinePoint>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = null,
                    tint = PlanetRoseLight,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "גרף תפוסה לאורך היום (דגימות מוקלטות)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Text(
                text = "${points.size} נקודות",
                style = MaterialTheme.typography.labelSmall,
                color = PlanetTextMuted
            )
        }

        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PlanetDarkBg),
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
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlanetDarkBg)
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Subtle horizontal grid lines
                    listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                        val y = h * frac
                        drawLine(
                            color = Color(0x22475569),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (points.size == 1) {
                        val y = h - ((points[0].totalCurrentViewers.toFloat() / maxViewers) * h * 0.85f)
                        drawCircle(
                            color = Color(0xFFFB7185),
                            radius = 5.dp.toPx(),
                            center = Offset(w / 2f, y)
                        )
                    } else {
                        val stepX = w / (points.size - 1).coerceAtLeast(1)
                        val linePath = Path()
                        points.forEachIndexed { idx, pt ->
                            val x = idx * stepX
                            val y = h - ((pt.totalCurrentViewers.toFloat() / maxViewers) * h * 0.85f)
                            if (idx == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                        }

                        drawPath(
                            path = linePath,
                            color = Color(0xFFE11D48),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        points.forEachIndexed { idx, pt ->
                            val x = idx * stepX
                            val y = h - ((pt.totalCurrentViewers.toFloat() / maxViewers) * h * 0.85f)
                            drawCircle(
                                color = Color(0xFFFB7185),
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

@Composable
private fun MonitoringGapsAndAccuracyCard(stats: DailyStatisticsSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PlanetCardBg.copy(alpha = 0.6f))
            .border(1.dp, PlanetAmber.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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

@Composable
private fun ScreeningBreakdownsCard(breakdowns: List<DailyScreeningBreakdownItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "פירוט לפי הקרנה (${breakdowns.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(PlanetDarkBg.copy(alpha = 0.7f))
                            .border(1.dp, PlanetBorder.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
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
                                color = Color.White,
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
                                    fontSize = 12.sp
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
