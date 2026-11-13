package com.example.ui.seatmap

import androidx.compose.ui.graphics.Color
import com.example.data.model.AvailabilitySource
import com.example.data.model.HallCapacityInfo
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.Seat
import com.example.data.model.SeatAvailabilityStatus
import com.example.data.model.SeatAvailabilitySummary
import com.example.data.model.SeatFetchState
import com.example.data.repository.CinemaRepository
import com.example.ui.theme.PlanetAmber
import com.example.ui.theme.PlanetEmerald
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

/**
 * Distinct accessible seat status categories rendered on the interactive auditorium map.
 * Never classifies UNKNOWN seats as AVAILABLE and never classifies BROKEN seats as OCCUPIED.
 */
enum class SeatVisualStatus(
    val hebrewLabel: String,
    val shortDescription: String,
    val fillColor: Color,
    val borderColor: Color,
    val contentColor: Color
) {
    AVAILABLE(
        hebrewLabel = "פנוי",
        shortDescription = "מושב פנוי להזמנה",
        fillColor = PlanetEmerald.copy(alpha = 0.24f),
        borderColor = PlanetEmeraldLight,
        contentColor = PlanetEmeraldLight
    ),
    RESERVED_OR_BOOKED(
        hebrewLabel = "תפוס / שמור",
        shortDescription = "מושב תפוס או שמור",
        fillColor = PlanetRose.copy(alpha = 0.85f),
        borderColor = PlanetRoseLight,
        contentColor = Color.White
    ),
    BLOCKED_OR_UNAVAILABLE(
        hebrewLabel = "חסום / לא זמין",
        shortDescription = "מושב חסום או לא זמין למכירה",
        fillColor = Color(0xFF334155),
        borderColor = Color(0xFF64748B),
        contentColor = PlanetTextSecondary
    ),
    BROKEN(
        hebrewLabel = "מושב תקול",
        shortDescription = "מושב תקול / לא שמיש (אינו נספר כקהל)",
        fillColor = Color(0xFF7C2D12),
        borderColor = Color(0xFFFB923C),
        contentColor = Color(0xFFFDBA74)
    ),
    UNKNOWN(
        hebrewLabel = "לא ידוע / לא מאומת",
        shortDescription = "סטטוס מושב טרם אומת מול האתר",
        fillColor = PlanetAmber.copy(alpha = 0.20f),
        borderColor = PlanetGold.copy(alpha = 0.85f),
        contentColor = PlanetGold
    )
}

enum class AuditoriumLayoutMode {
    SPATIAL_COORDINATES,
    ROW_GRID_FALLBACK,
    UNAVAILABLE
}

enum class SeatDataFreshnessState(val hebrewLabel: String) {
    LIVE_FRESH("נתון חי עדכני"),
    CACHED("נשמר במטמון"),
    STALE("נתון מיושן — מומלץ לרענן"),
    UNVERIFIED("גולמי (סטטוס לא מאומת)"),
    UNAVAILABLE("אין מפת מושבים זמינה")
}

data class AuditoriumRowLabel(
    val id: String,
    val label: String,
    val normalizedY: Float
)

data class AuditoriumSeatUiItem(
    val id: String,
    val seatKey: String,
    val roomKey: String,
    val roomName: String?,
    val groupKey: String,
    val rowKey: String,
    val rowNumber: String,
    val seatNumber: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val visualStatus: SeatVisualStatus,
    val apiStatus: SeatAvailabilityStatus,
    val isVerified: Boolean,
    val isBrokenSeat: Boolean,
    val lastRefreshedEpochMs: Long?,
    val lastRefreshedLabel: String,
    val isFromCache: Boolean,
    val officialBookingTimestamp: String?,
    val technicalRawFields: Map<String, String>
) {
    fun exactStatusLabel(): String = when {
        isBrokenSeat -> "מושב תקול / לא שמיש"
        apiStatus == SeatAvailabilityStatus.AVAILABLE -> "פנוי"
        apiStatus == SeatAvailabilityStatus.OCCUPIED -> "תפוס"
        apiStatus == SeatAvailabilityStatus.RESERVED -> "שמור"
        apiStatus == SeatAvailabilityStatus.BLOCKED -> "חסום"
        apiStatus == SeatAvailabilityStatus.UNAVAILABLE -> "לא זמין"
        else -> "לא ידוע / לא מאומת"
    }
}

