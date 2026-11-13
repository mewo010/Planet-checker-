package com.example.data.diagnostics

import com.example.data.model.AvailabilitySource
import com.example.data.model.OccupancyCountKind
import com.example.data.model.OccupancyResult
import com.example.data.model.Screening
import com.example.data.model.SeatAvailabilityStatus
import com.example.data.model.SeatFetchState
import com.example.data.model.calculateCinemaOccupancy
import com.example.data.model.canonicalSlotKey
import com.example.data.model.deduplicateScreenings
import com.example.data.remote.PlanetQuickBookClient
import com.example.data.remote.PlanetSeatsApi
import com.example.data.repository.CinemaRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Builds verifiable, JSON-serializable diagnostic snapshots of active cinema screenings,
 * comparing all 4 data sources explicitly and validating strict counting invariants.
 */
object LiveValidationSnapshotBuilder {

    private val jerusalemZone = ZoneId.of("Asia/Jerusalem")
    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val snapshotAdapter = moshi.adapter(LiveOccupancyValidationSnapshot::class.java).indent("  ")

    fun buildSnapshot(
        screenings: List<Screening>,
        now: ZonedDateTime = ZonedDateTime.now(jerusalemZone),
        nowEpochMs: Long = System.currentTimeMillis(),
        cinemaId: String = PlanetQuickBookClient.DEFAULT_CINEMA_ID,
        telemetry: SourceTelemetryBundle = SourceTelemetryBundle()
    ): LiveOccupancyValidationSnapshot {
        val uniqueScreenings = deduplicateScreenings(screenings)
        val effectiveCinemaDate = Screening.resolveIsraelCinemaDayAndMin(now).first
        val occupancyResult: OccupancyResult = calculateCinemaOccupancy(
            screenings = uniqueScreenings,
            now = now,
            lastUpdatedEpochMs = nowEpochMs
        )

        val screeningById = uniqueScreenings.associateBy { it.id }
        val activeEntries = occupancyResult.screeningDiagnostics.map { diag ->
            val screening = screeningById[diag.screeningId]
            buildActiveScreeningEntry(
                diag = diag,
                screening = screening,
                nowEpochMs = nowEpochMs,
                seatsTtlMs = telemetry.seatsApiTtlMs,
                cinemaId = cinemaId
            )
        }

        val freshnessList = buildSourceFreshnessList(
            screenings = uniqueScreenings,
            nowEpochMs = nowEpochMs,
            cinemaDate = effectiveCinemaDate,
            telemetry = telemetry
        )

        val verification = verifyCountingAccuracy(
            allUniqueScreenings = uniqueScreenings,
            activeEntries = activeEntries,
            now = now,
            expectedCinemaId = cinemaId,
            expectedCinemaDate = effectiveCinemaDate
        )

        val isoTs = Instant.ofEpochMilli(nowEpochMs).atZone(jerusalemZone).format(isoFormatter)
        val unknownCount = activeEntries.count { !it.includedInFinalTotal }

        return LiveOccupancyValidationSnapshot(
            schemaVersion = 1,
            snapshotTimestampEpochMs = nowEpochMs,
            snapshotTimestampIsraelIso = isoTs,
            cinemaId = cinemaId,
            cinemaName = "Planet Rishon LeZion",
            cinemaDate = effectiveCinemaDate.toString(),
            verifiedOccupiedAndReservedSeats = occupancyResult.verifiedOccupiedSeats,
            ratioEstimatedOccupancy = occupancyResult.estimatedOccupiedSeats,
            totalEstimatedOccupancy = occupancyResult.occupiedSeats,
            activeScreeningsCount = occupancyResult.activeScreenings,
            countedScreeningsCount = occupancyResult.countedScreenings,
            unknownOccupancyScreeningsCount = unknownCount,
            failedSeatRequestsCount = occupancyResult.failedScreenings,
            missingPresentationIdCount = occupancyResult.missingPresentationIdScreenings,
            dataCompletenessPercentage = occupancyResult.completenessPercentage,
            sourceFreshness = freshnessList,
            countingAccuracyVerification = verification,
            activeScreenings = activeEntries,
            excludedOrEndedCount = (uniqueScreenings.size - activeEntries.size).coerceAtLeast(0)
        )
    }

