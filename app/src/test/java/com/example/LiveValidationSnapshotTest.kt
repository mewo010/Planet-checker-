package com.example

import com.example.data.diagnostics.FeedTelemetry
import com.example.data.diagnostics.LiveValidationSnapshotBuilder
import com.example.data.diagnostics.SourceFreshnessState
import com.example.data.diagnostics.SourceTelemetryBundle
import com.example.data.model.FirstScheduleResponse
import com.example.data.model.HourlyScheduleResponse
import com.example.data.model.OccupancyCountKind
import com.example.data.model.RawScreening
import com.example.data.model.SeatAvailabilityStatus
import com.example.data.model.SeatFetchState
import com.example.data.model.mergeScreeningAvailability
import com.example.data.remote.PlanetQuickBookClient
import com.example.data.repository.CinemaRepository
import com.example.data.repository.SeatAvailabilityRepository
import com.example.data.repository.SeatFetchResult
import com.example.data.seats.SeatStatusMapper
import com.example.data.seats.SeatStatusMappingConfig
import com.example.data.seats.SeatStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LiveValidationSnapshotTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    private val verifiedSeatConfig = SeatStatusMappingConfig.fromRules(
        SeatStatusMappingConfig.exactFieldRule("avail", "st", "1", SeatAvailabilityStatus.AVAILABLE),
        SeatStatusMappingConfig.exactFieldRule("occ", "st", "2", SeatAvailabilityStatus.OCCUPIED),
        SeatStatusMappingConfig.exactFieldRule("res", "st", "3", SeatAvailabilityStatus.RESERVED),
        SeatStatusMappingConfig.exactFieldRule("blk", "st", "4", SeatAvailabilityStatus.BLOCKED),
        isVerifiedAgainstWebsite = true
    )

    @Test
    fun `full multi-source validation snapshot captures all fields, disagreements, 6 invariants, freshness, and JSON persistence`() {
        val repo = CinemaRepository()
        val cinemaDate = LocalDate.of(2026, 11, 12)
        val nowZoned = ZonedDateTime.of(2026, 11, 12, 20, 15, 0, 0, jerusalem)
        val nowEpochMs = nowZoned.toInstant().toEpochMilli()

        // 4 screenings:
        // 1) Hall 7 Active: FIRST_URL ratio=0.80 (no PID), HOURLY_URL ratio=0.60, QuickBook soldOut=false PID=359718 Code=PC-7,
        //    plus verified seats-statusV2 with 6 occupied + 2 reserved + 2 available (0.20 ratio).
        // 2) Hall 5 IMAX Active: FIRST_URL ratio=0.50, HOURLY_URL ratio=0.30, QuickBook soldOut=true -> 0.0f,
        //    plus partial/unverified seats-statusV2 (tg=1, hc=1 -> 4 objects vs 400 cap) -> must preserve cap=400 & HOURLY ratio=0.30!
        // 3) Hall 21 VIP Active: Missing in Gist, recovered from QuickBook soldOut=false, seats API failed HTTP 403 Cloudflare -> UNKNOWN, excluded!
        // 4) Hall 1 Ended: 17:00-18:30 (+15m = 18:45) -> MUST NOT appear in activeScreenings!
        val firstResponse = FirstScheduleResponse(
            date = "2026-11-12",
            screenings = listOf(
                RawScreening("Gladiator II", "אולם 7", "19:30", "22:00", availabilityRatio = 0.80f, presentationId = null),
                RawScreening("Dune IMAX", "אולם 5", "19:00", "21:45", availabilityRatio = 0.50f, presentationId = "5005"),
                RawScreening("Ended Comedy", "אולם 1", "17:00", "18:30", availabilityRatio = 0.40f, presentationId = "1001")
            )
        )
        val hourlyResponse = HourlyScheduleResponse(
            screenings = listOf(
                RawScreening("Gladiator II", "אולם 7", "19:30", "22:00", availabilityRatio = 0.60f, presentationId = null),
                RawScreening("Dune IMAX", "אולם 5", "19:00", "21:45", availabilityRatio = 0.30f, presentationId = "5005")
            )
        )
        val quickBookList = listOf(
            RawScreening(
                title = "Gladiator II",
                room = "אולם 7",
                start = "19:30",
                end = "22:00",
                availabilityRatio = null,
                presentationId = "359718",
                presentationCode = "PC-7",
                soldOut = false,
                date = "2026-11-12"
            ),
            RawScreening(
                title = "Dune IMAX",
                room = "אולם 5",
                start = "19:00",
                end = "21:45",
                availabilityRatio = 0.0f,
                presentationId = "5005",
                presentationCode = "PC-IMAX",
                soldOut = true,
                date = "2026-11-12"
            ),
            RawScreening(
                title = "VIP Mystery",
                room = "אולם 21 (VIP)",
                start = "19:45",
                end = "21:45",
                availabilityRatio = null,
                presentationId = "9021",
                presentationCode = "PC-VIP",
                soldOut = false,
                date = "2026-11-12"
            )
        )

        val mergedSchedule = repo.mergeAllScheduleSources(
            baseResponse = firstResponse,
            hourlyResponse = hourlyResponse,
            quickBookScreenings = quickBookList,
            defaultCinemaDate = cinemaDate,
            nowMinutes = 1215,
            nowZoned = nowZoned
        )

        // Duplicate entry injected purposely to verify deduplication invariant
        val duplicateHall7 = mergedSchedule.screenings.first { it.roomNumber == 7 }

        val verifiedHall7Json = """
            {"S":{"1":{"n":"אולם 7","G":{"0":{"R":{"1":{"n":"1","S":{
              "1":{"n":"1","st":2},
              "2":{"n":"2","st":2},
              "3":{"n":"3","st":2},
              "4":{"n":"4","st":2},
              "5":{"n":"5","st":2},
              "6":{"n":"6","st":2},
              "7":{"n":"7","st":3},
              "8":{"n":"8","st":3},
              "9":{"n":"9","st":1},
              "10":{"n":"10","st":1}
            }}}}}}}}
        """.trimIndent()
        val unverifiedImaxJson = """
            {"S":{"1":{"n":"אולם 5","G":{"0":{"R":{"1":{"n":"1","S":{
              "1":{"n":"1","tg":1,"svga":1},
              "2":{"n":"2","tg":1},
              "3":{"n":"3","hc":1},
              "4":{"n":"4","tg":1}
            }}}}}}}}
        """.trimIndent()

        val seatSummary7 = SeatStatusParser(SeatStatusMapper(verifiedSeatConfig))
            .parse(verifiedHall7Json, presentationId = "359718", httpStatusCode = 200, fetchedAtEpochMs = nowEpochMs - 30_000L)
            .getOrThrow()
        val seatSummary5 = SeatStatusParser(SeatStatusMapper(SeatStatusMappingConfig.UNVERIFIED_DEFAULT))
            .parse(unverifiedImaxJson, presentationId = "5005", httpStatusCode = 200, fetchedAtEpochMs = nowEpochMs - 45_000L)
            .getOrThrow()

        val finalScreenings = mergeScreeningAvailability(
            screenings = mergedSchedule.screenings + duplicateHall7,
            seatSummariesByKey = mapOf(
                "359718" to seatSummary7,
                "5005" to seatSummary5
            ),
            seatFetchResultsByKey = mapOf(
                "359718" to SeatFetchResult.Success(seatSummary7, fromCache = false, httpCode = 200, ttlMs = SeatAvailabilityRepository.DEFAULT_CACHE_TTL_MS),
                "5005" to SeatFetchResult.Success(seatSummary5, fromCache = true, httpCode = 200, ttlMs = SeatAvailabilityRepository.DEFAULT_CACHE_TTL_MS),
                "9021" to SeatFetchResult.AuthRequired(403, "Cloudflare WAF challenge")
            )
        )

        val telemetry = SourceTelemetryBundle(
            firstUrl = FeedTelemetry("FIRST_URL", CinemaRepository.FIRST_URL, 200, nowEpochMs - 120_000L, 24 * 3600_000L, succeeded = true),
            hourlyUrl = FeedTelemetry("HOURLY_URL", CinemaRepository.HOURLY_URL, 200, nowEpochMs - 600_000L, 3600_000L, succeeded = true, fromCache = true),
            quickBook = FeedTelemetry("OFFICIAL_QUICKBOOK", PlanetQuickBookClient.buildScheduleUrl(cinemaDate), 403, nowEpochMs - 15_000L, 900_000L, succeeded = false, cloudflareBlocked = true, errorDetail = "HTTP 403 WAF"),
            seatsApiTtlMs = SeatAvailabilityRepository.DEFAULT_CACHE_TTL_MS
        )

        val snapshot = LiveValidationSnapshotBuilder.buildSnapshot(
            screenings = finalScreenings,
            now = nowZoned,
            nowEpochMs = nowEpochMs,
            telemetry = telemetry
        )

        // Section 5: Overall counts & epistemic notice
        assertEquals("1072", snapshot.cinemaId)
        assertEquals("2026-11-12", snapshot.cinemaDate)
        assertTrue(snapshot.epistemicNotice.contains("NOT verified physical people"))
        // 3 active (Hall 7, Hall 5, Hall 21 VIP); Ended Comedy in Hall 1 is excluded!
        assertEquals(3, snapshot.activeScreeningsCount)
        assertEquals(1, snapshot.excludedOrEndedCount)
        assertEquals(2, snapshot.countedScreeningsCount)
        assertEquals(1, snapshot.unknownOccupancyScreeningsCount)
        assertEquals(1, snapshot.failedSeatRequestsCount)
        assertEquals(0, snapshot.missingPresentationIdCount)
        assertEquals(67, snapshot.dataCompletenessPercentage)

        // Hall 7 verified: 6 occupied + 2 reserved = 8
        assertEquals(8, snapshot.verifiedOccupiedAndReservedSeats)
        // Hall 5 ratio-estimated: HOURLY 0.30 on 398 usable cap (400 - 2 broken) = 279
        assertEquals(279, snapshot.ratioEstimatedOccupancy)
        assertEquals(287, snapshot.totalEstimatedOccupancy)

        // Section 1 & 2: Verify individual entries & explicit disagreements
        val h7 = snapshot.activeScreenings.first { it.roomNumber == 7 }
        assertEquals("359718", h7.presentationId)
        assertEquals("PC-7", h7.presentationCode)
        assertEquals(0.80f, h7.firstUrlAvailabilityRatio!!, 0.001f)
        assertEquals(0.60f, h7.hourlyUrlAvailabilityRatio!!, 0.001f)
        assertEquals(false, h7.quickBookSoldOut)
        assertEquals(6, h7.verifiedOccupiedSeats)
        assertEquals(2, h7.reservedSeats)
        assertEquals(2, h7.availableSeats)
        assertEquals(0, h7.unknownSeats)
        assertEquals(8, h7.estimatedOccupancy)
        assertEquals(OccupancyCountKind.VERIFIED_SEAT_MAP.name, h7.occupancyKind)
        assertEquals(SourceFreshnessState.CURRENT, h7.seatApiFreshness)
        assertEquals(30L, h7.seatApiAgeSeconds)
        assertTrue(h7.disagreements.any { it.field == "availabilityRatio" })
        assertTrue(h7.disagreements.any { it.field == "presentationId" })

        val h5 = snapshot.activeScreenings.first { it.roomNumber == 5 }
        assertEquals(400, h5.officialRoomCapacity) // Partial 4-seat payload did NOT shrink 400
        assertEquals(2, h5.brokenSeatsCount)
        assertEquals(398, h5.usableHallCapacity)
        assertTrue(h5.brokenSeatsKnown)
        assertEquals(4, h5.unknownSeats)
        assertEquals(279, h5.estimatedOccupancy)
        assertEquals(OccupancyCountKind.RATIO_ESTIMATE.name, h5.occupancyKind)
        assertEquals(SourceFreshnessState.CACHED, h5.seatApiFreshness)
        assertTrue(h5.disagreements.any { it.field == "soldOut_vs_availabilityRatio" })
        assertTrue(h5.disagreements.any { it.field == "auditoriumCapacity" })
        assertTrue(h5.disagreements.any { it.field == "seatStatusVerification" })

        val h21 = snapshot.activeScreenings.first { it.roomNumber == 21 }
        assertFalse(h21.includedInFinalTotal)
        assertNull(h21.estimatedOccupancy)
        assertEquals(SourceFreshnessState.CLOUDFLARE_BLOCKED, h21.seatApiFreshness)

        // Section 3: All 6 counting invariants must pass
        val verif = snapshot.countingAccuracyVerification
        assertTrue(verif.eachActiveScreeningCountedOnce)
        assertTrue(verif.allBelongToExpectedCinemaAndDate)
        assertTrue(verif.noEndedScreeningIncluded)
        assertTrue(verif.noMissingDataCountedAsZero)
        assertTrue(verif.noUnknownSeatClassifiedAsOccupiedOrAvailable)
        assertTrue(verif.noPartialSeatMapMistakenForFullCapacity)
        assertTrue(verif.allInvariantsPassed)

        // Section 4: Data Freshness states & exact ages
        val firstFresh = snapshot.sourceFreshness.first { it.sourceName == "FIRST_URL" }
        val hourlyFresh = snapshot.sourceFreshness.first { it.sourceName == "HOURLY_URL" }
        val qbFresh = snapshot.sourceFreshness.first { it.sourceName == "OFFICIAL_QUICKBOOK" }
        assertEquals(SourceFreshnessState.CURRENT, firstFresh.freshnessState)
        assertEquals(120L, firstFresh.ageSeconds)
        assertEquals(SourceFreshnessState.CACHED, hourlyFresh.freshnessState)
        assertEquals(600L, hourlyFresh.ageSeconds)
        assertEquals(SourceFreshnessState.CLOUDFLARE_BLOCKED, qbFresh.freshnessState)
        assertEquals(403, qbFresh.httpStatusCode)

        // Section 5: JSON serialization round-trip & file persistence
        val tempDir: File = Files.createTempDirectory("planet_validation_test").toFile()
        try {
            val latestFile = LiveValidationSnapshotBuilder.saveSnapshotJson(tempDir, snapshot)
            assertTrue(latestFile.exists())
            assertTrue(File(tempDir, "snapshot_$nowEpochMs.json").exists())

            val parsedBack = LiveValidationSnapshotBuilder.fromJson(latestFile.readText())
            assertNotNull(parsedBack)
            assertEquals(snapshot.totalEstimatedOccupancy, parsedBack!!.totalEstimatedOccupancy)
            assertEquals(snapshot.activeScreenings.size, parsedBack.activeScreenings.size)
            assertEquals(snapshot.countingAccuracyVerification, parsedBack.countingAccuracyVerification)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `compareSnapshots computes exact occupancy delta across two snapshots`() {
        val repo = CinemaRepository()
        val cinemaDate = LocalDate.of(2026, 11, 12)
        val t0Zoned = ZonedDateTime.of(2026, 11, 12, 19, 30, 0, 0, jerusalem)
        val t1Zoned = ZonedDateTime.of(2026, 11, 12, 19, 45, 0, 0, jerusalem)

        val t0Screenings = repo.prepareScreenings(
            rawList = listOf(RawScreening("Film 1", "אולם 5", "19:00", "21:30", 0.50f, presentationId = "10")),
            nowMinutes = 1170,
            defaultDate = cinemaDate,
            nowZoned = t0Zoned
        )
        val t1Screenings = repo.prepareScreenings(
            rawList = listOf(RawScreening("Film 1", "אולם 5", "19:00", "21:30", 0.40f, presentationId = "10")),
            nowMinutes = 1185,
            defaultDate = cinemaDate,
            nowZoned = t1Zoned
        )

        val snap0 = LiveValidationSnapshotBuilder.buildSnapshot(
            screenings = t0Screenings,
            now = t0Zoned,
            nowEpochMs = t0Zoned.toInstant().toEpochMilli()
        )
        val snap1 = LiveValidationSnapshotBuilder.buildSnapshot(
            screenings = t1Screenings,
            now = t1Zoned,
            nowEpochMs = t1Zoned.toInstant().toEpochMilli()
        )

        val delta = LiveValidationSnapshotBuilder.compareSnapshots(snap0, snap1)
        // Hall 5 usable capacity=398: 0.50->199 occupied at T0, 0.40->239 occupied at T1 => +40 delta
        assertEquals(40, delta.occupancyDelta)
        assertEquals(40, delta.ratioEstimatedDelta)
        assertEquals(0, delta.verifiedSeatsDelta)
        assertEquals(1, delta.changedScreenings.size)
        assertEquals(40, delta.changedScreenings.first().delta)
    }
}
