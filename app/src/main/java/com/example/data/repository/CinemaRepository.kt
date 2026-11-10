package com.example.data.repository

import com.example.data.model.CrowdLevel
import com.example.data.model.FirstScheduleResponse
import com.example.data.model.HourlyScheduleResponse
import com.example.data.model.MovieSummary
import com.example.data.model.RawScreening
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CinemaRepository {

    companion object {
        const val FIRST_URL = "https://gist.githubusercontent.com/Gaash14/a12b463cc829c43a7ab0bf8385f1be63/raw/first-schedule.json"
        const val HOURLY_URL = "https://gist.githubusercontent.com/Gaash14/da630d78754949bce95065eadb9fd370/raw/schedule.json"

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
        val screenings: List<Screening>
    )

    suspend fun loadSchedule(currentNowMinutes: Int): FetchResult = withContext(Dispatchers.IO) {
        try {
            val baseJson = fetchUrl("$FIRST_URL?nocache=${System.currentTimeMillis()}")
            val firstAdapter = moshi.adapter(FirstScheduleResponse::class.java)
            val baseResponse = firstAdapter.fromJson(baseJson)

            var hourlyResponse: HourlyScheduleResponse? = null
            try {
                val hourlyJson = fetchUrl("$HOURLY_URL?nocache=${System.currentTimeMillis()}")
                val hourlyAdapter = moshi.adapter(HourlyScheduleResponse::class.java)
                hourlyResponse = hourlyAdapter.fromJson(hourlyJson)
            } catch (_: Exception) {
                // Hourly availability update is optional
            }

            val availabilityMap = mutableMapOf<String, Float?>()
            hourlyResponse?.screenings?.forEach { s ->
                val key = "${s.room}|${s.start}|${s.title}"
                availabilityMap[key] = s.availabilityRatio
            }

            val rawList = baseResponse?.screenings.orEmpty().map { s ->
                val key = "${s.room}|${s.start}|${s.title}"
                if (availabilityMap.containsKey(key)) {
                    s.copy(availabilityRatio = availabilityMap[key])
                } else s
            }

            if (rawList.isEmpty()) {
                throw IllegalStateException("Empty screenings from server")
            }

            val processed = prepareScreenings(rawList, currentNowMinutes)
            FetchResult(
                isLive = true,
                dateLabel = baseResponse?.date ?: "היום",
                screenings = processed
            )
        } catch (e: Exception) {
            val fallback = prepareScreenings(MOCK_SCREENINGS, currentNowMinutes)
            FetchResult(
                isLive = false,
                dateLabel = "נתוני דוגמה (אין חיבור)",
                screenings = fallback
            )
        }
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

    fun prepareScreenings(rawList: List<RawScreening>, nowMinutes: Int): List<Screening> {
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

            val ratio = raw.availabilityRatio?.takeIf { it in 0.0f..1.0f }
            val occupancyPct = if (ratio != null) {
                ((1f - ratio) * 100f).toInt().coerceIn(0, 100)
            } else 0

            val estimatedPeople = if (capacity != null && ratio != null) {
                (capacity * (1f - ratio.coerceIn(0f, 1f))).toInt()
            } else null

            val status = calculateStatus(s, e, nowMinutes)
            val crowdLevel = calculateCrowdLevel(ratio)
            val poster = posterCache[raw.title.replace(Regex("\\(.*?\\)"), "").trim()]

            Screening(
                id = "${raw.room}_${raw.start}_${raw.title}",
                title = raw.title,
                room = raw.room,
                roomNumber = roomNum,
                roomType = roomType,
                startStr = raw.start,
                endStr = raw.end,
                startMin = s,
                endMin = e,
                entryMin = entry,
                capacity = capacity,
                availabilityRatio = ratio,
                occupancyPercentage = occupancyPct,
                estimatedPeople = estimatedPeople,
                status = status,
                crowdLevel = crowdLevel,
                posterUrl = poster
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
        val parts = timeStr.split(":")
        if (parts.size < 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
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

    private fun calculateCrowdLevel(availabilityRatio: Float?): CrowdLevel {
        if (availabilityRatio == null) return CrowdLevel.UNKNOWN
        val occupancy = ((1f - availabilityRatio) * 100f).toInt()
        return when {
            occupancy > 85 -> CrowdLevel.VERY_BUSY
            occupancy > 60 -> CrowdLevel.BUSY
            occupancy > 30 -> CrowdLevel.MEDIUM
            else -> CrowdLevel.QUIET
        }
    }
}
