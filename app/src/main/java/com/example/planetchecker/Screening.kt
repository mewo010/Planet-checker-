package com.example.planetchecker

// ============================================================================
// Screening.kt  --  שחזור Kotlin נקי של הלוגיקה שחולצה מה-APK (Planet Rishon LeZion)
// מקור: ScreeningKt / Screening / AudienceStat / MovieAudienceStat / ScheduleResponse
// שחזור התנהגות 1:1 מה-bytecode (כולל מה שה-decompiler לא הצליח לפענח, שנקרא מה-smali).
// ============================================================================

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Models ---

data class Screening(
    val room: String,                       // למשל "אולם 5" / "Hall 5" – המספר מחולץ ב-roomNumberForSort
    val start: String,                      // "HH:mm" או "HH:mm:ss"
    val end: String,                        // "HH:mm" או "HH:mm:ss"
    val title: String,
    val date: LocalDate? = null,
    val availabilityRatio: Double? = null,  // 0.0..1.0 = שיעור המושבים הפנויים
    val presentationCode: String? = null    // מפתח לחיבור בין 3 קבצי ה-JSON
)

data class ScheduleResponse(val date: String, val screenings: List<Screening>)
data class AudienceStat(val label: String, val people: Int)
data class MovieAudienceStat(val title: String, val people: Int, val showings: Int)

enum class SortOrder { TIME, END_TIME, ROOM }
enum class RoomFilterMode { ALL, EXCLUDE, ONLY }
enum class AppTab { SCHEDULE, STATS }

val ISRAEL_TIME_ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

// ---------------------------------------------------------------- Helpers ---

fun Screening.stableKey(): String =
    "${date ?: "undated"}|${room.roomNumberForSort() ?: room.lowercase(Locale.ROOT)}|$start|$title"

fun millisecondsUntilNextQuarterHour(now: ZonedDateTime): Long {
    val minutesUntilNextQuarter = 15 - (now.minute % 15)
    val nextQuarter = now.withSecond(0).withNano(0).plusMinutes(minutesUntilNextQuarter.toLong())
    return Duration.between(now, nextQuarter).toMillis()
}

fun String.asLocalTime(): LocalTime? {
    val parts = trim().split(":")
    if (parts.size !in 2..3) return null
    return try {
        LocalTime.of(parts[0].toInt(), parts[1].toInt(), parts.getOrNull(2)?.toInt() ?: 0)
    } catch (e: NumberFormatException) {
        null
    } catch (e: DateTimeException) {
        null
    }
}

/** מיון "מ-07:00 בבוקר": שעות 00:00–06:59 נחשבות כסוף היום (אחרי 23:59). */
fun LocalTime.sortFromSevenAm(): Int {
    val secondsAfterSeven = toSecondOfDay() - LocalTime.of(7, 0).toSecondOfDay()
    return if (secondsAfterSeven < 0) 86400 + secondsAfterSeven else secondsAfterSeven
}

fun String.roomNumberForSort(): Long? = Regex("\\d+").find(this)?.value?.toLongOrNull()

/** שעת סיום מותאמת = שעת הסיום ב-JSON + 15 דקות (פרסומות/ניקיון/כניסה). */
fun Screening.adjustedEndTime(): LocalTime? = end.asLocalTime()?.plusMinutes(15)

fun Screening.displayEndTime(): String =
    adjustedEndTime()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: end

// ------------------------------------------------- Room data (Planet RL) ---

/** סוג האולם לפי מספר: 1-4 ו-6-18 = 2D, 5 = IMAX, 19 = ScreenX, 20 = 4DX, 21-24 = VIP. */
fun Screening.roomType(): String? {
    val n = room.roomNumberForSort() ?: return null
    return when {
        n in 1L..4L || n in 6L..18L -> "2D"
        n == 5L -> "IMAX"
        n == 19L -> "ScreenX"
        n == 20L -> "4DX"
        n in 21L..24L -> "VIP"
        else -> null
    }
}

/** מספר המושבים בכל אולם. */
fun Screening.roomCapacity(): Int? = when (room.roomNumberForSort()) {
    1L -> 124
    2L -> 124
    3L -> 162
    4L -> 163
    5L -> 400   // IMAX
    6L -> 123
    7L -> 311
    8L -> 154
    9L -> 88
    10L -> 217
    11L -> 251
    12L -> 118
    13L -> 119
    14L -> 119
    15L -> 119
    16L -> 182
    17L -> 182
    18L -> 112
    19L -> 204  // ScreenX
    20L -> 124  // 4DX
    21L -> 54   // VIP
    22L -> 54   // VIP
    23L -> 70   // VIP
    24L -> 56   // VIP
    else -> null
}

