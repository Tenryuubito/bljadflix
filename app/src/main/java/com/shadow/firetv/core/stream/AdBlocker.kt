package com.shadow.firetv.core.stream

import java.util.Locale

object AdBlocker {

    private val AD_DOMAINS = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "imasdk.googleapis.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "adsrvr.org",
        "taboola.com",
        "outbrain.com",
        "mgid.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "adsterra.com",
        "adsterratools.com",
        "exoclick.com",
        "exosrv.com",
        "juicyads.com",
        "trafficjunky.net",
        "trafficstars.com",
        "hilltopads.net",
        "clickadu.com",
        "adcash.com",
        "a-ads.com",
        "adskeeper.com",
        "onclickads.net",
        "onclkds.com",
        "richpush.co",
        "pushnami.com",
        "pushwhy.com",
        "realsrv.com",
        "magsrv.com",
        "tsyndicate.com",
        "dtscout.com",
        "mc.yandex.ru",
        "histats.com",
        "hotjar.com",
        "scorecardresearch.com",
        "highperformanceformat.com",
        "profitablecpmrate.com",
        "profitablegatecpm.com",
        "acscdn.com",
        "rtmark.net"
    )

    private val AD_HOST_KEYWORDS = listOf(
        "adserver",
        "adsystem",
        "popunder",
        "popads",
        "clickunder",
        "doubleclick",
        "adtrack",
        "cpmrate",
        "cpmgate"
    )

    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.lowercase(Locale.ROOT).trimEnd('.')
        if (AD_DOMAINS.any { normalized == it || normalized.endsWith(".$it") }) return true
        return AD_HOST_KEYWORDS.any { normalized.contains(it) }
    }

    fun shouldBlockNavigation(currentHost: String?, targetHost: String?, isRedirect: Boolean, videoStarted: Boolean): Boolean {
        if (isAdHost(targetHost)) return true
        if (isRedirect || !videoStarted) return false
        return !sameSite(currentHost, targetHost)
    }

    private fun sameSite(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return registrableDomain(a) == registrableDomain(b)
    }

    private fun registrableDomain(host: String): String {
        val parts = host.lowercase(Locale.ROOT).trimEnd('.').split('.')
        return if (parts.size <= 2) parts.joinToString(".") else parts.takeLast(2).joinToString(".")
    }
}