data class BrokenSeatsReconciliation(
    val originalCapacity: Int?,
    val manualBrokenSeats: Int?,
    val usableCapacity: Int?,
    val isBrokenSeatsKnown: Boolean,
    val excludedByLiveMap: Boolean,
    val blockedOrUnavailableInMap: Int,
    val omittedFromOriginal: Int,
    val unaccountedManualBrokenSeats: Int,
    val summaryMessage: String
)

data class SeatStatusCounts(
    val available: Int = 0,
    val reservedOrBooked: Int = 0,
    val blockedOrUnavailable: Int = 0,
    val broken: Int = 0,
    val unknown: Int = 0
) {
    val totalMappedSeats: Int
        get() = available + reservedOrBooked + blockedOrUnavailable + broken + unknown
}

data class AuditoriumSeatMapUiModel(
    val screeningId: String,
    val hallNumber: Int?,
    val hallTitle: String,
    val roomType: RoomType,
    val movieTitle: String,
    val timeSlotLabel: String,
    val presentationId: String?,
    val layoutMode: AuditoriumLayoutMode,
    val seats: List<AuditoriumSeatUiItem>,
    val rowLabels: List<AuditoriumRowLabel>,
    val aspectRatio: Float,
    val minHorizontalSpacingNorm: Float,
    val minVerticalSpacingNorm: Float,
    val counts: SeatStatusCounts,
    val brokenReconciliation: BrokenSeatsReconciliation,
    val freshnessState: SeatDataFreshnessState,
    val lastRefreshedLabel: String,
    val lastRefreshedEpochMs: Long?,
    val isFromCache: Boolean,
    val hasUnverifiedStatuses: Boolean,
    val seatFetchState: SeatFetchState,
    val unavailableReason: String?,
    val availabilitySource: AvailabilitySource
)

object AuditoriumSeatMapBuilder {

    private val israelZone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val refreshTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private const val STALE_THRESHOLD_MS = 15 * 60 * 1000L

    private val blockedPiiKeys = setOf(
        "name", "fullname", "firstname", "lastname", "customername",
        "phone", "phonenumber", "mobile", "tel", "telephone",
        "email", "mail", "user", "userid", "username", "customer", "customerid",
        "client", "payment", "card", "creditcard", "cvv", "iban",
        "order", "orderid", "bookingid", "ticketid", "token", "cookie",
        "auth", "authorization", "ssn", "passport", "identity"
    )

