package com.example.data.model

/**
 * Classification of a seat parsed from Yes Planet's seats-statusV2 API.
 * Only assign non-UNKNOWN statuses when verified against the actual booking UI.
 */
enum class SeatAvailabilityStatus {
    AVAILABLE,
    OCCUPIED,
    RESERVED,
    BLOCKED,
    UNAVAILABLE,
    UNKNOWN
}

/**
 * Source of seat availability / occupancy ratio for a screening.
 */
enum class AvailabilitySource(val hebrewLabel: String) {
    LIVE_SEAT_MAP("מפת מושבים חיה"),
    HOURLY_FEED("פיד עומסים שעתי"),
    SCHEDULE_FEED("פיד לוח הקרנות"),
    OFFICIAL_QUICKBOOK("לוח רשמי (Planet)"),
    SAMPLE_FALLBACK("נתוני דוגמה (אופליין)"),
    NONE("לא ידוע")
}

/**
 * Epistemic classification of recorded attendance counts.
 */
enum class AttendanceKind(val hebrewLabel: String) {
    CONFIRMED("נתון קופה מאומת"),
    ESTIMATED("אומדן לפי תפוסת מושבים"),
    UNKNOWN("לא ידוע")
}

/**
 * Explicit outcome of fetching/interpreting seat availability for a screening.
 */
enum class SeatFetchState(val hebrewLabel: String) {
    NONE("טרם נבדק"),
    SUCCESS("התקבל בהצלחה"),
    MISSING_DATA("נתונים חסרים"),
    FAILED("שגיאת תקשורת/שרת"),
    AUTH_REQUIRED("נדרש אימות קופות"),
    MISSING_PRESENTATION_ID("חסר מזהה הקרנה"),
    UNVERIFIED_STATUSES("סטטוס מושבים לא מאומת")
}

data class SeatPosition(
    val cx: Double,
    val cy: Double
)

data class SeatLayoutMetadata(
    val seatRadius: Double? = null,
    val width: Double? = null,
    val height: Double? = null
)

/**
 * Individual seat extracted from S -> G -> R -> S in seats-statusV2.
 */
data class Seat(
    val seatKey: String,
    val roomKey: String,
    val roomName: String?,
    val groupKey: String,
    val rowKey: String,
    val rowNumber: String,
    val seatNumber: String,
    val position: SeatPosition?,
    val rawFields: Map<String, String>,
    val status: SeatAvailabilityStatus
) {
    val rawStatusSignature: String
        get() = if (rawFields.isEmpty()) {
            "(empty)"
        } else {
            rawFields.entries
                .sortedBy { it.key }
                .joinToString(", ") { "${it.key}=${it.value}" }
        }
}

/**
 * Aggregated seat status counts and availability ratio for a presentation.
 */
data class SeatAvailabilitySummary(
    val presentationId: String? = null,
    val layoutMetadata: SeatLayoutMetadata? = null,
    val seats: List<Seat> = emptyList(),
    val totalRawSeatObjects: Int = 0,
    val totalValidSeats: Int = 0,
    val availableSeats: Int = 0,
    val occupiedSeats: Int = 0,
    val reservedSeats: Int = 0,
    val blockedSeats: Int = 0,
    val unavailableSeats: Int = 0,
    val unknownSeats: Int = 0,
    val availabilityRatio: Double? = null,
    val rawStatusHistogram: Map<String, Int> = emptyMap(),
    val observedPropertyKeys: Set<String> = emptySet(),
    val hasUnverifiedStatuses: Boolean = false,
    val httpStatusCode: Int? = null,
    val fetchedAtEpochMs: Long = System.currentTimeMillis()
) {
    /**
     * Occupied + reserved seats represent booked/held seats when statuses are verified.
     * Blocked / unavailable / unknown seats are intentionally excluded from viewer counts.
     */
    val occupiedAndReservedSeats: Int
        get() = occupiedSeats + reservedSeats
}

/**
 * Calculates seat availability ratio = availableSeats / totalValidSeats.
 *
 * Rules:
 * - Excludes UNKNOWN seats from both numerator and denominator rather than guessing.
 * - Returns null if there are no reliably classified seats (totalValidSeats == 0).
 * - Clamps the result strictly within [0.0, 1.0].
 */
fun calculateAvailabilityRatio(seats: List<Seat>): Double? {
    var validSeats = 0
    var available = 0
    for (seat in seats) {
        when (seat.status) {
            SeatAvailabilityStatus.AVAILABLE -> {
                validSeats++
                available++
            }
            SeatAvailabilityStatus.OCCUPIED,
            SeatAvailabilityStatus.RESERVED,
            SeatAvailabilityStatus.BLOCKED,
            SeatAvailabilityStatus.UNAVAILABLE -> {
                validSeats++
            }
            SeatAvailabilityStatus.UNKNOWN -> {
                // Deliberately excluded from ratio calculation
            }
        }
    }
    if (validSeats == 0) return null
    return (available.toDouble() / validSeats.toDouble()).coerceIn(0.0, 1.0)
}

fun summarizeSeats(
    seats: List<Seat>,
    presentationId: String? = null,
    layoutMetadata: SeatLayoutMetadata? = null,
    hasUnverifiedStatuses: Boolean = seats.any { it.status == SeatAvailabilityStatus.UNKNOWN },
    httpStatusCode: Int? = null,
    fetchedAtEpochMs: Long = System.currentTimeMillis()
): SeatAvailabilitySummary {
    var available = 0
    var occupied = 0
    var reserved = 0
    var blocked = 0
    var unavailable = 0
    var unknown = 0
    val histogram = linkedMapOf<String, Int>()
    val propKeys = sortedSetOf<String>()

    for (seat in seats) {
        when (seat.status) {
            SeatAvailabilityStatus.AVAILABLE -> available++
            SeatAvailabilityStatus.OCCUPIED -> occupied++
            SeatAvailabilityStatus.RESERVED -> reserved++
            SeatAvailabilityStatus.BLOCKED -> blocked++
            SeatAvailabilityStatus.UNAVAILABLE -> unavailable++
            SeatAvailabilityStatus.UNKNOWN -> unknown++
        }
        val sig = seat.rawStatusSignature
        histogram[sig] = (histogram[sig] ?: 0) + 1
        propKeys.addAll(seat.rawFields.keys)
    }

    val totalValid = available + occupied + reserved + blocked + unavailable
    val ratio = calculateAvailabilityRatio(seats)

    return SeatAvailabilitySummary(
        presentationId = presentationId,
        layoutMetadata = layoutMetadata,
        seats = seats,
        totalRawSeatObjects = seats.size,
        totalValidSeats = totalValid,
        availableSeats = available,
        occupiedSeats = occupied,
        reservedSeats = reserved,
        blockedSeats = blocked,
        unavailableSeats = unavailable,
        unknownSeats = unknown,
        availabilityRatio = ratio,
        rawStatusHistogram = histogram,
        observedPropertyKeys = propKeys,
        hasUnverifiedStatuses = hasUnverifiedStatuses,
        httpStatusCode = httpStatusCode,
        fetchedAtEpochMs = fetchedAtEpochMs
    )
}
