package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CinemaTab
import com.example.data.model.CinemaUiState
import com.example.data.repository.CinemaRepository
import com.example.ui.components.CinemaHeader
import com.example.ui.components.FilterControlBar
import com.example.ui.components.HallsStatusGrid
import com.example.ui.components.KpiCardsSection
import com.example.ui.components.MovieCarousel
import com.example.ui.components.ScreeningDetailSheet
import com.example.ui.components.ScreeningTicketCard
import com.example.ui.components.TimelineView
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetNavy
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextSecondary
import com.example.ui.viewmodel.CinemaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaDashboardScreen(
    viewModel: CinemaViewModel,
    uiState: CinemaUiState,
    cinemaRepository: CinemaRepository = remember { CinemaRepository() },
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = PlanetDarkBg,
            topBar = {
                CinemaHeader(
                    currentTime = uiState.currentIsraelTime,
                    dataSourceLabel = uiState.dataSourceLabel,
                    isLive = uiState.isLiveDataSource,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.loadData(isUserInitiated = true) }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = PlanetRose,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("scroll_to_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "גלול לראש הדף",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PlanetRose,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "טוען לוח הקרנות ונתוני אולמות…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PlanetTextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Section: Tab Selection
                        item(key = "tab_navigation") {
                            CinemaTabSelector(
                                activeTab = uiState.activeTab,
                                onTabSelected = { tab ->
                                    viewModel.setActiveTab(tab)
                                    coroutineScope.launch { listState.scrollToItem(0) }
                                }
                            )
                        }

                        // Section: KPIs (Always visible for fast operational glance)
                        item(key = "kpi_section") {
                            KpiCardsSection(
                                kpiStats = uiState.kpiStats,
                                cinemaRepository = cinemaRepository
                            )
                        }

                        when (uiState.activeTab) {
                            CinemaTab.SCHEDULE -> {
                                // Movie Strip Carousel
                                if (uiState.movieSummaries.isNotEmpty()) {
                                    item(key = "movie_carousel") {
                                        MovieCarousel(
                                            movies = uiState.movieSummaries,
                                            selectedMovieTitle = uiState.selectedMovieTitle,
                                            onMovieClick = { viewModel.toggleMovieSelection(it) }
                                        )
                                    }
                                }

                                // Search & Filter Controls
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

                                // Active Filter Banner (if movie or query is selected)
                                if (uiState.selectedMovieTitle != null || uiState.searchQuery.isNotEmpty()) {
                                    item(key = "active_filter_banner") {
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

                                // Screening Ticket Cards or Empty View
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
                                            onClick = { viewModel.selectScreening(screening) }
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
                                        onScreeningClick = { viewModel.selectScreening(it) }
                                    )
                                }
                            }
                        }

                        // Footer Note
                        item(key = "footer") {
                            CinemaFooter(dataSourceLabel = uiState.dataSourceLabel)
                        }
                    }
                }

                // Bottom Sheet Modal
                uiState.selectedScreening?.let { screening ->
                    ScreeningDetailSheet(
                        screening = screening,
                        currentCinemaMinutes = uiState.currentCinemaMinutes,
                        cinemaRepository = cinemaRepository,
                        sheetState = sheetState,
                        onDismiss = { viewModel.selectScreening(null) },
                        onFilterByMovie = { movieTitle ->
                            viewModel.setActiveTab(CinemaTab.SCHEDULE)
                            viewModel.toggleMovieSelection(movieTitle)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CinemaTabSelector(
    activeTab: CinemaTab,
    onTabSelected: (CinemaTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CinemaTab.values().forEach { tab ->
            val isSelected = activeTab == tab
            val bg = if (isSelected) PlanetRose else Color.Transparent
            val textColor = if (isSelected) Color.White else PlanetTextSecondary

            val icon = when (tab) {
                CinemaTab.SCHEDULE -> Icons.Filled.CalendarMonth
                CinemaTab.TIMELINE -> Icons.Filled.Timeline
                CinemaTab.HALLS_MAP -> Icons.Filled.GridOn
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterBanner(
    title: String,
    resultCount: Int,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PlanetRose.copy(alpha = 0.12f))
            .border(1.dp, PlanetRose.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "מסונן לפי: $title",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "($resultCount תוצאות)",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlanetRoseLight
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onClear)
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
}

@Composable
private fun EmptyScreeningsState(
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MeetingRoom,
                contentDescription = null,
                tint = PlanetTextMuted,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "אין הקרנות מתאימות כרגע",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = "נסה לשנות את הסינון, מילות החיפוש או לאפשר הצגת הקרנות שהסתיימו.",
                style = MaterialTheme.typography.bodySmall,
                color = PlanetTextMuted,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = onReset)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
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
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "הנתונים מתרעננים אוטומטית • מושכים מהנתונים של אפליקציית Planet Checker",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = PlanetTextMuted
        )
        Text(
            text = "פלאנט ראשון לציון • מערכת ניטור אולמות ועומסי קהל",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = PlanetTextMuted.copy(alpha = 0.7f)
        )
    }
}
