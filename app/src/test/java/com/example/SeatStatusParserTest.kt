package com.example

import com.example.data.model.AvailabilitySource
import com.example.data.model.CrowdLevel
import com.example.data.model.RawScreening
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.model.SeatAvailabilityStatus
import com.example.data.model.calculateAvailabilityRatio
import com.example.data.model.currentlyOccupiedSeats
import com.example.data.model.mergeScreeningAvailability
import com.example.data.repository.CinemaRepository
import com.example.data.seats.SeatStatusMapper
import com.example.data.seats.SeatStatusMappingConfig
import com.example.data.seats.SeatStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SeatStatusParserTest {

    private val verifiedTestConfig = SeatStatusMappingConfig.fromRules(
        SeatStatusMappingConfig.exactFieldRule("occupied_st", "st", "2", SeatAvailabilityStatus.OCCUPIED),
        SeatStatusMappingConfig.exactFieldRule("reserved_st", "st", "3", SeatAvailabilityStatus.RESERVED),
        SeatStatusMappingConfig.exactFieldRule("blocked_st", "st", "4", SeatAvailabilityStatus.BLOCKED),
        SeatStatusMappingConfig.exactFieldRule("available_st", "st", "1", SeatAvailabilityStatus.AVAILABLE),
        isVerifiedAgainstWebsite = true
    )

    @Test
    fun `parse supplied seats-statusV2 reference payload and preserve unverified raw fields`() {
        val sampleJson = """
            {
              "rd": {
                "seatRadius": 30,
                "width": 1650,
                "height": 1170
              },
              "S": {
                "1": {
                  "r": 1,
                  "n": "אולם 7",
                  "G": {
                    "0": {
                      "R": {
                        "2": {
                          "n": "1",
                          "S": {
                            "4": {
                              "n": "22",
                              "tg": 1,
                              "rd": {
                                "cx": 240,
                                "cy": 48
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              "E": [],
              "ST": {
                "SJ": 1
              }
            }
        """.trimIndent()

        // Default unverified mapper must NOT assume tg=1 is AVAILABLE
        val parser = SeatStatusParser(SeatStatusMapper(SeatStatusMappingConfig.UNVERIFIED_DEFAULT))
        val result = parser.parse(sampleJson, presentationId = "359718")
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals("359718", summary.presentationId)
        assertNotNull(summary.layoutMetadata)
        assertEquals(30.0, summary.layoutMetadata!!.seatRadius!!, 0.001)
        assertEquals(1650.0, summary.layoutMetadata!!.width!!, 0.001)
        assertEquals(1170.0, summary.layoutMetadata!!.height!!, 0.001)

        // Only 1 actual seat object inside R["2"].S["4"]
        assertEquals(1, summary.totalRawSeatObjects)
        assertEquals(1, summary.seats.size)

        val seat = summary.seats.first()
        assertEquals("אולם 7", seat.roomName)
        assertEquals("1", seat.rowNumber)
        assertEquals("22", seat.seatNumber)
        assertEquals("1", seat.rawFields["tg"])
        assertEquals(240.0, seat.position!!.cx, 0.001)
        assertEquals(48.0, seat.position!!.cy, 0.001)
        assertEquals(SeatAvailabilityStatus.UNKNOWN, seat.status)

        // Unknown statuses are excluded -> totalValidSeats == 0 -> availabilityRatio == null
        assertEquals(0, summary.totalValidSeats)
        assertEquals(1, summary.unknownSeats)
        assertNull(summary.availabilityRatio)
        assertTrue(summary.hasUnverifiedStatuses)
    }

    @Test
    fun `extract seats across multiple groups and rows and ignore non-seat metadata`() {
        val multiRowJson = """
            {
              "rd": { "seatRadius": 28, "width": 1200, "height": 900 },
              "S": {
                "10": {
                  "r": 1,
                  "n": "אולם 5",
                  "G": {
                    "0": {
                      "R": {
                        "1": {
                          "n": "A",
                          "S": {
                            "1": { "n": "1", "st": 1, "rd": { "cx": 100, "cy": 100 } },
                            "2": { "n": "2", "st": 2, "rd": { "cx": 140, "cy": 100 } },
                            "meta_only": { "tg": 99 }
                          }
                        },
                        "2": {
                          "n": "B",
                          "S": {
                            "3": { "n": "5", "st": 1, "rd": { "cx": 100, "cy": 150 } }
                          }
                        }
                      }
                    },
                    "1": {
                      "R": {
                        "3": {
                          "n": "C",
                          "S": {
                            "4": { "n": "10", "st": 3, "rd": { "cx": 200, "cy": 220 } },
                            "5": { "n": "11", "st": 4, "rd": { "cx": 240, "cy": 220 } }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parser = SeatStatusParser(SeatStatusMapper(verifiedTestConfig))
        val summary = parser.parse(multiRowJson, presentationId = "777").getOrThrow()

        // meta_only has neither seat number "n" nor "rd" position, so it must be excluded
        assertEquals(5, summary.totalRawSeatObjects)
        assertEquals(5, summary.totalValidSeats)
        assertEquals(2, summary.availableSeats)
        assertEquals(1, summary.occupiedSeats)
        assertEquals(1, summary.reservedSeats)
        assertEquals(1, summary.blockedSeats)
        assertEquals(0, summary.unknownSeats)
        assertFalse(summary.hasUnverifiedStatuses)

        // 2 available out of 5 valid seats = 0.4
        assertEquals(0.4, summary.availabilityRatio!!, 0.0001)
        assertEquals(2, summary.occupiedAndReservedSeats)
    }

    @Test
    fun `exclude unknown statuses from calculateAvailabilityRatio and keep ratio within 0 to 1`() {
        val mixedJson = """
            {
              "S": {
                "1": {
                  "n": "אולם 3",
                  "G": {
                    "0": {
                      "R": {
                        "1": {
                          "n": "1",
                          "S": {
                            "1": { "n": "1", "st": 1 },
                            "2": { "n": "2", "st": 1 },
                            "3": { "n": "3", "st": 2 },
                            "4": { "n": "4", "hc": 1 },
                            "5": { "n": "5", "tg": 1 }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parser = SeatStatusParser(SeatStatusMapper(verifiedTestConfig))
        val summary = parser.parse(mixedJson).getOrThrow()

        // 2 available, 1 occupied, 2 unknown (hc=1 and tg=1 are NOT matched by verified rules)
        assertEquals(5, summary.totalRawSeatObjects)
        assertEquals(3, summary.totalValidSeats)
        assertEquals(2, summary.availableSeats)
        assertEquals(1, summary.occupiedSeats)
        assertEquals(2, summary.unknownSeats)
        assertEquals(2.0 / 3.0, calculateAvailabilityRatio(summary.seats)!!, 0.0001)
        assertTrue(summary.availabilityRatio!! in 0.0..1.0)
    }

    @Test
    fun `handle empty and malformed API responses safely`() {
        val parser = SeatStatusParser()
        assertTrue(parser.parse("").isFailure)
        assertTrue(parser.parse("   ").isFailure)
        assertTrue(parser.parse("{not-valid-json").isFailure)

        val emptyScreens = parser.parse("""{"S":{},"E":[]}""").getOrThrow()
        assertEquals(0, emptyScreens.totalRawSeatObjects)
        assertEquals(0, emptyScreens.totalValidSeats)
        assertNull(emptyScreens.availabilityRatio)
    }

    @Test
    fun `verify presentationId extraction from RawScreening without inventing ids`() {
        val withDirectPid = RawScreening(
            title = "Movie A",
            room = "אולם 5",
            start = "18:00",
            end = "20:00",
            presentationId = "359718"
        )
        assertEquals("359718", withDirectPid.resolvedPresentationId())

        val withBookingUrl = RawScreening(
            title = "Movie B",
            room = "אולם 7",
            start = "19:00",
            end = "21:00",
            bookingUrl = "https://www.planetcinema.co.il/order?presentationId=987654&venueTypeId=1"
        )
        assertEquals("987654", withBookingUrl.resolvedPresentationId())

        val withoutPid = RawScreening(
            title = "Movie C",
            room = "אולם 1",
            start = "20:00",
            end = "22:00"
        )
        assertNull(withoutPid.resolvedPresentationId())
    }

    @Test
    fun `verify screening time functions, isShowingAt, currentlyOccupiedSeats, and mergeScreeningAvailability`() {
        val repo = CinemaRepository()
        val zone = ZoneId.of("Asia/Jerusalem")
        val targetDate = LocalDate.of(2026, 11, 12)

        // Screenings at 18:00-20:00 (+15 EXTRA_END -> ends at 20:15 = 1215) and 21:00-23:00
        val raw = listOf(
            RawScreening("Movie Live", "אולם 7", "18:00", "20:00", 0.5f, presentationId = "1001"),
            RawScreening("Movie Upcoming", "אולם 5", "21:00", "23:00", 0.2f, presentationId = "1002"),
            RawScreening("Movie Unknown", "אולם 1", "18:30", "20:30", null, presentationId = "1003")
        )

        // Now = 19:00 (1140 mins) on 2026-11-12 Israel time
        val now = ZonedDateTime.of(2026, 11, 12, 19, 0, 0, 0, zone)
        val screenings = repo.prepareScreenings(raw, nowMinutes = 1140, defaultDate = targetDate)

        assertEquals(3, screenings.size)
        assertTrue(screenings[0].isShowingAt(now))
        assertFalse(screenings[1].isShowingAt(now))
        assertTrue(screenings[2].isShowingAt(now))

        // Movie Unknown has null ratio -> estimatedOccupiedSeats must be null (never 0 or fake)
        assertNull(screenings[2].estimatedOccupiedSeats())

        // Hall 7 original capacity = 311, broken = 2 -> usable = 309, 0.5 availability -> ~155 occupied initially
        assertEquals(311, screenings[0].roomCapacity())
        assertEquals(309, screenings[0].usableHallCapacity())
        assertEquals(155, screenings[0].estimatedOccupiedSeats())

        // Merge live seat map for presentationId 1001: 100 seats total (80 available, 15 occupied, 5 reserved)
        val liveSeatJson = """
            {
              "S": {
                "1": {
                  "n": "אולם 7",
                  "G": {
                    "0": {
                      "R": {
                        "1": {
                          "n": "1",
                          "S": {
                            "1": { "n": "1", "st": 1 },
                            "2": { "n": "2", "st": 1 },
                            "3": { "n": "3", "st": 2 },
                            "4": { "n": "4", "st": 3 }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val liveSummary = SeatStatusParser(SeatStatusMapper(verifiedTestConfig))
            .parse(liveSeatJson, presentationId = "1001")
            .getOrThrow()

        val merged = mergeScreeningAvailability(
            screenings = screenings,
            seatSummariesByKey = mapOf("1001" to liveSummary)
        )

        val liveMerged = merged[0]
        // Prefers actual seat count (4) over hardcoded HALL_CAPACITIES (311)
        assertEquals(4, liveMerged.roomCapacity())
        assertEquals(2, liveMerged.estimatedOccupiedSeats()) // 1 occupied + 1 reserved
        assertEquals(AvailabilitySource.LIVE_SEAT_MAP, liveMerged.availabilitySource)
        assertEquals(0.5f, liveMerged.availabilityRatio!!, 0.001f)
        assertEquals(CrowdLevel.MEDIUM, liveMerged.crowdLevel)

        // Currently showing: liveMerged (2 viewers) + Movie Unknown (null -> 0 contribution).
        // Movie Upcoming (21:00) is NOT showing at 19:00 -> excluded!
        assertEquals(2, currentlyOccupiedSeats(merged, now))

        // Verify post-midnight screening normalization & isShowingAt
        val lateRaw = listOf(RawScreening("Midnight Film", "אולם 2", "23:30", "01:00", 0.5f))
        val lateScreenings = repo.prepareScreenings(lateRaw, nowMinutes = 1470, defaultDate = targetDate)
        // 00:30 on 2026-11-13 belongs to cinema day 2026-11-12
        val afterMidnight = ZonedDateTime.of(2026, 11, 13, 0, 30, 0, 0, zone)
        assertTrue(lateScreenings[0].isShowingAt(afterMidnight))
    }
}