/** מושבים שנספרים כ"תפוסים" ב-availabilityRatio אבל אינם צופים אמיתיים (נכים/צוות וכד') – מופחתים מהחישוב. */
fun Screening.reservedSeats(): Int = when (room.roomNumberForSort()) {
    1L, 2L, 4L, 8L, 10L, 12L, 13L, 14L, 15L, 16L, 17L, 18L -> 3
    3L, 5L, 6L, 7L, 11L, 19L -> 2
    else -> 0   // 9, 20, 21, 22, 23, 24 וכל אולם לא מוכר
}

// ------------------------------------------------ THE audience formula ---

/**
 * הנוסחה המרכזית:
 *   צופים = round( קיבולת × (1 − availabilityRatio) ) − מושבים שמורים
 * מחזיר null אם: אולם לא מוכר, אין ratio, או ratio לא תקין (NaN / מחוץ ל-0..1).
 * שים לב: אין חיתוך ל-0, כלומר התוצאה עלולה (נדיר) להיות שלילית – כך זה במקור.
 */
fun Screening.estimatedOccupiedSeats(): Int? {
    val capacity = roomCapacity() ?: return null
    val ratio = availabilityRatio?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: return null
    return (capacity * (1.0 - ratio)).roundToInt() - reservedSeats()
}

// ----------------------------------------------------------- Time logic ---

fun Screening.scheduledStartAt(now: ZonedDateTime): ZonedDateTime? {
    val startTime = start.asLocalTime() ?: return null
    val scheduleDate = date ?: now.withZoneSameInstant(ISRAEL_TIME_ZONE).toLocalDate()
    return scheduleDate.atTime(startTime).atZone(ISRAEL_TIME_ZONE)
}

/** אם שעת הסיום (+15 דק') <= שעת ההתחלה – ההקרנה נגמרת למחרת (אחרי חצות). */
private fun Screening.scheduledEndAt(now: ZonedDateTime): ZonedDateTime? {
    val startTime = start.asLocalTime() ?: return null
    val endTime = adjustedEndTime() ?: return null
    val startAt = scheduledStartAt(now) ?: return null
    val endDate = if (endTime <= startTime) startAt.toLocalDate().plusDays(1) else startAt.toLocalDate()
    return endDate.atTime(endTime).atZone(ISRAEL_TIME_ZONE)
}

fun Screening.isShowingAt(now: ZonedDateTime): Boolean {
    val startAt = scheduledStartAt(now) ?: return false
    val endAt = scheduledEndAt(now) ?: return false
    val n = now.withZoneSameInstant(ISRAEL_TIME_ZONE)
    return n >= startAt && n < endAt
}

private fun Screening.finishedAt(now: ZonedDateTime): ZonedDateTime? {
    val endAt = scheduledEndAt(now) ?: return null
    return if (now.withZoneSameInstant(ISRAEL_TIME_ZONE) >= endAt) endAt else null
}

fun Screening.hasFinishedAt(now: ZonedDateTime): Boolean = finishedAt(now) != null

/** ברירת מחדל: הקרנה שהסתיימה נשארת ברשימה 180 שניות. */
fun Screening.hasEndedRecentlyAt(now: ZonedDateTime, retentionSeconds: Long = 180): Boolean {
    if (retentionSeconds <= 0) return false
    val finished = finishedAt(now) ?: return false
    val secondsSinceEnd = Duration.between(finished, now.withZoneSameInstant(ISRAEL_TIME_ZONE)).seconds
    return secondsSinceEnd in 0L until retentionSeconds
}

fun Screening.minutesUntilEndAt(now: ZonedDateTime): Long? {
    if (!isShowingAt(now)) return null
    val endAt = scheduledEndAt(now) ?: return null
    return Duration.between(now.withZoneSameInstant(ISRAEL_TIME_ZONE), endAt).toMinutes()
}

/** ההקרנה הבאה באותו אולם שמתחילה אחרי שההקרנה הנוכחית הסתיימה (null אם ההקרנה הנוכחית עוד לא הסתיימה). */
fun Screening.nextScreeningInSameRoom(screenings: List<Screening>, now: ZonedDateTime): Screening? {
    val finished = finishedAt(now) ?: return null
    val currentRoomNumber = room.roomNumberForSort()
    return screenings
        .asSequence()
        .filter { candidate ->
            candidate != this && (
                if (currentRoomNumber == null) candidate.room.equals(room, ignoreCase = true)
                else candidate.room.roomNumberForSort() == currentRoomNumber
            )
        }
        .mapNotNull { candidate ->
            val startAt = candidate.scheduledStartAt(now) ?: return@mapNotNull null
            if (startAt.isAfter(finished)) candidate to startAt else null
        }
        .minByOrNull { it.second }
        ?.first
}

fun Screening.nextScreeningStartInSameRoom(screenings: List<Screening>, now: ZonedDateTime): ZonedDateTime? =
    nextScreeningInSameRoom(screenings, now)?.scheduledStartAt(now)

