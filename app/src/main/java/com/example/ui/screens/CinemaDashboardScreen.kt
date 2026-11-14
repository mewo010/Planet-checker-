package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CinemaTab
import com.example.data.model.CinemaUiState
import com.example.data.repository.CinemaRepository
import com.example.ui.components.AudienceBreakdownDialog
import com.example.ui.components.CinemaHeader
import com.example.ui.components.CinemaNavigationBar
import com.example.ui.components.CinemaNavigationRail
import com.example.ui.components.DailyStatsView
import com.example.ui.components.DashboardCard
import com.example.ui.components.FilterControlBar
import com.example.ui.components.HallsStatusGrid
import com.example.ui.components.KpiCardsSection
import com.example.ui.components.MovieCarousel
import com.example.ui.components.ScreeningDetailSheet
import com.example.ui.components.ScreeningTicketCard
import com.example.ui.components.TimelineView
import com.example.ui.seatmap.AuditoriumSeatMapDialog
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetMotion
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseGlow
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.viewmodel.CinemaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaDashboardScreen(
    viewModel: CinemaViewModel,
    uiState: CinemaUiState,
    cinemaRepository: CinemaRepository = remember { CinemaRepository() },
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAudienceDialog by remember { mutableStateOf(false) }
    var seatMapScreeningId by remember { mutableStateOf<String?>(null) }
    val reducedMotion = rememberReducedMotion()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 840.dp

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = PlanetDarkBg,
                topBar = {
                    CinemaHeader(
                        isLive = uiState.isLiveDataSource,
                        isRefreshing = uiState.isRefreshing,
                        seatMapUpdateInProgress = uiState.seatMapUpdateInProgress,
                        dataFreshnessLabel = uiState.dataFreshnessLabel,
                        lastSuccessfulUpdateLabel = uiState.lastSuccessfulUpdateLabel,
                        onRefresh = { viewModel.loadData(isUserInitiated = true) }
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        CinemaNavigationBar(
                            activeTab = uiState.activeTab,
                            onTabSelected = { tab -> viewModel.setActiveTab(tab) }
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { paddingValues ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (useNavigationRail) {
                        CinemaNavigationRail(
                            activeTab = uiState.activeTab,
                            onTabSelected = { tab -> viewModel.setActiveTab(tab) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (uiState.isLoading) {
                            LoadingDashboardState(modifier = Modifier.fillMaxSize())
                        } else {
                            PullToRefreshBox(
                                isRefreshing = uiState.isRefreshing,
                                onRefresh = { viewModel.loadData(isUserInitiated = true) },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                AnimatedContent(
                                    targetState = uiState.activeTab,
                                    transitionSpec = {
                                        if (reducedMotion) {
                                            fadeIn(PlanetMotion.standardTween(0, true)) togetherWith
                                                fadeOut(PlanetMotion.standardTween(0, true))
                                        } else {
                                            val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                            (fadeIn(PlanetMotion.standardTween(PlanetMotion.mediumMs)) +
                                                slideInHorizontally(PlanetMotion.standardTween(PlanetMotion.mediumMs)) {
                                                    direction * (it / 10)
                                                }) togetherWith
                                                (fadeOut(PlanetMotion.standardTween(PlanetMotion.fastMs)) +
                                                    slideOutHorizontally(PlanetMotion.standardTween(PlanetMotion.fastMs)) {
                                                        -direction * (it / 10)
                                                    })
                                        }
                                    },
                                    label = "tab_content_transition",
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxHeight()
                                        .widthIn(max = 900.dp)
                                        .fillMaxWidth()
                                ) { activeTab ->
                                    val tabListState = rememberLazyListState()
                                    LazyColumn(
                                        state = tabListState,
                                        modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = PlanetSpacing.md,
                                        end = PlanetSpacing.md,
                                        top = PlanetSpacing.md,
                                        bottom = PlanetSpacing.xxl
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md)
                                ) {
                                    item(key = "kpi_section") {
                                        KpiCardsSection(
                                            kpiStats = uiState.kpiStats,
                                            cinemaRepository = cinemaRepository,
                                            isLiveDataSource = uiState.isLiveDataSource,
                                            lastUpdatedLabel = uiState.lastSuccessfulUpdateLabel,
                                            dataFreshnessLabel = uiState.dataFreshnessLabel,
                                            onInsideAudienceClick = { showAudienceDialog = true }
                                        )
                                    }

                                    when (activeTab) {
                                        CinemaTab.SCHEDULE -> {
                                            if (uiState.movieSummaries.isNotEmpty()) {
                                                item(key = "movie_carousel") {
                                                    MovieCarousel(
                                                        movies = uiState.movieSummaries,
                                                        selectedMovieTitle = uiState.selectedMovieTitle,
                                                        onMovieClick = { viewModel.toggleMovieSelection(it) }
                                                    )
                                                }
                                            }

                                            item(key = "filter_controls") {
                                                FilterControlBar(
                                                    searchQuery = uiState.searchQuery,
                                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                                    activeFilter = uiState.filterType,
                                                    onFilterChange = { viewModel.setFilterType(it) },
                                                    countsByType = uiState.countsByType,
                                                    sortBy = uiState.sortBy,
                                                    onSortChange = { viewModel.setSortOption(it) },
                                                    showEnded = uiState.showEnded,
                                                    onShowEndedChange = { viewModel.toggleShowEnded(it) }
                                                )
                                            }

                                            item(key = "active_filter_banner") {
                                                androidx.compose.animation.AnimatedVisibility(
                                                    visible = uiState.selectedMovieTitle != null || uiState.searchQuery.isNotEmpty(),
                                                    enter = expandVertically(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) +
                                                        fadeIn(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)),
                                                    exit = shrinkVertically(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion)) +
                                                        fadeOut(PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion))
                                                ) {
                                                    ActiveFilterBanner(
                                                        title = uiState.selectedMovieTitle ?: uiState.searchQuery,
                                                        resultCount = uiState.filteredScreenings.size,
                                                        onClear = {
                                                            if (uiState.selectedMovieTitle != null) {
                                                                viewModel.toggleMovieSelection(uiState.selectedMovieTitle)
                                                            }
                                                            if (uiState.searchQuery.isNotEmpty()) {
                                                                viewModel.setSearchQuery("")
                                                            }
                                                        }
                                                    )
                                                }
                                            }

                                            if (uiState.filteredScreenings.isEmpty()) {
                                                item(key = "empty_screenings") {
                                                    EmptyScreeningsState(
                                                        onReset = {
                                                            viewModel.setFilterType("ALL")
                                                            viewModel.setSearchQuery("")
                                                            if (uiState.selectedMovieTitle != null) {
                                                                viewModel.toggleMovieSelection(uiState.selectedMovieTitle)
                                                            }
                                                            viewModel.toggleShowEnded(true)
                                                        }
                                                    )
                                                }
                                            } else {
                                                items(
                                                    items = uiState.filteredScreenings,
                                                    key = { it.id }
                                                ) { screening ->
                                                    ScreeningTicketCard(
                                                        screening = screening,
                                                        currentCinemaMinutes = uiState.currentCinemaMinutes,
                                                        cinemaRepository = cinemaRepository,
                                                        onClick = { viewModel.selectScreening(screening) },
                                                        modifier = Modifier.animateItem()
                                                    )
                                                }
                                            }
                                        }

                                        CinemaTab.TIMELINE -> {
                                            item(key = "timeline_content") {
                                                TimelineView(
                                                    events = uiState.timelineEvents,
                                                    currentCinemaMinutes = uiState.currentCinemaMinutes,
                                                    cinemaRepository = cinemaRepository,
                                                    onScreeningClick = { viewModel.selectScreening(it) }
                                                )
                                            }
                                        }

                                        CinemaTab.HALLS_MAP -> {
                                            item(key = "halls_grid_content") {
                                                HallsStatusGrid(
                                                    allScreenings = uiState.allScreenings,
                                                    currentCinemaMinutes = uiState.currentCinemaMinutes,
                                                    cinemaRepository = cinemaRepository,
                                                    onScreeningClick = { viewModel.selectScreening(it) },
                                                    onOpenSeatMap = { screening ->
                                                        if (screening != null) {
                                                            seatMapScreeningId = screening.id
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        CinemaTab.DAILY_STATS -> {
                                            item(key = "daily_stats_content") {
                                                DailyStatsView(
                                                    dailyStats = uiState.dailyStats,
                                                    availableDates = uiState.availableHistoryDates,
                                                    selectedDate = uiState.selectedHistoryDate,
                                                    lastSuccessfulUpdateLabel = uiState.lastSuccessfulUpdateLabel,
                                                    dataFreshnessLabel = uiState.dataFreshnessLabel,
                                                    isLiveDataSource = uiState.isLiveDataSource,
                                                    hasUnverifiedSeatStatuses = uiState.hasUnverifiedSeatStatuses,
                                                    occupancyResult = uiState.kpiStats.occupancyResult,
                                                    quickBookStatus = uiState.quickBookStatus,
                                                    validationSnapshot = uiState.latestValidationSnapshot,
                                                    validationJsonPath = uiState.latestValidationJsonPath,
                                                    onSelectDate = { viewModel.selectHistoryDate(it) }
                                                )
                                            }
                                        }
                                    }

                                    item(key = "footer") {
                                        CinemaFooter(dataSourceLabel = uiState.dataSourceLabel)
                                    }
                                }
                            }
                            }
                        }

                        uiState.selectedScreening?.let { screening ->
                            val nowZoned = remember(uiState.currentIsraelTime, uiState.allScreenings) {
                                ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"))
                            }
                            ScreeningDetailSheet(
                                screening = screening,
                                currentCinemaMinutes = uiState.currentCinemaMinutes,
                                cinemaRepository = cinemaRepository,
                                sheetState = sheetState,
                                onDismiss = { viewModel.selectScreening(null) },
                                onFilterByMovie = { movieTitle ->
                                    viewModel.setActiveTab(CinemaTab.SCHEDULE)
                                    viewModel.toggleMovieSelection(movieTitle)
                                },
                                onOpenSeatMap = { target ->
                                    seatMapScreeningId = target.id
                                },
                                isPinned = screening.stableKey() in uiState.pinnedScreeningKeys,
                                onTogglePin = { viewModel.togglePinScreening(screening) },
                                nextScreeningInRoom = screening.nextScreeningInSameRoom(uiState.allScreenings, nowZoned)
                            )
                        }

                        seatMapScreeningId?.let { targetScreeningId ->
                            AuditoriumSeatMapDialog(
                                initialScreeningId = targetScreeningId,
                                allScreenings = uiState.allScreenings,
                                isRefreshingSeatMap = uiState.seatMapUpdateInProgress,
                                onRefreshScreeningSeatMap = { target ->
                                    viewModel.refreshScreeningSeatMap(target.id)
                                },
                                onDismiss = { seatMapScreeningId = null }
                            )
                        }

                        if (showAudienceDialog) {
                            AudienceBreakdownDialog(
                                allScreenings = uiState.allScreenings,
                                totalAudience = uiState.kpiStats.peopleInside,
                                isLiveDataSource = uiState.isLiveDataSource,
                                occupancyResult = uiState.kpiStats.occupancyResult,
                                lastUpdatedLabel = uiState.lastSuccessfulUpdateLabel,
                                onDismiss = { showAudienceDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingDashboardState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PlanetSpacing.md),
            modifier = Modifier.padding(PlanetSpacing.xxl)
        ) {
            CircularProgressIndicator(
                color = PlanetRose,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "טוען לוח הקרנות ונתוני אולמות…",
                style = MaterialTheme.typography.bodyMedium,
                color = PlanetTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActiveFilterBanner(
    title: String,
    resultCount: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(PlanetRadius.md))
            .background(PlanetRoseGlow)
            .border(1.dp, PlanetRose.copy(alpha = 0.4f), RoundedCornerShape(PlanetRadius.md))
            .padding(horizontal = PlanetSpacing.md, vertical = PlanetSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.xs)
        ) {
            Text(
                text = "מסונן לפי: $title",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = PlanetTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "($resultCount תוצאות)",
                style = MaterialTheme.typography.labelSmall,
                color = PlanetRoseLight,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(RoundedCornerShape(PlanetRadius.sm))
                .clickable(onClick = onClear)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = "בטל סינון",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = PlanetRoseLight
            )
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = "בטל סינון",
                tint = PlanetRoseLight,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun EmptyScreeningsState(
    onReset: () -> Unit
) {
    DashboardCard(
        contentPadding = PaddingValues(PlanetSpacing.xxl),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Filled.MeetingRoom,
                contentDescription = null,
                tint = PlanetTextMuted,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "אין הקרנות מתאימות כרגע",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PlanetTextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "נסה לשנות את הסינון, מילות החיפוש או לאפשר הצגת הקרנות שהסתיימו.",
                style = MaterialTheme.typography.bodySmall,
                color = PlanetTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = PlanetSpacing.md)
            )
            Spacer(modifier = Modifier.height(PlanetSpacing.xs))
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(PlanetRadius.md))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
                    .clickable(onClick = onReset)
                    .padding(horizontal = PlanetSpacing.lg, vertical = PlanetSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "אפס את כל הסינונים",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PlanetRoseLight
                )
            }
        }
    }
}

@Composable
private fun CinemaFooter(
    dataSourceLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PlanetSpacing.sm, bottom = PlanetSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "הנתונים מתרעננים אוטומטית • $dataSourceLabel",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = PlanetTextMuted,
            textAlign = TextAlign.Center
        )
        Text(
            text = "פלאנט ראשון לציון • מערכת ניטור אולמות ועומסי קהל",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = PlanetTextMuted.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}
