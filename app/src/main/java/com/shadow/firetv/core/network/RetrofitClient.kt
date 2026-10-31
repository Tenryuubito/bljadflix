package com.shadow.firetv.core.network

import com.shadow.firetv.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val STREAM_RESOLVE_TIMEOUT_SECONDS = 90
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url().encodedPath().endsWith("/resolve-stream")) {
                    chain.withReadTimeout(STREAM_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS).proceed(request)
                } else {
                    chain.proceed(request)
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val instance: MovieApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MovieApi::class.java)
    }
}