    /**
     * Builds an immutable UI model for the interactive auditorium seat map directly from
     * existing verified [Screening] + [SeatAvailabilitySummary] + [CinemaRepository.BROKEN_SEATS_BY_HALL] data.
     *
     * @param explicitBrokenSeatKeys Optional explicit per-seat keys (`seat.seatKey` or `row|seat`)
     *   if an authorized source or test designates specific broken seats. Never guessed arbitrarily.
     */
    fun build(
        screening: Screening,
        nowEpochMs: Long = System.currentTimeMillis(),
        explicitBrokenSeatKeys: Set<String> = emptySet()
    ): AuditoriumSeatMapUiModel {
        val summary = screening.seatSummary
        val hallInfo = screening.hallCapacityInfo()
        val lastUpdatedMs = summary?.fetchedAtEpochMs ?: screening.lastSeatUpdateEpochMs
        val lastUpdatedLabel = formatEpochMs(lastUpdatedMs)

        val brokenReconciliation = buildBrokenReconciliation(
            screening = screening,
            hallInfo = hallInfo,
            summary = summary,
            explicitBrokenCount = explicitBrokenSeatKeys.size
        )

        val freshnessState = resolveFreshnessState(
            summary = summary,
            seatFetchState = screening.seatFetchState,
            lastUpdatedMs = lastUpdatedMs,
            nowEpochMs = nowEpochMs,
            isFromCache = screening.seatApiFromCache
        )

        val hallTitle = RoomType.formatRoomLabel(screening.room)
        val timeSlotLabel = "${screening.startStr} - ${screening.endStr}"

        if (summary == null || summary.seats.isEmpty()) {
            val unavailableReason = buildUnavailableReason(screening, summary)
            return AuditoriumSeatMapUiModel(
                screeningId = screening.id,
                hallNumber = screening.roomNumber,
                hallTitle = hallTitle,
                roomType = screening.roomType,
                movieTitle = screening.title,
                timeSlotLabel = timeSlotLabel,
                presentationId = screening.presentationId,
                layoutMode = AuditoriumLayoutMode.UNAVAILABLE,
                seats = emptyList(),
                rowLabels = emptyList(),
                aspectRatio = 1.35f,
                minHorizontalSpacingNorm = 0.08f,
                minVerticalSpacingNorm = 0.12f,
                counts = SeatStatusCounts(),
                brokenReconciliation = brokenReconciliation,
                freshnessState = freshnessState,
                lastRefreshedLabel = lastUpdatedLabel,
                lastRefreshedEpochMs = lastUpdatedMs,
                isFromCache = screening.seatApiFromCache,
                hasUnverifiedStatuses = summary?.hasUnverifiedStatuses ?: false,
                seatFetchState = screening.seatFetchState,
                unavailableReason = unavailableReason,
                availabilitySource = screening.availabilitySource
            )
        }

        val allSeatsHaveSpatialCoords = summary.seats.all { seat ->
            val pos = seat.position
            pos != null && pos.cx.isFinite() && pos.cy.isFinite()
        }

        val layoutResult = if (allSeatsHaveSpatialCoords) {
            buildSpatialLayout(
                summary = summary,
                isFromCache = screening.seatApiFromCache,
                lastUpdatedMs = lastUpdatedMs,
                lastUpdatedLabel = lastUpdatedLabel,
                explicitBrokenSeatKeys = explicitBrokenSeatKeys
            )
        } else {
            buildGridFallbackLayout(
                summary = summary,
                isFromCache = screening.seatApiFromCache,
                lastUpdatedMs = lastUpdatedMs,
                lastUpdatedLabel = lastUpdatedLabel,
                explicitBrokenSeatKeys = explicitBrokenSeatKeys
            )
        }

        return AuditoriumSeatMapUiModel(
            screeningId = screening.id,
            hallNumber = screening.roomNumber,
            hallTitle = hallTitle,
            roomType = screening.roomType,
            movieTitle = screening.title,
            timeSlotLabel = timeSlotLabel,
            presentationId = screening.presentationId ?: summary.presentationId,
            layoutMode = if (allSeatsHaveSpatialCoords) {
                AuditoriumLayoutMode.SPATIAL_COORDINATES
            } else {
                AuditoriumLayoutMode.ROW_GRID_FALLBACK
            },
            seats = layoutResult.seats,
            rowLabels = layoutResult.rowLabels,
            aspectRatio = layoutResult.aspectRatio,
            minHorizontalSpacingNorm = layoutResult.minHorizontalSpacingNorm,
            minVerticalSpacingNorm = layoutResult.minVerticalSpacingNorm,
            counts = layoutResult.counts,
            brokenReconciliation = brokenReconciliation,
            freshnessState = freshnessState,
            lastRefreshedLabel = lastUpdatedLabel,
            lastRefreshedEpochMs = lastUpdatedMs,
            isFromCache = screening.seatApiFromCache,
            hasUnverifiedStatuses = summary.hasUnverifiedStatuses,
            seatFetchState = screening.seatFetchState,
            unavailableReason = null,
            availabilitySource = screening.availabilitySource
        )
    }

    private data class ComputedLayout(
        val seats: List<AuditoriumSeatUiItem>,
        val rowLabels: List<AuditoriumRowLabel>,
        val aspectRatio: Float,
        val minHorizontalSpacingNorm: Float,
        val minVerticalSpacingNorm: Float,
        val counts: SeatStatusCounts
    )

