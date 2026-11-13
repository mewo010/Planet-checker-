package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
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
import com.example.data.model.OccupancyCountKind
import com.example.data.model.OccupancyResult
import com.example.data.model.QuickBookSourceStatus
import com.example.data.model.RoomType
import com.example.data.model.ScreeningOccupancyDiagnostic
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetMotion
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary
import com.example.ui.theme.rememberReducedMotion

@Composable
fun LiveDataQualityDashboardCard(
    occupancyResult: OccupancyResult,
    quickBookStatus: QuickBookSourceStatus,
    lastSuccessfulUpdateLabel: String,
    dataFreshnessLabel: String,
    isLiveDataSource: Boolean,
    validationSnapshot: LiveOccupancyValidationSnapshot?,
    validationJsonPath: String?,
    modifier: Modifier = Modifier
) {
    var showSnapshotJson by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val completeness = occupancyResult.completenessPercentage
    val completenessColor = when {
        !isLiveDataSource -> PlanetAmber
        completeness >= 90 -> PlanetEmeraldLight
        completeness >= 50 -> PlanetGold
        else -> PlanetRoseLight
    }

    DashboardCard(
        contentPadding = PaddingValues(PlanetSpacing.lg),
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_quality_dashboard_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md)) {
            SectionHeaderRow(
                title = "לוח בקרת איכות נתונים",
                subtitle = "שקיפות מקורות, מושבים תקולים ושלמות ספירה",
                icon = Icons.Filled.VerifiedUser,
                iconTint = completenessColor,
                trailing = {
                    StatusPill(
                        text = "$completeness% שלמות",
                        textColor = completenessColor,
                        containerColor = completenessColor.copy(alpha = 0.16f)
                    )
                }
            )

            SmoothProgressBar(
                progress = (completeness / 100f).coerceIn(0f, 1f),
                color = completenessColor,
                height = 6.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
            ) {
                DataQualityMetricTile(
                    label = "פעילות / נספרו",
                    value = "${occupancyResult.countedScreenings} / ${occupancyResult.activeScreenings}",
                    valueColor = PlanetTextPrimary,
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
                horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PlanetRadius.md))
                    .background(PlanetDarkBg.copy(alpha = 0.78f))
                    .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
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

            val incompleteOrNotable = remember(occupancyResult.screeningDiagnostics) {
                occupancyResult.screeningDiagnostics.filter { diag ->
                    !diag.includedInOccupancyTotal ||
                        (diag.unknownSeats ?: 0) > 0 ||
                        diag.occupancyKind == OccupancyCountKind.RATIO_ESTIMATE
                }
            }
            if (incompleteOrNotable.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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

            if (validationSnapshot != null) {
                ValidationSnapshotSummarySection(
                    snapshot = validationSnapshot,
                    validationJsonPath = validationJsonPath,
                    showJsonPreview = showSnapshotJson,
                    reducedMotion = reducedMotion,
                    onToggleJsonPreview = { showSnapshotJson = !showSnapshotJson }
                )
            }

            HallCapacityAuditSection(reducedMotion = reducedMotion)
        }
    }
}

