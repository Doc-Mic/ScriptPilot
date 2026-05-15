package com.mic.scriptpilot.data.remote

import android.util.Log
import com.mic.scriptpilot.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Debug-only logging for ScriptPilot AI endpoints (URL, status, response body peek).
 */
class ScriptPilotHttpLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!BuildConfig.DEBUG) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val url = request.url().toString()
        Log.d(TAG, "→ ${request.method()} $url")

        val response = chain.proceed(request)
        val code = response.code()
        Log.d(TAG, "← http=$code $url")

        val bodySnippet =
            try {
                response.peekBody(MAX_BODY_BYTES).string()
            } catch (e: Exception) {
                "(could not peek body: ${e.message})"
            }
        Log.d(TAG, "raw body (peek, max ${MAX_BODY_BYTES}b): $bodySnippet")

        return response
    }

    private companion object {
        const val TAG = "ScriptPilotHttp"
        const val MAX_BODY_BYTES: Long = 32_768L
    }
}
