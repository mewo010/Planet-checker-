package com.example.planetchecker

import com.example.data.model.FirstScheduleResponse
import com.example.data.model.HourlyScheduleResponse
import com.example.data.model.RawScreening
import com.example.data.repository.CinemaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class ScreeningTest {

    private val date = LocalDate.of(2026, 11, 14)

    @Test
    fun `acceptance table for estimatedOccupiedSeats matches APK exact outputs`() {
        assertEquals(
            59,
            Screening("אולם 1", "18:00", "20:00", "S1", date, 0.5).estimatedOccupiedSeats()
        )
        assertEquals(
            298,
            Screening("אולם 5", "18:00", "20:30", "S5", date, 0.25).estimatedOccupiedSeats()
        )
        assertEquals(
            9,
            Screening("9", "18:00", "20:00", "S9", date, 0.9).estimatedOccupiedSeats()
        )
        assertEquals(
            120,
            Screening("19", "18:00", "20:00", "S19", date, 0.4).estimatedOccupiedSeats()
        )
        assertEquals(
            54,
            Screening("22", "18:00", "20:00", "S22", date, 0.0).estimatedOccupiedSeats()
        )
        assertNull(
            Screening("30", "18:00", "20:00", "S30", date, 0.5).estimatedOccupiedSeats()
        )
        assertNull(
            Screening("1", "18:00", "20:00", "BadRatio", date, 1.2).estimatedOccupiedSeats()
        )
        assertNull(
            Screening("1", "18:00", "20:00", "NaNRatio", date, Double.NaN).estimatedOccupiedSeats()
        )
    }

    @Test
    fun `midnight rollover 23_30 to 00_10 (+15m = 00_25) ends next day and is showing at 00_20`() {
        val late = Screening("5", "23:30", "00:10", "Late Show", date, 0.5)
        assertEquals("00:25", late.displayEndTime())
        assertTrue(late.isShowingAt(ZonedDateTime.of(2026, 11, 14, 23, 30, 0, 0, ISRAEL_TIME_ZONE)))
        assertTrue(late.isShowingAt(ZonedDateTime.of(2026, 11, 15, 0, 20, 0, 0, ISRAEL_TIME_ZONE)))
        assertFalse(late.isShowingAt(ZonedDateTime.of(2026, 11, 15, 0, 25, 0, 0, ISRAEL_TIME_ZONE)))
        assertTrue(late.hasEndedRecentlyAt(ZonedDateTime.of(2026, 11, 15, 0, 26, 0, 0, ISRAEL_TIME_ZONE), retentionSeconds = 180))
        assertFalse(late.hasEndedRecentlyAt(ZonedDateTime.of(2026, 11, 15, 0, 28, 0, 0, ISRAEL_TIME_ZONE), retentionSeconds = 180))
    }

    @Test
    fun `asLocalTime handles valid and invalid strings`() {
        assertNull("25:00".asLocalTime())
        assertNull("abc".asLocalTime())
        assertNull("19".asLocalTime())
        assertEquals(LocalTime.of(19, 30), "19:30".asLocalTime())
        assertEquals(LocalTime.of(19, 30, 45), "19:30:45".asLocalTime())
    }

    @Test
    fun `mergeScreeningAvailability applies priority hourly then history then previous then baseline by presentationCode`() {
        val baseline = listOf(
            Screening("1", "14:00", "16:00", "A", date, availabilityRatio = 0.8, presentationCode = "C1"),
            Screening("2", "15:00", "17:00", "B", date, availabilityRatio = 0.8, presentationCode = "C2"),
            Screening("3", "16:00", "18:00", "C", date, availabilityRatio = 0.8, presentationCode = "C3"),
            Screening("4", "17:00", "19:00", "D", date, availabilityRatio = 0.75, presentationCode = "C4"),
            Screening("5", "18:00", "20:00", "NoCode", date, availabilityRatio = 0.65, presentationCode = null)
        )
        val hourly = listOf(
            Screening("1", "14:00", "16:00", "A", date, availabilityRatio = 0.1, presentationCode = "C1")
        )
        val history = listOf(
            Screening("1", "14:00", "16:00", "A", date, availabilityRatio = 0.4, presentationCode = "C1"),
            Screening("2", "15:00", "17:00", "B", date, availabilityRatio = 0.2, presentationCode = "C2")
        )
        val previous = listOf(
            Screening("2", "15:00", "17:00", "B", date, availabilityRatio = 0.5, presentationCode = "C2"),
            Screening("3", "16:00", "18:00", "C", date, availabilityRatio = 0.3, presentationCode = "C3")
        )

        val merged = mergeScreeningAvailability(baseline, hourly, previous, history)
        assertEquals(5, merged.size)
        assertEquals(0.1, merged[0].availabilityRatio!!, 0.0001) // hourly wins
        assertEquals(0.2, merged[1].availabilityRatio!!, 0.0001) // history wins over previous
        assertEquals(0.3, merged[2].availabilityRatio!!, 0.0001) // previous wins over baseline
        assertEquals(0.75, merged[3].availabilityRatio!!, 0.0001) // baseline preserved
        assertEquals(0.65, merged[4].availabilityRatio!!, 0.0001) // no presentationCode -> baseline
    }

    @Test
    fun `aggregations, nextScreeningInSameRoom, millisecondsUntilNextQuarterHour, and stableKey behave as specified`() {
        val screenings = listOf(
            Screening("1", "14:00", "16:00", "Film A", date, 0.5, "C1"),  // 59
            Screening("5", "19:00", "21:30", "Film B", date, 0.25, "C2"), // 298 (live at 20:00)
            Screening("22", "21:00", "23:00", "Film A", date, 0.0, "C3"), // 54 (future at 20:00)
            Screening("1", "17:30", "19:30", "Film C", date, 0.5, "C4")   // 59 (next in Hall 1 after 14:00)
        )
        val now2000 = ZonedDateTime.of(2026, 11, 14, 20, 0, 0, 0, ISRAEL_TIME_ZONE)

        assertEquals(298, currentlyOccupiedSeats(screenings, now2000))
        assertEquals(59 + 298 + 54 + 59, estimatedAudienceForDate(screenings, date))
        assertEquals(54, estimatedAudienceRemainingToday(screenings, now2000))

        val formats = audienceByRoomType(screenings, date)
        assertEquals(listOf("2D", "IMAX", "ScreenX", "VIP", "4DX"), formats.map { it.label })
        assertEquals(118, formats.first { it.label == "2D" }.people)
        assertEquals(298, formats.first { it.label == "IMAX" }.people)
        assertEquals(0, formats.first { it.label == "ScreenX" }.people)
        assertEquals(54, formats.first { it.label == "VIP" }.people)
        assertEquals(0, formats.first { it.label == "4DX" }.people)

        val movies = dailyMovieStats(screenings, date)
        assertEquals("Film B", movies[0].title)
        assertEquals(298, movies[0].people)
        assertEquals(1, movies[0].showings)
        assertEquals("Film A", movies[1].title)
        assertEquals(59 + 54, movies[1].people)
        assertEquals(2, movies[1].showings)

        val now1700 = ZonedDateTime.of(2026, 11, 14, 17, 0, 0, 0, ISRAEL_TIME_ZONE)
        val nextInHall1 = screenings[0].nextScreeningInSameRoom(screenings, now1700)
        assertNotNull(nextInHall1)
        assertEquals("Film C", nextInHall1!!.title)
        assertNull(screenings[0].nextScreeningInSameRoom(screenings, ZonedDateTime.of(2026, 11, 14, 15, 0, 0, 0, ISRAEL_TIME_ZONE)))

        assertEquals(10 * 60_000L, millisecondsUntilNextQuarterHour(ZonedDateTime.of(2026, 11, 14, 14, 5, 0, 0, ISRAEL_TIME_ZONE)))
        assertEquals("2026-11-14|1|14:00|Film A", screenings[0].stableKey())

        // Verify CinemaRepository.mergeGistBaselineWithSources matches identical counts
        val repo = CinemaRepository()
        val repoMerged = repo.mergeGistBaselineWithSources(
            baseResponse = FirstScheduleResponse(
                date = "2026-11-14",
                screenings = listOf(RawScreening("Film B", "אולם 5", "19:00", "21:30", 0.9f, presentationCode = "C2"))
            ),
            hourlyResponse = HourlyScheduleResponse(
                screenings = emptyList()
            ),
            historyResponse = HourlyScheduleResponse(
                screenings = listOf(RawScreening("Film B", "אולם 5", "19:00", "21:30", 0.25f, presentationCode = "C2"))
            ),
            defaultCinemaDate = date,
            nowMinutes = 1200,
            nowZoned = now2000
        )
        assertEquals(298, repoMerged.screenings.single().estimatedOccupiedSeats())
    }
}