@Composable
private fun HallCapacityAuditSection(reducedMotion: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val hallInfos = remember { CinemaRepository.getAllHallCapacities() }
    val totalOriginal = remember(hallInfos) { hallInfos.sumOf { it.originalCapacity ?: 0 } }
    val knownInfos = remember(hallInfos) { hallInfos.filter { it.isBrokenSeatsKnown } }
    val totalKnownBroken = remember(knownInfos) { knownInfos.sumOf { it.brokenSeats ?: 0 } }
    val totalKnownUsable = remember(knownInfos) { knownInfos.sumOf { it.usableCapacity ?: 0 } }
    val unknownHalls = remember(hallInfos) { hallInfos.filter { !it.isBrokenSeatsKnown } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PlanetRadius.md))
            .background(PlanetDarkBg.copy(alpha = 0.82f))
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
            .padding(10.dp)
            .testTag("hall_capacity_audit_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(PlanetRadius.sm))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("toggle_hall_capacity_table_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "בקרת קיבולת אולמות ומושבים תקולים",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PlanetTextPrimary
                )
                Text(
                    text = "מקור (24 אולמות): $totalOriginal • תקולים מאומתים (23 אולמות): $totalKnownBroken • שמיש ידוע: $totalKnownUsable",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = PlanetTextSecondary
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(PlanetRadius.sm))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder, RoundedCornerShape(PlanetRadius.sm))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (expanded) "הסתר" else "פירוט 24",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PlanetImaxSkyLight
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = PlanetImaxSkyLight,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (unknownHalls.isNotEmpty()) {
            val unknownLabels = unknownHalls.joinToString(", ") { "אולם ${it.hallNumber} (מקור ${it.originalCapacity ?: "?"})" }
            Text(
                text = "לא ידוע (${unknownHalls.size}): $unknownLabels — בשיפוץ, מושבים תקולים וקיבולת שמישה מסומנים כ'לא ידוע' (null).",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = PlanetAmber
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(PlanetMotion.standardTween(PlanetMotion.mediumMs, reducedMotion)) +
                fadeIn(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)),
            exit = shrinkVertically(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) +
                fadeOut(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                hallInfos.forEach { info ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PlanetRadius.xs))
                            .background(PlanetCardBg.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "אולם ${info.hallNumber ?: "?"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = PlanetTextPrimary
                        )
                        Text(
                            text = if (info.isBrokenSeatsKnown) {
                                "מקור ${info.originalCapacity} • תקולים ${info.brokenSeats} • שמיש ${info.usableCapacity} (ידוע)"
                            } else {
                                "מקור ${info.originalCapacity ?: "?"} • תקולים לא ידוע • שמיש לא ידוע"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (info.isBrokenSeatsKnown) PlanetTextSecondary else PlanetAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationSnapshotSummarySection(
    snapshot: LiveOccupancyValidationSnapshot,
    validationJsonPath: String?,
    showJsonPreview: Boolean,
    reducedMotion: Boolean,
    onToggleJsonPreview: () -> Unit
) {
    val verification = snapshot.countingAccuracyVerification
    val allOk = verification.allInvariantsPassed
    val disagreementItems = remember(snapshot) {
        snapshot.activeScreenings.filter { it.disagreements.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PlanetRadius.md))
            .background(PlanetDarkBg.copy(alpha = 0.82f))
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
            .padding(10.dp)
            .testTag("validation_snapshot_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
                    color = if (allOk) PlanetEmeraldLight else PlanetRoseLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(RoundedCornerShape(PlanetRadius.sm))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder, RoundedCornerShape(PlanetRadius.sm))
                    .clickable(onClick = onToggleJsonPreview)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("toggle_validation_json_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showJsonPreview) "הסתר JSON" else "Snapshot JSON",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PlanetImaxSkyLight
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(snapshot.sourceFreshness, key = { it.sourceName }) { src ->
                SourceFreshnessBadge(info = src)
            }
        }

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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
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

        AnimatedVisibility(
            visible = showJsonPreview,
            enter = expandVertically(PlanetMotion.standardTween(PlanetMotion.mediumMs, reducedMotion)) +
                fadeIn(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)),
            exit = shrinkVertically(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) +
                fadeOut(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion))
        ) {
            val previewJson = remember(snapshot) {
                LiveValidationSnapshotBuilder.toJson(snapshot).take(6000)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PlanetRadius.sm))
                    .background(Color(0xFF070A12))
                    .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.sm))
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
            .clip(RoundedCornerShape(PlanetRadius.sm))
            .background(PlanetCardBg.copy(alpha = 0.75f))
            .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(PlanetRadius.sm))
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
            color = PlanetTextPrimary
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
            .clip(RoundedCornerShape(PlanetRadius.md))
            .background(PlanetCardBg.copy(alpha = 0.75f))
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFeatureSettings = "tnum"
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
            .clip(RoundedCornerShape(PlanetRadius.sm))
            .background(PlanetDarkBg.copy(alpha = 0.7f))
            .border(
                1.dp,
                if (diag.includedInOccupancyTotal) PlanetBorderSubtle else PlanetAmber.copy(alpha = 0.45f),
                RoundedCornerShape(PlanetRadius.sm)
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
                color = PlanetTextPrimary,
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
            append(" • מקור=${diag.originalHallCapacity ?: "?"}")
            append(" • תקולים=${diag.brokenSeatsCount ?: "לא ידוע"}")
            append(" • שמיש=${diag.usableHallCapacity ?: "לא ידוע"}")
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
