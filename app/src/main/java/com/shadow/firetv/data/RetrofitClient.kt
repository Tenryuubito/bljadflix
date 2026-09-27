package com.shadow.firetv.data

object RetrofitClient {
    val instance: MovieApi
        get() = com.shadow.firetv.core.network.RetrofitClient.instance
}
