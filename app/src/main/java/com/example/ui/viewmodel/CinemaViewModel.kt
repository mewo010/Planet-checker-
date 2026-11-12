package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.diagnostics.LiveValidationSnapshotBuilder
import com.example.data.diagnostics.SourceTelemetryBundle
import com.example.data.local.PlanetDatabase
import com.example.data.model.AvailabilitySource
import com.example.data.model.CinemaEvent
import com.example.data.model.CinemaTab
import com.example.data.model.CinemaUiState
import com.example.data.model.EventKind
import com.example.data.model.KpiStats
import com.example.data.model.MovieSummary
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.data.model.SortOption
import com.example.data.model.calculateCinemaOccupancy
import com.example.data.model.deduplicateScreenings
import com.example.data.repository.AttendanceHistoryRepository
import com.example.data.repository.CinemaRepository
import com.example.data.repository.SeatAvailabilityRepository
import com.example.worker.CinemaMonitorWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class CinemaViewModel private constructor(
    application: Application,
    private val repository: CinemaRepository,
    private val seatAvailabilityRepository: SeatAvailabilityRepository,
    private val attendanceRepository: AttendanceHistoryRepository?,
    private val scheduleBackgroundWork: Boolean
) : AndroidViewModel(application) {

    @Suppress("unused")
    constructor(application: Application) : this(
        application = application,
        repository = CinemaRepository(),
        seatAvailabilityRepository = SeatAvailabilityRepository(),
        attendanceRepository = AttendanceHistoryRepository(
            PlanetDatabase.getInstance(application).attendanceDao()
        ),
        scheduleBackgroundWork = true
    )

    private val _uiState = MutableStateFlow(CinemaUiState(isLoading = true))
    val uiState: StateFlow<CinemaUiState> = _uiState.asStateFlow()

    private val jerusalemZone = ZoneId.of("Asia/Jerusalem")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val hhmmFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private var tickerJob: Job? = null
    private var posterJob: Job? = null
    private var seatRefreshJob: Job? = null

    init {
        val initialCinemaDate = getEffectiveCinemaDate(getCurrentIsraelZonedTime()).toString()
        _uiState.update {
            it.copy(
                currentCinemaIsoDate = initialCinemaDate,
                selectedHistoryDate = initialCinemaDate
            )
        }
        if (scheduleBackgroundWork) {
            CinemaMonitorWorker.schedulePeriodicMonitoring(application)
        }
        observeAttendanceHistory()
        startClockAndPeriodicUpdates()
        loadData(isUserInitiated = false)
    }

    private fun observeAttendanceHistory() {
        val historyRepo = attendanceRepository ?: return
        viewModelScope.launch {
            historyRepo.observeAvailableDates().collect { storedDates ->
                val todayIso = _uiState.value.currentCinemaIsoDate
                val mergedDates = (listOf(todayIso) + storedDates)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedDescending()
                _uiState.update { it.copy(availableHistoryDates = mergedDates) }
            }
        }

        viewModelScope.launch {
            _uiState
                .map { it.selectedHistoryDate to it.currentCinemaIsoDate }
                .distinctUntilChanged()
                .flatMapLatest { (selectedDate, todayIso) ->
                    if (selectedDate.isBlank()) {
                        flowOf(null)
                    } else {
                        historyRepo.observeDailyStats(
                            dateIso = selectedDate,
                            todayIso = todayIso,
                            liveScreeningsProvider = { _uiState.value.allScreenings },
                            nowProvider = { getCurrentIsraelZonedTime() }
                        )
                    }
                }
                .collect { stats ->
                    _uiState.update { it.copy(dailyStats = stats) }
                }
        }
    }

    private fun startClockAndPeriodicUpdates() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var secondCounter = 0
            while (isActive) {
                updateCurrentTimeAndRecalculate()
                delay(1000)
                secondCounter++
                // Refresh live active seat maps every 5 minutes (only for screenings with presentationId)
                if (secondCounter % 300 == 0 && secondCounter % 900 != 0) {
                    refreshActiveSeatsOnly()
                }
                // Full schedule & hourly availability refresh every 15 minutes
                if (secondCounter >= 900) {
                    secondCounter = 0
                    loadData(isUserInitiated = false)
                }
            }
        }
    }

    private fun getCurrentIsraelZonedTime(): ZonedDateTime {
        return try {
            ZonedDateTime.now(jerusalemZone)
        } catch (_: Exception) {
            ZonedDateTime.now()
        }
    }

    private fun getEffectiveCinemaDate(now: ZonedDateTime): LocalDate {
        val jerusalem = now.withZoneSameInstant(jerusalemZone)
        val rawMinutes = jerusalem.hour * 60 + jerusalem.minute
        return if (rawMinutes < 420) jerusalem.toLocalDate().minusDays(1) else jerusalem.toLocalDate()
    }

    private fun updateCurrentTimeAndRecalculate() {
        val now = getCurrentIsraelZonedTime()
        val formattedTime = now.format(timeFormatter)
        val formattedDate = now.format(dateFormatter)
        val cinemaIsoDate = getEffectiveCinemaDate(now).toString()
        val rawMinutes = now.hour * 60 + now.minute
        val cinemaMinutes = repository.normalizeCinemaMinutes(rawMinutes)

        _uiState.update { state ->
            val updatedScreenings = state.allScreenings.map { s ->
                s.copy(status = s.statusAt(now))
            }

            val (kpis, events) = calculateKpisAndEvents(
                screenings = updatedScreenings,
                now = now,
                nowMin = cinemaMinutes,
                lastUpdatedEpochMs = state.lastSuccessfulUpdateEpochMs
            )
            val filtered = applyFilterAndSort(
                all = updatedScreenings,
                nowMin = cinemaMinutes,
                type = state.filterType,
                query = state.searchQuery,
                movieTitle = state.selectedMovieTitle,
                sortBy = state.sortBy,
                showEnded = state.showEnded
            )
            val counts = calculateTypeCounts(updatedScreenings)
            val freshness = computeFreshnessLabel(
                lastUpdateMs = state.lastSuccessfulUpdateEpochMs,
                isLiveSource = state.isLiveDataSource
            )

            // Keep selectedHistoryDate tracking today if previously on old date or unset
            val selectedDate = state.selectedHistoryDate.ifBlank { cinemaIsoDate }

            state.copy(
                currentIsraelTime = formattedTime,
                currentDate = formattedDate,
                currentCinemaIsoDate = cinemaIsoDate,
                selectedHistoryDate = selectedDate,
                currentCinemaMinutes = cinemaMinutes,
                dataFreshnessLabel = freshness,
                allScreenings = updatedScreenings,
                filteredScreenings = filtered,
                timelineEvents = events,
                kpiStats = kpis,
                countsByType = counts
            )
        }
    }

    private fun computeFreshnessLabel(lastUpdateMs: Long?, isLiveSource: Boolean): String {
        if (!isLiveSource || lastUpdateMs == null) return "אופליין / נתוני דוגמה"
        val ageMin = ((System.currentTimeMillis() - lastUpdateMs) / 60_000L).coerceAtLeast(0)
        return when {
            ageMin < 1 -> "עדכני (ברגע זה)"
            ageMin <= 5 -> "עדכני (לפני $ageMin דק')"
            ageMin <= 20 -> "עודכן לפני $ageMin דק'"
            else -> "נתון ישן (לפני $ageMin דק')"
        }
    }

    fun loadData(isUserInitiated: Boolean = true) {
        viewModelScope.launch {
            _uiState.update {
                if (isUserInitiated) it.copy(isRefreshing = true) else it
            }

            val now = getCurrentIsraelZonedTime()
            val rawMinutes = now.hour * 60 + now.minute
            val cinemaMinutes = repository.normalizeCinemaMinutes(rawMinutes)

            val result = repository.loadSchedule(cinemaMinutes)

            val fetchedAt = result.fetchedAtEpochMs
            val effectiveUpdatedMs = fetchedAt ?: _uiState.value.lastSuccessfulUpdateEpochMs
            val (kpis, events) = calculateKpisAndEvents(
                screenings = result.screenings,
                now = now,
                nowMin = cinemaMinutes,
                lastUpdatedEpochMs = effectiveUpdatedMs
            )
            val movieSummaries = generateMovieSummaries(result.screenings)
            val counts = calculateTypeCounts(result.screenings)
            val lastUpdateFormatted = fetchedAt?.let {
                Instant.ofEpochMilli(it).atZone(jerusalemZone).format(hhmmFormatter)
            } ?: "לא זמין"

            val cinemaIsoDate = result.cinemaDate.toString()

            _uiState.update { state ->
                val filtered = applyFilterAndSort(
                    all = result.screenings,
                    nowMin = cinemaMinutes,
                    type = state.filterType,
                    query = state.searchQuery,
                    movieTitle = state.selectedMovieTitle,
                    sortBy = state.sortBy,
                    showEnded = state.showEnded
                )

                val selectedHistory = if (state.selectedHistoryDate.isBlank()) cinemaIsoDate else state.selectedHistoryDate
                val mergedDates = (listOf(cinemaIsoDate) + state.availableHistoryDates).distinct().sortedDescending()

                state.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLiveDataSource = result.isLive,
                    dataSourceLabel = if (result.isLive) "נתונים חיים • ${result.dateLabel}" else "נתוני דוגמה (אין חיבור)",
                    currentCinemaIsoDate = cinemaIsoDate,
                    selectedHistoryDate = selectedHistory,
                    availableHistoryDates = mergedDates,
                    lastSuccessfulUpdateEpochMs = fetchedAt ?: state.lastSuccessfulUpdateEpochMs,
                    lastSuccessfulUpdateLabel = if (fetchedAt != null) lastUpdateFormatted else state.lastSuccessfulUpdateLabel,
                    dataFreshnessLabel = computeFreshnessLabel(fetchedAt ?: state.lastSuccessfulUpdateEpochMs, result.isLive),
                    quickBookStatus = result.quickBookStatus,
                    sourceTelemetry = result.sourceTelemetry,
                    allScreenings = result.screenings,
                    filteredScreenings = filtered,
                    timelineEvents = events,
                    movieSummaries = movieSummaries,
                    kpiStats = kpis,
                    countsByType = counts,
                    errorMessage = null
                )
            }

            buildAndPersistValidationSnapshot(
                screenings = if (result.isLive) result.screenings else emptyList(),
                telemetry = result.sourceTelemetry,
                now = now,
                nowEpochMs = fetchedAt ?: System.currentTimeMillis()
            )

            if (result.isLive) {
                attendanceRepository?.recordLiveObservation(
                    screenings = result.screenings,
                    cinemaDate = result.cinemaDate,
                    now = now,
                    cinemaNowMin = cinemaMinutes
                )
                refreshStatsSnapshotOnce()
                enrichSeatMapsInBackground(result.screenings, result.cinemaDate, forceRefresh = isUserInitiated)
            } else {
                attendanceRepository?.recordOfflineAttempt(result.cinemaDate)
                refreshStatsSnapshotOnce()
            }

            fetchPostersInBackground(result.screenings)
        }
    }

    private fun refreshActiveSeatsOnly() {
        val state = _uiState.value
        if (!state.isLiveDataSource) return
        val activeWithPid = state.allScreenings.filter {
            !it.presentationId.isNullOrBlank() && it.status != ScreeningStatus.ENDED
        }
        if (activeWithPid.isEmpty()) return
        val cinemaDate = LocalDate.parse(state.currentCinemaIsoDate)
        enrichSeatMapsInBackground(state.allScreenings, cinemaDate, forceRefresh = false)
    }

    private fun enrichSeatMapsInBackground(
        screenings: List<Screening>,
        cinemaDate: LocalDate,
        forceRefresh: Boolean
    ) {
        val hasAnyPresentationId = screenings.any { !it.presentationId.isNullOrBlank() && it.status != ScreeningStatus.ENDED }
        if (!hasAnyPresentationId) return

        seatRefreshJob?.cancel()
        seatRefreshJob = viewModelScope.launch {
            _uiState.update { it.copy(seatMapUpdateInProgress = true) }
            val enriched = repository.enrichWithLiveSeatAvailability(
                screenings = screenings,
                seatRepository = seatAvailabilityRepository,
                forceRefreshActive = forceRefresh
            )
            val now = getCurrentIsraelZonedTime()
            val cinemaMinutes = repository.normalizeCinemaMinutes(now.hour * 60 + now.minute)
            val anyUnverified = enriched.any { it.seatSummary?.hasUnverifiedStatuses == true }
            val nowEpoch = System.currentTimeMillis()
            val (kpis, events) = calculateKpisAndEvents(
                screenings = enriched,
                now = now,
                nowMin = cinemaMinutes,
                lastUpdatedEpochMs = nowEpoch
            )
            val lastUpdateFormatted = Instant.ofEpochMilli(nowEpoch).atZone(jerusalemZone).format(hhmmFormatter)

            _uiState.update { state ->
                val filtered = applyFilterAndSort(
                    all = enriched,
                    nowMin = cinemaMinutes,
                    type = state.filterType,
                    query = state.searchQuery,
                    movieTitle = state.selectedMovieTitle,
                    sortBy = state.sortBy,
                    showEnded = state.showEnded
                )
                val updatedSelected = state.selectedScreening?.let { sel ->
                    enriched.firstOrNull { it.id == sel.id } ?: sel
                }
                state.copy(
                    seatMapUpdateInProgress = false,
                    hasUnverifiedSeatStatuses = anyUnverified,
                    lastSuccessfulUpdateEpochMs = nowEpoch,
                    lastSuccessfulUpdateLabel = lastUpdateFormatted,
                    dataFreshnessLabel = computeFreshnessLabel(nowEpoch, state.isLiveDataSource),
                    allScreenings = enriched,
                    filteredScreenings = filtered,
                    timelineEvents = events,
                    kpiStats = kpis,
                    selectedScreening = updatedSelected
                )
            }

            buildAndPersistValidationSnapshot(
                screenings = enriched,
                telemetry = _uiState.value.sourceTelemetry,
                now = now,
                nowEpochMs = nowEpoch
            )

            attendanceRepository?.recordLiveObservation(
                screenings = enriched,
                cinemaDate = cinemaDate,
                now = now,
                cinemaNowMin = cinemaMinutes
            )
            refreshStatsSnapshotOnce()
        }
    }

    private suspend fun buildAndPersistValidationSnapshot(
        screenings: List<Screening>,
        telemetry: SourceTelemetryBundle,
        now: ZonedDateTime,
        nowEpochMs: Long
    ) {
        val snapshot = LiveValidationSnapshotBuilder.buildSnapshot(
            screenings = screenings,
            now = now,
            nowEpochMs = nowEpochMs,
            telemetry = telemetry
        )
        val savedPath = withContext(Dispatchers.IO) {
            try {
                val filesDir = getApplication<Application>().filesDir
                if (filesDir != null) {
                    val dir = File(filesDir, "validation_snapshots")
                    LiveValidationSnapshotBuilder.saveSnapshotJson(dir, snapshot).absolutePath
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
        _uiState.update { state ->
            state.copy(
                latestValidationSnapshot = snapshot,
                latestValidationJsonPath = savedPath ?: state.latestValidationJsonPath
            )
        }
    }

    private suspend fun refreshStatsSnapshotOnce() {
        val historyRepo = attendanceRepository ?: return
        val state = _uiState.value
        val selectedDate = state.selectedHistoryDate.ifBlank { state.currentCinemaIsoDate }
        if (selectedDate.isBlank()) return
        val stats = historyRepo.getDailyStatsOnce(
            dateIso = selectedDate,
            todayIso = state.currentCinemaIsoDate,
            liveScreenings = state.allScreenings,
            now = getCurrentIsraelZonedTime()
        )
        _uiState.update { it.copy(dailyStats = stats) }
    }

    fun selectHistoryDate(dateIso: String) {
        _uiState.update { it.copy(selectedHistoryDate = dateIso) }
    }

    private fun fetchPostersInBackground(screenings: List<Screening>) {
        posterJob?.cancel()
        posterJob = viewModelScope.launch {
            val uniqueTitles = screenings.map { it.title }.distinct()
            uniqueTitles.forEach { title ->
                val posterUrl = repository.getPosterUrl(title)
                if (posterUrl != null) {
                    _uiState.update { state ->
                        val updatedAll = state.allScreenings.map { s ->
                            if (s.title == title) s.copy(posterUrl = posterUrl) else s
                        }
                        val updatedFiltered = state.filteredScreenings.map { s ->
                            if (s.title == title) s.copy(posterUrl = posterUrl) else s
                        }
                        val updatedSummaries = state.movieSummaries.map { m ->
                            if (m.title == title) m.copy(posterUrl = posterUrl) else m
                        }
                        state.copy(
                            allScreenings = updatedAll,
                            filteredScreenings = updatedFiltered,
                            movieSummaries = updatedSummaries
                        )
                    }
                }
            }
        }
    }

    private fun generateMovieSummaries(screenings: List<Screening>): List<MovieSummary> {
        val countMap = screenings.groupingBy { it.title }.eachCount()
        return countMap.map { (title, count) ->
            val meta = CinemaRepository.MOVIE_METADATA[title]
            val genre = meta?.first ?: "קולנוע"
            val colorHex = meta?.second ?: "#E11D48"
            val parsedColor = try {
                Color(android.graphics.Color.parseColor(colorHex))
            } catch (_: Exception) {
                Color(0xFFE11D48)
            }
            MovieSummary(
                title = title,
                genre = genre,
                screeningCount = count,
                posterUrl = screenings.firstOrNull { it.title == title }?.posterUrl,
                primaryColor = parsedColor
            )
        }.sortedByDescending { it.screeningCount }
    }

    private fun calculateKpisAndEvents(
        screenings: List<Screening>,
        now: ZonedDateTime,
        nowMin: Int,
        lastUpdatedEpochMs: Long? = null
    ): Pair<KpiStats, List<CinemaEvent>> {
        val uniqueScreenings = deduplicateScreenings(screenings)
        val occupancy = calculateCinemaOccupancy(uniqueScreenings, now, lastUpdatedEpochMs = lastUpdatedEpochMs)
        var out30 = 0
        var out30n = 0
        var in30 = 0
        var in30n = 0
        val events = mutableListOf<CinemaEvent>()

        uniqueScreenings.forEach { s ->
            val p = s.estimatedOccupiedSeats() ?: 0
            when (s.statusAt(now)) {
                ScreeningStatus.LIVE -> {
                    if (s.endMin - nowMin <= 30) {
                        out30 += p
                        out30n++
                    }
                    if (s.endMin >= nowMin - CinemaRepository.ENDED_KEEP && s.endMin <= nowMin + 120) {
                        val diff = s.endMin - nowMin
                        events.add(
                            CinemaEvent(
                                timeMin = s.endMin,
                                kind = EventKind.OUT,
                                screening = s,
                                isUrgent = diff <= 10,
                                minutesUntil = diff
                            )
                        )
                    }
                }
                ScreeningStatus.ENDED_RECENTLY -> {
                    if (s.endMin >= nowMin - CinemaRepository.ENDED_KEEP && s.endMin <= nowMin + 120) {
                        val diff = s.endMin - nowMin
                        events.add(
                            CinemaEvent(
                                timeMin = s.endMin,
                                kind = EventKind.OUT,
                                screening = s,
                                isUrgent = diff <= 10,
                                minutesUntil = diff
                            )
                        )
                    }
                }
                ScreeningStatus.UPCOMING -> {
                    val entryTime = s.entryMin
                    if (entryTime <= nowMin + 120) {
                        val effectiveTime = entryTime.coerceAtLeast(nowMin)
                        val diff = effectiveTime - nowMin
                        events.add(
                            CinemaEvent(
                                timeMin = effectiveTime,
                                kind = EventKind.IN,
                                screening = s,
                                isUrgent = diff <= 10,
                                minutesUntil = diff
                            )
                        )
                    }
                    if (entryTime <= nowMin + 30) {
                        in30 += p
                        in30n++
                    }
                }
                ScreeningStatus.ENDED -> {
                    // Already completed
                }
            }
        }

        events.sortWith(compareBy<CinemaEvent> { it.timeMin }.thenBy { if (it.kind == EventKind.OUT) 0 else 1 })

        val nextEv = events.firstOrNull()
        val stats = KpiStats(
            peopleInside = occupancy.occupiedSeats,
            activeHallsCount = occupancy.activeScreenings,
            activeKnownHallsCount = occupancy.countedScreenings,
            occupancyResult = occupancy,
            out30MinPeople = out30,
            out30MinHalls = out30n,
            in30MinPeople = in30,
            in30MinHalls = in30n,
            nextEvent = nextEv
        )

        return Pair(stats, events)
    }

    private fun calculateTypeCounts(screenings: List<Screening>): Map<String, Int> {
        val counts = mutableMapOf(
            "ALL" to 0,
            "LIVE" to 0,
            "IMAX" to 0,
            "VIP" to 0,
            "4DX" to 0,
            "ScreenX" to 0,
            "2D" to 0
        )
        screenings.forEach { s ->
            counts["ALL"] = (counts["ALL"] ?: 0) + 1
            if (s.status == ScreeningStatus.LIVE) {
                counts["LIVE"] = (counts["LIVE"] ?: 0) + 1
            }
            when (s.roomType) {
                RoomType.IMAX -> counts["IMAX"] = (counts["IMAX"] ?: 0) + 1
                RoomType.VIP -> counts["VIP"] = (counts["VIP"] ?: 0) + 1
                RoomType.FOUR_DX -> counts["4DX"] = (counts["4DX"] ?: 0) + 1
                RoomType.SCREEN_X -> counts["ScreenX"] = (counts["ScreenX"] ?: 0) + 1
                RoomType.TWO_D -> counts["2D"] = (counts["2D"] ?: 0) + 1
            }
        }
        return counts
    }

    private fun applyFilterAndSort(
        all: List<Screening>,
        nowMin: Int,
        type: String,
        query: String,
        movieTitle: String?,
        sortBy: SortOption,
        showEnded: Boolean
    ): List<Screening> {
        val trimmedQuery = query.trim()
        val visible = all.filter { s ->
            if (!showEnded && s.status == ScreeningStatus.ENDED) return@filter false
            if (trimmedQuery.isNotEmpty()) {
                val matchesTitle = s.title.contains(trimmedQuery, ignoreCase = true)
                val matchesRoom = s.room.contains(trimmedQuery, ignoreCase = true)
                val matchesNum = s.roomNumber?.toString() == trimmedQuery
                if (!matchesTitle && !matchesRoom && !matchesNum) return@filter false
            }
            if (movieTitle != null && s.title != movieTitle) {
                return@filter false
            }
            when (type) {
                "LIVE" -> s.status == ScreeningStatus.LIVE
                "IMAX" -> s.roomType == RoomType.IMAX
                "VIP" -> s.roomType == RoomType.VIP
                "4DX" -> s.roomType == RoomType.FOUR_DX
                "ScreenX" -> s.roomType == RoomType.SCREEN_X
                "2D" -> s.roomType == RoomType.TWO_D
                else -> true
            }
        }

        return when (sortBy) {
            SortOption.START_TIME -> visible.sortedBy { it.startMin }
            SortOption.END_TIME -> visible.sortedBy { it.endMin }
            SortOption.ROOM -> visible.sortedWith(
                compareBy<Screening> { it.roomNumber ?: 99 }.thenBy { it.startMin }
            )
            SortOption.CROWD -> visible.sortedByDescending { it.estimatedOccupiedSeats() ?: 0 }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyFilterAndSort(
                all = state.allScreenings,
                nowMin = state.currentCinemaMinutes,
                type = state.filterType,
                query = query,
                movieTitle = state.selectedMovieTitle,
                sortBy = state.sortBy,
                showEnded = state.showEnded
            )
            state.copy(searchQuery = query, filteredScreenings = filtered)
        }
    }

    fun setFilterType(type: String) {
        _uiState.update { state ->
            val filtered = applyFilterAndSort(
                all = state.allScreenings,
                nowMin = state.currentCinemaMinutes,
                type = type,
                query = state.searchQuery,
                movieTitle = state.selectedMovieTitle,
                sortBy = state.sortBy,
                showEnded = state.showEnded
            )
            state.copy(filterType = type, filteredScreenings = filtered)
        }
    }

    fun toggleMovieSelection(title: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedMovieTitle == title) null else title
            val filtered = applyFilterAndSort(
                all = state.allScreenings,
                nowMin = state.currentCinemaMinutes,
                type = state.filterType,
                query = state.searchQuery,
                movieTitle = newSelection,
                sortBy = state.sortBy,
                showEnded = state.showEnded
            )
            state.copy(selectedMovieTitle = newSelection, filteredScreenings = filtered)
        }
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.update { state ->
            val filtered = applyFilterAndSort(
                all = state.allScreenings,
                nowMin = state.currentCinemaMinutes,
                type = state.filterType,
                query = state.searchQuery,
                movieTitle = state.selectedMovieTitle,
                sortBy = sortOption,
                showEnded = state.showEnded
            )
            state.copy(sortBy = sortOption, filteredScreenings = filtered)
        }
    }

    fun toggleShowEnded(show: Boolean) {
        _uiState.update { state ->
            val filtered = applyFilterAndSort(
                all = state.allScreenings,
                nowMin = state.currentCinemaMinutes,
                type = state.filterType,
                query = state.searchQuery,
                movieTitle = state.selectedMovieTitle,
                sortBy = state.sortBy,
                showEnded = show
            )
            state.copy(showEnded = show, filteredScreenings = filtered)
        }
    }

    fun selectScreening(screening: Screening?) {
        _uiState.update { it.copy(selectedScreening = screening) }
    }

    fun setActiveTab(tab: CinemaTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }
}
