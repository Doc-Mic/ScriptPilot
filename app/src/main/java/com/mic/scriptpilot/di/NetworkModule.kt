package com.mic.scriptpilot.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mic.scriptpilot.data.remote.FirebaseFunctionsEndpoints
import com.mic.scriptpilot.data.remote.ScriptPilotApi
import com.mic.scriptpilot.data.remote.ScriptPilotHttpLoggingInterceptor
import com.mic.scriptpilot.data.remote.TrendsApiService
import com.mic.scriptpilot.data.remote.TrendsHttpLoggingInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    private fun noCacheInterceptor(): Interceptor =
        Interceptor { chain ->
            val request =
                chain.request().newBuilder()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()
            chain.proceed(request)
        }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(noCacheInterceptor())
            .build()

    @Provides
    @Singleton
    @Named("scriptPilotOkHttp")
    fun provideScriptPilotOkHttp(): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(noCacheInterceptor())
                .addInterceptor(ScriptPilotHttpLoggingInterceptor())
        return builder.build()
    }

    /** OkHttp client used only for Find Trends — includes request/response logging. */
    @Provides
    @Singleton
    @Named("trendsOkHttp")
    fun provideTrendsOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(noCacheInterceptor())
            .addInterceptor(TrendsHttpLoggingInterceptor())
            .build()

    @Provides
    @Singleton
    @Named("scriptPilotRetrofit")
    fun provideScriptPilotRetrofit(
        @Named("scriptPilotOkHttp") client: OkHttpClient,
        gson: Gson,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(FirebaseFunctionsEndpoints.cloudFunctionsBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @Named("trendsRetrofit")
    fun provideTrendsRetrofit(
        @Named("trendsOkHttp") client: OkHttpClient,
        gson: Gson,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(FirebaseFunctionsEndpoints.cloudFunctionsBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideScriptPilotApi(@Named("scriptPilotRetrofit") retrofit: Retrofit): ScriptPilotApi =
        retrofit.create(ScriptPilotApi::class.java)

    @Provides
    @Singleton
    fun provideTrendsApiService(@Named("trendsRetrofit") retrofit: Retrofit): TrendsApiService =
        retrofit.create(TrendsApiService::class.java)
}
