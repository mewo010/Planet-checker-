package com.example

import com.example.data.model.AvailabilitySource
import com.example.data.model.FirstScheduleResponse
import com.example.data.model.HourlyScheduleResponse
import com.example.data.model.OccupancyCountKind
import com.example.data.model.RawScreening
import com.example.data.model.SeatAvailabilityStatus
import com.example.data.model.SeatFetchState
import com.example.data.model.calculateCinemaOccupancy
import com.example.data.model.mergeScreeningAvailability
import com.example.data.remote.PlanetQuickBookClient
import com.example.data.remote.PlanetSeatsApi
import com.example.data.remote.QuickBookFetchResult
import com.example.data.remote.parseQuickBookScheduleJson
import com.example.data.repository.CinemaRepository
import com.example.data.repository.SeatAvailabilityRepository
import com.example.data.repository.SeatFetchResult
import com.example.data.seats.SeatStatusMapper
import com.example.data.seats.SeatStatusMappingConfig
import com.example.data.seats.SeatStatusParser
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickBookApiAndDataQualityTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    private val verifiedConfig = SeatStatusMappingConfig.fromRules(
        SeatStatusMappingConfig.exactFieldRule("avail", "st", "1", SeatAvailabilityStatus.AVAILABLE),
        SeatStatusMappingConfig.exactFieldRule("occ", "st", "2", SeatAvailabilityStatus.OCCUPIED),
        SeatStatusMappingConfig.exactFieldRule("res", "st", "3", SeatAvailabilityStatus.RESERVED),
        SeatStatusMappingConfig.exactFieldRule("blk", "st", "4", SeatAvailabilityStatus.BLOCKED),
        isVerifiedAgainstWebsite = true
    )

    @Test
    fun `parseQuickBookScheduleJson extracts films, lengths, presentationIds, presentationCodes, posters, and soldOut flag`() {
        val sampleQuickBookJson = """
            {
              "body": {
                "films": [
                  {
                    "id": "ho00009001",
                    "name": "גלדיאטור 2",
                    "length": 148,
                    "posterLink": "https://www.planetcinema.co.il/x/gladiator2.jpg"
                  },
                  {
                    "id": "ho00009002",
                    "name": "וויקד",
                    "length": 160,
                    "posterLink": "https://www.planetcinema.co.il/x/wicked.jpg"
                  }
                ],
                "events": [
                  {
                    "id": "359718",
                    "filmId": "ho00009001",
                    "eventDateTime": "2026-11-12T19:30:00",
                    "auditorium": "7",
                    "soldOut": false,
                    "presentationCode": "PC-77",
                    "bookingLink": "https://www.planetcinema.co.il/il/booking?presentationId=359718",
                    "businessDay": "2026-11-12"
                  },
                  {
                    "id": "359799",
                    "filmId": "ho00009002",
                    "eventDateTime": "2026-11-12T22:00:00",
                    "auditorium": "אולם 5",
                    "soldOut": true,
                    "businessDay": "2026-11-12"
                  }
                ]
              }
            }
        """.trimIndent()

        val date = LocalDate.of(2026, 11, 12)
        val parsed = parseQuickBookScheduleJson(sampleQuickBookJson, date)

        assertEquals(2, parsed.screenings.size)
        assertEquals("https://www.planetcinema.co.il/x/gladiator2.jpg", parsed.posterByTitle["גלדיאטור 2"])

        val first = parsed.screenings[0]
        assertEquals("גלדיאטור 2", first.title)
        assertEquals("אולם 7", first.room)
        assertEquals("19:30", first.start)
        assertEquals("21:58", first.end)
        assertEquals("359718", first.presentationId)
        assertEquals("PC-77", first.presentationCode)
        // Not sold out -> must NOT invent availability ratio
        assertNull(first.availabilityRatio)

        val second = parsed.screenings[1]
        assertEquals("וויקד", second.title)
        assertEquals("אולם 5", second.room)
        assertEquals("22:00", second.start)
        assertEquals("00:40", second.end)
        assertEquals("359799", second.presentationId)
        // Binary soldOut=true -> 0.0f availability ratio
        assertEquals(0.0f, second.availabilityRatio!!, 0.0001f)
    }

    @Test
    fun `mergeAllScheduleSources recovers missing screenings and presentationIds from official QuickBook feed`() {
        val repo = CinemaRepository()
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)

        // Gist first-schedule has Movie A without presentationId and is missing Movie B completely
        val base = FirstScheduleResponse(
            date = "2026-11-12",
            screenings = listOf(
                RawScreening("Movie A", "אולם 7", "19:00", "21:30", 0.50f, presentationId = null)
            )
        )
        val hourly = HourlyScheduleResponse(screenings = emptyList())
        val quickBook = listOf(
            RawScreening("Movie A", "אולם 7", "19:00", "21:30", null, presentationId = "pid_qb_1", presentationCode = "C1"),
            RawScreening("Movie B", "אולם 21 (VIP)", "19:30", "21:30", 0.0f, presentationId = "pid_qb_2", presentationCode = "C2")
        )

        val merged = repo.mergeAllScheduleSources(
            baseResponse = base,
            hourlyResponse = hourly,
            quickBookScreenings = quickBook,
            defaultCinemaDate = date,
            nowMinutes = 1200,
            nowZoned = now
        )

        assertEquals(2, merged.screenings.size)
        assertEquals(1, merged.recoveredMissingFromQuickBook)
        assertEquals(1, merged.recoveredPidsFromQuickBook)

        val movieA = merged.screenings.first { it.title == "Movie A" }
        assertEquals("pid_qb_1", movieA.presentationId)
        assertEquals("C1", movieA.presentationCode)
        // Must still preserve base 0.50 ratio without QuickBook null overwriting it!
        assertEquals(0.50f, movieA.availabilityRatio!!, 0.001f)

        val movieB = merged.screenings.first { it.title == "Movie B" }
        assertEquals(AvailabilitySource.OFFICIAL_QUICKBOOK, movieB.availabilitySource)
        assertEquals("pid_qb_2", movieB.presentationId)
        assertEquals(54, movieB.estimatedOccupiedSeats()) // VIP Hall 21 (54 cap), soldOut=0.0f -> 54
    }

    @Test
    fun `unverified partial seats-statusV2 response never shrinks HALL_CAPACITIES or ruins schedule ratio estimate`() {
        val repo = CinemaRepository()
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)

        // Hall 7 original capacity is 311, broken=2 -> usable=309. Feed says 0.50 ratio -> 155 occupied.
        val raw = listOf(
            RawScreening("Big Film", "אולם 7", "19:00", "21:30", 0.50f, presentationId = "777")
        )
        val initial = repo.prepareScreenings(raw, nowMinutes = 1200, defaultDate = date, nowZoned = now)
        assertEquals(311, initial[0].roomCapacity())
        assertEquals(309, initial[0].usableHallCapacity())
        assertEquals(155, initial[0].estimatedOccupiedSeats())

        // Imagine seats-statusV2 only returned 4 open seat objects with unverified tg=1 / svga=1 fields
        val partialUnverifiedJson = """
            {"S":{"1":{"n":"אולם 7","G":{"0":{"R":{"1":{"n":"1","S":{
              "1":{"n":"1","tg":1,"svga":1},
              "2":{"n":"2","tg":1},
              "3":{"n":"3","hc":1},
              "4":{"n":"4","tg":1}
            }}}}}}}}
        """.trimIndent()
        val unverifiedSummary = SeatStatusParser(SeatStatusMapper(SeatStatusMappingConfig.UNVERIFIED_DEFAULT))
            .parse(partialUnverifiedJson, presentationId = "777", httpStatusCode = 200)
            .getOrThrow()

        assertTrue(unverifiedSummary.observedPropertyKeys.containsAll(listOf("tg", "svga", "hc")))

        val merged = mergeScreeningAvailability(
            screenings = initial,
            seatSummariesByKey = mapOf("777" to unverifiedSummary)
        )
        val screening = merged[0]

        // Critical bug check: original capacity must STILL be 311 (NOT shrunk to 4!) and usable 309
        assertEquals(311, screening.roomCapacity())
        assertEquals(309, screening.usableHallCapacity())
        assertEquals(155, screening.estimatedOccupiedSeats())
        assertEquals(OccupancyCountKind.RATIO_ESTIMATE, screening.occupancyCountKind())
        assertEquals(200, screening.seatApiHttpCode)
    }

    @Test
    fun `occupancy separates verified occupied seats from ratio estimates and computes completeness`() {
        val repo = CinemaRepository()
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)

        val raw = listOf(
            RawScreening("Verified Show", "אולם 5", "19:00", "21:30", null, presentationId = "v1"),
            RawScreening("Ratio Show", "אולם 21 (VIP)", "19:00", "21:30", 0.5f, presentationId = "e1"),
            RawScreening("Missing Show", "אולם 1", "19:00", "21:30", null, presentationId = null)
        )
        val verifiedJson = """
            {"S":{"1":{"n":"אולם 5","G":{"0":{"R":{"1":{"n":"1","S":{
              "1":{"n":"1","st":2},
              "2":{"n":"2","st":2},
              "3":{"n":"3","st":3},
              "4":{"n":"4","st":1}
            }}}}}}}}
        """.trimIndent()
        val summary = SeatStatusParser(SeatStatusMapper(verifiedConfig))
            .parse(verifiedJson, presentationId = "v1", httpStatusCode = 200)
            .getOrThrow()

        val initial = repo.prepareScreenings(raw, nowMinutes = 1200, defaultDate = date, nowZoned = now)
        val merged = mergeScreeningAvailability(
            screenings = initial,
            seatSummariesByKey = mapOf("v1" to summary)
        )

        val result = calculateCinemaOccupancy(merged, now, lastUpdatedEpochMs = 123456L)

        assertEquals(3, result.activeScreenings)
        assertEquals(2, result.countedScreenings)
        assertEquals(1, result.verifiedScreeningsCount)
        assertEquals(1, result.estimatedScreeningsCount)
        assertEquals(1, result.missingScreenings)
        assertEquals(1, result.missingPresentationIdScreenings)
        assertEquals(0, result.failedScreenings)
        assertEquals(3, result.verifiedOccupiedSeats) // 2 occupied + 1 reserved
        assertEquals(27, result.estimatedOccupiedSeats) // VIP 54 * 0.5
        assertEquals(30, result.occupiedSeats)
        assertEquals(67, result.completenessPercentage)
        assertEquals(123456L, result.lastUpdatedEpochMs)
        assertFalse(result.isComplete)
    }

    @Test
    fun `expired seat cache is never returned as live Success even inside cooldown window`() = runTest {
        var fakeClockMs = 1_000_000L
        var throwOnRefresh = false
        val fakeApi = object : PlanetSeatsApi {
            override suspend fun getSeatsStatus(
                presentationId: String,
                venueTypeId: Int,
                isReserved: Int
            ): Response<ResponseBody> {
                if (throwOnRefresh) throw IOException("Timeout")
                return Response.success(
                    """{"S":{"1":{"n":"אולם 1","G":{"0":{"R":{"1":{"n":"1","S":{"1":{"n":"1","st":1}}}}}}}}}"""
                        .toResponseBody("application/json".toMediaType())
                )
            }
        }
        val repo = SeatAvailabilityRepository(
            api = fakeApi,
            parser = SeatStatusParser(SeatStatusMapper(verifiedConfig)),
            nowProvider = { fakeClockMs },
            minCooldownMs = 60_000L,
            interRequestDelayMs = 0L
        )

        val r1 = repo.fetchSeatAvailability("p_ttl", cacheTtlMs = 10_000L)
        assertTrue(r1 is SeatFetchResult.Success)
        assertEquals(200, (r1 as SeatFetchResult.Success).httpCode)

        // Advance past TTL (15s > 10s) while still inside cooldown (15s < 60s), and simulate network failure:
        // Must NOT return the expired cache entry!
        fakeClockMs += 15_000L
        throwOnRefresh = true
        val r2 = repo.fetchSeatAvailability("p_ttl", cacheTtlMs = 10_000L)
        assertTrue(r2 is SeatFetchResult.NetworkError)
        assertNull(repo.getCachedSummary("p_ttl", maxAgeMs = 10_000L))
    }

    @Test
    fun `multi-snapshot time-series tracks occupancy rising on bookings, dropping on release, and dropping when screening ends`() {
        val repo = CinemaRepository()
        val parser = SeatStatusParser(SeatStatusMapper(verifiedConfig))
        val date = LocalDate.of(2026, 11, 12)

        fun buildSeatsJson(occupiedCount: Int, reservedCount: Int, availableCount: Int): String {
            var idx = 1
            val entries = mutableListOf<String>()
            repeat(occupiedCount) {
                entries.add("\"$idx\":{\"n\":\"$idx\",\"st\":2}")
                idx++
            }
            repeat(reservedCount) {
                entries.add("\"$idx\":{\"n\":\"$idx\",\"st\":3}")
                idx++
            }
            repeat(availableCount) {
                entries.add("\"$idx\":{\"n\":\"$idx\",\"st\":1}")
                idx++
            }
            return """{"S":{"1":{"n":"אולם 3","G":{"0":{"R":{"1":{"n":"1","S":{${entries.joinToString(",")}}}}}}}}}"""
        }

        // Two screenings: Show 1 runs 18:00..19:45 (+15m -> 20:00), Show 2 runs 19:00..21:00 (+15m -> 21:15)
        val raw = listOf(
            RawScreening("Show One", "אולם 3", "18:00", "19:45", null, presentationId = "s1"),
            RawScreening("Show Two", "אולם 5", "19:00", "21:00", null, presentationId = "s2")
        )

        // T0 (19:15): Show 1 has 2 occ + 1 res = 3; Show 2 has 4 occ + 0 res = 4 -> Total = 7
        val t0 = ZonedDateTime.of(2026, 11, 12, 19, 15, 0, 0, jerusalem)
        val baseT0 = repo.prepareScreenings(raw, nowMinutes = 1155, defaultDate = date, nowZoned = t0)
        val s1T0 = parser.parse(buildSeatsJson(2, 1, 7), "s1").getOrThrow()
        val s2T0 = parser.parse(buildSeatsJson(4, 0, 6), "s2").getOrThrow()
        val occT0 = calculateCinemaOccupancy(
            mergeScreeningAvailability(baseT0, seatSummariesByKey = mapOf("s1" to s1T0, "s2" to s2T0)),
            t0
        )
        assertEquals(2, occT0.activeScreenings)
        assertEquals(7, occT0.occupiedSeats)

        // T1 (19:25): More tickets bought (+3 seats on Show 2 -> 7 occ) => Total rises 7 -> 10
        val t1 = ZonedDateTime.of(2026, 11, 12, 19, 25, 0, 0, jerusalem)
        val s2T1 = parser.parse(buildSeatsJson(7, 0, 3), "s2").getOrThrow()
        val occT1 = calculateCinemaOccupancy(
            mergeScreeningAvailability(baseT0, seatSummariesByKey = mapOf("s1" to s1T0, "s2" to s2T1)),
            t1
        )
        assertEquals(10, occT1.occupiedSeats)
        assertTrue(occT1.occupiedSeats > occT0.occupiedSeats)

        // T2 (19:35): Held reserved seat in Show 1 expires/releases (-1) & 1 ticket cancelled in Show 2 (-1) => drops 10 -> 8
        val t2 = ZonedDateTime.of(2026, 11, 12, 19, 35, 0, 0, jerusalem)
        val s1T2 = parser.parse(buildSeatsJson(2, 0, 8), "s1").getOrThrow()
        val s2T2 = parser.parse(buildSeatsJson(6, 0, 4), "s2").getOrThrow()
        val occT2 = calculateCinemaOccupancy(
            mergeScreeningAvailability(baseT0, seatSummariesByKey = mapOf("s1" to s1T2, "s2" to s2T2)),
            t2
        )
        assertEquals(8, occT2.occupiedSeats)
        assertTrue(occT2.occupiedSeats < occT1.occupiedSeats)

        // T3 (20:05): Show 1 ended at 20:00 (19:45 + 15m EXTRA_END) -> excluded cleanly, only Show 2 (6 seats) remains!
        val t3 = ZonedDateTime.of(2026, 11, 12, 20, 5, 0, 0, jerusalem)
        val occT3 = calculateCinemaOccupancy(
            mergeScreeningAvailability(baseT0, seatSummariesByKey = mapOf("s1" to s1T2, "s2" to s2T2)),
            t3
        )
        assertEquals(1, occT3.activeScreenings)
        assertEquals(6, occT3.occupiedSeats)
    }

    @Test
    fun `QuickBook HTTP 403 Cloudflare block surfaces transparently without breaking Gist schedule load`() = runTest {
        val blockedClient = object : PlanetQuickBookClient {
            override suspend fun fetchDailySchedule(
                cinemaDate: LocalDate,
                cinemaId: String
            ): QuickBookFetchResult {
                return QuickBookFetchResult.HttpBlockedOrError(403, "WAF blocked")
            }
        }
        val repo = CinemaRepository(quickBookClient = blockedClient)
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, jerusalem)
        val res = blockedClient.fetchDailySchedule(now.toLocalDate())
        assertTrue(res is QuickBookFetchResult.HttpBlockedOrError)
        assertEquals(403, (res as QuickBookFetchResult.HttpBlockedOrError).httpCode)
        // Repository constructs cleanly with injected client
        assertTrue(repo.normalizeCinemaMinutes(120) == 1560)
    }
}