    private fun buildActiveScreeningEntry(
        diag: com.example.data.model.ScreeningOccupancyDiagnostic,
        screening: Screening?,
        nowEpochMs: Long,
        seatsTtlMs: Long,
        cinemaId: String
    ): ActiveScreeningSnapshotEntry {
        val trace = screening?.sourceSnapshot
        val summary = screening?.seatSummary
        val officialCap = diag.roomNumber?.let { CinemaRepository.HALL_CAPACITIES[it] } ?: diag.roomCapacity

        val effectiveGistRatio = when {
            trace?.hourlyUrlRatio != null -> trace.hourlyUrlRatio
            trace?.firstUrlRatio != null -> trace.firstUrlRatio
            screening != null && screening.availabilitySource != AvailabilitySource.LIVE_SEAT_MAP -> screening.availabilityRatio
            else -> null
        }

        val seatFetchedMs = trace?.seatApiFetchedAtEpochMs ?: screening?.lastSeatUpdateEpochMs
        val seatAgeMs = seatFetchedMs?.let { (nowEpochMs - it).coerceAtLeast(0L) }
        val seatTtl = trace?.seatApiTtlMs ?: seatsTtlMs
        val seatFreshness = classifySeatFreshness(
            screening = screening,
            fetchedMs = seatFetchedMs,
            ageMs = seatAgeMs,
            ttlMs = seatTtl
        )

        val disagreements = detectDisagreements(
            diag = diag,
            screening = screening,
            trace = trace,
            officialCap = officialCap
        )

        val trustedSource = when (diag.occupancyKind) {
            OccupancyCountKind.VERIFIED_SEAT_MAP -> AvailabilitySource.LIVE_SEAT_MAP.name
            OccupancyCountKind.RATIO_ESTIMATE -> screening?.availabilitySource?.name ?: AvailabilitySource.NONE.name
            OccupancyCountKind.UNKNOWN -> "NONE (unverified or missing)"
        }

        return ActiveScreeningSnapshotEntry(
            room = diag.room,
            roomNumber = diag.roomNumber,
            movieTitle = diag.title,
            startTime = diag.startTime,
            endTime = diag.endTime,
            cinemaId = trace?.cinemaId ?: cinemaId,
            screeningDate = screening?.date?.toString(),
            presentationId = diag.presentationId,
            presentationCode = diag.presentationCode,
            officialRoomCapacity = officialCap,
            brokenSeatsCount = diag.brokenSeatsCount,
            usableHallCapacity = diag.usableHallCapacity,
            brokenSeatsKnown = diag.brokenSeatsKnown,
            brokenSeatsExcludedByLiveMap = diag.brokenSeatsExcludedByLiveMap,
            firstUrlAvailabilityRatio = trace?.firstUrlRatio,
            hourlyUrlAvailabilityRatio = trace?.hourlyUrlRatio,
            effectiveGistAvailabilityRatio = effectiveGistRatio,
            quickBookSoldOut = trace?.quickBookSoldOut,
            seatApiStatus = diag.seatFetchState.name,
            seatApiHttpCode = diag.seatApiHttpCode,
            seatApiFreshness = seatFreshness,
            seatApiAgeSeconds = seatAgeMs?.div(1000L),
            totalRawSeatObjects = diag.totalRawSeatObjects,
            verifiedOccupiedSeats = summary?.takeIf { diag.occupancyKind == OccupancyCountKind.VERIFIED_SEAT_MAP }?.occupiedSeats,
            reservedSeats = summary?.takeIf { diag.occupancyKind == OccupancyCountKind.VERIFIED_SEAT_MAP }?.reservedSeats,
            availableSeats = diag.availableSeats,
            blockedSeats = diag.blockedSeats,
            unknownSeats = diag.unknownSeats,
            observedSeatPropertyKeys = diag.observedPropertyKeys.sorted(),
            estimatedOccupancy = diag.countedOccupiedSeats,
            occupancyKind = diag.occupancyKind.name,
            includedInFinalTotal = diag.includedInOccupancyTotal,
            exclusionReason = diag.exclusionReason,
            trustedSource = trustedSource,
            disagreements = disagreements
        )
    }

