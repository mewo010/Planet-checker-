package com.example

import com.example.data.model.AvailabilitySource
import com.example.data.model.FirstScheduleResponse
import com.example.data.model.HourlyScheduleResponse
import com.example.data.model.RawScreening
import com.example.data.model.ScreeningStatus
import com.example.data.model.SeatAvailabilityStatus
import com.example.data.model.SeatFetchState
import com.example.data.model.calculateCinemaOccupancy
import com.example.data.model.currentlyOccupiedSeats
import com.example.data.model.deduplicateScreenings
import com.example.data.model.mergeScreeningAvailability
import com.example.data.remote.PlanetSeatsApi
import com.example.data.repository.CinemaRepository
import com.example.data.repository.SeatAvailabilityRepository
import com.example.data.seats.SeatStatusMapper
import com.example.data.seats.SeatStatusMappingConfig
import com.example.data.seats.SeatStatusParser
import com.example.util.CinemaDiagnosticsLogger
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OccupancyCalculationTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val repo = CinemaRepository()

    private val verifiedConfig = SeatStatusMappingConfig.fromRules(
        SeatStatusMappingConfig.exactFieldRule("avail", "st", "1", SeatAvailabilityStatus.AVAILABLE),
        SeatStatusMappingConfig.exactFieldRule("occ", "st", "2", SeatAvailabilityStatus.OCCUPIED),
        SeatStatusMappingConfig.exactFieldRule("res", "st", "3", SeatAvailabilityStatus.RESERVED),
        SeatStatusMappingConfig.exactFieldRule("blk", "st", "4", SeatAvailabilityStatus.BLOCKED),
        SeatStatusMappingConfig.exactFieldRule("unavail", "st", "5", SeatAvailabilityStatus.UNAVAILABLE),
        isVerifiedAgainstWebsite = true
    )

    @After
    fun cleanupLogger() {
        CinemaDiagnosticsLogger.customPrinter = null
    }

    @Test
    fun `empty screening list produces clean complete zero occupancy`() {
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)
        val result = calculateCinemaOccupancy(emptyList(), now)

        assertEquals(0, result.occupiedSeats)
        assertEquals(0, result.activeScreenings)
        assertEquals(0, result.countedScreenings)
        assertEquals(0, result.missingScreenings)
        assertEquals(0, result.failedScreenings)
        assertFalse(result.unknownOccupancy)
        assertTrue(result.isComplete)
        assertTrue(result.screeningDiagnostics.isEmpty())
    }

    @Test
    fun `multiple active shows sum equals exact counted screenings total and excludes upcoming and ended`() {
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 19, 30, 0, 0, jerusalem) // 1170 min
        val raw = listOf(
            // Ended at 17:00 + 15 = 17:15
            RawScreening("Ended Film", "אולם 1", "15:00", "17:00", 0.1f, presentationId = "101"),
            // Active 1: Hall 5 (400 cap, reserved 2), 0.25 avail -> 298 occupied
            RawScreening("IMAX Hit", "אולם 5", "18:30", "21:00", 0.25f, presentationId = "102"),
            // Active 2: Hall 21 VIP (54 cap, reserved 0), 0.50 avail -> 27 occupied
            RawScreening("VIP Drama", "אולם 21 (VIP)", "19:00", "21:00", 0.50f, presentationId = "103"),
            // Active 3: Hall 20 4DX (124 cap, reserved 0), 0.0 avail -> 124 occupied
            RawScreening("4DX Action", "אולם 20", "19:15", "21:15", 0.0f, presentationId = "104"),
            // Upcoming: starts 21:00
            RawScreening("Late Film", "אולם 7", "21:00", "23:00", 0.1f, presentationId = "105")
        )

        val screenings = repo.prepareScreenings(raw, nowMinutes = 1170, defaultDate = date, nowZoned = now)
        val result = calculateCinemaOccupancy(screenings, now)

        assertEquals(3, result.activeScreenings)
        assertEquals(3, result.countedScreenings)
        assertEquals(0, result.missingScreenings)
        assertEquals(0, result.failedScreenings)
        assertEquals(1, result.unknownBrokenSeatsScreenings)
        assertFalse(result.unknownOccupancy)
        assertTrue(result.isComplete)

        val sumOfIncluded = result.screeningDiagnostics
            .filter { it.includedInOccupancyTotal }
            .sumOf { it.countedOccupiedSeats ?: 0 }
        assertEquals(298 + 27 + 124, result.occupiedSeats)
        assertEquals(result.occupiedSeats, sumOfIncluded)
        assertEquals(result.occupiedSeats, currentlyOccupiedSeats(screenings, now))
    }

    @Test
    fun `missing seat data and unverified status codes are tracked instead of hidden as zero`() {
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 19, 30, 0, 0, jerusalem)
        val raw = listOf(
            RawScreening("No Ratio No PID", "אולם 1", "19:00", "21:00", null),
            RawScreening("Unverified Live Map", "אולם 7", "19:00", "21:00", null, presentationId = "701")
        )

        val unverifiedJson = """
            {"S":{"1":{"n":"אולם 7","G":{"0":{"R":{"1":{"n":"1","S":{
              "1":{"n":"1","tg":1},
              "2":{"n":"2","hc":1}
            }}}}}}}}
        """.trimIndent()
        val unverifiedSummary = SeatStatusParser(SeatStatusMapper(SeatStatusMappingConfig.UNVERIFIED_DEFAULT))
            .parse(unverifiedJson, presentationId = "701")
            .getOrThrow()

        val screenings = repo.prepareScreenings(raw, nowMinutes = 1170, defaultDate = date, nowZoned = now)
            .let { mergeScreeningAvailability(it, seatSummariesByKey = mapOf("701" to unverifiedSummary)) }

        val result = calculateCinemaOccupancy(screenings, now)

        assertEquals(2, result.activeScreenings)
        assertEquals(0, result.countedScreenings)
        assertEquals(2, result.missingScreenings)
        assertEquals(0, result.failedScreenings)
        assertEquals(0, result.occupiedSeats)
        assertTrue(result.unknownOccupancy)
        assertFalse(result.isComplete)
        assertTrue(result.screeningDiagnostics.all { !it.includedInOccupancyTotal })
    }

    @Test
    fun `partial and full API failures surface failedScreenings explicitly via repository enrichment`() = runTest {
        val fakeApi = object : PlanetSeatsApi {
            override suspend fun getSeatsStatus(
                presentationId: String,
                venueTypeId: Int,
                isReserved: Int
            ): Response<ResponseBody> {
                return when (presentationId) {
                    "ok_1" -> Response.success(
                        """
                        {"S":{"1":{"n":"אולם 3","G":{"0":{"R":{"1":{"n":"1","S":{
                          "1":{"n":"1","st":2},
                          "2":{"n":"2","st":3},
                          "3":{"n":"3","st":4},
                          "4":{"n":"4","st":5},
                          "5":{"n":"5","st":1},
                          "6":{"n":"6","tg":1}
                        }}}}}}}}
                        """.trimIndent().toResponseBody("application/json".toMediaType())
                    )
                    "err_net" -> throw IOException("Connection reset")
                    "err_auth" -> Response.error(403, "Forbidden".toResponseBody("text/plain".toMediaType()))
                    else -> Response.success("{}".toResponseBody("application/json".toMediaType()))
                }
            }
        }

        val seatRepo = SeatAvailabilityRepository(
            api = fakeApi,
            parser = SeatStatusParser(SeatStatusMapper(verifiedConfig)),
            interRequestDelayMs = 0L
        )

        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)
        val raw = listOf(
            RawScreening("Working Live Map", "אולם 3", "19:30", "21:30", null, presentationId = "ok_1"),
            RawScreening("Network Failed Show", "אולם 5", "19:30", "21:30", null, presentationId = "err_net"),
            RawScreening("Auth Blocked Show", "אולם 8", "19:30", "21:30", null, presentationId = "err_auth"),
            RawScreening("Empty Body Show", "אולם 9", "19:30", "21:30", null, presentationId = "empty_1")
        )

        val initial = repo.prepareScreenings(raw, nowMinutes = 1200, defaultDate = date, nowZoned = now)
        val enriched = repo.enrichWithLiveSeatAvailability(initial, seatRepo)

        val netShow = enriched.first { it.presentationId == "err_net" }
        val authShow = enriched.first { it.presentationId == "err_auth" }
        val emptyShow = enriched.first { it.presentationId == "empty_1" }
        assertEquals(SeatFetchState.FAILED, netShow.seatFetchState)
        assertEquals(SeatFetchState.AUTH_REQUIRED, authShow.seatFetchState)
        assertEquals(SeatFetchState.MISSING_DATA, emptyShow.seatFetchState)

        val occupancy = calculateCinemaOccupancy(enriched, now)
        // ok_1: 1 occupied (st=2) + 1 reserved (st=3) = 2 viewers; blocked (st=4), unavail (st=5), unknown (tg=1), avail (st=1) NOT counted!
        assertEquals(2, occupancy.occupiedSeats)
        assertEquals(4, occupancy.activeScreenings)
        assertEquals(1, occupancy.countedScreenings)
        assertEquals(2, occupancy.failedScreenings)
        assertEquals(1, occupancy.missingScreenings)
        assertFalse(occupancy.unknownOccupancy)
        assertFalse(occupancy.isComplete)
    }

    @Test
    fun `duplicate screenings and duplicate seats are deduplicated and extra hourly shows are included without null overwrites`() {
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)

        // Base feed has a duplicate show in differing hall formats and an active show with valid ratio 0.50
        val base = FirstScheduleResponse(
            date = "2026-11-12",
            screenings = listOf(
                RawScreening("Dune 2", "אולם 7", "19:00", "21:30", 0.50f, presentationId = "p_dune"),
                RawScreening("Dune 2", "7", "19:0", "21:30", 0.50f, presentationId = "p_dune") // Duplicate in slightly different text
            )
        )
        // Hourly feed has null ratio for Dune (must NOT wipe out 0.50f!) PLUS an extra active screening in Hall 10 missing from base!
        val hourly = HourlyScheduleResponse(
            screenings = listOf(
                RawScreening("Dune 2", "אולם 7", "19:00", "21:30", null, presentationId = "p_dune"),
                RawScreening("Extra Late Hit", "אולם 10", "19:30", "21:45", 0.0f, presentationId = "p_extra")
            )
        )

        val (_, merged) = repo.mergeRawSchedules(
            baseResponse = base,
            hourlyResponse = hourly,
            defaultCinemaDate = date,
            nowMinutes = 1200,
            nowZoned = now
        )

        // Exactly 2 unique screenings (Dune deduplicated + Extra included)
        assertEquals(2, merged.size)
        val occupancy = calculateCinemaOccupancy(merged, now)
        // Hall 7 (round(311 * 0.5) - 2 = 154) + Hall 10 (217 - 3 = 214) = 368
        assertEquals(2, occupancy.activeScreenings)
        assertEquals(2, occupancy.countedScreenings)
        assertEquals(154 + 214, occupancy.occupiedSeats)

        // Also test deduplicateScreenings directly when one lacks presentationId initially
        val manualDupes = repo.prepareScreenings(
            listOf(
                RawScreening("Film X", "אולם 5", "19:00", "21:00", 0.5f, presentationId = null),
                RawScreening("Film X", "5", "19:00", "21:00", 0.5f, presentationId = "999")
            ),
            nowMinutes = 1200,
            defaultDate = date,
            nowZoned = now
        )
        val deduped = deduplicateScreenings(manualDupes)
        assertEquals(1, deduped.size)
        assertEquals("999", deduped[0].presentationId)
    }

    @Test
    fun `invalid presentationId placeholders are rejected`() {
        assertNull(RawScreening("A", "1", "19:00", "21:00", presentationId = "").resolvedPresentationId())
        assertNull(RawScreening("A", "1", "19:00", "21:00", presentationId = "null").resolvedPresentationId())
        assertNull(RawScreening("A", "1", "19:00", "21:00", presentationId = "undefined").resolvedPresentationId())
        assertNull(RawScreening("A", "1", "19:00", "21:00", presentationId = "0").resolvedPresentationId())
        assertEquals(
            "359718",
            RawScreening("A", "1", "19:00", "21:00", bookingUrl = "https://planet.co.il/order?pid=359718").resolvedPresentationId()
        )
    }

    @Test
    fun `midnight, start-end exact boundaries, UTC conversion, and Israel DST transitions behave accurately`() {
        // 1. Exact boundaries + midnight rollover (23:30 - 01:00 -> +15m EXTRA_END = 01:15)
        val date = LocalDate.of(2026, 11, 12)
        val lateRaw = listOf(RawScreening("Midnight Feature", "אולם 1", "23:30", "01:00", 0.5f, presentationId = "m1"))
        val late = repo.prepareScreenings(lateRaw, nowMinutes = 1410, defaultDate = date)

        val beforeStart = ZonedDateTime.of(2026, 11, 12, 23, 29, 0, 0, jerusalem)
        val exactStart = ZonedDateTime.of(2026, 11, 12, 23, 30, 0, 0, jerusalem)
        val afterMidnight = ZonedDateTime.of(2026, 11, 13, 0, 45, 0, 0, jerusalem)
        val lastActiveMinute = ZonedDateTime.of(2026, 11, 13, 1, 14, 0, 0, jerusalem)
        val exactEndPlusExtra = ZonedDateTime.of(2026, 11, 13, 1, 15, 0, 0, jerusalem)

        assertFalse(late[0].isShowingAt(beforeStart))
        assertEquals(ScreeningStatus.UPCOMING, late[0].statusAt(beforeStart))

        assertTrue(late[0].isShowingAt(exactStart))
        assertEquals(ScreeningStatus.LIVE, late[0].statusAt(exactStart))

        assertTrue(late[0].isShowingAt(afterMidnight))
        assertEquals(ScreeningStatus.LIVE, late[0].statusAt(afterMidnight))

        // Foreign UTC instant equivalent to 2026-11-13 00:45+02:00 (UTC = 22:45 on Nov 12)
        val utcInstant = ZonedDateTime.of(2026, 11, 12, 22, 45, 0, 0, ZoneOffset.UTC)
        assertTrue(late[0].isShowingAt(utcInstant))

        assertTrue(late[0].isShowingAt(lastActiveMinute))
        assertFalse(late[0].isShowingAt(exactEndPlusExtra))
        assertEquals(ScreeningStatus.ENDED_RECENTLY, late[0].statusAt(exactEndPlusExtra))

        // 2. Israel DST spring-forward night (2026-03-27 at 02:00 -> 03:00 IDT)
        // Cinema day 2026-03-26 show from 23:45 to 03:15 (+15m -> 03:30)
        val springDate = LocalDate.of(2026, 3, 26)
        val springShow = repo.prepareScreenings(
            listOf(RawScreening("DST Marathon", "אולם 5", "23:45", "03:15", 0.5f, presentationId = "dst1")),
            nowMinutes = 1425,
            defaultDate = springDate
        )
        // 01:30+02:00 (before jump) and 03:05+03:00 (just after 02:00->03:00 jump)
        assertTrue(springShow[0].isShowingAt(ZonedDateTime.of(2026, 3, 27, 1, 30, 0, 0, jerusalem)))
        assertTrue(springShow[0].isShowingAt(ZonedDateTime.of(2026, 3, 27, 3, 5, 0, 0, jerusalem)))
        assertFalse(springShow[0].isShowingAt(ZonedDateTime.of(2026, 3, 27, 3, 30, 0, 0, jerusalem)))

        // 3. Stale schedule date from yesterday does NOT count as active today
        val wrongDateNow = ZonedDateTime.of(2026, 11, 13, 23, 45, 0, 0, jerusalem)
        assertFalse(late[0].isShowingAt(wrongDateNow))
        assertEquals(ScreeningStatus.ENDED, late[0].statusAt(wrongDateNow))
    }

    @Test
    fun `diagnostic logger records active breakdown without failing on JVM and never exposes secrets`() {
        val captured = mutableListOf<String>()
        CinemaDiagnosticsLogger.customPrinter = { captured.add(it) }

        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)
        val screenings = repo.prepareScreenings(
            listOf(
                RawScreening("Logged Movie", "אולם 7", "19:30", "21:30", 0.5f, presentationId = "pid_log"),
                RawScreening("Missing Movie", "אולם 1", "19:30", "21:30", null)
            ),
            nowMinutes = 1200,
            defaultDate = date,
            nowZoned = now
        )

        val result = calculateCinemaOccupancy(screenings, now)
        CinemaDiagnosticsLogger.logOccupancyCalculation("unitTest", result)

        assertEquals(3, captured.size)
        assertTrue(captured[0].contains("occupiedSeats=154"))
        assertTrue(captured[0].contains("active=2"))
        assertTrue(captured[1].contains("pid=pid_log"))
        assertTrue(captured[1].contains("included=true"))
        assertTrue(captured[2].contains("included=false"))
        assertTrue(captured[2].contains("MISSING_PRESENTATION_ID_AND_RATIO"))
        assertFalse(captured.joinToString("\n").contains("Cookie", ignoreCase = true))
        assertFalse(captured.joinToString("\n").contains("Authorization", ignoreCase = true))
    }
}