    private fun buildSpatialLayout(
        summary: SeatAvailabilitySummary,
        isFromCache: Boolean,
        lastUpdatedMs: Long?,
        lastUpdatedLabel: String,
        explicitBrokenSeatKeys: Set<String>
    ): ComputedLayout {
        val rawSeats = summary.seats
        val minX = rawSeats.minOf { it.position!!.cx }
        val maxX = rawSeats.maxOf { it.position!!.cx }
        val minY = rawSeats.minOf { it.position!!.cy }
        val maxY = rawSeats.maxOf { it.position!!.cy }

        val seatRadius = summary.layoutMetadata?.seatRadius?.takeIf { it.isFinite() && it > 0.0 }
            ?: estimateSpatialStep(rawSeats.map { it.position!!.cx }) * 0.42
        val pad = max(seatRadius * 1.4, 24.0)

        val spanX = max(maxX - minX, seatRadius * 2.0)
        val spanY = max(maxY - minY, seatRadius * 2.0)
        val worldW = spanX + pad * 2.0
        val worldH = spanY + pad * 2.0
        val aspectRatio = (worldW / worldH).toFloat().coerceIn(0.65f, 2.2f)

        var avail = 0
        var reservedBooked = 0
        var blocked = 0
        var broken = 0
        var unknown = 0

        val rowYBuckets = linkedMapOf<String, MutableList<Float>>()
        val rowLabelsByKey = linkedMapOf<String, String>()

        val uiSeats = rawSeats.map { seat ->
            val pos = seat.position!!
            val normX = ((pos.cx - minX + pad) / worldW).toFloat().coerceIn(0.02f, 0.98f)
            val normY = ((pos.cy - minY + pad) / worldH).toFloat().coerceIn(0.02f, 0.98f)

            val rowId = "${seat.roomKey}|${seat.groupKey}|${seat.rowKey}"
            rowYBuckets.getOrPut(rowId) { mutableListOf() }.add(normY)
            rowLabelsByKey.putIfAbsent(rowId, seat.rowNumber)

            val isBroken = isExplicitlyBrokenSeat(seat, explicitBrokenSeatKeys)
            val visual = mapToVisualStatus(seat.status, isBroken)
            when (visual) {
                SeatVisualStatus.AVAILABLE -> avail++
                SeatVisualStatus.RESERVED_OR_BOOKED -> reservedBooked++
                SeatVisualStatus.BLOCKED_OR_UNAVAILABLE -> blocked++
                SeatVisualStatus.BROKEN -> broken++
                SeatVisualStatus.UNKNOWN -> unknown++
            }

            toUiItem(
                seat = seat,
                normX = normX,
                normY = normY,
                visualStatus = visual,
                isBroken = isBroken,
                summary = summary,
                lastUpdatedMs = lastUpdatedMs,
                lastUpdatedLabel = lastUpdatedLabel,
                isFromCache = isFromCache
            )
        }

        val rowLabels = rowYBuckets.entries.map { (rowId, ys) ->
            AuditoriumRowLabel(
                id = rowId,
                label = rowLabelsByKey[rowId] ?: "",
                normalizedY = ys.average().toFloat()
            )
        }.sortedBy { it.normalizedY }

        val minHorizNorm = computeMinPositiveSpacingNorm(uiSeats.map { it.normalizedX }, defaultMin = 0.045f)
        val minVertNorm = computeMinPositiveSpacingNorm(uiSeats.map { it.normalizedY }, defaultMin = 0.06f)

        return ComputedLayout(
            seats = uiSeats,
            rowLabels = rowLabels,
            aspectRatio = aspectRatio,
            minHorizontalSpacingNorm = minHorizNorm,
            minVerticalSpacingNorm = minVertNorm,
            counts = SeatStatusCounts(
                available = avail,
                reservedOrBooked = reservedBooked,
                blockedOrUnavailable = blocked,
                broken = broken,
                unknown = unknown
            )
        )
    }