    private fun detectDisagreements(
        diag: com.example.data.model.ScreeningOccupancyDiagnostic,
        screening: Screening?,
        trace: ScreeningSourceSnapshot?,
        officialCap: Int?
    ): List<SourceDisagreement> {
        val list = mutableListOf<SourceDisagreement>()
        if (trace == null && screening?.seatSummary == null) return list

        val r1 = trace?.firstUrlRatio
        val r2 = trace?.hourlyUrlRatio
        if (r1 != null && r2 != null && abs(r1 - r2) > 0.01f) {
            list.add(
                SourceDisagreement(
                    field = "availabilityRatio",
                    firstUrlValue = r1.toString(),
                    hourlyUrlValue = r2.toString(),
                    quickBookValue = trace.quickBookSoldOut?.toString(),
                    seatsApiValue = screening?.seatSummary?.availabilityRatio?.toString(),
                    trustedSource = if (diag.occupancyKind == OccupancyCountKind.VERIFIED_SEAT_MAP) {
                        "SEATS_STATUS_V2"
                    } else {
                        "HOURLY_URL"
                    },
                    trustedValue = (screening?.seatSummary?.availabilityRatio ?: r2).toString(),
                    reason = if (diag.occupancyKind == OccupancyCountKind.VERIFIED_SEAT_MAP) {
                        "Verified live seats-statusV2 overrides static/hourly snapshots."
                    } else {
                        "HOURLY_URL is updated closer to showtime than initial daily FIRST_URL snapshot."
                    }
                )
            )
        }

        val qbSoldOut = trace?.quickBookSoldOut
        val gistRatio = r2 ?: r1
        if (qbSoldOut == true && gistRatio != null && gistRatio > 0.01f) {
            val seatAvail = screening?.seatSummary?.takeIf { !it.hasUnverifiedStatuses }?.availableSeats
            list.add(
                SourceDisagreement(
                    field = "soldOut_vs_availabilityRatio",
                    firstUrlValue = r1?.toString(),
                    hourlyUrlValue = r2?.toString(),
                    quickBookValue = "soldOut=true (0.0f)",
                    seatsApiValue = seatAvail?.let { "$it verified available seats" },
                    trustedSource = if (seatAvail != null && seatAvail > 0) "SEATS_STATUS_V2" else "HOURLY_OR_QUICKBOOK",
                    trustedValue = (if (seatAvail != null && seatAvail > 0) screening?.availabilityRatio else gistRatio).toString(),
                    reason = "QuickBook marks event soldOut=true while Gist still shows non-zero availability; verified seat map or fresher hourly feed resolves conflict explicitly."
                )
            )
        }

        val pid1 = trace?.firstUrlPresentationId
        val pid2 = trace?.hourlyUrlPresentationId
        val pidQb = trace?.quickBookPresentationId
        val distinctPids = listOfNotNull(pid1, pid2, pidQb).distinct()
        if (distinctPids.size > 1 || ((pid1 == null || pid2 == null) && pidQb != null)) {
            list.add(
                SourceDisagreement(
                    field = "presentationId",
                    firstUrlValue = pid1,
                    hourlyUrlValue = pid2,
                    quickBookValue = pidQb,
                    seatsApiValue = screening?.presentationId,
                    trustedSource = when {
                        pidQb != null -> "OFFICIAL_QUICKBOOK"
                        pid2 != null -> "HOURLY_URL"
                        else -> "FIRST_URL"
                    },
                    trustedValue = diag.presentationId,
                    reason = "Official QuickBook event id / HOURLY_URL provides live box-office presentationId when FIRST_URL omits or differs."
                )
            )
        }

        val rawSeatObjs = diag.totalRawSeatObjects
        if (rawSeatObjs != null && officialCap != null && rawSeatObjs != officialCap) {
            val trustedCap = screening?.roomCapacity() ?: officialCap
            val partial = screening?.seatSummary?.hasUnverifiedStatuses == true ||
                screening?.seatSummary?.availabilityRatio == null
            list.add(
                SourceDisagreement(
                    field = "auditoriumCapacity",
                    firstUrlValue = officialCap.toString(),
                    hourlyUrlValue = officialCap.toString(),
                    quickBookValue = null,
                    seatsApiValue = "$rawSeatObjs raw seat objects (valid=${diag.totalValidSeats}, unknown=${diag.unknownSeats})",
                    trustedSource = if (partial) "OFFICIAL_HALL_CAPACITIES" else "VERIFIED_SEATS_STATUS_V2",
                    trustedValue = trustedCap.toString(),
                    reason = if (partial) {
                        "seats-statusV2 returned $rawSeatObjs seat objects with unverified statuses (likely partial/open-only); official hall capacity ($officialCap) is preserved to avoid shrinking auditorium size."
                    } else {
                        "All $rawSeatObjs seats had verified statuses, matching physical mapped seats."
                    }
                )
            )
        }

        val summary = screening?.seatSummary
        if (summary != null && summary.hasUnverifiedStatuses) {
            list.add(
                SourceDisagreement(
                    field = "seatStatusVerification",
                    firstUrlValue = r1?.toString(),
                    hourlyUrlValue = r2?.toString(),
                    quickBookValue = qbSoldOut?.let { "soldOut=$it" },
                    seatsApiValue = "unknown=${summary.unknownSeats}/${summary.totalRawSeatObjects}, keys=${summary.observedPropertyKeys.sorted()}",
                    trustedSource = "CONSERVATIVE_UNVERIFIED_GUARD",
                    trustedValue = diag.countedOccupiedSeats?.toString() ?: "UNKNOWN (excluded)",
                    reason = "Unverified seats-statusV2 keys (${summary.observedPropertyKeys.sorted()}) remain UNKNOWN until browser DOM verification; fallback Gist ratio used only if available."
                )
            )
        }

        return list
    }

