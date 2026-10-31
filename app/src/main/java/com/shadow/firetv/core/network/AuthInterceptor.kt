package com.shadow.firetv.core.network

import com.shadow.firetv.core.auth.AuthManager
import com.shadow.firetv.core.source.SourceManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val authManager: AuthManager = AuthManager.getInstance(),
    private val sourceManager: SourceManager = SourceManager.getInstance()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        val token = authManager.getToken()
        val deviceId = authManager.getDeviceId()
        val source = sourceManager.getSelectedSource()

        if (!token.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }
        if (deviceId.isNotEmpty()) {
            builder.header("X-Device-ID", deviceId)
        }

        builder.header("X-Source", source.id)
        builder.header("X-Source-Key", source.key)
        builder.header("X-Source-Url", source.url)

        val response = chain.proceed(builder.build())

        if (response.code() == 401 || response.code() == 409) {
            authManager.clearSession()
        }

        return response
    }
}
