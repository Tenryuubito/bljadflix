package com.shadow.firetv.core.network

import com.shadow.firetv.core.auth.AuthManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val authManager: AuthManager = AuthManager.getInstance()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        val token = authManager.getToken()
        val deviceId = authManager.getDeviceId()

        if (!token.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }
        if (deviceId.isNotEmpty()) {
            builder.header("X-Device-ID", deviceId)
        }

        val response = chain.proceed(builder.build())

        if (response.code() == 401 || response.code() == 409) {
            authManager.clearSession()
        }

        return response
    }
}