    private fun buildGridFallbackLayout(
        summary: SeatAvailabilitySummary,
        isFromCache: Boolean,
        lastUpdatedMs: Long?,
        lastUpdatedLabel: String,
        explicitBrokenSeatKeys: Set<String>
    ): ComputedLayout {
        val groupedRows = summary.seats
            .groupBy { "${it.roomKey}|${it.groupKey}|${it.rowKey}|${it.rowNumber}" }
            .entries
            .sortedWith { a, b ->
                val seatA = a.value.first()
                val seatB = b.value.first()
                compareNaturalStrings(seatA.groupKey, seatB.groupKey).takeIf { it != 0 }
                    ?: compareNaturalStrings(seatA.rowNumber, seatB.rowNumber)
            }

        val rowCount = groupedRows.size.coerceAtLeast(1)
        val maxCols = groupedRows.maxOfOrNull { it.value.size }?.coerceAtLeast(1) ?: 1

        var avail = 0
        var reservedBooked = 0
        var blocked = 0
        var broken = 0
        var unknown = 0

        val uiSeats = mutableListOf<AuditoriumSeatUiItem>()
        val rowLabels = mutableListOf<AuditoriumRowLabel>()

        groupedRows.forEachIndexed { rowIdx, (rowId, rowSeats) ->
            val sortedSeats = rowSeats.sortedWith { s1, s2 ->
                compareNaturalStrings(s1.seatNumber, s2.seatNumber)
            }
            val normY = if (rowCount == 1) 0.5f else (0.08f + (rowIdx.toFloat() / (rowCount - 1)) * 0.84f)
            rowLabels.add(
                AuditoriumRowLabel(
                    id = rowId,
                    label = sortedSeats.first().rowNumber,
                    normalizedY = normY
                )
            )

            val colCount = sortedSeats.size
            // Center shorter rows relative to the widest row in the hall
            val rowWidthSpan = if (maxCols <= 1) 0f else ((colCount - 1).toFloat() / (maxCols - 1).toFloat()) * 0.84f
            val rowStartX = 0.5f - (rowWidthSpan / 2f)

            sortedSeats.forEachIndexed { colIdx, seat ->
                val normX = if (colCount <= 1) {
                    0.5f
                } else {
                    rowStartX + (colIdx.toFloat() / (colCount - 1).toFloat()) * rowWidthSpan
                }
                val isBroken = isExplicitlyBrokenSeat(seat, explicitBrokenSeatKeys)
                val visual = mapToVisualStatus(seat.status, isBroken)
                when (visual) {
                    SeatVisualStatus.AVAILABLE -> avail++
                    SeatVisualStatus.RESERVED_OR_BOOKED -> reservedBooked++
                    SeatVisualStatus.BLOCKED_OR_UNAVAILABLE -> blocked++
                    SeatVisualStatus.BROKEN -> broken++
                    SeatVisualStatus.UNKNOWN -> unknown++
                }

                uiSeats.add(
                    toUiItem(
                        seat = seat,
                        normX = normX.coerceIn(0.05f, 0.95f),
                        normY = normY.coerceIn(0.05f, 0.95f),
                        visualStatus = visual,
                        isBroken = isBroken,
                        summary = summary,
                        lastUpdatedMs = lastUpdatedMs,
                        lastUpdatedLabel = lastUpdatedLabel,
                        isFromCache = isFromCache
                    )
                )
            }
        }

        val aspectRatio = ((maxCols + 1f) / (rowCount + 1.5f)).coerceIn(0.75f, 2.0f)
        val horizStep = if (maxCols <= 1) 0.16f else (0.84f / (maxCols - 1)).coerceIn(0.03f, 0.18f)
        val vertStep = if (rowCount <= 1) 0.18f else (0.84f / (rowCount - 1)).coerceIn(0.04f, 0.18f)

        return ComputedLayout(
            seats = uiSeats,
            rowLabels = rowLabels,
            aspectRatio = aspectRatio,
            minHorizontalSpacingNorm = horizStep,
            minVerticalSpacingNorm = vertStep,
            counts = SeatStatusCounts(
                available = avail,
                reservedOrBooked = reservedBooked,
                blockedOrUnavailable = blocked,
                broken = broken,
                unknown = unknown
            )
        )
    }

    private fun toUiItem(
        seat: Seat,
        normX: Float,
        normY: Float,
        visualStatus: SeatVisualStatus,
        isBroken: Boolean,
        summary: SeatAvailabilitySummary,
        lastUpdatedMs: Long?,
        lastUpdatedLabel: String,
        isFromCache: Boolean
    ): AuditoriumSeatUiItem {
        val id = "${seat.roomKey}_${seat.groupKey}_${seat.rowKey}_${seat.seatKey}"
        val isVerified = !summary.hasUnverifiedStatuses && seat.status != SeatAvailabilityStatus.UNKNOWN
        return AuditoriumSeatUiItem(
            id = id,
            seatKey = seat.seatKey,
            roomKey = seat.roomKey,
            roomName = seat.roomName,
            groupKey = seat.groupKey,
            rowKey = seat.rowKey,
            rowNumber = seat.rowNumber,
            seatNumber = seat.seatNumber,
            normalizedX = normX,
            normalizedY = normY,
            visualStatus = visualStatus,
            apiStatus = seat.status,
            isVerified = isVerified,
            isBrokenSeat = isBroken,
            lastRefreshedEpochMs = lastUpdatedMs,
            lastRefreshedLabel = lastUpdatedLabel,
            isFromCache = isFromCache,
            // Public seats-statusV2 API does not expose booking timestamps; never infer from polling/cache time.
            officialBookingTimestamp = null,
            technicalRawFields = sanitizeTechnicalRawFields(seat.rawFields)
        )
    }

