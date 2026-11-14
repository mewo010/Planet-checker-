package com.example.planetchecker

// ============================================================================
// ScheduleRepository.kt  --  שחזור טעינת הנתונים (fetchScheduleFromGist + loadData)
// ============================================================================

import android.util.Log
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// שלוש כתובות הנתונים (GitHub Gist) – בדיוק כפי שהן באפליקציה
const val FIRST_SCHEDULE_URL =
    "https://gist.githubusercontent.com/Gaash14/a12b463cc829c43a7ab0bf8385f1be63/raw/first-schedule.json"
const val HOURLY_SCHEDULE_URL =
    "https://gist.githubusercontent.com/Gaash14/da630d78754949bce95065eadb9fd370/raw/schedule.json"
const val AVAILABILITY_HISTORY_URL =
    "https://gist.githubusercontent.com/Gaash14/da630d78754949bce95065eadb9fd370/raw/history.json"

/**
 * מבנה ה-JSON הצפוי בכל אחד משלושת הקבצים:
 * {
 *   "date": "2026-11-14",                      // ISO; אם לא תקין -> date=null בכל ההקרנות
 *   "screenings": [
 *     {
 *       "room": "5",                           // ברירת מחדל "N/A"
 *       "start": "20:15",                      // ברירת מחדל ""
 *       "end": "22:40",                        // ברירת מחדל ""
 *       "title": "Movie name",                 // ברירת מחדל "Unknown Title"
 *       "availabilityRatio": 0.37,             // אופציונלי; חייב להיות 0..1 אחרת מתעלמים
 *       "presentationCode": "ABC123"           // אופציונלי; ריק = null
 *     }
 *   ]
 * }
 */
suspend fun fetchScheduleFromGist(urlStr: String): List<Screening>? = withContext(Dispatchers.IO) {
    try {
        val json = JSONObject(String(URL(urlStr).readBytes(), Charsets.UTF_8))
        val date: LocalDate? = runCatching { LocalDate.parse(json.optString("date")) }.getOrNull()
        val array = json.getJSONArray("screenings")
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val ratio = o.optDouble("availabilityRatio", Double.NaN)
                    .takeIf { it.isFinite() && it in 0.0..1.0 }
                val code = o.optString("presentationCode").takeIf { it.isNotBlank() }
                add(
                    Screening(
                        room = o.optString("room", "N/A"),
                        start = o.optString("start", ""),
                        end = o.optString("end", ""),
                        title = o.optString("title", "Unknown Title"),
                        date = date,
                        availabilityRatio = ratio,
                        presentationCode = code
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e("CinemaApp", "Error fetching or parsing Gist schedule", e)
        null
    }
}

/**
 * זרימת loadData המקורית:
 *  - cacheBuster = System.currentTimeMillis() מצורף לכל כתובת כ-"?nocache=<ms>"
 *  - אם baseline נכשל (null) -> לא נוגעים ברשימה הקיימת
 *  - hourly ו-history יכולים להיכשל (null) – ממשיכים בלעדיהם
 *  - previous = הרשימה שכבר מוצגת באפליקציה
 */
suspend fun loadScreenings(current: List<Screening>): List<Screening>? {
    val cacheBuster = System.currentTimeMillis()
    val baseline = fetchScheduleFromGist("$FIRST_SCHEDULE_URL?nocache=$cacheBuster") ?: return null
    val hourly = fetchScheduleFromGist("$HOURLY_SCHEDULE_URL?nocache=$cacheBuster")
    val history = fetchScheduleFromGist("$AVAILABILITY_HISTORY_URL?nocache=$cacheBuster")
    return mergeScreeningAvailability(baseline, hourly, current, history)
}
