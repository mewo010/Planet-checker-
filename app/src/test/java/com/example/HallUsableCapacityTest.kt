package com.example

import com.example.data.diagnostics.LiveValidationSnapshotBuilder
import com.example.data.model.AvailabilitySource
import com.example.data.model.OccupancyCountKind
import com.example.data.model.RawScreening
import com.example.data.model.SeatAvailabilityStatus
import com.example.data.model.UsableCapacityStatus
import com.example.data.model.calculateCinemaOccupancy
import com.example.data.model.calculateUsableCapacity
import com.example.data.model.deduplicateScreenings
import com.example.data.model.mergeScreeningAvailability
import com.example.data.model.resolveLiveSeatMapUsableCapacity
import com.example.data.repository.CinemaRepository
import com.example.data.seats.SeatStatusMapper
import com.example.data.seats.SeatStatusMappingConfig
import com.example.data.seats.SeatStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HallUsableCapacityTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val repo = CinemaRepository()

    private val verifiedSeatConfig = SeatStatusMappingConfig.fromRules(
        SeatStatusMappingConfig.exactFieldRule("avail", "st", "1", SeatAvailabilityStatus.AVAILABLE),
        SeatStatusMappingConfig.exactFieldRule("occ", "st", "2", SeatAvailabilityStatus.OCCUPIED),
        SeatStatusMappingConfig.exactFieldRule("res", "st", "3", SeatAvailabilityStatus.RESERVED),
        SeatStatusMappingConfig.exactFieldRule("blk", "st", "4", SeatAvailabilityStatus.BLOCKED),
        SeatStatusMappingConfig.exactFieldRule("unavail", "st", "5", SeatAvailabilityStatus.UNAVAILABLE),
        isVerifiedAgainstWebsite = true
    )

    @Test
    fun `original HALL_CAPACITIES remains completely unchanged and usable capacity matches every known hall`() {
        val expectedOriginal = mapOf(
            1 to 124, 2 to 124, 3 to 162, 4 to 163, 5 to 400,
            6 to 123, 7 to 311, 8 to 154, 9 to 88, 10 to 217,
            11 to 251, 12 to 118, 13 to 119, 14 to 119, 15 to 119,
            16 to 182, 17 to 182, 18 to 112, 19 to 204, 20 to 124,
            21 to 54, 22 to 54, 23 to 70, 24 to 56
        )
        assertEquals(expectedOriginal, CinemaRepository.HALL_CAPACITIES)

        val expectedBroken = mapOf(
            1 to 3, 2 to 3, 3 to 2, 4 to 3, 5 to 2, 6 to 2,
            7 to 2, 8 to 3, 9 to 0, 10 to 3, 11 to 2, 12 to 3,
            13 to 3, 14 to 3, 15 to 3, 16 to 3, 17 to 3, 18 to 3,
            19 to 2, 20 to null, 21 to 0, 22 to 0, 23 to 0, 24 to 0
        )
        assertEquals(expectedBroken, CinemaRepository.BROKEN_SEATS_BY_HALL)

        val expectedUsableByKnownHall = mapOf(
            1 to 121, 2 to 121, 3 to 160, 4 to 160, 5 to 398,
            6 to 121, 7 to 309, 8 to 151, 9 to 88, 10 to 214,
            11 to 249, 12 to 115, 13 to 116, 14 to 116, 15 to 116,
            16 to 179, 17 to 179, 18 to 109, 19 to 202,
            21 to 54, 22 to 54, 23 to 70, 24 to 56
        )

        for ((hall, expectedUsable) in expectedUsableByKnownHall) {
            val info = CinemaRepository.getHallCapacityInfo(hall)
            assertEquals("Hall $hall original capacity", expectedOriginal[hall], info.originalCapacity)
            assertEquals("Hall $hall broken seats", expectedBroken[hall], info.brokenSeats)
            assertEquals("Hall $hall usable capacity", expectedUsable, info.usableCapacity)
            assertTrue("Hall $hall should have known broken seats", info.isBrokenSeatsKnown)
            assertEquals(UsableCapacityStatus.KNOWN, info.status)
        }
    }

    @Test
    fun `halls with zero broken seats retain their exact original capacity`() {
        val zeroBrokenHalls = listOf(9, 21, 22, 23, 24)
        for (hall in zeroBrokenHalls) {
            val info = CinemaRepository.getHallCapacityInfo(hall)
            assertEquals(0, info.brokenSeats)
            assertEquals(CinemaRepository.HALL_CAPACITIES[hall], info.usableCapacity)
            assertEquals(info.originalCapacity, info.usableCapacity)
            assertTrue(info.isBrokenSeatsKnown)
            assertEquals(UsableCapacityStatus.KNOWN, info.status)
        }
    }

    @Test
    fun `hall 20 remains UNKNOWN and unknown broken-seat data is never silently treated as zero`() {
        val hall20 = CinemaRepository.getHallCapacityInfo(20)
        assertEquals(124, hall20.originalCapacity)
        assertNull(hall20.brokenSeats)
        assertNull(hall20.usableCapacity)
        assertFalse(hall20.isBrokenSeatsKnown)
        assertEquals(UsableCapacityStatus.UNKNOWN_BROKEN_SEATS, hall20.status)

        // Direct calculation helper never treats null as 0
        assertNull(calculateUsableCapacity(originalCapacity = 124, brokenSeats = null))
        assertNull(calculateUsableCapacity(originalCapacity = null, brokenSeats = 3))

        // Unknown / unmapped hall is also never treated as 0 broken seats
        val unknownHall = CinemaRepository.getHallCapacityInfo(99, fallbackOriginalCapacity = 100)
        assertEquals(100, unknownHall.originalCapacity)
        assertNull(unknownHall.brokenSeats)
        assertNull(unknownHall.usableCapacity)
        assertFalse(unknownHall.isBrokenSeatsKnown)
        assertEquals(UsableCapacityStatus.UNKNOWN_BROKEN_SEATS, unknownHall.status)

        // Screening & diagnostics preserve UNKNOWN status for Hall 20 without assuming 0 broken seats
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)
        val screenings = repo.prepareScreenings(
            rawList = listOf(RawScreening("Test 4DX", "אולם 20", "19:30", "21:30", 0.5f, presentationId = "2001")),
            nowMinutes = 1200,
            defaultDate = date,
            nowZoned = now
        )
        val screening = screenings.single()
        assertEquals(124, screening.originalHallCapacity())
        assertNull(screening.brokenSeatsCount())
        assertNull(screening.usableHallCapacity())
        assertFalse(screening.isBrokenSeatsKnown())

        val occupancy = calculateCinemaOccupancy(screenings, now)
        assertEquals(1, occupancy.unknownBrokenSeatsScreenings)
        val diag = occupancy.screeningDiagnostics.single()
        assertNull(diag.brokenSeatsCount)
        assertNull(diag.usableHallCapacity)
        assertFalse(diag.brokenSeatsKnown)

        val snapshot = LiveValidationSnapshotBuilder.buildSnapshot(screenings, now = now)
        val snapEntry = snapshot.activeScreenings.single()
        assertEquals(124, snapEntry.officialRoomCapacity)
        assertNull(snapEntry.brokenSeatsCount)
        assertNull(snapEntry.usableHallCapacity)
        assertFalse(snapEntry.brokenSeatsKnown)
    }

    @Test
    fun `broken seats are not subtracted twice when already excluded by a verified live seat map`() {
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)

        // Hall 9 (88 cap, 0 broken) vs Hall 7 (311 cap, 2 broken -> 309 usable)
        // Case A: Verified live seat map for Hall 7 omits the 2 broken seats (309 raw/valid seats: 100 occ, 9 res, 200 avail)
        val omittedEntries = buildList {
            var id = 1
            repeat(100) { add("\"$id\":{\"n\":\"$id\",\"st\":2}"); id++ }
            repeat(9) { add("\"$id\":{\"n\":\"$id\",\"st\":3}"); id++ }
            repeat(200) { add("\"$id\":{\"n\":\"$id\",\"st\":1}"); id++ }
        }
        val omittedJson = """{"S":{"1":{"n":"אולם 7","G":{"0":{"R":{"1":{"n":"1","S":{${omittedEntries.joinToString(",")}}}}}}}}}"""

        // Case B: Verified live seat map for Hall 7 contains all 311 raw seats, with 2 marked BLOCKED (st=4)
        val blockedEntries = buildList {
            var id = 1
            repeat(100) { add("\"$id\":{\"n\":\"$id\",\"st\":2}"); id++ }
            repeat(9) { add("\"$id\":{\"n\":\"$id\",\"st\":3}"); id++ }
            repeat(200) { add("\"$id\":{\"n\":\"$id\",\"st\":1}"); id++ }
            repeat(2) { add("\"$id\":{\"n\":\"$id\",\"st\":4}"); id++ }
        }
        val blockedJson = """{"S":{"1":{"n":"אולם 7","G":{"0":{"R":{"1":{"n":"1","S":{${blockedEntries.joinToString(",")}}}}}}}}}"""

        // Case C: Verified live seat map for Hall 7 contains all 311 raw seats, with 2 marked UNAVAILABLE (st=5)
        val unavailEntries = buildList {
            var id = 1
            repeat(100) { add("\"$id\":{\"n\":\"$id\",\"st\":2}"); id++ }
            repeat(9) { add("\"$id\":{\"n\":\"$id\",\"st\":3}"); id++ }
            repeat(200) { add("\"$id\":{\"n\":\"$id\",\"st\":1}"); id++ }
            repeat(2) { add("\"$id\":{\"n\":\"$id\",\"st\":5}"); id++ }
        }
        val unavailJson = """{"S":{"1":{"n":"אולם 7","G":{"0":{"R":{"1":{"n":"1","S":{${unavailEntries.joinToString(",")}}}}}}}}}"""

        // Case D: Verified live seat map for Hall 7 lists all 311 seats as sellable (0 blocked, 0 unavail, 0 omitted)
        val fullSellableEntries = buildList {
            var id = 1
            repeat(100) { add("\"$id\":{\"n\":\"$id\",\"st\":2}"); id++ }
            repeat(9) { add("\"$id\":{\"n\":\"$id\",\"st\":3}"); id++ }
            repeat(202) { add("\"$id\":{\"n\":\"$id\",\"st\":1}"); id++ }
        }
        val fullSellableJson = """{"S":{"1":{"n":"אולם 7","G":{"0":{"R":{"1":{"n":"1","S":{${fullSellableEntries.joinToString(",")}}}}}}}}}"""

        val parser = SeatStatusParser(SeatStatusMapper(verifiedSeatConfig))
        val sumOmitted = parser.parse(omittedJson, "p_omit").getOrThrow()
        val sumBlocked = parser.parse(blockedJson, "p_blk").getOrThrow()
        val sumUnavail = parser.parse(unavailJson, "p_unavail").getOrThrow()
        val sumFull = parser.parse(fullSellableJson, "p_full").getOrThrow()

        val initial = repo.prepareScreenings(
            listOf(
                RawScreening("Movie Omitted", "אולם 7", "19:00", "21:30", null, presentationId = "p_omit"),
                RawScreening("Movie Blocked", "אולם 7", "19:15", "21:45", null, presentationId = "p_blk"),
                RawScreening("Movie Unavail", "אולם 7", "19:30", "22:00", null, presentationId = "p_unavail"),
                RawScreening("Movie FullMap", "אולם 7", "19:45", "22:15", null, presentationId = "p_full")
            ),
            nowMinutes = 1200,
            defaultDate = date,
            nowZoned = now
        )
        val merged = mergeScreeningAvailability(
            initial,
            seatSummariesByKey = mapOf(
                "p_omit" to sumOmitted,
                "p_blk" to sumBlocked,
                "p_unavail" to sumUnavail,
                "p_full" to sumFull
            )
        )

        val omitted = merged[0]
        val blocked = merged[1]
        val unavail = merged[2]
        val full = merged[3]

        // All 4 must yield usable capacity 309 (NEVER 307 from double subtraction!)
        assertEquals(309, omitted.usableHallCapacity())
        assertTrue(omitted.brokenSeatsExcludedByLiveMap())
        assertEquals(109, omitted.estimatedOccupiedSeats())

        assertEquals(309, blocked.usableHallCapacity())
        assertTrue(blocked.brokenSeatsExcludedByLiveMap())
        assertEquals(109, blocked.estimatedOccupiedSeats())

        assertEquals(309, unavail.usableHallCapacity())
        assertTrue(unavail.brokenSeatsExcludedByLiveMap())
        assertEquals(109, unavail.estimatedOccupiedSeats())

        assertEquals(309, full.usableHallCapacity())
        assertFalse(full.brokenSeatsExcludedByLiveMap())
        assertEquals(109, full.estimatedOccupiedSeats())
    }

    @Test
    fun `usable capacity never becomes negative`() {
        assertEquals(0, calculateUsableCapacity(originalCapacity = 50, brokenSeats = 80))
        assertEquals(0, calculateUsableCapacity(originalCapacity = 0, brokenSeats = 5))
        assertEquals(0, calculateUsableCapacity(originalCapacity = 10, brokenSeats = 10))
        // Negative broken-seat input cannot inflate capacity above original
        assertEquals(50, calculateUsableCapacity(originalCapacity = 50, brokenSeats = -4))

        val tinyJson = """{"S":{"1":{"n":"אולם 1","G":{"0":{"R":{"1":{"n":"1","S":{"1":{"n":"1","st":1}}}}}}}}}"""
        val summary = SeatStatusParser(SeatStatusMapper(verifiedSeatConfig)).parse(tinyJson).getOrThrow()
        assertEquals(0, resolveLiveSeatMapUsableCapacity(originalCapacity = 1, manualBrokenSeats = 10, summary = summary))
    }

    @Test
    fun `ratio-based occupancy estimates use usable capacity consistently across pipeline`() {
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)

        // Hall 10: original 217, reserved 3 -> 0.0f => 214
        // Hall 18: original 112, reserved 3 -> 0.50f => 53 (round(112 * 0.5) - 3)
        // Hall 9: original 88, reserved 0 -> 0.50f => 44
        val raw = listOf(
            RawScreening("Show A", "אולם 10", "19:00", "21:30", 0.0f, presentationId = "s10"),
            RawScreening("Show B", "אולם 18", "19:15", "21:45", 0.50f, presentationId = "s18"),
            RawScreening("Show C", "אולם 9", "19:30", "22:00", 0.50f, presentationId = "s9")
        )
        val screenings = repo.prepareScreenings(raw, nowMinutes = 1200, defaultDate = date, nowZoned = now)

        assertEquals(214, screenings[0].estimatedPeople)
        assertEquals(214, screenings[0].estimatedOccupiedSeats())
        assertEquals(53, screenings[1].estimatedPeople)
        assertEquals(53, screenings[1].estimatedOccupiedSeats())
        assertEquals(44, screenings[2].estimatedPeople)
        assertEquals(44, screenings[2].estimatedOccupiedSeats())

        // Hourly ratio override also uses APK formula
        val hourlyUpdated = mergeScreeningAvailability(
            screenings = listOf(screenings[0].copy(availabilityRatio = null, estimatedPeople = null, availabilitySource = AvailabilitySource.NONE)),
            scheduleRatiosByKey = mapOf(screenings[0].canonicalSlotKey() to 0.0f)
        )
        assertEquals(214, hourlyUpdated[0].estimatedPeople)
        assertEquals(214, hourlyUpdated[0].estimatedOccupiedSeats())

        // Deduplication merges also preserve APK ratio estimate
        val duped = deduplicateScreenings(listOf(screenings[0], screenings[0].copy(id = "dup")))
        assertEquals(1, duped.size)
        assertEquals(214, duped[0].estimatedPeople)

        val occupancy = calculateCinemaOccupancy(screenings, now)
        assertEquals(214 + 53 + 44, occupancy.occupiedSeats)
        assertEquals(0, occupancy.unknownBrokenSeatsScreenings)
    }

    @Test
    fun `existing verified seat-map occupancy calculations remain exact and ignore blocked unavailable and unknown seats`() {
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)

        val json = """
            {"S":{"1":{"n":"אולם 3","G":{"0":{"R":{"1":{"n":"1","S":{
              "1":{"n":"1","st":2},
              "2":{"n":"2","st":2},
              "3":{"n":"3","st":3},
              "4":{"n":"4","st":4},
              "5":{"n":"5","st":5},
              "6":{"n":"6","st":1},
              "7":{"n":"7","tg":1}
            }}}}}}}}
        """.trimIndent()
        val summary = SeatStatusParser(SeatStatusMapper(verifiedSeatConfig))
            .parse(json, presentationId = "v3")
            .getOrThrow()

        val initial = repo.prepareScreenings(
            listOf(RawScreening("Verified Show", "אולם 3", "19:30", "21:30", null, presentationId = "v3")),
            nowMinutes = 1200,
            defaultDate = date,
            nowZoned = now
        )
        val merged = mergeScreeningAvailability(initial, seatSummariesByKey = mapOf("v3" to summary))
        val screening = merged.single()

        // 2 occupied + 1 reserved = 3 viewers; blocked, unavailable, available, and unknown are NOT counted as viewers
        assertEquals(OccupancyCountKind.VERIFIED_SEAT_MAP, screening.occupancyCountKind())
        assertEquals(3, screening.estimatedOccupiedSeats())
        val occ = calculateCinemaOccupancy(merged, now)
        assertEquals(3, occ.verifiedOccupiedSeats)
        assertEquals(0, occ.estimatedOccupiedSeats)
        assertEquals(3, occ.occupiedSeats)
    }
}