    private fun isExplicitlyBrokenSeat(seat: Seat, explicitBrokenKeys: Set<String>): Boolean {
        if (explicitBrokenKeys.isEmpty()) return false
        val compositeRowSeat = "${seat.rowNumber}|${seat.seatNumber}"
        val fullKey = "${seat.roomKey}_${seat.groupKey}_${seat.rowKey}_${seat.seatKey}"
        return seat.seatKey in explicitBrokenKeys ||
            compositeRowSeat in explicitBrokenKeys ||
            fullKey in explicitBrokenKeys
    }

    fun mapToVisualStatus(
        apiStatus: SeatAvailabilityStatus,
        isBrokenSeat: Boolean = false
    ): SeatVisualStatus {
        if (isBrokenSeat) return SeatVisualStatus.BROKEN
        return when (apiStatus) {
            SeatAvailabilityStatus.AVAILABLE -> SeatVisualStatus.AVAILABLE
            SeatAvailabilityStatus.OCCUPIED,
            SeatAvailabilityStatus.RESERVED -> SeatVisualStatus.RESERVED_OR_BOOKED
            SeatAvailabilityStatus.BLOCKED,
            SeatAvailabilityStatus.UNAVAILABLE -> SeatVisualStatus.BLOCKED_OR_UNAVAILABLE
            SeatAvailabilityStatus.UNKNOWN -> SeatVisualStatus.UNKNOWN
        }
    }

    private fun buildBrokenReconciliation(
        screening: Screening,
        hallInfo: HallCapacityInfo,
        summary: SeatAvailabilitySummary?,
        explicitBrokenCount: Int
    ): BrokenSeatsReconciliation {
        val originalCap = hallInfo.originalCapacity ?: screening.originalHallCapacity()
        val manualBroken = hallInfo.brokenSeats
        val usableCap = screening.usableHallCapacity()
        val excludedByLiveMap = screening.brokenSeatsExcludedByLiveMap()
        val blockedInMap = (summary?.blockedSeats ?: 0) + (summary?.unavailableSeats ?: 0)
        val omittedFromOriginal = if (originalCap != null && summary != null) {
            (originalCap - summary.totalRawSeatObjects).coerceAtLeast(0)
        } else {
            0
        }
        val alreadyExcluded = blockedInMap + (summary?.unknownSeats ?: 0) + omittedFromOriginal + explicitBrokenCount
        val unaccounted = if (manualBroken != null && summary != null && !summary.hasUnverifiedStatuses) {
            (manualBroken - alreadyExcluded).coerceAtLeast(0)
        } else {
            manualBroken ?: 0
        }

        val message = when {
            !hallInfo.isBrokenSeatsKnown -> {
                "מצב המושבים התקולים באולם ${screening.roomNumber ?: ""} אינו ידוע (אולם בשיפוצים/ללא נתון)."
            }
            manualBroken == 0 -> {
                "אין מושבים תקולים מדווחים באולם זה (קיבולת שמישה: ${usableCap ?: originalCap ?: "?"})."
            }
            excludedByLiveMap -> {
                "דווחו $manualBroken מושבים תקולים — כבר מוחרגים במפת המושבים החיה (ללא הפחתה כפולה; שמיש: ${usableCap ?: "?"})."
            }
            else -> {
                "דווחו $manualBroken מושבים תקולים ברמת אולם (ה-API אינו מסמן אילו כיסאות תקולים; שמיש: ${usableCap ?: "?"})."
            }
        }

        return BrokenSeatsReconciliation(
            originalCapacity = originalCap,
            manualBrokenSeats = manualBroken,
            usableCapacity = usableCap,
            isBrokenSeatsKnown = hallInfo.isBrokenSeatsKnown,
            excludedByLiveMap = excludedByLiveMap,
            blockedOrUnavailableInMap = blockedInMap,
            omittedFromOriginal = omittedFromOriginal,
            unaccountedManualBrokenSeats = unaccounted,
            summaryMessage = message
        )
    }

