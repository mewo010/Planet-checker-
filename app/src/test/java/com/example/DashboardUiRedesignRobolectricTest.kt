package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.data.model.AvailabilitySource
import com.example.data.model.CinemaEvent
import com.example.data.model.CinemaTab
import com.example.data.model.CinemaUiState
import com.example.data.model.CrowdLevel
import com.example.data.model.EventKind
import com.example.data.model.KpiStats
import com.example.data.model.OccupancyResult
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.repository.CinemaRepository
import com.example.ui.components.AudienceBreakdownDialog
import com.example.ui.components.CinemaHeader
import com.example.ui.components.CinemaNavigationBar
import com.example.ui.components.DailyStatsView
import com.example.ui.components.HallsStatusGrid
import com.example.ui.components.KpiCardsSection
import com.example.ui.components.ScreeningTicketCard
import com.example.ui.components.TimelineView
import com.example.ui.theme.PlanetCheckerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardUiRedesignRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val repo = CinemaRepository()

    private fun sampleScreenings(): List<Screening> = listOf(
        Screening(
            id = "s_live_5",
            title = "אוואטר: אש ואפר — גרסה מורחבת באולם איימקס",
            room = "אולם 5",
            roomNumber = 5,
            roomType = RoomType.IMAX,
            startStr = "19:30",
            endStr = "22:15",
            startMin = 1170,
            endMin = 1335,
            entryMin = 1150,
            capacity = 400,
            availabilityRatio = 0.5f,
            occupancyPercentage = 50,
            estimatedPeople = 199,
            status = ScreeningStatus.LIVE,
            crowdLevel = CrowdLevel.MEDIUM,
            presentationId = "5001",
            availabilitySource = AvailabilitySource.HOURLY_FEED
        ),
        Screening(
            id = "s_up_7",
            title = "Mission Impossible",
            room = "אולם 7",
            roomNumber = 7,
            roomType = RoomType.TWO_D,
            startStr = "20:20",
            endStr = "22:45",
            startMin = 1220,
            endMin = 1365,
            entryMin = 1200,
            capacity = 311,
            availabilityRatio = 0.7f,
            occupancyPercentage = 30,
            estimatedPeople = 93,
            status = ScreeningStatus.UPCOMING,
            crowdLevel = CrowdLevel.QUIET,
            presentationId = "7002",
            availabilitySource = AvailabilitySource.SCHEDULE_FEED
        )
    )

    private fun sampleUiState(): CinemaUiState {
        val screenings = sampleScreenings()
        val occ = OccupancyResult(
            occupiedSeats = 199,
            verifiedOccupiedSeats = 0,
            estimatedOccupiedSeats = 199,
            activeScreenings = 1,
            countedScreenings = 1,
            isComplete = true
        )
        val nextEvent = CinemaEvent(
            timeMin = 1200,
            kind = EventKind.IN,
            screening = screenings[1],
            isUrgent = true,
            minutesUntil = 10
        )
        return CinemaUiState(
            isLoading = false,
            isLiveDataSource = true,
            dataSourceLabel = "נתונים חיים • 12/11/2026",
            currentCinemaMinutes = 1190,
            lastSuccessfulUpdateLabel = "19:50",
            dataFreshnessLabel = "עדכני (ברגע זה)",
            allScreenings = screenings,
            filteredScreenings = screenings,
            timelineEvents = listOf(nextEvent),
            kpiStats = KpiStats(
                peopleInside = 199,
                activeHallsCount = 1,
                activeKnownHallsCount = 1,
                occupancyResult = occ,
                out30MinPeople = 40,
                out30MinHalls = 1,
                in30MinPeople = 93,
                in30MinHalls = 1,
                nextEvent = nextEvent
            )
        )
    }

    @Test
    fun `kpi cards display live audience, entries, exits, 30-min forecast, and next event`() {
        val state = sampleUiState()
        var breakdownOpened = false

        composeTestRule.setContent {
            PlanetCheckerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        KpiCardsSection(
                            kpiStats = state.kpiStats,
                            cinemaRepository = repo,
                            isLiveDataSource = true,
                            lastUpdatedLabel = state.lastSuccessfulUpdateLabel,
                            dataFreshnessLabel = state.dataFreshnessLabel,
                            onInsideAudienceClick = { breakdownOpened = true }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("kpi_inside").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("199").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("kpi_arriving").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("+93").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("kpi_leaving").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("-40").performScrollTo().assertIsDisplayed()

        // Forecast = 199 + 93 - 40 = 252
        composeTestRule.onNodeWithTag("kpi_forecast_30m").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("252").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("+53 מהמצב כעת").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("kpi_next_event").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("kpi_inside").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(breakdownOpened)
    }

    @Test
    fun `cinema header applies status bar top inset and enforces RTL without duplicate clock`() {
        var hostView: android.view.View? = null

        composeTestRule.setContent {
            hostView = LocalView.current
            PlanetCheckerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CinemaHeader(
                            isLive = true,
                            isRefreshing = false,
                            seatMapUpdateInProgress = false,
                            dataFreshnessLabel = "עדכני (ברגע זה)",
                            lastSuccessfulUpdateLabel = "19:50",
                            onRefresh = {}
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        val statusBarHeightPx = 96
        composeTestRule.runOnUiThread {
            val view = hostView ?: return@runOnUiThread
            val insets = WindowInsetsCompat.Builder()
                .setInsets(
                    WindowInsetsCompat.Type.statusBars(),
                    Insets.of(0, statusBarHeightPx, 0, 0)
                )
                .build()
            ViewCompat.dispatchApplyWindowInsets(view.rootView, insets)
        }
        composeTestRule.waitForIdle()

        val headerNode = composeTestRule.onNodeWithTag("cinema_header").fetchSemanticsNode()
        val refreshNode = composeTestRule.onNodeWithTag("refresh_button").fetchSemanticsNode()

        // Refresh button inside header content MUST sit below the 96px status bar inset!
        assertTrue(
            "Refresh button top (${refreshNode.boundsInRoot.top}) must be >= status bar height ($statusBarHeightPx)",
            refreshNode.boundsInRoot.top >= statusBarHeightPx.toFloat()
        )
        assertEquals(LayoutDirection.Rtl, headerNode.layoutInfo.layoutDirection)
    }

    @Test
    fun `narrow 320dp dashboard KPIs and bottom navigation switch tabs cleanly`() {
        val state = sampleUiState()
        var activeTab by mutableStateOf(CinemaTab.SCHEDULE)

        composeTestRule.setContent {
            PlanetCheckerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(modifier = Modifier.width(320.dp).fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            KpiCardsSection(
                                kpiStats = state.kpiStats,
                                cinemaRepository = repo,
                                isLiveDataSource = true,
                                lastUpdatedLabel = state.lastSuccessfulUpdateLabel,
                                dataFreshnessLabel = state.dataFreshnessLabel
                            )
                        }
                        CinemaNavigationBar(
                            activeTab = activeTab,
                            onTabSelected = { activeTab = it }
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("kpi_forecast_30m").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_HALLS_MAP").performClick()
        composeTestRule.waitForIdle()
        assertEquals(CinemaTab.HALLS_MAP, activeTab)
        composeTestRule.onNodeWithTag("tab_DAILY_STATS").performClick()
        composeTestRule.waitForIdle()
        assertEquals(CinemaTab.DAILY_STATS, activeTab)
    }

    @Test
    fun `screening cards, timeline, halls map, breakdown dialog, and expandable diagnostics render cleanly`() {
        val state = sampleUiState()
        var currentTab by mutableStateOf(CinemaTab.SCHEDULE)
        var showDialog by mutableStateOf(false)

        composeTestRule.setContent {
            PlanetCheckerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (currentTab) {
                            CinemaTab.SCHEDULE -> {
                                ScreeningTicketCard(
                                    screening = state.allScreenings.first(),
                                    currentCinemaMinutes = state.currentCinemaMinutes,
                                    cinemaRepository = repo,
                                    onClick = {}
                                )
                            }
                            CinemaTab.TIMELINE -> {
                                TimelineView(
                                    events = state.timelineEvents,
                                    currentCinemaMinutes = state.currentCinemaMinutes,
                                    cinemaRepository = repo,
                                    onScreeningClick = {}
                                )
                            }
                            CinemaTab.HALLS_MAP -> {
                                HallsStatusGrid(
                                    allScreenings = state.allScreenings,
                                    currentCinemaMinutes = state.currentCinemaMinutes,
                                    cinemaRepository = repo,
                                    onScreeningClick = {}
                                )
                            }
                            CinemaTab.DAILY_STATS -> {
                                DailyStatsView(
                                    dailyStats = null,
                                    availableDates = listOf("2026-11-12"),
                                    selectedDate = "2026-11-12",
                                    lastSuccessfulUpdateLabel = "19:50",
                                    dataFreshnessLabel = "עדכני",
                                    isLiveDataSource = true,
                                    hasUnverifiedSeatStatuses = false,
                                    occupancyResult = state.kpiStats.occupancyResult,
                                    onSelectDate = {}
                                )
                            }
                        }
                    }

                    if (showDialog) {
                        AudienceBreakdownDialog(
                            allScreenings = state.allScreenings,
                            totalAudience = 199,
                            isLiveDataSource = true,
                            occupancyResult = state.kpiStats.occupancyResult,
                            lastUpdatedLabel = "19:50",
                            onDismiss = { showDialog = false }
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screening_card_s_live_5").performScrollTo().assertIsDisplayed()

        // Switch to Timeline
        currentTab = CinemaTab.TIMELINE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timeline_event_s_up_7").performScrollTo().assertIsDisplayed()

        // Switch to Halls Map (Halls 1..24 including Hall 20 unknown)
        currentTab = CinemaTab.HALLS_MAP
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hall_card_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hall_card_20").performScrollTo().assertIsDisplayed()

        // Switch to Daily Stats & expand 24-hall audit table
        currentTab = CinemaTab.DAILY_STATS
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("daily_stats_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hall_capacity_audit_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_hall_capacity_table_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("אולם 20").performScrollTo().assertIsDisplayed()

        // Open Audience Breakdown Dialog
        showDialog = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("audience_breakdown_dialog").assertIsDisplayed()
    }
}
