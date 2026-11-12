package com.example.data.diagnostics

import com.squareup.moshi.JsonClass

enum class SourceFreshnessState {
    CURRENT,
    CACHED,
    EXPIRED,
    FAILED,
    CLOUDFLARE_BLOCKED,
    NOT_QUERIED
}

@JsonClass(generateAdapter = true)
data class SourceFreshnessInfo(
    val sourceName: String,
    val endpointUrl: String,
    val freshnessState: SourceFreshnessState,
    val httpStatusCode: Int? = null,
    val fetchedAtEpochMs: Long? = null,
    val fetchedAtIso: String? = null,
    val ageMs: Long? = null,
    val ageSeconds: Long? = null,
    val ttlMs: Long? = null,
    val detail: String? = null
)

@JsonClass(generateAdapter = true)
data class SourceDisagreement(
    val field: String,
    val firstUrlValue: String? = null,
    val hourlyUrlValue: String? = null,
    val quickBookValue: String? = null,
    val seatsApiValue: String? = null,
    val trustedSource: String,
    val trustedValue: String? = null,
    val reason: String
)

/**
 * Raw per-source trace attached to a Screening during schedule merging and seat enrichment
 * so downstream validation can compare sources without discarding disagreements.
 */
@JsonClass(generateAdapter = true)
data class ScreeningSourceSnapshot(
    val cinemaId: String = "1072",
    val cinemaDate: String? = null,
    val inFirstUrl: Boolean = false,
    val firstUrlRatio: Float? = null,
    val firstUrlPresentationId: String? = null,
    val firstUrlEnd: String? = null,
    val inHourlyUrl: Boolean = false,
    val hourlyUrlRatio: Float? = null,
    val hourlyUrlPresentationId: String? = null,
    val hourlyUrlEnd: String? = null,
    val inQuickBook: Boolean = false,
    val quickBookPresentationId: String? = null,
    val quickBookPresentationCode: String? = null,
    val quickBookSoldOut: Boolean? = null,
    val quickBookEnd: String? = null,
    val seatApiAttempted: Boolean = false,
    val seatApiFromCache: Boolean = false,
    val seatApiHttpCode: Int? = null,
    val seatApiFetchedAtEpochMs: Long? = null,
    val seatApiTtlMs: Long? = null,
    val seatApiError: String? = null
)

@JsonClass(generateAdapter = true)
data class ActiveScreeningSnapshotEntry(
    val room: String,
    val roomNumber: Int?,
    val movieTitle: String,
    val startTime: String,
    val endTime: String,
    val cinemaId: String = "1072",
    val screeningDate: String? = null,
    val presentationId: String?,
    val presentationCode: String? = null,
    val officialRoomCapacity: Int?,
    val firstUrlAvailabilityRatio: Float? = null,
    val hourlyUrlAvailabilityRatio: Float? = null,
    val effectiveGistAvailabilityRatio: Float? = null,
    val quickBookSoldOut: Boolean? = null,
    val seatApiStatus: String,
    val seatApiHttpCode: Int? = null,
    val seatApiFreshness: SourceFreshnessState = SourceFreshnessState.NOT_QUERIED,
    val seatApiAgeSeconds: Long? = null,
    val totalRawSeatObjects: Int? = null,
    val verifiedOccupiedSeats: Int? = null,
    val reservedSeats: Int? = null,
    val availableSeats: Int? = null,
    val blockedSeats: Int? = null,
    val unknownSeats: Int? = null,
    val observedSeatPropertyKeys: List<String> = emptyList(),
    val estimatedOccupancy: Int? = null,
    val occupancyKind: String,
    val includedInFinalTotal: Boolean,
    val exclusionReason: String? = null,
    val trustedSource: String,
    val disagreements: List<SourceDisagreement> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CountingAccuracyVerification(
    val eachActiveScreeningCountedOnce: Boolean,
    val allBelongToExpectedCinemaAndDate: Boolean,
    val noEndedScreeningIncluded: Boolean,
    val noMissingDataCountedAsZero: Boolean,
    val noUnknownSeatClassifiedAsOccupiedOrAvailable: Boolean,
    val noPartialSeatMapMistakenForFullCapacity: Boolean,
    val allInvariantsPassed: Boolean,
    val notes: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class LiveOccupancyValidationSnapshot(
    val schemaVersion: Int = 1,
    val snapshotTimestampEpochMs: Long,
    val snapshotTimestampIsraelIso: String,
    val cinemaId: String = "1072",
    val cinemaName: String = "Planet Rishon LeZion",
    val cinemaDate: String,
    val epistemicNotice: String = "Represents estimated/booked cinema screening occupancy from schedule availability ratios and verified seat maps — NOT verified physical people inside the building.",
    val verifiedOccupiedAndReservedSeats: Int,
    val ratioEstimatedOccupancy: Int,
    val totalEstimatedOccupancy: Int,
    val activeScreeningsCount: Int,
    val countedScreeningsCount: Int,
    val unknownOccupancyScreeningsCount: Int,
    val failedSeatRequestsCount: Int,
    val missingPresentationIdCount: Int,
    val dataCompletenessPercentage: Int,
    val sourceFreshness: List<SourceFreshnessInfo>,
    val countingAccuracyVerification: CountingAccuracyVerification,
    val activeScreenings: List<ActiveScreeningSnapshotEntry>,
    val excludedOrEndedCount: Int
)

@JsonClass(generateAdapter = true)
data class ScreeningDeltaSummary(
    val slotKey: String,
    val room: String,
    val movieTitle: String,
    val previousOccupancy: Int?,
    val currentOccupancy: Int?,
    val delta: Int?
)

@JsonClass(generateAdapter = true)
data class SnapshotComparisonDelta(
    val previousTimestampIso: String,
    val currentTimestampIso: String,
    val occupancyDelta: Int,
    val verifiedSeatsDelta: Int,
    val ratioEstimatedDelta: Int,
    val activeScreeningsDelta: Int,
    val completenessDelta: Int,
    val changedScreenings: List<ScreeningDeltaSummary>
)

@JsonClass(generateAdapter = true)
data class FeedTelemetry(
    val sourceName: String,
    val endpointUrl: String,
    val httpStatusCode: Int? = null,
    val fetchedAtEpochMs: Long? = null,
    val ttlMs: Long? = null,
    val succeeded: Boolean = false,
    val fromCache: Boolean = false,
    val cloudflareBlocked: Boolean = false,
    val errorDetail: String? = null
)

data class SourceTelemetryBundle(
    val firstUrl: FeedTelemetry? = null,
    val hourlyUrl: FeedTelemetry? = null,
    val quickBook: FeedTelemetry? = null,
    val seatsApiTtlMs: Long = 4 * 60 * 1000L
)