    private fun resolveFreshnessState(
        summary: SeatAvailabilitySummary?,
        seatFetchState: SeatFetchState,
        lastUpdatedMs: Long?,
        nowEpochMs: Long,
        isFromCache: Boolean
    ): SeatDataFreshnessState {
        if (summary == null || summary.seats.isEmpty()) {
            return SeatDataFreshnessState.UNAVAILABLE
        }
        if (summary.hasUnverifiedStatuses || seatFetchState == SeatFetchState.UNVERIFIED_STATUSES) {
            return SeatDataFreshnessState.UNVERIFIED
        }
        if (lastUpdatedMs != null && (nowEpochMs - lastUpdatedMs) > STALE_THRESHOLD_MS) {
            return SeatDataFreshnessState.STALE
        }
        return if (isFromCache) SeatDataFreshnessState.CACHED else SeatDataFreshnessState.LIVE_FRESH
    }

    private fun buildUnavailableReason(
        screening: Screening,
        summary: SeatAvailabilitySummary?
    ): String {
        return when {
            screening.seatFetchState == SeatFetchState.MISSING_PRESENTATION_ID ->
                "להקרנה זו אין מזהה הקרנה (presentationId) בפיד ולכן לא ניתן לשלוף מפת מושבים."
            screening.seatFetchState == SeatFetchState.AUTH_REQUIRED ->
                "שרת הכרטיסים דרש אימות/הרשאה (HTTP ${screening.seatApiHttpCode ?: 401})."
            screening.seatFetchState == SeatFetchState.FAILED ->
                "שגיאת תקשורת בקריאת מפת המושבים (${screening.seatFetchError ?: "שגיאה זמנית"})."
            screening.seatFetchState == SeatFetchState.MISSING_DATA || (summary != null && summary.seats.isEmpty()) ->
                "תגובת שרת המושבים לא כללה אובייקטי מושבים להצגה."
            else ->
                "מפת המושבים להקרנה זו טרם נטענה או אינה זמינה מהמקור."
        }
    }

    fun sanitizeTechnicalRawFields(rawFields: Map<String, String>): Map<String, String> {
        if (rawFields.isEmpty()) return emptyMap()
        return rawFields.filterKeys { key ->
            val clean = key.lowercase().replace("_", "").replace("-", "")
            clean !in blockedPiiKeys &&
                !clean.contains("email") &&
                !clean.contains("phone") &&
                !clean.contains("customer") &&
                !clean.contains("payment")
        }
    }

    private fun formatEpochMs(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L) return "לא זמין"
        return try {
            Instant.ofEpochMilli(epochMs).atZone(israelZone).format(refreshTimeFormatter)
        } catch (_: Exception) {
            "לא זמין"
        }
    }

    private fun estimateSpatialStep(values: List<Double>): Double {
        val sorted = values.distinct().sorted()
        if (sorted.size < 2) return 40.0
        var minDiff = Double.MAX_VALUE
        for (i in 1 until sorted.size) {
            val d = sorted[i] - sorted[i - 1]
            if (d > 1.0 && d < minDiff) {
                minDiff = d
            }
        }
        return if (minDiff == Double.MAX_VALUE) 40.0 else minDiff
    }

    private fun computeMinPositiveSpacingNorm(values: List<Float>, defaultMin: Float): Float {
        val sorted = values.distinct().sorted()
        if (sorted.size < 2) return defaultMin
        var minDiff = 1f
        for (i in 1 until sorted.size) {
            val d = abs(sorted[i] - sorted[i - 1])
            if (d > 0.005f && d < minDiff) {
                minDiff = d
            }
        }
        return if (minDiff >= 1f) defaultMin else minDiff.coerceIn(0.018f, 0.22f)
    }

    internal fun compareNaturalStrings(a: String, b: String): Int {
        val numA = a.trim().toIntOrNull()
        val numB = b.trim().toIntOrNull()
        if (numA != null && numB != null) return numA.compareTo(numB)
        if (numA != null) return -1
        if (numB != null) return 1
        return a.compareTo(b, ignoreCase = true)
    }
}
