package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PlanetDatabase
import com.example.data.model.AttendanceKind
import com.example.data.model.AvailabilitySource
import com.example.data.model.RawScreening
import com.example.data.remote.PlanetSeatsApi
import com.example.data.repository.AttendanceHistoryRepository
import com.example.data.repository.CinemaRepository
import com.example.data.repository.SeatAvailabilityRepository
import com.example.data.repository.SeatFetchResult
import com.example.data.seats.SeatStatusMapper
import com.example.data.seats.SeatStatusMappingConfig
import com.example.data.seats.SeatStatusParser
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AttendanceHistoryRobolectricTest {

    private lateinit var db: PlanetDatabase
    private lateinit var historyRepo: AttendanceHistoryRepository
    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PlanetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `seat repository handles network errors, auth required, empty bodies, and caching`() = runTest {
        var callCount = 0
        var nextBehavior: () -> Response<okhttp3.ResponseBody> = {
            throw IOException("Simulated timeout")
        }

        val fakeApi = object : PlanetSeatsApi {
            override suspend fun getSeatsStatus(
                presentationId: String,
                venueTypeId: Int,
                isReserved: Int
            ): Response<okhttp3.ResponseBody> {
                callCount++
                return nextBehavior()
            }
        }

        val verifiedConfig = SeatStatusMappingConfig.fromRules(
            SeatStatusMappingConfig.exactFieldRule("avail", "st", "1", com.example.data.model.SeatAvailabilityStatus.AVAILABLE)
        )
        var currentNow = 1_000_000L
        val seatRepo = SeatAvailabilityRepository(
            api = fakeApi,
            parser = SeatStatusParser(SeatStatusMapper(verifiedConfig)),
            nowProvider = { currentNow },
            minCooldownMs = 60_000L,
            interRequestDelayMs = 0L
        )

        // 1. Missing presentationId -> no network call
        assertEquals(SeatFetchResult.MissingPresentationId, seatRepo.fetchSeatAvailability(null))
        assertEquals(0, callCount)

        // 2. Network IOException -> NetworkError
        val err = seatRepo.fetchSeatAvailability("100")
        assertTrue(err is SeatFetchResult.NetworkError)
        assertEquals(1, callCount)

        // 3. HTTP 403 -> AuthRequired
        nextBehavior = {
            Response.error(403, "Forbidden".toResponseBody("text/plain".toMediaType()))
        }
        val authErr = seatRepo.fetchSeatAvailability("100")
        assertTrue(authErr is SeatFetchResult.AuthRequired)
        assertEquals(2, callCount)

        // 4. Empty body -> EmptyOrInvalid
        nextBehavior = {
            Response.success("".toResponseBody("application/json".toMediaType()))
        }
        val emptyRes = seatRepo.fetchSeatAvailability("100")
        assertTrue(emptyRes is SeatFetchResult.EmptyOrInvalid)

        // 5. Valid payload -> Success & cached on repeated call within TTL
        val validPayload = """
            {"S":{"1":{"n":"אולם 7","G":{"0":{"R":{"1":{"n":"1","S":{"1":{"n":"1","st":1}}}}}}}}}
        """.trimIndent()
        nextBehavior = {
            Response.success(validPayload.toResponseBody("application/json".toMediaType()))
        }
        val ok1 = seatRepo.fetchSeatAvailability("200")
        assertTrue(ok1 is SeatFetchResult.Success)
        assertFalse((ok1 as SeatFetchResult.Success).fromCache)
        val callsBeforeCache = callCount

        currentNow += 5_000L
        val ok2 = seatRepo.fetchSeatAvailability("200")
        assertTrue(ok2 is SeatFetchResult.Success)
        assertTrue((ok2 as SeatFetchResult.Success).fromCache)
        assertEquals(callsBeforeCache, callCount)
    }

    @Test
    fun `daily attendance preserves pre-end snapshots, marks unmonitored ended screenings UNKNOWN, and avoids duplicates`() = runTest {
        var fakeClockMs = 1_700_000_000_000L
        historyRepo = AttendanceHistoryRepository(db.attendanceDao()) { fakeClockMs }
        val cinemaRepo = CinemaRepository()

        val date = LocalDate.of(2026, 11, 12)
        val now1630 = ZonedDateTime.of(2026, 11, 12, 16, 30, 0, 0, jerusalem) // 990 min

        val raw = listOf(
            // Already ended long before monitoring started at 16:30 (11:00-13:00+15 = 795)
            RawScreening("Already Ended", "אולם 1", "11:00", "13:00", 0.90f, presentationId = "p1"),
            // Currently active at 16:30 (15:00-16:30+15 = 900..1005 -> near end, 15 mins left!)
            RawScreening("Live Near End", "אולם 7", "15:00", "16:30", 0.50f, presentationId = "p2"),
            // Upcoming at 19:00
            RawScreening("Evening Show", "אולם 5", "19:00", "21:00", 0.25f, presentationId = "p3")
        )

        val batch1 = cinemaRepo.prepareScreenings(raw, nowMinutes = 990, defaultDate = date)
        historyRepo.recordLiveObservation(batch1, date, now1630, cinemaNowMin = 990)

        // Advance time by 1 hour (> 35m gap) to 17:30 -> Live Near End is now ENDED
        fakeClockMs += 60 * 60 * 1000L
        val now1730 = ZonedDateTime.of(2026, 11, 12, 17, 30, 0, 0, jerusalem) // 1050 min
        // Imagine API now returns availabilityRatio=1.0 (empty/reset) for the ended p2 screening
        val rawLater = listOf(
            RawScreening("Already Ended", "אולם 1", "11:00", "13:00", 1.0f, presentationId = "p1"),
            RawScreening("Live Near End", "אולם 7", "15:00", "16:30", 1.0f, presentationId = "p2"),
            RawScreening("Evening Show", "אולם 5", "19:00", "21:00", 0.25f, presentationId = "p3")
        )
        val batch2 = cinemaRepo.prepareScreenings(rawLater, nowMinutes = 1050, defaultDate = date)
        historyRepo.recordLiveObservation(batch2, date, now1730, cinemaNowMin = 1050)

        val stats = historyRepo.getDailyStatsOnce(
            dateIso = date.toString(),
            todayIso = date.toString(),
            liveScreenings = batch2,
            now = now1730
        )

        // Exactly 3 unique screenings, no duplicate counting
        assertEquals(3, stats.totalScreeningsToday)
        assertEquals(1, stats.monitoringGaps.size)

        val snapP1 = stats.screeningBreakdowns.first { it.presentationId == "p1" }
        // Ended BEFORE monitoring started -> MUST be UNKNOWN, NOT fabricated from 0.90f!
        assertEquals(AttendanceKind.UNKNOWN, snapP1.attendanceKind)
        assertNull(snapP1.estimatedOccupiedSeats)

        val snapP2 = stats.screeningBreakdowns.first { it.presentationId == "p2" }
        // Captured near end (50% of 309 usable = 155), MUST NOT be overwritten by post-end 1.0 availability!
        assertEquals(AttendanceKind.ESTIMATED, snapP2.attendanceKind)
        assertEquals(155, snapP2.estimatedOccupiedSeats)
        assertTrue(snapP2.isFinal)

        // Total completed attendance includes ONLY p2 (155), while p1 counts as completed unknown (1)
        assertEquals(155, stats.estimatedCompletedAttendance)
        assertEquals(1, stats.completedWithKnownAttendance)
        assertEquals(1, stats.completedWithUnknownAttendance)
    }

    @Test
    fun `sample fallback screenings are never saved into Room attendance snapshots`() = runTest {
        historyRepo = AttendanceHistoryRepository(db.attendanceDao())
        val repo = CinemaRepository()
        val date = LocalDate.of(2026, 11, 12)
        val now = ZonedDateTime.of(2026, 11, 12, 18, 0, 0, 0, jerusalem)

        val sampleScreenings = repo.prepareScreenings(
            rawList = CinemaRepository.MOCK_SCREENINGS,
            nowMinutes = 1080,
            defaultDate = date,
            defaultSource = AvailabilitySource.SAMPLE_FALLBACK
        )

        historyRepo.recordLiveObservation(sampleScreenings, date, now, cinemaNowMin = 1080)
        val stats = historyRepo.getDailyStatsOnce(date.toString(), date.toString(), emptyList(), now)
        assertEquals(0, stats.totalScreeningsToday)
    }
}
