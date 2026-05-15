package com.mic.scriptpilot.data.remote

import com.mic.scriptpilot.data.remote.model.TrendResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface TrendsApiService {
    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
    )
    /** Must match [functions/index.js] export name `findTrends`. */
    @GET("findTrends")
    suspend fun getTrends(
        // Must match Cloud Function query param names exactly
        @Query("category") category: String?,
        @Query("region") region: String,
        @Query("maxResults") maxResults: Int = 20,
    ): TrendResponse
}