    private fun verifyCountingAccuracy(
        allUniqueScreenings: List<Screening>,
        activeEntries: List<ActiveScreeningSnapshotEntry>,
        now: ZonedDateTime,
        expectedCinemaId: String,
        expectedCinemaDate: LocalDate
    ): CountingAccuracyVerification {
        val notes = mutableListOf<String>()

        // 1. Each active screening counted once (check unique slot keys & non-null unique presentationIds)
        val slotKeys = activeEntries.map { canonicalSlotKey(it.room, it.startTime, it.movieTitle) }
        val pidKeys = activeEntries.mapNotNull { it.presentationId?.takeIf { p -> p.isNotBlank() } }
        val uniqueSlots = slotKeys.size == slotKeys.distinct().size
        val uniquePids = pidKeys.size == pidKeys.distinct().size
        val eachCountedOnce = uniqueSlots && uniquePids
        if (!eachCountedOnce) {
            notes.add("Duplicate active slot or presentationId detected in activeScreenings.")
        }

        // 2. Cinema ID & Date verification (allows cinema date D and post-midnight rollover from D-1 past 07:00)
        val validDates = setOf(expectedCinemaDate.toString(), expectedCinemaDate.minusDays(1).toString())
        val rightCinemaAndDate = activeEntries.all { entry ->
            entry.cinemaId == expectedCinemaId && (entry.screeningDate == null || entry.screeningDate in validDates)
        }
        if (!rightCinemaAndDate) {
            notes.add("Found active screening outside cinemaId=$expectedCinemaId or cinemaDate=$expectedCinemaDate.")
        }

        // 3. No ended screening included
        val screeningMap = allUniqueScreenings.associateBy { it.canonicalSlotKey() }
        val noEndedIncluded = allUniqueScreenings
            .filter { !it.isShowingAt(now) }
            .none { s -> activeEntries.any { it.room == s.room && it.startTime == s.startStr && it.movieTitle == s.title } }
        if (!noEndedIncluded) {
            notes.add("Non-active (ended or upcoming) screening leaked into activeEntries.")
        }

        // 4. No screening without valid data counted as zero
        val noMissingAsZero = activeEntries.none { it.estimatedOccupancy == null && it.includedInFinalTotal } &&
            activeEntries.none { it.includedInFinalTotal && it.occupancyKind == OccupancyCountKind.UNKNOWN.name }
        if (!noMissingAsZero) {
            notes.add("Screening with missing/unknown data marked as included in total.")
        }

        // 5. No unknown seat status classified as occupied or available
        val noUnknownClassified = allUniqueScreenings
            .filter { it.isShowingAt(now) }
            .all { s ->
                val sum = s.seatSummary ?: return@all true
                val sumValidMatches = (sum.availableSeats + sum.occupiedSeats + sum.reservedSeats + sum.blockedSeats + sum.unavailableSeats) == sum.totalValidSeats
                val unknownMatches = sum.seats.count { it.status == SeatAvailabilityStatus.UNKNOWN } == sum.unknownSeats
                sumValidMatches && unknownMatches
            }
        if (!noUnknownClassified) {
            notes.add("SeatSummary invariant mismatch between valid/unknown seat totals.")
        }

        // 6. No partial/unverified seat map mistaken for full auditorium capacity
        val noPartialCapacityOverwrite = activeEntries.all { entry ->
            val s = screeningMap[canonicalSlotKey(entry.room, entry.startTime, entry.movieTitle)] ?: return@all true
            val summary = s.seatSummary ?: return@all true
            if (summary.hasUnverifiedStatuses || summary.availabilityRatio == null) {
                val hallCap = s.roomNumber?.let { CinemaRepository.HALL_CAPACITIES[it] }
                hallCap == null || entry.officialRoomCapacity == hallCap
            } else {
                true
            }
        }
        if (!noPartialCapacityOverwrite) {
            notes.add("Unverified/partial seat map overwrote official room capacity.")
        }

        val allPassed = eachCountedOnce &&
            rightCinemaAndDate &&
            noEndedIncluded &&
            noMissingAsZero &&
            noUnknownClassified &&
            noPartialCapacityOverwrite

        if (allPassed) {
            notes.add("All 6 counting accuracy invariants passed cleanly on ${activeEntries.size} active screenings.")
        }
        val unknownBrokenActive = activeEntries.count { !it.brokenSeatsKnown }
        if (unknownBrokenActive > 0) {
            notes.add("$unknownBrokenActive active screening(s) are in hall(s) with UNKNOWN broken-seat counts; usable capacity marked incomplete.")
        }

        return CountingAccuracyVerification(
            eachActiveScreeningCountedOnce = eachCountedOnce,
            allBelongToExpectedCinemaAndDate = rightCinemaAndDate,
            noEndedScreeningIncluded = noEndedIncluded,
            noMissingDataCountedAsZero = noMissingAsZero,
            noUnknownSeatClassifiedAsOccupiedOrAvailable = noUnknownClassified,
            noPartialSeatMapMistakenForFullCapacity = noPartialCapacityOverwrite,
            allInvariantsPassed = allPassed,
            notes = notes
        )
    }