// ------------------------------------------------------- Aggregations ---

/** "צופים כרגע" – סכום הצופים בהקרנות שמתקיימות ברגע זה. */
fun currentlyOccupiedSeats(screenings: List<Screening>, now: ZonedDateTime): Int =
    screenings.asSequence()
        .filter { it.isShowingAt(now) }
        .mapNotNull { it.estimatedOccupiedSeats() }
        .sum()

/** "קהל משוער היום" – כל ההקרנות של התאריך (כולל מה שכבר הסתיים ומה שעוד לא התחיל). */
fun estimatedAudienceForDate(screenings: List<Screening>, date: LocalDate): Int =
    screenings.asSequence()
        .filter { it.date == date }
        .mapNotNull { it.estimatedOccupiedSeats() }
        .sum()

/** "צופים בהקרנות עתידיות" – הקרנות של היום שעדיין לא התחילו. */
fun estimatedAudienceRemainingToday(screenings: List<Screening>, now: ZonedDateTime): Int =
    screenings.asSequence()
        .filter { it.date == now.toLocalDate() }
        .filter { s ->
            val startAt = s.scheduledStartAt(now)
            startAt != null && startAt.isAfter(now.withZoneSameInstant(ISRAEL_TIME_ZONE))
        }
        .mapNotNull { it.estimatedOccupiedSeats() }
        .sum()

/** "קהל לפי פורמט" – תמיד 5 שורות בסדר קבוע, גם אם 0. */
fun audienceByRoomType(screenings: List<Screening>, date: LocalDate): List<AudienceStat> {
    val formats = listOf("2D", "IMAX", "ScreenX", "VIP", "4DX")
    val totals = screenings.asSequence()
        .filter { it.date == date }
        .mapNotNull { s ->
            val type = s.roomType() ?: return@mapNotNull null
            val people = s.estimatedOccupiedSeats() ?: return@mapNotNull null
            type to people
        }
        .groupBy({ it.first }, { it.second })
    return formats.map { AudienceStat(it, (totals[it] ?: emptyList()).sum()) }
}

/** "קהל לפי סרט" (גרסת AudienceStat) – ממוין מהגבוה לנמוך. */
fun audienceByMovie(screenings: List<Screening>, date: LocalDate): List<AudienceStat> =
    screenings.asSequence()
        .filter { it.date == date }
        .mapNotNull { s ->
            val title = s.title.trim()
            val people = s.estimatedOccupiedSeats() ?: return@mapNotNull null
            if (title.isNotEmpty()) title to people else null
        }
        .groupBy({ it.first }, { it.second })
        .map { (title, list) -> AudienceStat(title, list.sum()) }
        .sortedByDescending { it.people }

/** כרטיס "קהל לפי סרט" במסך הסטטיסטיקה: צופים + מספר הקרנות לכל סרט, ממוין לפי צופים. */
fun dailyMovieStats(screenings: List<Screening>, date: LocalDate): List<MovieAudienceStat> =
    screenings.asSequence()
        .filter { it.date == date }
        .filter { it.title.isNotBlank() }
        .groupBy { it.title.trim() }
        .map { (title, list) ->
            MovieAudienceStat(
                title = title,
                people = list.mapNotNull { it.estimatedOccupiedSeats() }.sum(),
                showings = list.size
            )
        }
        .sortedByDescending { it.people }

// -------------------------------------------- Merging the 3 data sources ---

/**
 * חיבור שלוש מקורות לפי presentationCode.
 * הרשימה הסופית = baseline (first-schedule.json), וה-availabilityRatio של כל הקרנה נקבע בעדיפות:
 *   1. hourly   (schedule.json)    ← הכי טרי
 *   2. history  (history.json)
 *   3. previous (המצב שכבר בזיכרון האפליקציה לפני הרענון)
 *   4. הערך שיש ב-baseline עצמו
 * הקרנה בלי presentationCode מקבלת את הערך מה-baseline בלבד.
 */
fun mergeScreeningAvailability(
    baseline: List<Screening>,
    hourly: List<Screening>?,
    previous: List<Screening>,
    history: List<Screening>? = null
): List<Screening> {
    fun List<Screening>.byCode(): Map<String, Screening> =
        mapNotNull { s -> s.presentationCode?.let { it to s } }.toMap()

    val hourlyByCode = (hourly ?: emptyList()).byCode()
    val historyByCode = (history ?: emptyList()).byCode()
    val previousByCode = previous.byCode()

    return baseline.map { s ->
        val code = s.presentationCode
        val ratio = if (code != null) {
            hourlyByCode[code]?.availabilityRatio
                ?: historyByCode[code]?.availabilityRatio
                ?: previousByCode[code]?.availabilityRatio
        } else null
        s.copy(availabilityRatio = ratio ?: s.availabilityRatio)
    }
}
