package com.mic.scriptpilot.data.remote

/**
 * HTTPS URLs for Firebase Callable-style [onRequest] functions.
 *
 * Pattern: https://REGION-PROJECT_ID.cloudfunctions.net/FUNCTION_NAME
 *
 * Project and region must match [firebase deploy] / `.firebaserc`. Default function
 * region is [DEFAULT_FUNCTIONS_REGION] unless overridden in Firebase console.
 */
object FirebaseFunctionsEndpoints {
    const val PROJECT_ID: String = "scriptpilot-d0e9a"
    const val DEFAULT_FUNCTIONS_REGION: String = "us-central1"

    /** Base URL with trailing slash for Retrofit. */
    val cloudFunctionsBaseUrl: String
        get() = "https://$DEFAULT_FUNCTIONS_REGION-$PROJECT_ID.cloudfunctions.net/"
}