    private fun classifySeatFreshness(
        screening: Screening?,
        fetchedMs: Long?,
        ageMs: Long?,
        ttlMs: Long
    ): SourceFreshnessState {
        if (screening == null) return SourceFreshnessState.NOT_QUERIED
        return when {
            screening.seatFetchState == SeatFetchState.AUTH_REQUIRED && screening.seatApiHttpCode == 403 ->
                SourceFreshnessState.CLOUDFLARE_BLOCKED
            screening.seatFetchState == SeatFetchState.FAILED ->
                SourceFreshnessState.FAILED
            fetchedMs == null || ageMs == null ->
                SourceFreshnessState.NOT_QUERIED
            ageMs >= ttlMs ->
                SourceFreshnessState.EXPIRED
            screening.seatApiFromCache ->
                SourceFreshnessState.CACHED
            else ->
                SourceFreshnessState.CURRENT
        }
    }

    private fun buildSourceFreshnessList(
        screenings: List<Screening>,
        nowEpochMs: Long,
        cinemaDate: LocalDate,
        telemetry: SourceTelemetryBundle
    ): List<SourceFreshnessInfo> {
        fun buildFeedInfo(
            defaultName: String,
            defaultUrl: String,
            feed: FeedTelemetry?
        ): SourceFreshnessInfo {
            if (feed == null) {
                return SourceFreshnessInfo(
                    sourceName = defaultName,
                    endpointUrl = defaultUrl,
                    freshnessState = SourceFreshnessState.NOT_QUERIED,
                    detail = "No telemetry captured for this snapshot"
                )
            }
            val ageMs = feed.fetchedAtEpochMs?.let { (nowEpochMs - it).coerceAtLeast(0L) }
            val state = when {
                feed.cloudflareBlocked || feed.httpStatusCode == 403 -> SourceFreshnessState.CLOUDFLARE_BLOCKED
                !feed.succeeded -> SourceFreshnessState.FAILED
                feed.ttlMs != null && ageMs != null && ageMs >= feed.ttlMs -> SourceFreshnessState.EXPIRED
                feed.fromCache -> SourceFreshnessState.CACHED
                else -> SourceFreshnessState.CURRENT
            }
            val iso = feed.fetchedAtEpochMs?.let {
                Instant.ofEpochMilli(it).atZone(jerusalemZone).format(isoFormatter)
            }
            return SourceFreshnessInfo(
                sourceName = feed.sourceName,
                endpointUrl = feed.endpointUrl,
                freshnessState = state,
                httpStatusCode = feed.httpStatusCode,
                fetchedAtEpochMs = feed.fetchedAtEpochMs,
                fetchedAtIso = iso,
                ageMs = ageMs,
                ageSeconds = ageMs?.div(1000L),
                ttlMs = feed.ttlMs,
                detail = feed.errorDetail
            )
        }

        val seatsAttempted = screenings.filter {
            it.seatSummary != null ||
                it.seatFetchState == SeatFetchState.FAILED ||
                it.seatFetchState == SeatFetchState.AUTH_REQUIRED ||
                it.seatFetchState == SeatFetchState.MISSING_DATA
        }
        val latestSeatTimestamp = seatsAttempted.mapNotNull { it.lastSeatUpdateEpochMs }.maxOrNull()
        val anyCloudflare = seatsAttempted.any { it.seatFetchState == SeatFetchState.AUTH_REQUIRED && it.seatApiHttpCode == 403 }
        val allFailed = seatsAttempted.isNotEmpty() && seatsAttempted.all { it.seatFetchState == SeatFetchState.FAILED || it.seatFetchState == SeatFetchState.AUTH_REQUIRED }
        val anyCached = seatsAttempted.any { it.seatApiFromCache }
        val seatAgeMs = latestSeatTimestamp?.let { (nowEpochMs - it).coerceAtLeast(0L) }

        val seatsFreshnessState = when {
            seatsAttempted.isEmpty() -> SourceFreshnessState.NOT_QUERIED
            anyCloudflare && allFailed -> SourceFreshnessState.CLOUDFLARE_BLOCKED
            allFailed -> SourceFreshnessState.FAILED
            seatAgeMs != null && seatAgeMs >= telemetry.seatsApiTtlMs -> SourceFreshnessState.EXPIRED
            anyCached -> SourceFreshnessState.CACHED
            else -> SourceFreshnessState.CURRENT
        }

        val seatsInfo = SourceFreshnessInfo(
            sourceName = "SEATS_STATUS_V2",
            endpointUrl = "${PlanetSeatsApi.BASE_URL}api/seats/seats-statusV2",
            freshnessState = seatsFreshnessState,
            httpStatusCode = seatsAttempted.firstNotNullOfOrNull { it.seatApiHttpCode },
            fetchedAtEpochMs = latestSeatTimestamp,
            fetchedAtIso = latestSeatTimestamp?.let {
                Instant.ofEpochMilli(it).atZone(jerusalemZone).format(isoFormatter)
            },
            ageMs = seatAgeMs,
            ageSeconds = seatAgeMs?.div(1000L),
            ttlMs = telemetry.seatsApiTtlMs,
            detail = "queried=${seatsAttempted.size}, ok=${seatsAttempted.count { it.seatSummary != null }}, failed=${seatsAttempted.count { it.seatFetchState == SeatFetchState.FAILED || it.seatFetchState == SeatFetchState.AUTH_REQUIRED }}"
        )

        return listOf(
            buildFeedInfo("FIRST_URL", CinemaRepository.FIRST_URL, telemetry.firstUrl),
            buildFeedInfo("HOURLY_URL", CinemaRepository.HOURLY_URL, telemetry.hourlyUrl),
            buildFeedInfo("OFFICIAL_QUICKBOOK", PlanetQuickBookClient.buildScheduleUrl(cinemaDate), telemetry.quickBook),
            seatsInfo
        )
    }

