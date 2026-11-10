package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class RoomType(val label: String, val badgeColor: Color, val containerColor: Color) {
    IMAX("IMAX", Color(0xFF38BDF8), Color(0xFF082F49)),
    VIP("VIP", Color(0xFFFACC15), Color(0xFF422006)),
    FOUR_DX("4DX", Color(0xFFA78BFA), Color(0xFF2E1065)),
    SCREEN_X("ScreenX", Color(0xFF34D399), Color(0xFF064E3B)),
    TWO_D("2D", Color(0xFF94A3B8), Color(0xFF1E293B));

    companion object {
        fun fromRoom(room: String): RoomType {
            val num = extractRoomNumber(room) ?: return TWO_D
            return when {
                num == 5 -> IMAX
                num == 19 -> SCREEN_X
                num == 20 -> FOUR_DX
                num in 21..24 -> VIP
                else -> TWO_D
            }
        }

        fun extractRoomNumber(room: String): Int? {
            val regex = Regex("\\d+")
            val match = regex.find(room)
            return match?.value?.toIntOrNull()
        }

        fun formatRoomLabel(room: String): String {
            return if (room.contains("אולם")) room else "אולם $room"
        }
    }
}

enum class ScreeningStatus {
    LIVE,
    UPCOMING,
    ENDED_RECENTLY,
    ENDED
}

enum class CrowdLevel(val label: String, val color: Color, val progressColor: Color) {
    QUIET("שקט", Color(0xFF34D399), Color(0xFF10B981)),
    MEDIUM("בינוני", Color(0xFF38BDF8), Color(0xFF0284C7)),
    BUSY("עמוס", Color(0xFFFBBF24), Color(0xFFF59E0B)),
    VERY_BUSY("עמוס מאוד", Color(0xFFFB7185), Color(0xFFE11D48)),
    UNKNOWN("אין נתונים", Color(0xFF94A3B8), Color(0xFF475569))
}

@JsonClass(generateAdapter = true)
data class RawScreening(
    @param:Json(name = "title") val title: String = "",
    @param:Json(name = "room") val room: String = "",
    @param:Json(name = "start") val start: String = "",
    @param:Json(name = "end") val end: String = "",
    @param:Json(name = "availabilityRatio") val availabilityRatio: Float? = null
)

@JsonClass(generateAdapter = true)
data class FirstScheduleResponse(
    @param:Json(name = "date") val date: String? = null,
    @param:Json(name = "screenings") val screenings: List<RawScreening> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HourlyScheduleResponse(
    @param:Json(name = "screenings") val screenings: List<RawScreening> = emptyList()
)

data class Screening(
    val id: String,
    val title: String,
    val room: String,
    val roomNumber: Int?,
    val roomType: RoomType,
    val startStr: String,
    val endStr: String,
    val startMin: Int,
    val endMin: Int,
    val entryMin: Int,
    val capacity: Int?,
    val availabilityRatio: Float?,
    val occupancyPercentage: Int,
    val estimatedPeople: Int?,
    val status: ScreeningStatus,
    val crowdLevel: CrowdLevel,
    val posterUrl: String? = null
) {
    val progress: Float
        get() = if (status == ScreeningStatus.LIVE) {
            val total = (endMin - startMin).coerceAtLeast(1)
            // Evaluated dynamically with current time
            0.5f
        } else 0f
}

data class CinemaEvent(
    val timeMin: Int,
    val kind: EventKind, // IN or OUT
    val screening: Screening,
    val isUrgent: Boolean,
    val minutesUntil: Int
)

enum class EventKind {
    IN,
    OUT
}

data class KpiStats(
    val peopleInside: Int = 0,
    val activeHallsCount: Int = 0,
    val out30MinPeople: Int = 0,
    val out30MinHalls: Int = 0,
    val in30MinPeople: Int = 0,
    val in30MinHalls: Int = 0,
    val nextEvent: CinemaEvent? = null
)

data class MovieSummary(
    val title: String,
    val genre: String,
    val screeningCount: Int,
    val posterUrl: String? = null,
    val primaryColor: Color = Color(0xFFE11D48)
)

data class CinemaUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLiveDataSource: Boolean = false,
    val dataSourceLabel: String = "טוען…",
    val currentDate: String = "",
    val currentIsraelTime: String = "--:--:--",
    val currentCinemaMinutes: Int = 0,
    val allScreenings: List<Screening> = emptyList(),
    val filteredScreenings: List<Screening> = emptyList(),
    val timelineEvents: List<CinemaEvent> = emptyList(),
    val movieSummaries: List<MovieSummary> = emptyList(),
    val kpiStats: KpiStats = KpiStats(),
    val filterType: String = "ALL", // ALL, LIVE, IMAX, VIP, 4DX, ScreenX, 2D
    val searchQuery: String = "",
    val selectedMovieTitle: String? = null,
    val sortBy: SortOption = SortOption.START_TIME,
    val showEnded: Boolean = false,
    val selectedScreening: Screening? = null,
    val activeTab: CinemaTab = CinemaTab.SCHEDULE,
    val errorMessage: String? = null,
    val countsByType: Map<String, Int> = emptyMap()
)

enum class SortOption(val title: String) {
    START_TIME("שעת התחלה"),
    END_TIME("שעת סיום"),
    ROOM("אולם"),
    CROWD("הכי עמוס")
}

enum class CinemaTab(val title: String) {
    SCHEDULE("לוח הקרנות"),
    TIMELINE("ציר עומסים"),
    HALLS_MAP("מפת אולמות")
}
