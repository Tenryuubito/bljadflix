package com.shadow.firetv.installer.network

import com.shadow.firetv.installer.data.AppVersionInfo
import com.shadow.firetv.installer.data.AuthRequest
import com.shadow.firetv.installer.data.AuthResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface InstallerApi {

    @GET("app/version")
    suspend fun getVersionInfo(): Response<AppVersionInfo>

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @GET("auth/status")
    suspend fun checkAuthStatus(): Response<Unit>

    @Streaming
    @GET
    suspend fun downloadApk(@Url fileUrl: String): Response<ResponseBody>
}
