package com.example.data.remote

import com.example.data.model.RawScreening
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Client for YES PLANET's official public QuickBook schedule endpoint:
 * GET https://www.planetcinema.co.il/il/data-api-service/v1/quickbook/10100/film-events/in-cinema/{cinemaId}/at-date/{YYYY-MM-DD}?attr=&lang=he_IL
 *
 * Tenant `10100` = Planet Israel.
 * Cinema `1072` = Planet Rishon LeZion.
 *
 * Strictly read-only public schedule endpoint — does NOT bypass Cloudflare challenges,
 * Turnstile, authentication, or checkout protections; surfaces HTTP blocks transparently.
 */
interface PlanetQuickBookClient {
    suspend fun fetchDailySchedule(
        cinemaDate: LocalDate,
        cinemaId: String = DEFAULT_CINEMA_ID
    ): QuickBookFetchResult

    companion object {
        const val TENANT_ID = "10100"
        const val DEFAULT_CINEMA_ID = "1072"
        const val DEFAULT_DURATION_MINUTES = 120

        fun buildScheduleUrl(
            cinemaDate: LocalDate,
            cinemaId: String = DEFAULT_CINEMA_ID
        ): String {
            return "https://www.planetcinema.co.il/il/data-api-service/v1/quickbook/$TENANT_ID/film-events/in-cinema/$cinemaId/at-date/$cinemaDate?attr=&lang=he_IL"
        }
    }
}

sealed class QuickBookFetchResult {
    data class Success(
        val cinemaDate: LocalDate,
        val screenings: List<RawScreening>,
        val posterByTitle: Map<String, String>,
        val httpCode: Int = 200,
        val fetchedAtEpochMs: Long = System.currentTimeMillis()
    ) : QuickBookFetchResult()

    data class HttpBlockedOrError(
        val httpCode: Int,
        val message: String
    ) : QuickBookFetchResult()

    data class NetworkError(
        val message: String,
        val cause: Throwable? = null
    ) : QuickBookFetchResult()
}

data class QuickBookParsedSchedule(
    val screenings: List<RawScreening>,
    val posterByTitle: Map<String, String>
)

class DefaultPlanetQuickBookClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : PlanetQuickBookClient {

    override suspend fun fetchDailySchedule(
        cinemaDate: LocalDate,
        cinemaId: String
    ): QuickBookFetchResult {
        val url = PlanetQuickBookClient.buildScheduleUrl(cinemaDate, cinemaId)
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "he-IL,he;q=0.9,en;q=0.8")
                .header("User-Agent", "PlanetChecker-Android/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = when (code) {
                        403 -> "נחסם ע\"י הגנת WAF/Cloudflare באתר Planet (HTTP 403)"
                        429 -> "חריגת קצב בקשות באתר Planet (HTTP 429)"
                        else -> "שגיאת HTTP $code משרת Planet QuickBook"
                    }
                    return QuickBookFetchResult.HttpBlockedOrError(code, reason)
                }
                if (bodyStr.isBlank() || bodyStr.trimStart().startsWith("<")) {
                    return QuickBookFetchResult.HttpBlockedOrError(
                        httpCode = code,
                        message = "אתר Planet החזיר דף HTML/חסימה במקום JSON"
                    )
                }
                val parsed = parseQuickBookScheduleJson(bodyStr, cinemaDate)
                QuickBookFetchResult.Success(
                    cinemaDate = cinemaDate,
                    screenings = parsed.screenings,
                    posterByTitle = parsed.posterByTitle,
                    httpCode = code,
                    fetchedAtEpochMs = nowProvider()
                )
            }
        } catch (e: IOException) {
            QuickBookFetchResult.NetworkError(
                message = e.message ?: "שגיאת תקשורת מול planetcinema.co.il",
                cause = e
            )
        } catch (e: Exception) {
            QuickBookFetchResult.NetworkError(
                message = e.message ?: "שגיאת פענוח QuickBook",
                cause = e
            )
        }
    }
}

private val quickBookMoshi: Moshi = Moshi.Builder().build()
private val quickBookMapType = Types.newParameterizedType(
    Map::class.java,
    String::class.java,
    Any::class.java
)
private val quickBookMapAdapter = quickBookMoshi.adapter<Map<String, Any?>>(quickBookMapType)

/**
 * Pure JSON parser for QuickBook v1 `film-events/in-cinema/{id}/at-date/{date}` powered by Moshi
 * so it works identically on Android runtime and pure JVM unit tests.
 */
