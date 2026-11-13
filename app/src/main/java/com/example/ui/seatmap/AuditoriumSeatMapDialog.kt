package com.example.ui.seatmap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.ui.components.DashboardCard
import com.example.ui.components.StatusPill
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
import com.example.ui.theme.PlanetMotion
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseGlow
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSkyGlow
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetSurfaceGlass
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary
import com.example.ui.theme.rememberReducedMotion

/**
 * Full-screen Material 3 interactive dialog for inspecting an auditorium's row/seat map,
 * accessible status legend, broken-seat capacity reconciliation, and verified seat details.
 *
 * Opening, closing, or switching auditoriums uses cached/in-memory state and never triggers
 * unnecessary network calls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuditoriumSeatMapDialog(
    initialScreeningId: String,
    allScreenings: List<Screening>,
    isRefreshingSeatMap: Boolean = false,
    onRefreshScreeningSeatMap: ((Screening) -> Unit)? = null,
    onDismiss: () -> Unit,
    explicitBrokenSeatKeysProvider: (Screening) -> Set<String> = { emptySet() }
) {
    var selectedScreeningId by remember(initialScreeningId) { mutableStateOf(initialScreeningId) }
    var selectedSeatId by remember(selectedScreeningId) { mutableStateOf<String?>(null) }

    val selectorScreenings = remember(allScreenings) {
        allScreenings
            .sortedWith(
                compareBy<Screening> { it.roomNumber ?: 99 }
                    .thenBy { it.startMin }
            )
    }

    val activeScreening = remember(selectedScreeningId, selectorScreenings) {
        selectorScreenings.firstOrNull { it.id == selectedScreeningId }
            ?: selectorScreenings.firstOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AuditoriumSeatMapContent(
                activeScreening = activeScreening,
                selectorScreenings = selectorScreenings,
                selectedSeatId = selectedSeatId,
                isRefreshingSeatMap = isRefreshingSeatMap,
                explicitBrokenSeatKeys = activeScreening?.let(explicitBrokenSeatKeysProvider).orEmpty(),
                onSelectScreening = { screening ->
                    selectedScreeningId = screening.id
                    selectedSeatId = null
                },
                onSelectSeat = { seat ->
                    selectedSeatId = if (selectedSeatId == seat.id) null else seat.id
                },
                onClearSeatSelection = { selectedSeatId = null },
                onRefreshSeatMap = {
                    if (activeScreening != null) {
                        onRefreshScreeningSeatMap?.invoke(activeScreening)
                    }
                },
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AuditoriumSeatMapContent(
    activeScreening: Screening?,
    selectorScreenings: List<Screening>,
    selectedSeatId: String?,
    isRefreshingSeatMap: Boolean,
    explicitBrokenSeatKeys: Set<String> = emptySet(),
    onSelectScreening: (Screening) -> Unit,
    onSelectSeat: (AuditoriumSeatUiItem) -> Unit,
    onClearSeatSelection: () -> Unit,
    onRefreshSeatMap: () -> Unit,
    onDismiss: () -> Unit,
    nowEpochMs: Long = remember(activeScreening?.seatSummary?.fetchedAtEpochMs) { System.currentTimeMillis() }
) {
    val reducedMotion = rememberReducedMotion()
    // Recompute ONLY when screening seat payload / broken keys / fetch state change — never every 1s clock tick
    val seatMapModel = remember(
        activeScreening?.id,
        activeScreening?.seatSummary,
        activeScreening?.seatFetchState,
        activeScreening?.seatApiFromCache,
        activeScreening?.lastSeatUpdateEpochMs,
        activeScreening?.availabilityRatio,
        explicitBrokenSeatKeys
    ) {
        activeScreening?.let { screening ->
            AuditoriumSeatMapBuilder.build(
                screening = screening,
                nowEpochMs = nowEpochMs,
                explicitBrokenSeatKeys = explicitBrokenSeatKeys
            )
        }
    }

    val selectedSeat = remember(selectedSeatId, seatMapModel?.seats) {
        seatMapModel?.seats?.firstOrNull { it.id == selectedSeatId }
    }

    Surface(
        color = PlanetDarkBg,
        modifier = Modifier
            .fillMaxSize()
            .testTag("auditorium_seat_map_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.displayCutout)
                        .union(WindowInsets.navigationBars)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            SeatMapTopBar(
                model = seatMapModel,
                isRefreshing = isRefreshingSeatMap,
                reducedMotion = reducedMotion,
                onRefresh = onRefreshSeatMap,
                onDismiss = onDismiss
            )

            HorizontalDivider(thickness = 1.dp, color = PlanetBorderSubtle)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PlanetSpacing.md, vertical = PlanetSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md)
            ) {
                // 1. Auditorium & Screening Selector
                if (selectorScreenings.isNotEmpty()) {
                    AuditoriumSelectorRow(
                        screenings = selectorScreenings,
                        selectedId = activeScreening?.id,
                        onSelect = onSelectScreening
                    )
                }

                if (seatMapModel == null) {
                    UnavailableSeatMapCard(
                        reason = "לא נמצאה הקרנה פעילה להצגה.",
                        brokenReconciliation = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // 2. Data Freshness + Last Refresh Time Strip
                    FreshnessStatusBanner(model = seatMapModel)

                    // 3. Unverified status warning if raw codes are unverified
                    AnimatedVisibility(
                        visible = seatMapModel.hasUnverifiedStatuses && seatMapModel.layoutMode != AuditoriumLayoutMode.UNAVAILABLE,
                        enter = expandVertically(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) + fadeIn(),
                        exit = shrinkVertically(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) + fadeOut()
                    ) {
                        UnverifiedNoticeCard()
                    }

                    // 4. Accessible 5-Status Legend with Counts
                    SeatStatusLegendCard(counts = seatMapModel.counts)

                    // 5. Interactive Seat Map Canvas OR Honest Unavailable Card
                    AnimatedContent(
                        targetState = seatMapModel,
                        contentKey = { Triple(it.screeningId, it.layoutMode, it.seats.size) },
                        transitionSpec = {
                            fadeIn(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) togetherWith
                                fadeOut(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion))
                        },
                        label = "seat_map_body_transition"
                    ) { currentModel ->
                        if (currentModel.layoutMode == AuditoriumLayoutMode.UNAVAILABLE) {
                            UnavailableSeatMapCard(
                                reason = currentModel.unavailableReason ?: "מפת המושבים אינה זמינה כרגע.",
                                brokenReconciliation = currentModel.brokenReconciliation
                            )
                        } else {
                            AuditoriumSeatCanvas(
                                model = currentModel,
                                selectedSeatId = selectedSeat?.id,
                                onSeatSelected = onSelectSeat
                            )
                        }
                    }

                    // 6. Selected Seat Details Panel (revealed smoothly on seat tap)
                    AnimatedVisibility(
                        visible = selectedSeat != null,
                        enter = expandVertically(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) +
                            fadeIn(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)),
                        exit = shrinkVertically(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) +
                            fadeOut(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion))
                    ) {
                        selectedSeat?.let { seat ->
                            SeatDetailsPanel(
                                seat = seat,
                                onClose = onClearSeatSelection
                            )
                        }
                    }

                    // 7. Broken Seats & Usable Capacity Reconciliation (No double subtraction)
                    BrokenSeatsReconciliationCard(
                        reconciliation = seatMapModel.brokenReconciliation,
                        hallTitle = seatMapModel.hallTitle
                    )
                }

                Spacer(modifier = Modifier.height(PlanetSpacing.lg))
            }
        }
    }
}

@Composable
private fun SeatMapTopBar(
    model: AuditoriumSeatMapUiModel?,
    isRefreshing: Boolean,
    reducedMotion: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val spinAngle = if (isRefreshing && !reducedMotion) {
        val infinite = rememberInfiniteTransition(label = "seat_map_refresh_spin")
        val angle by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "seat_map_refresh_angle"
        )
        angle
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlanetSurfaceGlass)
            .padding(horizontal = PlanetSpacing.sm, vertical = PlanetSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.xs)
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(48.dp)
                .testTag("seat_map_close_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "סגור מפת מושבים",
                tint = PlanetTextPrimary
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = model?.hallTitle ?: "מפת מושבים באולם",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PlanetTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                model?.roomType?.let { type ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(PlanetRadius.xs))
                            .background(type.containerColor)
                            .border(1.dp, type.badgeColor.copy(alpha = 0.5f), RoundedCornerShape(PlanetRadius.xs))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = type.badgeColor
                        )
                    }
                }
            }

            if (model != null) {
                Text(
                    text = "${model.movieTitle} • ${model.timeSlotLabel}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = PlanetTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing && model?.presentationId != null,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(48.dp)
                .testTag("seat_map_refresh_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "רענן מפת מושבים",
                tint = if (model?.presentationId != null) PlanetTextPrimary else PlanetTextMuted,
                modifier = Modifier.rotate(spinAngle)
            )
        }
    }
}

@Composable
private fun AuditoriumSelectorRow(
    screenings: List<Screening>,
    selectedId: String?,
    onSelect: (Screening) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seat_map_auditorium_selector"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "בחר אולם והקרנה (${screenings.size})",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = PlanetTextSecondary
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(
                items = screenings,
                key = { it.id }
            ) { screening ->
                val isSelected = screening.id == selectedId
                val hasSeats = screening.seatSummary?.seats?.isNotEmpty() == true
                val isLive = screening.status == ScreeningStatus.LIVE
                val shape = RoundedCornerShape(PlanetRadius.md)
                val border = when {
                    isSelected -> PlanetRose
                    hasSeats -> PlanetEmeraldLight.copy(alpha = 0.45f)
                    else -> PlanetBorderSubtle
                }
                val bg = if (isSelected) PlanetRoseGlow else PlanetSurface

                Column(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(shape)
                        .background(bg)
                        .border(if (isSelected) 1.5.dp else 1.dp, border, shape)
                        .clickable { onSelect(screening) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("seat_selector_chip_${screening.id}"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "אולם ${screening.roomNumber ?: screening.room}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) PlanetTextPrimary else PlanetTextSecondary
                        )
                        if (isLive) {
                            StatusPill(
                                text = "שידור חי",
                                textColor = PlanetRoseLight,
                                containerColor = PlanetRoseGlow
                            )
                        } else if (hasSeats) {
                            StatusPill(
                                text = "מפה זמינה",
                                textColor = PlanetEmeraldLight,
                                containerColor = PlanetEmeraldGlow
                            )
                        }
                    }
                    Text(
                        text = "${screening.startStr} • ${screening.title}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = PlanetTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 175.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreshnessStatusBanner(model: AuditoriumSeatMapUiModel) {
    val (fg, bg) = when (model.freshnessState) {
        SeatDataFreshnessState.LIVE_FRESH -> PlanetEmeraldLight to PlanetEmeraldGlow
        SeatDataFreshnessState.CACHED -> PlanetImaxSkyLight to PlanetSkyGlow
        SeatDataFreshnessState.STALE,
        SeatDataFreshnessState.UNVERIFIED,
        SeatDataFreshnessState.UNAVAILABLE -> PlanetGold to PlanetAmberGlow
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seat_map_freshness_pill"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatusPill(
            text = model.freshnessState.hebrewLabel,
            textColor = fg,
            containerColor = bg,
            borderColor = fg.copy(alpha = 0.35f),
            leadingIcon = Icons.Filled.Schedule
        )
        StatusPill(
            text = "עדכון אחרון: ${model.lastRefreshedLabel}",
            textColor = PlanetTextSecondary,
            containerColor = PlanetCardBg,
            borderColor = PlanetBorderSubtle
        )
        StatusPill(
            text = "מקור: ${model.availabilitySource.hebrewLabel}",
            textColor = PlanetTextMuted,
            containerColor = PlanetSurface,
            borderColor = PlanetBorderSubtle
        )
    }
}

@Composable
private fun UnverifiedNoticeCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PlanetRadius.md))
            .background(PlanetAmberGlow)
            .border(1.dp, PlanetAmber.copy(alpha = 0.45f), RoundedCornerShape(PlanetRadius.md))
            .padding(PlanetSpacing.md)
            .testTag("seat_map_unverified_banner"),
        horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = PlanetGold,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "קודי הסטטוס הגולמיים משרת המושבים טרם אומתו מול צבעי האתר. מושבים אלה מוצגים כ'לא ידוע / לא מאומת' ואינם מסווגים כפנויים.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
            color = PlanetGold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatStatusLegendCard(counts: SeatStatusCounts) {
    DashboardCard(
        contentPadding = PaddingValues(PlanetSpacing.md),
        gradientColors = listOf(PlanetSurface, PlanetCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seat_map_legend")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "מקרא סטטוס מושבים",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PlanetTextPrimary
                )
                Text(
                    text = "סה\"כ במפה: ${counts.totalMappedSeats}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = PlanetTextMuted
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LegendChip(SeatVisualStatus.AVAILABLE, counts.available, "✓")
                LegendChip(SeatVisualStatus.RESERVED_OR_BOOKED, counts.reservedOrBooked, "●")
                LegendChip(SeatVisualStatus.BLOCKED_OR_UNAVAILABLE, counts.blockedOrUnavailable, "×")
                LegendChip(SeatVisualStatus.BROKEN, counts.broken, "!")
                LegendChip(SeatVisualStatus.UNKNOWN, counts.unknown, "?")
            }
        }
    }
}

@Composable
private fun LegendChip(
    status: SeatVisualStatus,
    count: Int,
    glyph: String
) {
    val shape = RoundedCornerShape(PlanetRadius.sm)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(PlanetDarkBg.copy(alpha = 0.72f))
            .border(1.dp, PlanetBorderSubtle, shape)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                .background(status.fillColor)
                .border(1.dp, status.borderColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = status.contentColor
            )
        }
        Text(
            text = "${status.hebrewLabel} ($count)",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontFeatureSettings = "tnum"
            ),
            color = PlanetTextSecondary
        )
    }
}

@Composable
private fun SeatDetailsPanel(
    seat: AuditoriumSeatUiItem,
    onClose: () -> Unit
) {
    DashboardCard(
        borderColor = seat.visualStatus.borderColor.copy(alpha = 0.7f),
        borderWidth = 1.25.dp,
        gradientColors = listOf(PlanetCardBg, PlanetSurface),
        contentPadding = PaddingValues(PlanetSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seat_details_panel")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
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
                            .size(34.dp)
                            .clip(RoundedCornerShape(PlanetRadius.sm))
                            .background(seat.visualStatus.fillColor)
                            .border(1.dp, seat.visualStatus.borderColor, RoundedCornerShape(PlanetRadius.sm)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EventSeat,
                            contentDescription = null,
                            tint = seat.visualStatus.contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "שורה ${seat.rowNumber} • מושב ${seat.seatNumber}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = PlanetTextPrimary
                        )
                        seat.roomName?.takeIf { it.isNotBlank() }?.let { roomName ->
                            Text(
                                text = roomName,
                                style = MaterialTheme.typography.labelSmall,
                                color = PlanetTextMuted
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("seat_details_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "סגור פרטי מושב",
                        tint = PlanetTextSecondary
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = PlanetBorderSubtle)

            SeatDetailInfoRow(
                label = "סטטוס נוכחי",
                value = seat.exactStatusLabel(),
                valueColor = seat.visualStatus.borderColor
            )
            SeatDetailInfoRow(
                label = "אימות סטטוס",
                value = if (seat.isVerified) "מאומת מול מפת האתר" else "לא מאומת (שדות גולמיים ללא מיפוי ודאי)",
                valueColor = if (seat.isVerified) PlanetEmeraldLight else PlanetGold,
                leadingIcon = if (seat.isVerified) Icons.Filled.CheckCircle else Icons.Filled.HelpOutline
            )
            SeatDetailInfoRow(
                label = "עדכון סטטוס אחרון",
                value = buildString {
                    append(seat.lastRefreshedLabel)
                    if (seat.lastRefreshedEpochMs != null) {
                        append(if (seat.isFromCache) " • ממטמון" else " • קריאה ישירה")
                    }
                },
                valueColor = PlanetTextPrimary
            )
            SeatDetailInfoRow(
                label = "זמן הזמנה",
                value = seat.officialBookingTimestamp ?: "לא מסופק ע\"י ה-API הרשמי",
                valueColor = if (seat.officialBookingTimestamp != null) PlanetTextPrimary else PlanetTextMuted
            )

            if (seat.technicalRawFields.isNotEmpty()) {
                val rawTag = seat.technicalRawFields.entries
                    .sortedBy { it.key }
                    .joinToString(", ") { "${it.key}=${it.value}" }
                Text(
                    text = "שדות API טכניים: $rawTag",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = PlanetTextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PlanetRadius.xs))
                        .background(PlanetDarkBg.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun SeatDetailInfoRow(
    label: String,
    value: String,
    valueColor: Color,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PlanetTextMuted
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = valueColor,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun BrokenSeatsReconciliationCard(
    reconciliation: BrokenSeatsReconciliation,
    hallTitle: String
) {
    DashboardCard(
        gradientColors = listOf(PlanetSurface, PlanetDarkBg),
        borderColor = if (!reconciliation.isBrokenSeatsKnown) {
            PlanetAmber.copy(alpha = 0.4f)
        } else {
            PlanetBorderSubtle
        },
        contentPadding = PaddingValues(PlanetSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seat_map_broken_seats_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (reconciliation.excludedByLiveMap) Icons.Filled.VerifiedUser else Icons.Filled.Build,
                    contentDescription = null,
                    tint = if (!reconciliation.isBrokenSeatsKnown) PlanetGold else Color(0xFFFB923C),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "מושבים תקולים וקיבולת שמישה ($hallTitle)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = PlanetTextPrimary
                )
            }

            Text(
                text = "קיבולת מקורית: ${reconciliation.originalCapacity ?: "לא ידוע"} • " +
                    "תקולים ידנית: ${reconciliation.manualBrokenSeats?.toString() ?: "לא ידוע"} • " +
                    "קיבולת שמישה: ${reconciliation.usableCapacity?.toString() ?: "לא ידוע"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = PlanetTextSecondary
            )

            Text(
                text = reconciliation.summaryMessage,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = if (reconciliation.excludedByLiveMap) PlanetEmeraldLight else PlanetAmber
            )
        }
    }
}

@Composable
private fun UnavailableSeatMapCard(
    reason: String,
    brokenReconciliation: BrokenSeatsReconciliation?,
    modifier: Modifier = Modifier
) {
    DashboardCard(
        contentPadding = PaddingValues(PlanetSpacing.xl),
        borderColor = PlanetAmber.copy(alpha = 0.4f),
        modifier = modifier.testTag("seat_map_unavailable_state")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = PlanetGold,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = "מפת מושבים מפורטת אינה זמינה",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PlanetTextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = PlanetTextSecondary,
                textAlign = TextAlign.Center
            )
            if (brokenReconciliation != null) {
                Text(
                    text = "קיבולת שמישה ידועה לאולם: ${brokenReconciliation.usableCapacity ?: "לא ידוע"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlanetGold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
