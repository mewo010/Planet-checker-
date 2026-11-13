package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EventKind
import com.example.data.model.KpiStats
import com.example.data.model.RoomType
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetAmberGlow
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetCardElevated
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmerald
import com.example.ui.theme.PlanetEmeraldGlow
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetImaxSky
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
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KpiCardsSection(
    kpiStats: KpiStats,
    cinemaRepository: CinemaRepository,
    isLiveDataSource: Boolean = true,
    lastUpdatedLabel: String = "",
    dataFreshnessLabel: String = "",
    onInsideAudienceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val occ = kpiStats.occupancyResult
    val incompleteCount = occ.missingScreenings + occ.failedScreenings
    val insideValue = if (occ.unknownOccupancy) "?" else kpiStats.peopleInside.toString()

    // Exact 30-minute forecast derived directly from existing ViewModel metrics
    val forecast30MinPeople = if (occ.unknownOccupancy) {
        null
    } else {
        (kpiStats.peopleInside + kpiStats.in30MinPeople - kpiStats.out30MinPeople).coerceAtLeast(0)
    }
    val forecastDelta = if (forecast30MinPeople != null) {
        forecast30MinPeople - kpiStats.peopleInside
    } else {
        null
    }

    val insideBadgeText = when {
        !isLiveDataSource -> "דוגמה"
        occ.unknownOccupancy -> "אין נתונים"
        !occ.isComplete -> "חלקי"
        dataFreshnessLabel.startsWith("נתון ישן") -> "לא עדכני"
        occ.verifiedOccupiedSeats > 0 && occ.estimatedOccupiedSeats == 0 -> "מאומת חי"
        else -> "אומדן חי"
    }
    val isWarningState = occ.unknownOccupancy || !occ.isComplete || !isLiveDataSource || dataFreshnessLabel.startsWith("נתון ישן")
    val badgeBg = if (isWarningState) PlanetAmberGlow else PlanetEmeraldGlow
    val badgeFg = if (isWarningState) PlanetGold else PlanetEmeraldLight

    val activeHallsSubtitle = buildString {
        if (occ.activeScreenings == 0) {
            append("${kpiStats.activeHallsCount} אולמות פעילים כרגע")
        } else if (!occ.isComplete) {
            append("נספרו ${occ.countedScreenings}/${occ.activeScreenings} הקרנות פעילות")
            if (occ.failedScreenings > 0) append(" • ${occ.failedScreenings} שגיאות")
            else if (incompleteCount > 0) append(" • $incompleteCount חסרות")
        } else {
            append("${occ.activeScreenings} הקרנות פעילות כעת")
        }
    }

    val heroBorder = rememberValueChangeHighlight(
        triggerKey = insideValue,
        baseColor = PlanetRose.copy(alpha = 0.35f),
        highlightColor = PlanetRoseLight.copy(alpha = 0.85f)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
    ) {
        // 1. Hero Card: Current Audience Count + Verified vs Estimated Breakdown + Freshness
        DashboardCard(
            borderColor = heroBorder,
            borderWidth = 1.dp,
            gradientColors = listOf(PlanetNavy, PlanetCardBg),
            contentPadding = PaddingValues(PlanetSpacing.lg),
            onClick = onInsideAudienceClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kpi_inside")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(PlanetRadius.sm))
                            .background(PlanetRoseGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            tint = PlanetRoseLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "קהל בקולנוע כרגע",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = PlanetTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeHallsSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = PlanetTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                StatusPill(
                    text = "$insideBadgeText ↗",
                    textColor = badgeFg,
                    containerColor = badgeBg,
                    borderColor = badgeFg.copy(alpha = 0.35f)
                )
            }

            Spacer(modifier = Modifier.height(PlanetSpacing.md))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedMetricValue(
                    value = insideValue,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PlanetTextPrimary
                )
                Text(
                    text = "צופים",
                    style = MaterialTheme.typography.labelLarge,
                    color = PlanetTextSecondary,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(PlanetSpacing.sm))

            // Clear distinction between Verified Seat Map vs Ratio Estimate (wraps cleanly on narrow screens)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusPill(
                    text = "מאומתים ${occ.verifiedOccupiedSeats}",
                    textColor = PlanetEmeraldLight,
                    containerColor = PlanetEmeraldGlow,
                    leadingIcon = Icons.Filled.CheckCircle
                )
                StatusPill(
                    text = "אומדן ${occ.estimatedOccupiedSeats}",
                    textColor = PlanetImaxSkyLight,
                    containerColor = PlanetSkyGlow,
                    leadingIcon = Icons.Filled.QueryStats
                )
                if (lastUpdatedLabel.isNotBlank() && lastUpdatedLabel != "טרם עודכן") {
                    StatusPill(
                        text = "$dataFreshnessLabel • $lastUpdatedLabel",
                        textColor = PlanetTextMuted,
                        containerColor = PlanetDarkBg.copy(alpha = 0.65f)
                    )
                }
            }
        }

        // 2. Flow & 30-Minute Forecast Row (responsive for narrow screens)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isVeryNarrow = maxWidth < 340.dp
            val arrivingCard: @Composable (Modifier) -> Unit = { mod ->
                FlowForecastKpiCard(
                    title = "נכנסים ב-30 דק'",
                    value = "+${kpiStats.in30MinPeople}",
                    subtitle = "${kpiStats.in30MinHalls} הקרנות",
                    icon = Icons.AutoMirrored.Filled.Login,
                    accentColor = PlanetEmerald,
                    valueColor = PlanetEmeraldLight,
                    kindLabel = "תנועה צפויה",
                    isForecast = false,
                    testTag = "kpi_arriving",
                    modifier = mod
                )
            }
            val leavingCard: @Composable (Modifier) -> Unit = { mod ->
                FlowForecastKpiCard(
                    title = "יוצאים ב-30 דק'",
                    value = "-${kpiStats.out30MinPeople}",
                    subtitle = "${kpiStats.out30MinHalls} אולמות",
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    accentColor = PlanetAmber,
                    valueColor = PlanetGold,
                    kindLabel = "פינוי צפוי",
                    isForecast = false,
                    testTag = "kpi_leaving",
                    modifier = mod
                )
            }
            val forecastCard: @Composable (Modifier) -> Unit = { mod ->
                val deltaSubtitle = when {
                    forecastDelta == null -> "אין בסיס חישוב"
                    forecastDelta > 0 -> "+$forecastDelta מהמצב כעת"
                    forecastDelta < 0 -> "-${abs(forecastDelta)} מהמצב כעת"
                    else -> "ללא שינוי נטו"
                }
                FlowForecastKpiCard(
                    title = "תחזית עוד 30 דק'",
                    value = forecast30MinPeople?.toString() ?: "?",
                    subtitle = deltaSubtitle,
                    icon = Icons.Filled.AutoAwesome,
                    accentColor = PlanetImaxSky,
                    valueColor = PlanetImaxSkyLight,
                    kindLabel = "תחזית",
                    isForecast = true,
                    testTag = "kpi_forecast_30m",
                    modifier = mod
                )
            }

            if (isVeryNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                    ) {
                        arrivingCard(Modifier.weight(1f).fillMaxHeight())
                        leavingCard(Modifier.weight(1f).fillMaxHeight())
                    }
                    forecastCard(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                ) {
                    arrivingCard(Modifier.weight(1f).fillMaxHeight())
                    leavingCard(Modifier.weight(1f).fillMaxHeight())
                    forecastCard(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        // 3. Next Event Strip (preserves kpi_next_event cleanly without cramping 2x2 grid)
        val next = kpiStats.nextEvent
        val nextHeadline = if (next != null) {
            val kindText = if (next.kind == EventKind.OUT) "יציאה" else "כניסה"
            "$kindText • ${RoomType.formatRoomLabel(next.screening.room)} • ${next.screening.title}"
        } else {
            "אין אירועים קרובים ב-2 השעות הקרובות"
        }
        val nextTiming = if (next != null) {
            val formattedTime = cinemaRepository.formatCinemaMinutes(next.timeMin)
            val relTime = cinemaRepository.relativeTimeSpan(next.minutesUntil)
            "$formattedTime ($relTime)"
        } else {
            "—"
        }

        DashboardCard(
            borderColor = if (next?.isUrgent == true) PlanetAmber.copy(alpha = 0.55f) else PlanetBorderSubtle,
            gradientColors = listOf(PlanetSurface, PlanetCardBg),
            contentPadding = PaddingValues(horizontal = PlanetSpacing.md, vertical = PlanetSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kpi_next_event")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(PlanetRadius.sm))
                            .background(PlanetSkyGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = PlanetImaxSkyLight,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "האירוע הבא",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlanetTextMuted
                        )
                        Text(
                            text = nextHeadline,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = PlanetTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.size(PlanetSpacing.sm))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(PlanetRadius.sm))
                        .background(PlanetDarkBg.copy(alpha = 0.8f))
                        .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.sm))
                        .padding(horizontal = PlanetSpacing.sm, vertical = 4.dp)
                ) {
                    Text(
                        text = nextTiming,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (next?.isUrgent == true) PlanetGold else PlanetTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowForecastKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    valueColor: Color,
    kindLabel: String,
    isForecast: Boolean,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val borderBase = if (isForecast) accentColor.copy(alpha = 0.45f) else PlanetBorderSubtle
    val animatedBorder = rememberValueChangeHighlight(
        triggerKey = value,
        baseColor = borderBase,
        highlightColor = accentColor.copy(alpha = 0.85f)
    )
    val gradient = if (isForecast) {
        listOf(PlanetNavy, PlanetCardElevated)
    } else {
        listOf(PlanetSurface, PlanetCardBg)
    }

    DashboardCard(
        borderColor = animatedBorder,
        gradientColors = gradient,
        contentPadding = PaddingValues(PlanetSpacing.md),
        modifier = modifier
            .defaultMinSize(minHeight = 108.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(PlanetRadius.xs))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(PlanetRadius.pill))
                        .background(
                            if (isForecast) accentColor.copy(alpha = 0.18f)
                            else PlanetDarkBg.copy(alpha = 0.65f)
                        )
                        .border(
                            1.dp,
                            if (isForecast) accentColor.copy(alpha = 0.45f) else PlanetBorderSubtle,
                            RoundedCornerShape(PlanetRadius.pill)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = kindLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isForecast) valueColor else PlanetTextMuted,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(PlanetSpacing.sm))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = PlanetTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                AnimatedMetricValue(
                    value = value,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = valueColor
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = PlanetTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