fun parseQuickBookScheduleJson(
    rawJson: String,
    defaultDate: LocalDate,
    defaultDurationMinutes: Int = PlanetQuickBookClient.DEFAULT_DURATION_MINUTES
): QuickBookParsedSchedule {
    val root = quickBookMapAdapter.fromJson(rawJson.trim()) ?: emptyMap()
    val body = (root["body"] as? Map<*, *>) ?: root

    val filmsArray = body["films"] as? List<*> ?: emptyList<Any?>()
    val eventsArray = body["events"] as? List<*> ?: emptyList<Any?>()

    data class FilmInfo(
        val id: String,
        val title: String,
        val lengthMinutes: Int?,
        val posterUrl: String?
    )

    val filmsById = linkedMapOf<String, FilmInfo>()
    val posterByTitle = linkedMapOf<String, String>()

    for (item in filmsArray) {
        val obj = item as? Map<*, *> ?: continue
        val id = optMapScalarString(obj, "id") ?: continue
        val name = (optMapScalarString(obj, "name") ?: optMapScalarString(obj, "title")) ?: continue

        val len = optMapInt(obj, "length")?.takeIf { it in 20..420 }
            ?: optMapInt(obj, "duration")?.takeIf { it in 20..420 }
        val poster = optMapScalarString(obj, "posterLink")?.takeIf { it.startsWith("http") }

        filmsById[id] = FilmInfo(id = id, title = name, lengthMinutes = len, posterUrl = poster)
        if (poster != null) {
            posterByTitle[name] = poster
        }
    }

    val screenings = mutableListOf<RawScreening>()
    val hhmmFormatter = DateTimeFormatter.ofPattern("HH:mm")

    for (item in eventsArray) {
        val ev = item as? Map<*, *> ?: continue
        val filmId = optMapScalarString(ev, "filmId")
        val film = filmId?.let { filmsById[it] }
        val title = film?.title
            ?: optMapScalarString(ev, "filmName")
            ?: optMapScalarString(ev, "title")
            ?: continue

        val eventDateTimeStr = optMapScalarString(ev, "eventDateTime") ?: continue
        val startLocal = parseQuickBookDateTime(eventDateTimeStr) ?: continue
        val durationMin = film?.lengthMinutes
            ?: optMapInt(ev, "length")?.takeIf { it in 20..420 }
            ?: defaultDurationMinutes
        val endLocal = startLocal.plusMinutes(durationMin.toLong())

        val auditoriumRaw = optMapScalarString(ev, "auditorium")
            ?: optMapScalarString(ev, "auditoriumTinyName")
            ?: optMapScalarString(ev, "screen")
            ?: continue

        val normalizedRoom = if (auditoriumRaw.contains("אולם") || auditoriumRaw.any { !it.isDigit() && !it.isWhitespace() }) {
            auditoriumRaw
        } else {
            "אולם $auditoriumRaw"
        }

        val eventId = optMapScalarString(ev, "id")
        val presId = optMapScalarString(ev, "presentationId") ?: eventId
        val presCode = optMapScalarString(ev, "presentationCode")
        val bookingUrl = optMapScalarString(ev, "bookingLink") ?: optMapScalarString(ev, "url")

        val soldOut = ev["soldOut"] as? Boolean
        val binaryRatio = if (soldOut == true) 0.0f else null

        val businessDate = optMapScalarString(ev, "businessDay")
            ?.takeIf { it.length >= 10 }
            ?.substring(0, 10)
            ?: defaultDate.toString()

        screenings.add(
            RawScreening(
                title = title,
                room = normalizedRoom,
                start = startLocal.format(hhmmFormatter),
                end = endLocal.format(hhmmFormatter),
                availabilityRatio = binaryRatio,
                presentationId = presId,
                presentationCode = presCode,
                bookingUrl = bookingUrl,
                date = businessDate,
                soldOut = soldOut
            )
        )
    }

    return QuickBookParsedSchedule(
        screenings = screenings,
        posterByTitle = posterByTitle
    )
}

private fun parseQuickBookDateTime(raw: String): LocalDateTime? {
    if (raw.isBlank()) return null
    val cleaned = raw.trim().replace("Z", "").substringBefore("+")
    return try {
        LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(cleaned.take(16), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        } catch (_: Exception) {
            null
        }
    }
}

private fun optMapScalarString(map: Map<*, *>, key: String): String? {
    val v = map[key] ?: return null
    val str = when (v) {
        is String -> v.trim()
        is Boolean -> v.toString()
        is Number -> {
            val d = v.toDouble()
            if (d.isFinite() && d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) {
                d.toLong().toString()
            } else {
                BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()
            }
        }
        else -> return null
    }
    return str.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun optMapInt(map: Map<*, *>, key: String): Int? {
    val v = map[key] ?: return null
    return when (v) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }
}
