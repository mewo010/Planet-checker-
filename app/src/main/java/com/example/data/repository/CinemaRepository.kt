package com.example.data.repository

import com.example.data.diagnostics.FeedTelemetry
import com.example.data.diagnostics.ScreeningSourceSnapshot
import com.example.data.diagnostics.SourceTelemetryBundle
import com.example.data.model.AvailabilitySource
import com.example.data.model.CrowdLevel
import com.example.data.model.FirstScheduleResponse
import com.example.data.model.HallCapacityInfo
import com.example.data.model.HourlyScheduleResponse
import com.example.data.model.QuickBookSourceStatus
import com.example.data.model.RawScreening
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.model.SeatAvailabilitySummary
import com.example.data.model.SeatFetchState
import com.example.data.model.UsableCapacityStatus
import com.example.data.model.calculateCinemaOccupancy
import com.example.data.model.calculateCrowdLevelForRatio
import com.example.data.model.calculateUsableCapacity
import com.example.data.model.canonicalSlotKey
import com.example.data.model.deduplicateScreenings
import com.example.data.model.mergeScreeningAvailability
import com.example.planetchecker.estimatedOccupiedSeats as coreEstimatedOccupiedSeats
import com.example.planetchecker.roomCapacity as coreRoomCapacity
import com.example.planetchecker.roomType as coreRoomType
import com.example.data.remote.DefaultPlanetQuickBookClient
import com.example.data.remote.PlanetQuickBookClient
import com.example.data.remote.QuickBookFetchResult
import com.example.util.CinemaDiagnosticsLogger
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class CinemaRepository(
    private val quickBookClient: PlanetQuickBookClient = DefaultPlanetQuickBookClient()
) {

    companion object {
        const val FIRST_URL = com.example.planetchecker.FIRST_SCHEDULE_URL
        const val HOURLY_URL = com.example.planetchecker.HOURLY_SCHEDULE_URL
        const val AVAILABILITY_HISTORY_URL = com.example.planetchecker.AVAILABILITY_HISTORY_URL

        const val ENTRY_LEAD = 20
        const val EXTRA_END = 15
        const val ENDED_KEEP = 3

        val HALL_CAPACITIES: Map<Int, Int> = mapOf(
            1 to 124, 2 to 124, 3 to 162, 4 to 163, 5 to 400,
            6 to 123, 7 to 311, 8 to 154, 9 to 88, 10 to 217,
            11 to 251, 12 to 118, 13 to 119, 14 to 119, 15 to 119,
            16 to 182, 17 to 182, 18 to 112, 19 to 204, 20 to 124,
            21 to 54, 22 to 54, 23 to 70, 24 to 56
        )

        /**
         * Manually verified count of broken/unusable seats per auditorium.
         * - `0` means the hall is confirmed to have zero broken seats.
         * - `null` (e.g. Hall 20 currently under renovation) means the broken-seat count is UNKNOWN.
         */
        val BROKEN_SEATS_BY_HALL: Map<Int, Int?> = mapOf(
            1 to 3,
            2 to 3,
            3 to 2,
            4 to 3,
            5 to 2,
            6 to 2,
            7 to 2,
            8 to 3,
            9 to 0,
            10 to 3,
            11 to 2,
            12 to 3,
            13 to 3,
            14 to 3,
            15 to 3,
            16 to 3,
            17 to 3,
            18 to 3,
            19 to 2,
            20 to null,
            21 to 0,
            22 to 0,
            23 to 0,
            24 to 0
        )

        fun getHallCapacityInfo(
            hallNumber: Int?,
            fallbackOriginalCapacity: Int? = null,
            originalCapacities: Map<Int, Int> = HALL_CAPACITIES,
            brokenSeatsByHall: Map<Int, Int?> = BROKEN_SEATS_BY_HALL
        ): HallCapacityInfo {
            val original = hallNumber?.let { originalCapacities[it] } ?: fallbackOriginalCapacity
            val hasHallKey = hallNumber != null && brokenSeatsByHall.containsKey(hallNumber)
            val broken = if (hasHallKey) brokenSeatsByHall[hallNumber] else null
            val usable = calculateUsableCapacity(original, broken)
            val status = when {
                original == null -> UsableCapacityStatus.UNKNOWN_HALL
                broken == null -> UsableCapacityStatus.UNKNOWN_BROKEN_SEATS
                else -> UsableCapacityStatus.KNOWN
            }
            return HallCapacityInfo(
                hallNumber = hallNumber,
                originalCapacity = original,
                brokenSeats = broken,
                usableCapacity = usable,
                isBrokenSeatsKnown = broken != null && original != null,
                status = status
            )
        }

        fun getAllHallCapacities(): List<HallCapacityInfo> = (1..24).map { getHallCapacityInfo(it) }

        val MOVIE_METADATA: Map<String, Pair<String, String>> = mapOf(
            "חולית: חלק שני" to Pair("מדע בדיוני", "#C2410C"),
            "גזע המפלצות: גודזילה x קונג" to Pair("אקשן", "#0E7490"),
            "אופנהיימר" to Pair("דרמה", "#B45309"),
            "אהבה אופקית" to Pair("רומנטי", "#BE185D"),
            "קונג פו פנדה 4" to Pair("אנימציה", "#15803D"),
            "הקול בראש 2" to Pair("אנימציה", "#7C3AED"),
            "באטמן: אגדת האביר" to Pair("אקשן", "#334155"),
            "דדפול & וולברין" to Pair("אקשן", "#DC2626"),
            "גלדיאטור 2" to Pair("היסטורי", "#A16207")
        )

        val MOCK_SCREENINGS = listOf(
            RawScreening("חולית: חלק שני", "אולם 5", "14:00", "16:45", 0.15f),
            RawScreening("חולית: חלק שני", "אולם 5", "17:30", "20:15", 0.40f),
            RawScreening("חולית: חלק שני", "אולם 5", "21:00", "23:45", 0.05f),
            RawScreening("גזע המפלצות: גודזילה x קונג", "אולם 20", "16:15", "18:15", 0.25f),
            RawScreening("גזע המפלצות: גודזילה x קונג", "אולם 20", "19:00", "21:00", 0.10f),
            RawScreening("אופנהיימר", "אולם 19", "18:00", "21:00", 0.50f),
            RawScreening("אהבה אופקית", "אולם 21 (VIP)", "19:30", "21:20", 0.0f),
            RawScreening("אהבה אופקית", "אולם 22 (VIP)", "20:30", "22:20", 0.35f),
            RawScreening("קונג פו פנדה 4", "אולם 1", "15:00", "16:35", 0.80f),
            RawScreening("קונג פו פנדה 4", "אולם 1", "17:15", "18:50", 0.45f),
            RawScreening("הקול בראש 2", "אולם 3", "16:00", "17:40", 0.60f),
            RawScreening("הקול בראש 2", "אולם 3", "18:20", "20:00", 0.20f),
            RawScreening("באטמן: אגדת האביר", "אולם 10", "21:30", "23:55", 0.70f),
            RawScreening("דדפול & וולברין", "אולם 7", "19:15", "21:25", 0.05f),
            RawScreening("גלדיאטור 2", "אולם 8", "20:00", "22:30", 0.30f)
        )
    }

    private val jerusalemZone = ZoneId.of("Asia/Jerusalem")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val posterCache = ConcurrentHashMap<String, String>()

    data class FetchResult(
        val isLive: Boolean,
        val dateLabel: String,
        val cinemaDate: LocalDate,
        val screenings: List<Screening>,
        val fetchedAtEpochMs: Long? = null,
        val quickBookStatus: QuickBookSourceStatus = QuickBookSourceStatus(),
        val sourceTelemetry: SourceTelemetryBundle = SourceTelemetryBundle()
    )

    data class ScheduleMergeResult(
        val cinemaDate: LocalDate,
        val screenings: List<Screening>,
        val recoveredMissingFromQuickBook: Int = 0,
        val recoveredPidsFromQuickBook: Int = 0
    )

    suspend fun loadSchedule(
        currentNowMinutes: Int,
        previousScreenings: List<Screening> = emptyList()
    ): FetchResult = withContext(Dispatchers.IO) {
        val nowZoned = ZonedDateTime.now(jerusalemZone)
        val defaultCinemaDate = Screening.resolveIsraelCinemaDayAndMin(nowZoned).first
        val cacheBuster = System.currentTimeMillis()

        var baseResponse: FirstScheduleResponse? = null
        val (baseJson, baseTelemetryInit) = fetchUrlWithTelemetry(
            sourceName = "FIRST_URL",
            url = "$FIRST_URL?nocache=$cacheBuster",
            endpointUrl = FIRST_URL,
            ttlMs = 24 * 60 * 60 * 1000L
        )
        var firstTelemetry = baseTelemetryInit
        if (baseJson != null) {
            try {
                baseResponse = moshi.adapter(FirstScheduleResponse::class.java).fromJson(baseJson)
            } catch (e: Exception) {
                firstTelemetry = firstTelemetry.copy(
                    succeeded = false,
                    errorDetail = "JSON parse error: ${e.message}"
                )
            }
        }

        var hourlyResponse: HourlyScheduleResponse? = null
        val (hourlyJson, hourlyTelemetryInit) = fetchUrlWithTelemetry(
            sourceName = "HOURLY_URL",
            url = "$HOURLY_URL?nocache=$cacheBuster",
            endpointUrl = HOURLY_URL,
            ttlMs = 60 * 60 * 1000L
        )
        var hourlyTelemetry = hourlyTelemetryInit
        if (hourlyJson != null) {
            try {
                hourlyResponse = moshi.adapter(HourlyScheduleResponse::class.java).fromJson(hourlyJson)
            } catch (e: Exception) {
                hourlyTelemetry = hourlyTelemetry.copy(
                    succeeded = false,
                    errorDetail = "JSON parse error: ${e.message}"
                )
            }
        }

        var historyResponse: HourlyScheduleResponse? = null
        val (historyJson, _) = fetchUrlWithTelemetry(
            sourceName = "HISTORY_URL",
            url = "$AVAILABILITY_HISTORY_URL?nocache=$cacheBuster",
            endpointUrl = AVAILABILITY_HISTORY_URL,
            ttlMs = 60 * 60 * 1000L
        )
        if (historyJson != null) {
            try {
                historyResponse = moshi.adapter(HourlyScheduleResponse::class.java).fromJson(historyJson)
            } catch (_: Exception) {
                // history.json failure is non-fatal
            }
        }

        val targetDate = parseDateFlexible(baseResponse?.date)
            ?: previousScreenings.firstNotNullOfOrNull { it.date }
            ?: defaultCinemaDate
        val qbUrl = PlanetQuickBookClient.buildScheduleUrl(targetDate)
        val qbResult = quickBookClient.fetchDailySchedule(targetDate)
        val qbScreenings: List<RawScreening> = when (qbResult) {
            is QuickBookFetchResult.Success -> {
                qbResult.posterByTitle.forEach { (title, url) ->
                    val clean = title.replace(Regex("\\(.*?\\)"), "").trim()
                    if (clean.isNotEmpty() && !posterCache.containsKey(clean)) {
                        posterCache[clean] = url
                    }
                }
                qbResult.screenings
            }
            else -> emptyList()
        }

        val qbTelemetry = when (qbResult) {
            is QuickBookFetchResult.Success -> FeedTelemetry(
                sourceName = "OFFICIAL_QUICKBOOK",
                endpointUrl = qbUrl,
                httpStatusCode = qbResult.httpCode,
                fetchedAtEpochMs = qbResult.fetchedAtEpochMs,
                ttlMs = 15 * 60 * 1000L,
                succeeded = true,
                fromCache = false,
                cloudflareBlocked = false,
                errorDetail = null
            )
            is QuickBookFetchResult.HttpBlockedOrError -> FeedTelemetry(
                sourceName = "OFFICIAL_QUICKBOOK",
                endpointUrl = qbUrl,
                httpStatusCode = qbResult.httpCode,
                fetchedAtEpochMs = System.currentTimeMillis(),
                ttlMs = 15 * 60 * 1000L,
                succeeded = false,
                fromCache = false,
                cloudflareBlocked = qbResult.httpCode == 403,
                errorDetail = qbResult.message
            )
            is QuickBookFetchResult.NetworkError -> FeedTelemetry(
                sourceName = "OFFICIAL_QUICKBOOK",
                endpointUrl = qbUrl,
                httpStatusCode = null,
                fetchedAtEpochMs = System.currentTimeMillis(),
                ttlMs = 15 * 60 * 1000L,
                succeeded = false,
                fromCache = false,
                cloudflareBlocked = false,
                errorDetail = qbResult.message
            )
        }

        val telemetryBundle = SourceTelemetryBundle(
            firstUrl = firstTelemetry,
            hourlyUrl = hourlyTelemetry,
            quickBook = qbTelemetry,
            seatsApiTtlMs = SeatAvailabilityRepository.DEFAULT_CACHE_TTL_MS
        )

        val qbStatus = when (qbResult) {
            is QuickBookFetchResult.Success -> QuickBookSourceStatus(
                stateLabel = "זמין (${qbResult.screenings.size} הקרנות)",
                httpCode = qbResult.httpCode,
                totalOfficialEvents = qbResult.screenings.size,
                recoveredMissingScreenings = 0,
                recoveredPresentationIds = 0,
                isReachable = true,
                errorDetail = null
            )
            is QuickBookFetchResult.HttpBlockedOrError -> QuickBookSourceStatus(
                stateLabel = "חסום / שגיאה (HTTP ${qbResult.httpCode})",
                httpCode = qbResult.httpCode,
                isReachable = false,
                errorDetail = qbResult.message
            )
            is QuickBookFetchResult.NetworkError -> QuickBookSourceStatus(
                stateLabel = "לא זמין (שגיאת רשת)",
                httpCode = null,
                isReachable = false,
                errorDetail = qbResult.message
            )
        }

        // APK rule: if baseline (first-schedule.json) fails, do NOT touch existing screenings in memory.
        if (baseResponse == null) {
            val nonFallbackPrev = previousScreenings.filter { it.availabilitySource != AvailabilitySource.SAMPLE_FALLBACK }
            if (nonFallbackPrev.isNotEmpty()) {
                return@withContext FetchResult(
                    isLive = true,
                    dateLabel = targetDate.toString(),
                    cinemaDate = targetDate,
                    screenings = nonFallbackPrev,
                    fetchedAtEpochMs = null,
                    quickBookStatus = qbStatus,
                    sourceTelemetry = telemetryBundle
                )
            }
            val fallback = deduplicateScreenings(
                prepareScreenings(
                    rawList = MOCK_SCREENINGS,
                    nowMinutes = currentNowMinutes,
                    defaultDate = defaultCinemaDate,
                    defaultSource = AvailabilitySource.SAMPLE_FALLBACK,
                    nowZoned = nowZoned
                )
            )
            return@withContext FetchResult(
                isLive = false,
                dateLabel = "נתוני דוגמה (אין חיבור)",
                cinemaDate = defaultCinemaDate,
                screenings = fallback,
                fetchedAtEpochMs = null,
                quickBookStatus = qbStatus,
                sourceTelemetry = telemetryBundle
            )
        }

        val mergeResult = mergeGistBaselineWithSources(
            baseResponse = baseResponse,
            baseJson = baseJson,
            hourlyResponse = hourlyResponse,
            hourlyJson = hourlyJson,
            historyResponse = historyResponse,
            historyJson = historyJson,
            previousScreenings = previousScreenings,
            quickBookScreenings = qbScreenings,
            defaultCinemaDate = defaultCinemaDate,
            nowMinutes = currentNowMinutes,
            nowZoned = nowZoned
        )

        if (mergeResult.screenings.isEmpty()) {
            val fallback = deduplicateScreenings(
                prepareScreenings(
                    rawList = MOCK_SCREENINGS,
                    nowMinutes = currentNowMinutes,
                    defaultDate = defaultCinemaDate,
                    defaultSource = AvailabilitySource.SAMPLE_FALLBACK,
                    nowZoned = nowZoned
                )
            )
            return@withContext FetchResult(
                isLive = false,
                dateLabel = "נתוני דוגמה (אין חיבור)",
                cinemaDate = defaultCinemaDate,
                screenings = fallback,
                fetchedAtEpochMs = null,
                quickBookStatus = qbStatus,
                sourceTelemetry = telemetryBundle
            )
        }

        val fetchedAt = System.currentTimeMillis()
        CinemaDiagnosticsLogger.logOccupancyCalculation(
            "loadSchedule",
            calculateCinemaOccupancy(mergeResult.screenings, nowZoned, lastUpdatedEpochMs = fetchedAt)
        )

        FetchResult(
            isLive = true,
            dateLabel = baseResponse.date ?: mergeResult.cinemaDate.toString(),
            cinemaDate = mergeResult.cinemaDate,
            screenings = mergeResult.screenings,
            fetchedAtEpochMs = fetchedAt,
            quickBookStatus = qbStatus.copy(recoveredPresentationIds = mergeResult.recoveredPidsFromQuickBook),
            sourceTelemetry = telemetryBundle
        )
    }

    /**
     * Extracts exact Double precision availability ratios from raw Gist JSON so precision is never lost to Float.
     */
    private fun extractDoubleRatios(rawJson: String?): Pair<Map<String, Double>, Map<String, Double>> {
        if (rawJson.isNullOrBlank()) return emptyMap<String, Double>() to emptyMap()
        val byCode = mutableMapOf<String, Double>()
        val bySlot = mutableMapOf<String, Double>()
        try {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("screenings") ?: return byCode to bySlot
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ratio = o.optDouble("availabilityRatio", Double.NaN)
                    .takeIf { it.isFinite() && it in 0.0..1.0 } ?: continue
                val code = o.optString("presentationCode").trim().takeIf { it.isNotEmpty() }
                if (code != null) byCode[code] = ratio
                val room = o.optString("room", "")
                val start = o.optString("start", "")
                val title = o.optString("title", "")
                if (room.isNotBlank() && start.isNotBlank() && title.isNotBlank()) {
                    bySlot[canonicalSlotKey(room, start, title)] = ratio
                }
            }
        } catch (_: Exception) {
            // Fall back to Moshi floats if raw JSON shape differs
        }
        return byCode to bySlot
    }

    /**
     * APK source-of-truth Gist merger:
     * Final screening list = baseline (first-schedule.json).
     * For each screening with a presentationCode, availabilityRatio resolves by priority:
     *   1. hourly   (schedule.json)
     *   2. history  (history.json)
     *   3. previous (in-memory state prior to refresh)
     *   4. baseline (first-schedule.json)
     * Screenings without presentationCode keep their baseline availabilityRatio.
     */
    fun mergeGistBaselineWithSources(
        baseResponse: FirstScheduleResponse,
        baseJson: String? = null,
        hourlyResponse: HourlyScheduleResponse? = null,
        hourlyJson: String? = null,
        historyResponse: HourlyScheduleResponse? = null,
        historyJson: String? = null,
        previousScreenings: List<Screening> = emptyList(),
        quickBookScreenings: List<RawScreening> = emptyList(),
        defaultCinemaDate: LocalDate,
        nowMinutes: Int,
        nowZoned: ZonedDateTime = ZonedDateTime.now(jerusalemZone)
    ): ScheduleMergeResult {
        val parsedHeaderDate = parseDateFlexible(baseResponse.date) ?: defaultCinemaDate
        val (baseDoubleByCode, baseDoubleBySlot) = extractDoubleRatios(baseJson)
        val (hourlyDoubleByCode, _) = extractDoubleRatios(hourlyJson)
        val (historyDoubleByCode, _) = extractDoubleRatios(historyJson)

        val hourlyByCode = linkedMapOf<String, RawScreening>()
        hourlyResponse?.screenings.orEmpty().forEach { item ->
            val code = item.presentationCode?.trim()?.takeIf { it.isNotEmpty() }
            if (code != null) hourlyByCode[code] = item
        }

        val historyByCode = linkedMapOf<String, RawScreening>()
        historyResponse?.screenings.orEmpty().forEach { item ->
            val code = item.presentationCode?.trim()?.takeIf { it.isNotEmpty() }
            if (code != null) historyByCode[code] = item
        }

        val prevByCode = linkedMapOf<String, Screening>()
        previousScreenings.forEach { item ->
            val code = item.presentationCode?.trim()?.takeIf { it.isNotEmpty() }
            if (code != null) prevByCode[code] = item
        }

        val qbBySlotKey = linkedMapOf<String, RawScreening>()
        val qbByCode = linkedMapOf<String, RawScreening>()
        quickBookScreenings.forEach { item ->
            qbBySlotKey[canonicalSlotKey(item.room, item.start, item.title)] = item
            item.presentationCode?.trim()?.takeIf { it.isNotEmpty() }?.let { qbByCode[it] = item }
        }

        val basePrepared = prepareScreenings(
            rawList = baseResponse.screenings,
            nowMinutes = nowMinutes,
            defaultDate = parsedHeaderDate,
            defaultSource = AvailabilitySource.SCHEDULE_FEED,
            nowZoned = nowZoned,
            doubleRatiosByCode = baseDoubleByCode,
            doubleRatiosBySlot = baseDoubleBySlot
        )

        var recoveredPids = 0
        val merged = basePrepared.map { s ->
            val code = s.presentationCode?.trim()?.takeIf { it.isNotEmpty() }
            val hourlyItem = code?.let { hourlyByCode[it] }
            val historyItem = code?.let { historyByCode[it] }
            val prevItem = code?.let { prevByCode[it] }

            val hourlyRatio = code?.let {
                (hourlyDoubleByCode[it] ?: hourlyItem?.availabilityRatio?.toDouble())
                    ?.takeIf { r -> r.isFinite() && r in 0.0..1.0 }
            }
            val historyRatio = code?.let {
                (historyDoubleByCode[it] ?: historyItem?.availabilityRatio?.toDouble())
                    ?.takeIf { r -> r.isFinite() && r in 0.0..1.0 }
            }
            val prevRatio = prevItem?.effectiveAvailabilityDouble()
            val baselineRatio = s.effectiveAvailabilityDouble()

            val chosenRatio: Double?
            val chosenSource: AvailabilitySource
            when {
                hourlyRatio != null -> {
                    chosenRatio = hourlyRatio
                    chosenSource = AvailabilitySource.HOURLY_FEED
                }
                historyRatio != null -> {
                    chosenRatio = historyRatio
                    chosenSource = AvailabilitySource.HISTORY_FEED
                }
                prevRatio != null -> {
                    chosenRatio = prevRatio
                    chosenSource = prevItem.availabilitySource.takeIf { it != AvailabilitySource.NONE }
                        ?: AvailabilitySource.SCHEDULE_FEED
                }
                else -> {
                    chosenRatio = baselineRatio
                    chosenSource = if (baselineRatio != null) AvailabilitySource.SCHEDULE_FEED else AvailabilitySource.NONE
                }
            }

            val qbItem = code?.let { qbByCode[it] } ?: qbBySlotKey[s.canonicalSlotKey()]
            val resolvedPid = s.presentationId
                ?: hourlyItem?.resolvedPresentationId()
                ?: prevItem?.presentationId
                ?: qbItem?.resolvedPresentationId()
            if (s.presentationId == null && hourlyItem?.resolvedPresentationId() == null && qbItem?.resolvedPresentationId() != null) {
                recoveredPids++
            }

            val ratioFloat = chosenRatio?.toFloat()
            val updatedCore = com.example.planetchecker.Screening(
                room = s.room,
                start = s.startStr,
                end = s.endStr,
                title = s.title,
                date = s.date,
                availabilityRatio = chosenRatio,
                presentationCode = code
            )
            val estPeople = updatedCore.coreEstimatedOccupiedSeats()
            val occPct = if (chosenRatio != null) ((1.0 - chosenRatio) * 100.0).roundToInt().coerceIn(0, 100) else 0

            val trace = ScreeningSourceSnapshot(
                cinemaId = PlanetQuickBookClient.DEFAULT_CINEMA_ID,
                cinemaDate = (s.date ?: parsedHeaderDate).toString(),
                inFirstUrl = true,
                firstUrlRatio = baselineRatio?.toFloat(),
                firstUrlPresentationId = s.presentationId,
                firstUrlEnd = s.endStr.takeIf { it.isNotBlank() },
                inHourlyUrl = hourlyItem != null,
                hourlyUrlRatio = hourlyRatio?.toFloat(),
                hourlyUrlPresentationId = hourlyItem?.resolvedPresentationId(),
                hourlyUrlEnd = hourlyItem?.end?.takeIf { it.isNotBlank() },
                inQuickBook = qbItem != null,
                quickBookPresentationId = qbItem?.resolvedPresentationId(),
                quickBookPresentationCode = qbItem?.presentationCode?.takeIf { it.isNotBlank() },
                quickBookSoldOut = qbItem?.soldOut,
                quickBookEnd = qbItem?.end?.takeIf { it.isNotBlank() }
            )

            s.copy(
                presentationId = resolvedPid,
                availabilityRatio = ratioFloat,
                availabilityRatioDouble = chosenRatio,
                occupancyPercentage = occPct,
                estimatedPeople = estPeople,
                crowdLevel = calculateCrowdLevelForRatio(ratioFloat),
                availabilitySource = chosenSource,
                seatFetchState = when {
                    chosenRatio != null -> SeatFetchState.SUCCESS
                    resolvedPid == null -> SeatFetchState.MISSING_PRESENTATION_ID
                    else -> SeatFetchState.NONE
                },
                sourceSnapshot = trace
            )
        }

        return ScheduleMergeResult(
            cinemaDate = parsedHeaderDate,
            screenings = merged,
            recoveredMissingFromQuickBook = 0,
            recoveredPidsFromQuickBook = recoveredPids
        )
    }

    /**
     * Backward-compatible schedule merger combining Gist base + hourly feeds.
     */
    fun mergeRawSchedules(
        baseResponse: FirstScheduleResponse?,
        hourlyResponse: HourlyScheduleResponse?,
        defaultCinemaDate: LocalDate,
        nowMinutes: Int,
        nowZoned: ZonedDateTime = ZonedDateTime.now(jerusalemZone)
    ): Pair<LocalDate, List<Screening>> {
        val result = mergeAllScheduleSources(
            baseResponse = baseResponse,
            hourlyResponse = hourlyResponse,
            quickBookScreenings = emptyList(),
            defaultCinemaDate = defaultCinemaDate,
            nowMinutes = nowMinutes,
            nowZoned = nowZoned
        )
        return result.cinemaDate to result.screenings
    }

    /**
     * Reconciles base schedule (FIRST_URL), hourly feed (HOURLY_URL), and Planet's official QuickBook API:
     * - Matches screenings across normalized room numbers, formatted start times, titles, and presentationIds.
     * - Recovers missing presentationIds, presentationCodes, and any active/upcoming shows omitted by Gist feeds.
     * - Preserves hourly/base ratios cleanly and never overwrites valid ratios with nulls.
     */
    fun mergeAllScheduleSources(
        baseResponse: FirstScheduleResponse?,
        hourlyResponse: HourlyScheduleResponse?,
        quickBookScreenings: List<RawScreening>,
        defaultCinemaDate: LocalDate,
        nowMinutes: Int,
        nowZoned: ZonedDateTime = ZonedDateTime.now(jerusalemZone)
    ): ScheduleMergeResult {
        val parsedHeaderDate = parseDateFlexible(baseResponse?.date) ?: defaultCinemaDate
        val firstList = baseResponse?.screenings.orEmpty()
        val firstBySlotKey = linkedMapOf<String, RawScreening>()
        val firstByPid = linkedMapOf<String, RawScreening>()
        for (item in firstList) {
            firstBySlotKey[canonicalSlotKey(item.room, item.start, item.title)] = item
            item.resolvedPresentationId()?.let { firstByPid[it] = item }
        }

        val basePrepared = prepareScreenings(
            rawList = firstList,
            nowMinutes = nowMinutes,
            defaultDate = parsedHeaderDate,
            defaultSource = AvailabilitySource.SCHEDULE_FEED,
            nowZoned = nowZoned
        )

        val hourlyList = hourlyResponse?.screenings.orEmpty()
        val hourlyBySlotKey = linkedMapOf<String, RawScreening>()
        val hourlyByPid = linkedMapOf<String, RawScreening>()
        for (item in hourlyList) {
            hourlyBySlotKey[canonicalSlotKey(item.room, item.start, item.title)] = item
            item.resolvedPresentationId()?.let { hourlyByPid[it] = item }
        }

        val qbBySlotKey = linkedMapOf<String, RawScreening>()
        val qbByPid = linkedMapOf<String, RawScreening>()
        for (item in quickBookScreenings) {
            qbBySlotKey[canonicalSlotKey(item.room, item.start, item.title)] = item
            item.resolvedPresentationId()?.let { qbByPid[it] = item }
        }

        val ratioMap = linkedMapOf<String, Float?>()
        hourlyBySlotKey.forEach { (slotKey, raw) ->
            ratioMap[slotKey] = raw.availabilityRatio
        }
        qbBySlotKey.forEach { (slotKey, raw) ->
            if (ratioMap[slotKey] == null && raw.availabilityRatio != null) {
                ratioMap[slotKey] = raw.availabilityRatio
            }
        }

        var recoveredPids = 0

        val updatedBase = basePrepared.map { screening ->
            val slotKey = screening.canonicalSlotKey()
            val hourlyItem = hourlyBySlotKey[slotKey]
                ?: screening.presentationId?.let { hourlyByPid[it] }
            val qbItem = qbBySlotKey[slotKey]
                ?: (hourlyItem?.resolvedPresentationId() ?: screening.presentationId)?.let { qbByPid[it] }

            val hourlyPresId = hourlyItem?.resolvedPresentationId()
            val qbPresId = qbItem?.resolvedPresentationId()
            val resolvedPid = screening.presentationId ?: hourlyPresId ?: qbPresId
            if (screening.presentationId == null && hourlyPresId == null && qbPresId != null) {
                recoveredPids++
            }
            val resolvedCode = screening.presentationCode
                ?: hourlyItem?.presentationCode?.takeIf { it.isNotBlank() }
                ?: qbItem?.presentationCode?.takeIf { it.isNotBlank() }
            val resolvedVenueId = hourlyItem?.venueTypeId ?: qbItem?.venueTypeId ?: screening.venueTypeId

            screening.copy(
                presentationId = resolvedPid,
                presentationCode = resolvedCode,
                venueTypeId = resolvedVenueId
            )
        }.let { list ->
            mergeScreeningAvailability(list, scheduleRatiosByKey = ratioMap)
        }

        // Include any extra hourly screenings missing from first-schedule.json
        val seenKeys = updatedBase.mapTo(mutableSetOf()) { it.canonicalSlotKey() }
        val seenPids = updatedBase.mapNotNullTo(mutableSetOf()) { it.presentationId }
        val extraHourlyRaw = hourlyList.filter { raw ->
            val pid = raw.resolvedPresentationId()
            val slotKey = canonicalSlotKey(raw.room, raw.start, raw.title)
            slotKey !in seenKeys && (pid == null || pid !in seenPids)
        }.map { raw ->
            val slotKey = canonicalSlotKey(raw.room, raw.start, raw.title)
            val qbMatch = qbBySlotKey[slotKey] ?: raw.resolvedPresentationId()?.let { qbByPid[it] }
            if (qbMatch != null) {
                val qbPid = qbMatch.resolvedPresentationId()
                if (raw.resolvedPresentationId() == null && qbPid != null) recoveredPids++
                raw.copy(
                    presentationId = raw.resolvedPresentationId() ?: qbPid,
                    presentationCode = raw.presentationCode ?: qbMatch.presentationCode
                )
            } else raw
        }
        val extraHourlyPrepared = prepareScreenings(
            rawList = extraHourlyRaw,
            nowMinutes = nowMinutes,
            defaultDate = parsedHeaderDate,
            defaultSource = AvailabilitySource.HOURLY_FEED,
            nowZoned = nowZoned
        )

        val seenKeysAfterHourly = (updatedBase + extraHourlyPrepared).mapTo(mutableSetOf()) { it.canonicalSlotKey() }
        val seenPidsAfterHourly = (updatedBase + extraHourlyPrepared).mapNotNullTo(mutableSetOf()) { it.presentationId }

        val extraQuickBookRaw = quickBookScreenings.filter { raw ->
            val pid = raw.resolvedPresentationId()
            val slotKey = canonicalSlotKey(raw.room, raw.start, raw.title)
            slotKey !in seenKeysAfterHourly && (pid == null || pid !in seenPidsAfterHourly)
        }
        val extraQuickBookPrepared = prepareScreenings(
            rawList = extraQuickBookRaw,
            nowMinutes = nowMinutes,
            defaultDate = parsedHeaderDate,
            defaultSource = AvailabilitySource.OFFICIAL_QUICKBOOK,
            nowZoned = nowZoned
        )

        val combined = deduplicateScreenings(updatedBase + extraHourlyPrepared + extraQuickBookPrepared)
            .map { screening ->
                val slotKey = screening.canonicalSlotKey()
                val pid = screening.presentationId
                val firstItem = firstBySlotKey[slotKey] ?: pid?.let { firstByPid[it] }
                val hourlyItem = hourlyBySlotKey[slotKey] ?: pid?.let { hourlyByPid[it] }
                val qbItem = qbBySlotKey[slotKey] ?: pid?.let { qbByPid[it] }

                val trace = ScreeningSourceSnapshot(
                    cinemaId = PlanetQuickBookClient.DEFAULT_CINEMA_ID,
                    cinemaDate = (screening.date ?: parsedHeaderDate).toString(),
                    inFirstUrl = firstItem != null,
                    firstUrlRatio = firstItem?.availabilityRatio,
                    firstUrlPresentationId = firstItem?.resolvedPresentationId(),
                    firstUrlEnd = firstItem?.end?.takeIf { it.isNotBlank() },
                    inHourlyUrl = hourlyItem != null,
                    hourlyUrlRatio = hourlyItem?.availabilityRatio,
                    hourlyUrlPresentationId = hourlyItem?.resolvedPresentationId(),
                    hourlyUrlEnd = hourlyItem?.end?.takeIf { it.isNotBlank() },
                    inQuickBook = qbItem != null,
                    quickBookPresentationId = qbItem?.resolvedPresentationId(),
                    quickBookPresentationCode = qbItem?.presentationCode?.takeIf { it.isNotBlank() },
                    quickBookSoldOut = qbItem?.soldOut,
                    quickBookEnd = qbItem?.end?.takeIf { it.isNotBlank() }
                )
                screening.copy(sourceSnapshot = trace)
            }

        return ScheduleMergeResult(
            cinemaDate = parsedHeaderDate,
            screenings = combined,
            recoveredMissingFromQuickBook = extraQuickBookPrepared.size,
            recoveredPidsFromQuickBook = recoveredPids
        )
    }

    /**
     * Fetches live seat maps for non-ended screenings that provide a real presentationId.
     * - Records explicit [SeatFetchResult] for every active/upcoming candidate (Success, NetworkError,
     *   AuthRequired, EmptyOrInvalid, MissingPresentationId) so failures are surfaced instead of 0.
     * - Skips ENDED screenings so post-end seat clears never corrupt historical attendance.
     */
    suspend fun enrichWithLiveSeatAvailability(
        screenings: List<Screening>,
        seatRepository: SeatAvailabilityRepository,
        forceRefreshActive: Boolean = false
    ): List<Screening> = withContext(Dispatchers.IO) {
        val candidates = screenings.filter { it.status != ScreeningStatus.ENDED }
        if (candidates.isEmpty()) return@withContext screenings

        val summaries = mutableMapOf<String, SeatAvailabilitySummary>()
        val fetchResults = mutableMapOf<String, SeatFetchResult>()

        for (screening in candidates) {
            val pid = screening.presentationId?.trim()?.takeIf { it.isNotEmpty() }
            if (pid == null) {
                if (screening.availabilityRatio == null) {
                    fetchResults[screening.id] = SeatFetchResult.MissingPresentationId
                }
                continue
            }

            val ttl = if (screening.status == ScreeningStatus.LIVE || screening.status == ScreeningStatus.ENDED_RECENTLY) {
                SeatAvailabilityRepository.DEFAULT_CACHE_TTL_MS
            } else {
                SeatAvailabilityRepository.UPCOMING_CACHE_TTL_MS
            }
            val result = seatRepository.fetchSeatAvailability(
                presentationId = pid,
                venueTypeId = screening.venueTypeId,
                isReserved = 1,
                cacheTtlMs = ttl,
                forceRefresh = forceRefreshActive && screening.status == ScreeningStatus.LIVE
            )
            fetchResults[pid] = result
            if (result is SeatFetchResult.Success) {
                summaries[pid] = result.summary
            }
        }

        val updated = mergeScreeningAvailability(
            screenings = screenings,
            seatSummariesByKey = summaries,
            seatFetchResultsByKey = fetchResults
        )
        CinemaDiagnosticsLogger.logOccupancyCalculation(
            "enrichWithLiveSeatAvailability",
            calculateCinemaOccupancy(updated)
        )
        updated
    }

    suspend fun getPosterUrl(movieTitle: String): String? = withContext(Dispatchers.IO) {
        val cleanTitle = movieTitle.replace(Regex("\\(.*?\\)"), "").trim()
        if (posterCache.containsKey(cleanTitle)) {
            return@withContext posterCache[cleanTitle]
        }
        try {
            val query = URLEncoder.encode("$cleanTitle סרט", "UTF-8")
            val wikiUrl = "https://he.wikipedia.org/w/api.php?action=query&generator=search&gsrlimit=3&prop=pageimages&piprop=thumbnail&pithumbsize=500&format=json&origin=*&gsrsearch=$query"
            val response = fetchUrl(wikiUrl)
            val json = JSONObject(response)
            val queryObj = json.optJSONObject("query")
            val pagesObj = queryObj?.optJSONObject("pages")
            if (pagesObj != null) {
                val keys = pagesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val page = pagesObj.getJSONObject(key)
                    val thumb = page.optJSONObject("thumbnail")
                    val source = thumb?.optString("source")
                    if (!source.isNullOrEmpty()) {
                        posterCache[cleanTitle] = source
                        return@withContext source
                    }
                }
            }
        } catch (_: Exception) {
            // Wikipedia thumbnail lookup failure is non-fatal
        }
        return@withContext null
    }

    fun prepareScreenings(
        rawList: List<RawScreening>,
        nowMinutes: Int,
        defaultDate: LocalDate? = null,
        defaultSource: AvailabilitySource = AvailabilitySource.SCHEDULE_FEED,
        nowZoned: ZonedDateTime? = null,
        doubleRatiosByCode: Map<String, Double> = emptyMap(),
        doubleRatiosBySlot: Map<String, Double> = emptyMap()
    ): List<Screening> {
        return rawList.mapNotNull { raw ->
            val startParsed = parseTime(raw.start) ?: return@mapNotNull null
            val endParsed = parseTime(raw.end) ?: return@mapNotNull null

            val roomNum = RoomType.extractRoomNumber(raw.room)
            val capacity = roomNum?.let { HALL_CAPACITIES[it] }
            val roomType = RoomType.fromRoom(raw.room)

            val s = normalizeCinemaMinutes(startParsed)
            var e = normalizeCinemaMinutes(endParsed) + EXTRA_END
            if (e <= s) e += 1440

            val entry = s - ENTRY_LEAD

            val presCode = raw.presentationCode?.trim()?.takeIf { it.isNotEmpty() }
            val slotKey = canonicalSlotKey(raw.room, raw.start, raw.title)
            val doubleRatio = (presCode?.let { doubleRatiosByCode[it] }
                ?: doubleRatiosBySlot[slotKey]
                ?: raw.availabilityRatio?.toDouble())
                ?.takeIf { it.isFinite() && it in 0.0..1.0 }
            val ratio = doubleRatio?.toFloat()
            val occupancyPct = if (doubleRatio != null) {
                ((1.0 - doubleRatio) * 100.0).roundToInt().coerceIn(0, 100)
            } else 0

            val screeningDate = parseDateFlexible(raw.date) ?: defaultDate
            val estimatedPeople = com.example.planetchecker.Screening(
                room = raw.room.trim(),
                start = raw.start.trim(),
                end = raw.end.trim(),
                title = raw.title.trim(),
                date = screeningDate,
                availabilityRatio = doubleRatio,
                presentationCode = presCode
            ).coreEstimatedOccupiedSeats()

            val crowdLevel = calculateCrowdLevelForRatio(ratio)
            val poster = posterCache[raw.title.replace(Regex("\\(.*?\\)"), "").trim()]
            val pid = raw.resolvedPresentationId()
            val source = if (doubleRatio == null) AvailabilitySource.NONE else defaultSource
            val fetchState = when {
                doubleRatio != null -> SeatFetchState.SUCCESS
                pid == null -> SeatFetchState.MISSING_PRESENTATION_ID
                else -> SeatFetchState.NONE
            }

            val prelim = Screening(
                id = "${raw.room.trim()}_${raw.start.trim()}_${raw.title.trim()}",
                title = raw.title.trim(),
                room = raw.room.trim(),
                roomNumber = roomNum,
                roomType = roomType,
                startStr = raw.start.trim(),
                endStr = raw.end.trim(),
                startMin = s,
                endMin = e,
                entryMin = entry,
                capacity = capacity,
                availabilityRatio = ratio,
                occupancyPercentage = occupancyPct,
                estimatedPeople = estimatedPeople,
                status = calculateStatus(s, e, nowMinutes),
                crowdLevel = crowdLevel,
                posterUrl = poster,
                presentationId = pid,
                presentationCode = presCode,
                venueTypeId = raw.venueTypeId ?: 1,
                date = screeningDate,
                seatSummary = null,
                availabilitySource = source,
                seatFetchState = fetchState,
                lastSeatUpdateEpochMs = null,
                availabilityRatioDouble = doubleRatio
            )

            if (nowZoned != null) {
                prelim.copy(status = prelim.statusAt(nowZoned))
            } else {
                prelim
            }
        }
    }

    fun currentIsraelCinemaDate(): LocalDate {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"))
        val rawMinutes = now.hour * 60 + now.minute
        return if (rawMinutes < 420) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    fun parseDateFlexible(rawDate: String?): LocalDate? {
        val trimmed = rawDate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val patterns = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
        )
        for (fmt in patterns) {
            try {
                return LocalDate.parse(trimmed, fmt)
            } catch (_: Exception) {
                // Try next format
            }
        }
        return null
    }

    private fun fetchUrlWithTelemetry(
        sourceName: String,
        url: String,
        endpointUrl: String,
        ttlMs: Long?
    ): Pair<String?, FeedTelemetry> {
        val attemptAt = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PlanetChecker-Android/1.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return null to FeedTelemetry(
                        sourceName = sourceName,
                        endpointUrl = endpointUrl,
                        httpStatusCode = code,
                        fetchedAtEpochMs = attemptAt,
                        ttlMs = ttlMs,
                        succeeded = false,
                        fromCache = false,
                        cloudflareBlocked = code == 403,
                        errorDetail = "HTTP $code"
                    )
                }
                body to FeedTelemetry(
                    sourceName = sourceName,
                    endpointUrl = endpointUrl,
                    httpStatusCode = code,
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    ttlMs = ttlMs,
                    succeeded = true,
                    fromCache = false,
                    cloudflareBlocked = false,
                    errorDetail = null
                )
            }
        } catch (e: Exception) {
            null to FeedTelemetry(
                sourceName = sourceName,
                endpointUrl = endpointUrl,
                httpStatusCode = null,
                fetchedAtEpochMs = attemptAt,
                ttlMs = ttlMs,
                succeeded = false,
                fromCache = false,
                cloudflareBlocked = false,
                errorDetail = e.message ?: "Network error"
            )
        }
    }

    private fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PlanetChecker-Android/1.0")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun parseTime(timeStr: String): Int? {
        val parts = timeStr.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun normalizeCinemaMinutes(min: Int): Int {
        // Cinema day begins at 07:00 (420 minutes). If < 420 (i.e. early morning 00:00 - 06:59), add 24 hours.
        return if (min < 420) min + 1440 else min
    }

    fun formatCinemaMinutes(min: Int): String {
        val wrapped = ((min % 1440) + 1440) % 1440
        val h = wrapped / 60
        val m = wrapped % 60
        return String.format("%02d:%02d", h, m)
    }

    fun relativeTimeSpan(diffMinutes: Int): String {
        return when {
            diffMinutes <= 0 -> "עכשיו"
            diffMinutes < 60 -> "בעוד $diffMinutes דק'"
            else -> {
                val h = diffMinutes / 60
                val rem = diffMinutes % 60
                if (rem > 0) "בעוד $h ש' ו-$rem דק'" else "בעוד $h שעות"
            }
        }
    }

    private fun calculateStatus(startMin: Int, endMin: Int, nowMin: Int): ScreeningStatus {
        return when {
            nowMin in startMin until endMin -> ScreeningStatus.LIVE
            nowMin < startMin -> ScreeningStatus.UPCOMING
            nowMin < endMin + ENDED_KEEP -> ScreeningStatus.ENDED_RECENTLY
            else -> ScreeningStatus.ENDED
        }
    }
}
