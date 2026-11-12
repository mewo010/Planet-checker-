package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for Yes Planet / Planet Cinema seat availability endpoint.
 * Note: CORS is a browser-only restriction; Android OkHttp performs direct HTTPS calls.
 */
interface PlanetSeatsApi {
    @GET("api/seats/seats-statusV2")
    suspend fun getSeatsStatus(
        @Query("presentationId") presentationId: String,
        @Query("venueTypeId") venueTypeId: Int = 1,
        @Query("isReserved") isReserved: Int = 1
    ): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://tickets5.planetcinema.co.il/"
    }
}

/**
 * Configurable header provider for Planet Cinema public seat API requests.
 *
 * Note: The public `seats-statusV2` endpoint expects a client-generated `uuid` header.
 * Security rule: Never hardcode personal cookies, session IDs, or tokens in source code
 * and never log sensitive authorization headers. Each request generates an ephemeral UUID.
 */
fun interface PlanetApiHeadersProvider {
    fun extraHeaders(): Map<String, String>

    companion object {
        val DEFAULT = PlanetApiHeadersProvider {
            mapOf(
                "Accept" to "application/json",
                "Accept-Language" to "he-IL,he;q=0.9,en;q=0.8",
                "uuid" to java.util.UUID.randomUUID().toString()
            )
        }
    }
}
