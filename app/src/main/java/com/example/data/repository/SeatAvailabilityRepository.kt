package com.example.data.repository

import com.example.data.model.SeatAvailabilitySummary
import com.example.data.remote.PlanetApiHeadersProvider
import com.example.data.remote.PlanetSeatsApi
import com.example.data.seats.SeatStatusMapper
import com.example.data.seats.SeatStatusParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class SeatFetchResult {
    data class Success(
        val summary: SeatAvailabilitySummary,
        val fromCache: Boolean,
        val httpCode: Int = summary.httpStatusCode ?: 200,
        val ttlMs: Long = SeatAvailabilityRepository.DEFAULT_CACHE_TTL_MS
    ) : SeatFetchResult()

    data class AuthRequired(
        val httpCode: Int,
        val message: String
    ) : SeatFetchResult()

    data class EmptyOrInvalid(
        val message: String,
        val httpCode: Int? = null
    ) : SeatFetchResult()

    data class NetworkError(
        val message: String,
        val cause: Throwable? = null,
        val httpCode: Int? = null
    ) : SeatFetchResult()

    data object MissingPresentationId : SeatFetchResult()
}

/**
 * Fetches and caches live seat availability from `tickets5.planetcinema.co.il/api/seats/seats-statusV2`
 * with rate limiting, timeouts, safe header handling, strict cache TTL enforcement, and structured status parsing.
 */