    fun toJson(snapshot: LiveOccupancyValidationSnapshot): String = snapshotAdapter.toJson(snapshot)

    fun fromJson(rawJson: String): LiveOccupancyValidationSnapshot? = snapshotAdapter.fromJson(rawJson)

    fun saveSnapshotJson(directory: File, snapshot: LiveOccupancyValidationSnapshot): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val json = toJson(snapshot)
        val archiveFile = File(directory, "snapshot_${snapshot.snapshotTimestampEpochMs}.json")
        archiveFile.writeText(json, Charsets.UTF_8)

        val latestFile = File(directory, "latest_validation_snapshot.json")
        latestFile.writeText(json, Charsets.UTF_8)
        return latestFile
    }

    fun compareSnapshots(
        previous: LiveOccupancyValidationSnapshot,
        current: LiveOccupancyValidationSnapshot
    ): SnapshotComparisonDelta {
        val prevBySlot = previous.activeScreenings.associateBy {
            canonicalSlotKey(it.room, it.startTime, it.movieTitle)
        }
        val currBySlot = current.activeScreenings.associateBy {
            canonicalSlotKey(it.room, it.startTime, it.movieTitle)
        }

        val allKeys = (prevBySlot.keys + currBySlot.keys).distinct()
        val changed = mutableListOf<ScreeningDeltaSummary>()
        for (key in allKeys) {
            val prev = prevBySlot[key]
            val curr = currBySlot[key]
            val prevOcc = prev?.estimatedOccupancy
            val currOcc = curr?.estimatedOccupancy
            if (prevOcc != currOcc || prev == null || curr == null) {
                val delta = if (prevOcc != null && currOcc != null) currOcc - prevOcc else null
                changed.add(
                    ScreeningDeltaSummary(
                        slotKey = key,
                        room = curr?.room ?: prev?.room ?: "",
                        movieTitle = curr?.movieTitle ?: prev?.movieTitle ?: "",
                        previousOccupancy = prevOcc,
                        currentOccupancy = currOcc,
                        delta = delta
                    )
                )
            }
        }

        return SnapshotComparisonDelta(
            previousTimestampIso = previous.snapshotTimestampIsraelIso,
            currentTimestampIso = current.snapshotTimestampIsraelIso,
            occupancyDelta = current.totalEstimatedOccupancy - previous.totalEstimatedOccupancy,
            verifiedSeatsDelta = current.verifiedOccupiedAndReservedSeats - previous.verifiedOccupiedAndReservedSeats,
            ratioEstimatedDelta = current.ratioEstimatedOccupancy - previous.ratioEstimatedOccupancy,
            activeScreeningsDelta = current.activeScreeningsCount - previous.activeScreeningsCount,
            completenessDelta = current.dataCompletenessPercentage - previous.dataCompletenessPercentage,
            changedScreenings = changed
        )
    }
}
