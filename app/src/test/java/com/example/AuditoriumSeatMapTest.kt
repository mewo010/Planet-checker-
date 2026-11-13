package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.model.AvailabilitySource
import com.example.data.model.CrowdLevel
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.model.Seat
import com.example.data.model.SeatAvailabilityStatus
import com.example.data.model.SeatFetchState
import com.example.data.model.SeatLayoutMetadata
import com.example.data.model.SeatPosition
import com.example.data.model.mergeScreeningAvailability
import com.example.data.model.summarizeSeats
import com.example.data.repository.CinemaRepository
import com.example.data.seats.SeatStatusMapper
import com.example.data.seats.SeatStatusMappingConfig
import com.example.data.seats.SeatStatusParser
import com.example.ui.seatmap.AuditoriumLayoutMode
import com.example.ui.seatmap.AuditoriumSeatMapBuilder
import com.example.ui.seatmap.AuditoriumSeatMapContent
import com.example.ui.seatmap.SeatDataFreshnessState
import com.example.ui.seatmap.SeatVisualStatus
import com.example.ui.theme.PlanetCheckerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditoriumSeatMapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val verifiedTestConfig = SeatStatusMappingConfig.fromRules(
        SeatStatusMappingConfig.exactFieldRule("avail", "st", "1", SeatAvailabilityStatus.AVAILABLE),
        SeatStatusMappingConfig.exactFieldRule("occ", "st", "2", SeatAvailabilityStatus.OCCUPIED),
        SeatStatusMappingConfig.exactFieldRule("res", "st", "3", SeatAvailabilityStatus.RESERVED),
        SeatStatusMappingConfig.exactFieldRule("blk", "st", "4", SeatAvailabilityStatus.BLOCKED),
        SeatStatusMappingConfig.exactFieldRule("unavail", "st", "5", SeatAvailabilityStatus.UNAVAILABLE),
        isVerifiedAgainstWebsite = true
    )

    private fun baseScreening(
        id: String = "s_hall_7",
        roomNumber: Int = 7,
        roomType: RoomType = RoomType.TWO_D,
        presentationId: String? = "359718",
        seatFetchState: SeatFetchState = SeatFetchState.SUCCESS
    ): Screening = Screening(
        id = id,
        title = "סרט בדיקה",
        room = "אולם $roomNumber",
        roomNumber = roomNumber,
        roomType = roomType,
        startStr = "19:30",
        endStr = "21:45",
        startMin = 1170,
        endMin = 1305,
        entryMin = 1150,
        capacity = CinemaRepository.HALL_CAPACITIES[roomNumber],
        availabilityRatio = 0.5f,
        occupancyPercentage = 50,
        estimatedPeople = 50,
        status = ScreeningStatus.LIVE,
        crowdLevel = CrowdLevel.MEDIUM,
        presentationId = presentationId,
        seatFetchState = seatFetchState
    )

    @Test
    fun `spatial coordinates preserve row front-to-back and left-to-right seat positions`() {
        val seats = listOf(
            Seat("1", "1", "אולם 7", "0", "r1", "1", "10", SeatPosition(100.0, 50.0), mapOf("st" to "1"), SeatAvailabilityStatus.AVAILABLE),
            Seat("2", "1", "אולם 7", "0", "r1", "1", "11", SeatPosition(200.0, 50.0), mapOf("st" to "2"), SeatAvailabilityStatus.OCCUPIED),
            Seat("3", "1", "אולם 7", "0", "r2", "2", "10", SeatPosition(100.0, 150.0), mapOf("st" to "3"), SeatAvailabilityStatus.RESERVED),
            Seat("4", "1", "אולם 7", "0", "r2", "2", "11", SeatPosition(200.0, 150.0), mapOf("st" to "4"), SeatAvailabilityStatus.BLOCKED)
        )
        val summary = summarizeSeats(
            seats = seats,
            presentationId = "359718",
            layoutMetadata = SeatLayoutMetadata(seatRadius = 20.0, width = 400.0, height = 300.0),
            hasUnverifiedStatuses = false,
            fetchedAtEpochMs = 1_700_000_000_000L
        )
        val screening = baseScreening().copy(seatSummary = summary, lastSeatUpdateEpochMs = summary.fetchedAtEpochMs)

        val model = AuditoriumSeatMapBuilder.build(screening, nowEpochMs = 1_700_000_010_000L)

        assertEquals(AuditoriumLayoutMode.SPATIAL_COORDINATES, model.layoutMode)
        assertEquals(2, model.rowLabels.size)
        assertEquals("1", model.rowLabels[0].label)
        assertEquals("2", model.rowLabels[1].label)
        assertTrue(model.rowLabels[0].normalizedY < model.rowLabels[1].normalizedY)

        val r1s10 = model.seats.first { it.seatKey == "1" }
        val r1s11 = model.seats.first { it.seatKey == "2" }
        val r2s10 = model.seats.first { it.seatKey == "3" }
        assertTrue(r1s10.normalizedX < r1s11.normalizedX)
        assertTrue(r1s10.normalizedY < r2s10.normalizedY)
        assertEquals(SeatVisualStatus.AVAILABLE, r1s10.visualStatus)
        assertEquals(SeatVisualStatus.RESERVED_OR_BOOKED, r1s11.visualStatus)
        assertEquals(SeatVisualStatus.RESERVED_OR_BOOKED, r2s10.visualStatus)
        assertEquals(SeatVisualStatus.BLOCKED_OR_UNAVAILABLE, model.seats.first { it.seatKey == "4" }.visualStatus)
    }

    @Test
    fun `missing spatial coordinates fall back to natural row and seat grid ordering`() {
        val seats = listOf(
            Seat("s10_2", "1", "אולם 21", "0", "r10", "10", "2", null, mapOf("st" to "1"), SeatAvailabilityStatus.AVAILABLE),
            Seat("s2_10", "1", "אולם 21", "0", "r2", "2", "10", null, mapOf("st" to "2"), SeatAvailabilityStatus.OCCUPIED),
            Seat("s2_2", "1", "אולם 21", "0", "r2", "2", "2", null, mapOf("st" to "1"), SeatAvailabilityStatus.AVAILABLE)
        )
        val summary = summarizeSeats(
            seats = seats,
            presentationId = "9001",
            hasUnverifiedStatuses = false,
            fetchedAtEpochMs = 1_700_000_000_000L
        )
        val screening = baseScreening(roomNumber = 21, roomType = RoomType.VIP).copy(seatSummary = summary)

        val model = AuditoriumSeatMapBuilder.build(screening, nowEpochMs = 1_700_000_005_000L)

        assertEquals(AuditoriumLayoutMode.ROW_GRID_FALLBACK, model.layoutMode)
        // Row "2" must appear before Row "10" (natural numeric sort)
        assertEquals(listOf("2", "10"), model.rowLabels.map { it.label })
        val row2Seat2 = model.seats.first { it.seatKey == "s2_2" }
        val row2Seat10 = model.seats.first { it.seatKey == "s2_10" }
        assertTrue(row2Seat2.normalizedX < row2Seat10.normalizedX)
    }

    @Test
    fun `unknown statuses are never classified as available and trigger unverified freshness`() {
        val rawJson = """
            {
              "rd": { "seatRadius": 30, "width": 800, "height": 600 },
              "S": {
                "1": {
                  "n": "אולם 7",
                  "G": {
                    "0": {
                      "R": {
                        "1": {
                          "n": "1",
                          "S": {
                            "1": { "n": "1", "tg": 1, "rd": { "cx": 100, "cy": 100 } },
                            "2": { "n": "2", "hc": 1, "rd": { "cx": 160, "cy": 100 } }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val summary = SeatStatusParser(SeatStatusMapper(SeatStatusMappingConfig.UNVERIFIED_DEFAULT))
            .parse(rawJson, presentationId = "359718", fetchedAtEpochMs = 1_700_000_000_000L)
            .getOrThrow()

        val screening = baseScreening().copy(seatSummary = summary, availabilitySource = AvailabilitySource.LIVE_SEAT_MAP)
        val model = AuditoriumSeatMapBuilder.build(screening, nowEpochMs = 1_700_000_005_000L)

        assertEquals(0, model.counts.available)
        assertEquals(2, model.counts.unknown)
        assertTrue(model.hasUnverifiedStatuses)
        assertEquals(SeatDataFreshnessState.UNVERIFIED, model.freshnessState)
        assertTrue(model.seats.all { it.visualStatus == SeatVisualStatus.UNKNOWN && !it.isVerified })
    }

    @Test
    fun `broken seats are never treated as occupied and avoid double subtraction when excluded by live map`() {
        // Hall 9 has 0 broken seats; Hall 7 has 2 broken seats (original capacity 311, usable = 309)
        // Build a verified live map for Hall 7 where 2 seats are already BLOCKED (excluded by live map)
        val seats = (1..309).map { idx ->
            Seat(
                seatKey = idx.toString(),
                roomKey = "1",
                roomName = "אולם 7",
                groupKey = "0",
                rowKey = ((idx - 1) / 20 + 1).toString(),
                rowNumber = ((idx - 1) / 20 + 1).toString(),
                seatNumber = ((idx - 1) % 20 + 1).toString(),
                position = SeatPosition(((idx - 1) % 20) * 40.0, ((idx - 1) / 20) * 45.0),
                rawFields = mapOf("st" to if (idx <= 50) "2" else "1"),
                status = if (idx <= 50) SeatAvailabilityStatus.OCCUPIED else SeatAvailabilityStatus.AVAILABLE
            )
        } + listOf(
            Seat("310", "1", "אולם 7", "0", "16", "16", "10", SeatPosition(400.0, 720.0), mapOf("st" to "4"), SeatAvailabilityStatus.BLOCKED),
            Seat("311", "1", "אולם 7", "0", "16", "16", "11", SeatPosition(440.0, 720.0), mapOf("st" to "4"), SeatAvailabilityStatus.BLOCKED)
        )
        val summary = summarizeSeats(
            seats = seats,
            presentationId = "359718",
            hasUnverifiedStatuses = false,
            fetchedAtEpochMs = 1_700_000_000_000L
        )
        val screening = mergeScreeningAvailability(
            screenings = listOf(baseScreening(roomNumber = 7)),
            seatSummariesByKey = mapOf("359718" to summary)
        ).first()

        val model = AuditoriumSeatMapBuilder.build(
            screening = screening,
            nowEpochMs = 1_700_000_005_000L,
            explicitBrokenSeatKeys = setOf("310", "311")
        )

        // Broken seats must appear in BROKEN bucket, NEVER in reservedOrBooked (occupied)
        assertEquals(50, model.counts.reservedOrBooked)
        assertEquals(259, model.counts.available)
        assertEquals(2, model.counts.broken)
        // Usable capacity must be 309 (311 - 2 blocked), NOT 307 (double subtraction)
        assertTrue(model.brokenReconciliation.excludedByLiveMap)
        assertEquals(2, model.brokenReconciliation.manualBrokenSeats)
        assertEquals(309, model.brokenReconciliation.usableCapacity)

        // Hall 20 (4DX) has null broken seats (unknown)
        val hall20 = baseScreening(id = "s_hall_20", roomNumber = 20, roomType = RoomType.FOUR_DX)
        val model20 = AuditoriumSeatMapBuilder.build(hall20, nowEpochMs = 1_700_000_005_000L)
        assertFalse(model20.brokenReconciliation.isBrokenSeatsKnown)
        assertNull(model20.brokenReconciliation.manualBrokenSeats)
        assertNull(model20.brokenReconciliation.usableCapacity)
    }

    @Test
    fun `pii keys are stripped and missing or stale seat data is reported honestly`() {
        val sanitized = AuditoriumSeatMapBuilder.sanitizeTechnicalRawFields(
            mapOf(
                "tg" to "1",
                "hc" to "0",
                "customerName" to "Jane Doe",
                "email" to "jane@example.com",
                "phone" to "0500000000",
                "orderId" to "ORD123"
            )
        )
        assertEquals(mapOf("tg" to "1", "hc" to "0"), sanitized)

        val missingPid = baseScreening(presentationId = null, seatFetchState = SeatFetchState.MISSING_PRESENTATION_ID)
        val missingModel = AuditoriumSeatMapBuilder.build(missingPid)
        assertEquals(AuditoriumLayoutMode.UNAVAILABLE, missingModel.layoutMode)
        assertEquals(SeatDataFreshnessState.UNAVAILABLE, missingModel.freshnessState)
        assertTrue(missingModel.unavailableReason!!.contains("presentationId"))

        val staleSummary = summarizeSeats(
            seats = listOf(
                Seat("1", "1", "אולם 5", "0", "1", "1", "1", SeatPosition(10.0, 10.0), mapOf("st" to "1"), SeatAvailabilityStatus.AVAILABLE)
            ),
            hasUnverifiedStatuses = false,
            fetchedAtEpochMs = 1_700_000_000_000L
        )
        val staleScreening = baseScreening(roomNumber = 5, roomType = RoomType.IMAX).copy(seatSummary = staleSummary)
        val staleModel = AuditoriumSeatMapBuilder.build(
            screening = staleScreening,
            nowEpochMs = 1_700_000_000_000L + (20 * 60 * 1000L) // 20 min later
        )
        assertEquals(SeatDataFreshnessState.STALE, staleModel.freshnessState)
    }

    @Test
    fun `interactive seat map UI supports seat selection, details without fabricated booking time, switching auditoriums, and live refresh`() {
        val summaryVip = summarizeSeats(
            seats = listOf(
                Seat("1", "1", "אולם 21", "0", "r1", "1", "1", SeatPosition(50.0, 50.0), mapOf("st" to "1"), SeatAvailabilityStatus.AVAILABLE),
                Seat("2", "1", "אולם 21", "0", "r1", "1", "2", SeatPosition(100.0, 50.0), mapOf("st" to "2"), SeatAvailabilityStatus.OCCUPIED)
            ),
            presentationId = "vip_21",
            hasUnverifiedStatuses = false,
            fetchedAtEpochMs = 1_700_000_000_000L
        )
        val vipScreening = baseScreening(id = "vip_21", roomNumber = 21, roomType = RoomType.VIP, presentationId = "vip_21")
            .copy(seatSummary = summaryVip, lastSeatUpdateEpochMs = summaryVip.fetchedAtEpochMs)

        val imaxScreeningMissing = baseScreening(
            id = "imax_5",
            roomNumber = 5,
            roomType = RoomType.IMAX,
            presentationId = null,
            seatFetchState = SeatFetchState.MISSING_PRESENTATION_ID
        )

        var screeningsState by mutableStateOf(listOf(vipScreening, imaxScreeningMissing))
        var activeId by mutableStateOf(vipScreening.id)
        var selectedSeatId by mutableStateOf<String?>(null)
        var refreshClickCount = 0

        composeTestRule.setContent {
            PlanetCheckerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val active = screeningsState.first { it.id == activeId }
                    AuditoriumSeatMapContent(
                        activeScreening = active,
                        selectorScreenings = screeningsState,
                        selectedSeatId = selectedSeatId,
                        isRefreshingSeatMap = false,
                        onSelectScreening = {
                            activeId = it.id
                            selectedSeatId = null
                        },
                        onSelectSeat = { selectedSeatId = it.id },
                        onClearSeatSelection = { selectedSeatId = null },
                        onRefreshSeatMap = { refreshClickCount++ },
                        onDismiss = {},
                        nowEpochMs = 1_700_000_010_000L
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Opening must NOT trigger network refresh automatically
        assertEquals(0, refreshClickCount)

        composeTestRule.onNodeWithTag("auditorium_seat_map_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("seat_map_legend").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("seat_map_screen_banner").performScrollTo().assertIsDisplayed()

        // Tap occupied seat #2 ("1_0_r1_2") -> details panel displays verified info without fabricated booking time
        composeTestRule.onNodeWithTag("seat_1_0_r1_2").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("seat_details_panel").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("שורה 1 • מושב 2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("תפוס").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("מאומת מול מפת האתר").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("לא מסופק ע\"י ה-API הרשמי").performScrollTo().assertIsDisplayed()

        // Live state update (e.g. background refresh changes seat 1 from AVAILABLE to RESERVED) updates map smoothly
        val updatedVipSummary = summarizeSeats(
            seats = listOf(
                Seat("1", "1", "אולם 21", "0", "r1", "1", "1", SeatPosition(50.0, 50.0), mapOf("st" to "3"), SeatAvailabilityStatus.RESERVED),
                Seat("2", "1", "אולם 21", "0", "r1", "1", "2", SeatPosition(100.0, 50.0), mapOf("st" to "2"), SeatAvailabilityStatus.OCCUPIED)
            ),
            presentationId = "vip_21",
            hasUnverifiedStatuses = false,
            fetchedAtEpochMs = 1_700_000_060_000L
        )
        screeningsState = listOf(
            vipScreening.copy(seatSummary = updatedVipSummary, lastSeatUpdateEpochMs = updatedVipSummary.fetchedAtEpochMs),
            imaxScreeningMissing
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("פנוי (0)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("תפוס / שמור (2)").performScrollTo().assertIsDisplayed()

        // Test zoom controls do not crash and reset works
        composeTestRule.onNodeWithTag("seat_map_zoom_in").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("seat_map_zoom_out").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("seat_map_zoom_reset").performScrollTo().performClick()

        // Broken seats reconciliation card is displayed clearly
        composeTestRule.onNodeWithTag("seat_map_broken_seats_card").performScrollTo().assertIsDisplayed()

        // Explicit refresh button increments refreshClickCount once
        composeTestRule.onNodeWithTag("seat_map_refresh_button").performClick()
        assertEquals(1, refreshClickCount)

        // Scroll selector row into vertical viewport first, then scroll horizontally and select Hall 5
        composeTestRule.onNodeWithTag("seat_map_auditorium_selector").performScrollTo()
        composeTestRule.onNodeWithTag("seat_selector_chip_imax_5").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("imax_5", activeId)
        assertEquals(1, refreshClickCount)
        composeTestRule.onNodeWithTag("seat_map_unavailable_state").performScrollTo().assertIsDisplayed()
    }
}