class SeatAvailabilityRepository(
    private val api: PlanetSeatsApi,
    private val parser: SeatStatusParser = SeatStatusParser(SeatStatusMapper()),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val minCooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val interRequestDelayMs: Long = DEFAULT_INTER_REQUEST_DELAY_MS
) {
    constructor(
        headersProvider: PlanetApiHeadersProvider = PlanetApiHeadersProvider.DEFAULT,
        statusMapper: SeatStatusMapper = SeatStatusMapper(),
        okHttpClient: OkHttpClient = buildDefaultClient(headersProvider)
    ) : this(
        api = Retrofit.Builder()
            .baseUrl(PlanetSeatsApi.BASE_URL)
            .client(okHttpClient)
            .build()
            .create(PlanetSeatsApi::class.java),
        parser = SeatStatusParser(statusMapper)
    )

    private data class CacheEntry(
        val summary: SeatAvailabilitySummary,
        val cachedAtEpochMs: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val lastRequestTimestamps = ConcurrentHashMap<String, Long>()
    private val requestSemaphore = Semaphore(permits = 2)
    private val rateLimitMutex = Mutex()
    private var lastGlobalRequestAtMs = 0L

    suspend fun fetchSeatAvailability(
        presentationId: String?,
        venueTypeId: Int = 1,
        isReserved: Int = 1,
        cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
        forceRefresh: Boolean = false
    ): SeatFetchResult = withContext(Dispatchers.IO) {
        val cleanId = presentationId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@withContext SeatFetchResult.MissingPresentationId

        val cacheKey = "${cleanId}_${venueTypeId}_$isReserved"
        val now = nowProvider()

        val cached = cache[cacheKey]
        if (cached != null) {
            val isCacheFresh = (now - cached.cachedAtEpochMs) < cacheTtlMs
            if (!forceRefresh && isCacheFresh) {
                return@withContext SeatFetchResult.Success(
                    summary = cached.summary,
                    fromCache = true,
                    httpCode = cached.summary.httpStatusCode ?: 200,
                    ttlMs = cacheTtlMs
                )
            }

            // Only short-circuit on rate-limit cooldown when cached data is STILL within TTL;
            // expired cache must never be returned as current live occupancy.
            val lastAttempt = lastRequestTimestamps[cacheKey] ?: 0L
            if (isCacheFresh && (now - lastAttempt) < minCooldownMs) {
                return@withContext SeatFetchResult.Success(
                    summary = cached.summary,
                    fromCache = true,
                    httpCode = cached.summary.httpStatusCode ?: 200,
                    ttlMs = cacheTtlMs
                )
            }
        }

        requestSemaphore.withPermit {
            rateLimitMutex.withLock {
                val elapsed = nowProvider() - lastGlobalRequestAtMs
                if (elapsed in 0 until interRequestDelayMs) {
                    delay(interRequestDelayMs - elapsed)
                }
                lastGlobalRequestAtMs = nowProvider()
            }
            lastRequestTimestamps[cacheKey] = nowProvider()

            try {
                val response = api.getSeatsStatus(
                    presentationId = cleanId,
                    venueTypeId = venueTypeId,
                    isReserved = isReserved
                )

                val httpCode = response.code()
                if (httpCode == 401 || httpCode == 403) {
                    cache.remove(cacheKey)
                    return@withContext SeatFetchResult.AuthRequired(
                        httpCode = httpCode,
                        message = "נדרש אימות/סשן מערכת הקופות (HTTP $httpCode)"
                    )
                }

                if (!response.isSuccessful) {
                    cache.remove(cacheKey)
                    return@withContext SeatFetchResult.NetworkError(
                        message = "שגיאת שרת קופות HTTP $httpCode",
                        httpCode = httpCode
                    )
                }

                val rawBody = response.body()?.string().orEmpty()
                if (rawBody.isBlank()) {
                    cache.remove(cacheKey)
                    return@withContext SeatFetchResult.EmptyOrInvalid(
                        message = "תגובה ריקה משרת המושבים",
                        httpCode = httpCode
                    )
                }

                val parsed = parser.parse(
                    rawJson = rawBody,
                    presentationId = cleanId,
                    httpStatusCode = httpCode,
                    fetchedAtEpochMs = nowProvider()
                )

                val summary = parsed.getOrElse { error ->
                    cache.remove(cacheKey)
                    return@withContext SeatFetchResult.EmptyOrInvalid(
                        message = error.message ?: "מבנה JSON לא תקין",
                        httpCode = httpCode
                    )
                }

                if (summary.totalRawSeatObjects == 0) {
                    cache.remove(cacheKey)
                    return@withContext SeatFetchResult.EmptyOrInvalid(
                        message = "לא נמצאו מושבים במפת האולם",
                        httpCode = httpCode
                    )
                }

                cache[cacheKey] = CacheEntry(summary, nowProvider())
                SeatFetchResult.Success(
                    summary = summary,
                    fromCache = false,
                    httpCode = httpCode,
                    ttlMs = cacheTtlMs
                )
            } catch (e: IOException) {
                cache.remove(cacheKey)
                SeatFetchResult.NetworkError(
                    message = e.message ?: "שגיאת תקשורת מול שרת פלאנט",
                    cause = e
                )
            } catch (e: Exception) {
                cache.remove(cacheKey)
                SeatFetchResult.NetworkError(
                    message = e.message ?: "שגיאה בלתי צפויה בעדכון מושבים",
                    cause = e
                )
            }
        }
    }

    fun getCachedSummary(
        presentationId: String,
        venueTypeId: Int = 1,
        isReserved: Int = 1,
        maxAgeMs: Long = DEFAULT_CACHE_TTL_MS
    ): SeatAvailabilitySummary? {
        val entry = cache["${presentationId}_${venueTypeId}_$isReserved"] ?: return null
        return if ((nowProvider() - entry.cachedAtEpochMs) < maxAgeMs) entry.summary else null
    }

    fun clearCache() {
        cache.clear()
        lastRequestTimestamps.clear()
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 4 * 60 * 1000L
        const val UPCOMING_CACHE_TTL_MS = 15 * 60 * 1000L
        const val DEFAULT_COOLDOWN_MS = 60 * 1000L
        const val DEFAULT_INTER_REQUEST_DELAY_MS = 250L

        fun buildDefaultClient(
            headersProvider: PlanetApiHeadersProvider = PlanetApiHeadersProvider.DEFAULT
        ): OkHttpClient {
            // BASIC logging only: never log Authorization/Cookie headers
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                        .header("User-Agent", "PlanetChecker-Android/1.0")
                    headersProvider.extraHeaders().forEach { (k, v) ->
                        if (k.isNotBlank() && v.isNotBlank()) {
                            builder.header(k, v)
                        }
                    }
                    chain.proceed(builder.build())
                }
                .addInterceptor(logging)
                .build()
        }
    }
}
