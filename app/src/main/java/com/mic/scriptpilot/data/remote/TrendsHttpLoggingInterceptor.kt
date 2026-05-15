package com.mic.scriptpilot.data.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Logs Find Trends requests and non-2xx responses (URL, query, status, error body peek).
 */
class TrendsHttpLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val httpUrl = request.url()
        val url = httpUrl.toString()
        val category = httpUrl.queryParameter("category") ?: "(none)"
        val region = httpUrl.queryParameter("region") ?: "(none)"
        val maxResults = httpUrl.queryParameter("maxResults") ?: "(default)"

        Log.d(
            TAG,
            "FindTrends → method=${request.method()} url=$url category=$category region=$region maxResults=$maxResults",
        )

        val response = chain.proceed(request)
        val code = response.code()
        Log.d(TAG, "FindTrends ← http=$code url=$url")

        if (code !in 200..299) {
            val bodySnippet =
                try {
                    response.peekBody(MAX_ERROR_BODY_BYTES).string()
                } catch (e: Exception) {
                    "(could not peek body: ${e.message})"
                }
            Log.e(TAG, "FindTrends error body (peek): $bodySnippet")
        }

        return response
    }

    private companion object {
        const val TAG = "FindTrendsHttp"
        const val MAX_ERROR_BODY_BYTES: Long = 16_384L
    }
}
