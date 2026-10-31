package com.shadow.firetv.installer.network

import com.shadow.firetv.installer.data.AppVersionInfo
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface InstallerApi {

    @GET("app/version")
    suspend fun getVersionInfo(): Response<AppVersionInfo>

    @Streaming
    @GET
    suspend fun downloadApk(@Url fileUrl: String): Response<ResponseBody>
}
