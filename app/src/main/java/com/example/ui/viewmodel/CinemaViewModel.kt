package com.example.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.data.repository.CinemaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CinemaViewModel(
    private val repository: CinemaRepository = CinemaRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CinemaUiState(isLoading = true))
    val uiState: StateFlow<CinemaUiState> = _uiState.asStateFlow()

    private val jerusalemZone = ZoneId.of("Asia/Jerusalem")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private var tickerJob: Job? = null
    private var posterJob: Job? = null

    init {
        startClockAndPeriodicUpdates()
        loadData()
    }

    private fun startClockAndPeriodicUpdates() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var secondCounter = 0
            while (isActive) {
                updateCurrentTimeAndRecalculate()
                delay(1000)
                secondCounter++
                // Automatically refresh network data every 30 minutes
                if (secondCounter >= 1800) {
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

    private fun updateCurrentTimeAndRecalculate() {
        val now = getCurrentIsraelZonedTime()
        val formattedTime = now.format(timeFormatter)
        val formattedDate = now.format(dateFormatter)
        val rawMinutes = now.hour * 60 + now.minute
        val cinemaMinutes = repository.normalizeCinemaMinutes(rawMinutes)

        _uiState.update { state ->
            val updatedScreenings = state.allScreenings.map { s ->
                val newStatus = when {
                    cinemaMinutes in s.startMin until s.endMin -> ScreeningStatus.LIVE
                    cinemaMinutes < s.startMin -> ScreeningStatus.UPCOMING
                    cinemaMinutes < s.endMin + CinemaRepository.ENDED_KEEP -> ScreeningStatus.ENDED_RECENTLY
                    else -> ScreeningStatus.ENDED
                }
                s.copy(status = newStatus)
            }

            val (kpis, events) = calculateKpisAndEvents(updatedScreenings, cinemaMinutes)
            val filtered = applyFilterAndSort(
                all = updatedScreenings,
                nowMin = cinemaMinutes,
                type = state.filterType,
                query = state.searchQuery,
                movieTitle = state.selectedMovieTitle,
                sortBy = state.sortBy,
                showEnded = state.showEnded
            )
            val counts = calculateTypeCounts(updatedScreenings, cinemaMinutes)

            state.copy(
                currentIsraelTime = formattedTime,
                currentDate = formattedDate,
                currentCinemaMinutes = cinemaMinutes,
                allScreenings = updatedScreenings,
                filteredScreenings = filtered,
                timelineEvents = events,
                kpiStats = kpis,
                countsByType = counts
            )
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

            val (kpis, events) = calculateKpisAndEvents(result.screenings, cinemaMinutes)
            val movieSummaries = generateMovieSummaries(result.screenings)
            val counts = calculateTypeCounts(result.screenings, cinemaMinutes)

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

                state.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLiveDataSource = result.isLive,
                    dataSourceLabel = if (result.isLive) "נתונים חיים • ${result.dateLabel}" else "נתוני דוגמה (אין חיבור)",
                    allScreenings = result.screenings,
                    filteredScreenings = filtered,
                    timelineEvents = events,
                    movieSummaries = movieSummaries,
                    kpiStats = kpis,
                    countsByType = counts,
                    errorMessage = null
                )
            }

            fetchPostersInBackground(result.screenings)
        }
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

    private fun calculateKpisAndEvents(screenings: List<Screening>, nowMin: Int): Pair<KpiStats, List<CinemaEvent>> {
        var inside = 0
        var activeRooms = 0
        var out30 = 0
        var out30n = 0
        var in30 = 0
        var in30n = 0
        val events = mutableListOf<CinemaEvent>()

        screenings.forEach { s ->
            val p = s.estimatedPeople ?: 0
            when (s.status) {
                ScreeningStatus.LIVE -> {
                    inside += p
                    activeRooms++
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
            peopleInside = inside,
            activeHallsCount = activeRooms,
            out30MinPeople = out30,
            out30MinHalls = out30n,
            in30MinPeople = in30,
            in30MinHalls = in30n,
            nextEvent = nextEv
        )

        return Pair(stats, events)
    }

    private fun calculateTypeCounts(screenings: List<Screening>, nowMin: Int): Map<String, Int> {
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
            SortOption.CROWD -> visible.sortedByDescending { it.estimatedPeople ?: 0 }
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
