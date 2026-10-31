package com.shadow.firetv

import com.shadow.firetv.core.stream.AdBlocker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    @Test
    fun `known ad domains and subdomains are blocked`() {
        assertTrue(AdBlocker.isAdHost("securepubads.g.doubleclick.net"))
        assertTrue(AdBlocker.isAdHost("pagead2.googlesyndication.com"))
        assertTrue(AdBlocker.isAdHost("a.magsrv.com"))
        assertTrue(AdBlocker.isAdHost("cdn.popunder-network.xyz"))
    }

    @Test
    fun `hoster and source domains are not blocked`() {
        assertFalse(AdBlocker.isAdHost("voe.sx"))
        assertFalse(AdBlocker.isAdHost("vidmoly.to"))
        assertFalse(AdBlocker.isAdHost("aniworld.to"))
        assertFalse(AdBlocker.isAdHost("186.2.175.5"))
        assertFalse(AdBlocker.isAdHost(null))
    }

    @Test
    fun `cross site navigation is only blocked once the video runs`() {
        assertFalse(AdBlocker.shouldBlockNavigation("voe.sx", "jilliandescribecompany.com", isRedirect = false, videoStarted = false))
        assertTrue(AdBlocker.shouldBlockNavigation("jilliandescribecompany.com", "casino.example", isRedirect = false, videoStarted = true))
        assertFalse(AdBlocker.shouldBlockNavigation("voe.sx", "cdn.voe.sx", isRedirect = false, videoStarted = true))
        assertFalse(AdBlocker.shouldBlockNavigation("voe.sx", "other.example", isRedirect = true, videoStarted = true))
    }

    @Test
    fun `ad hosts are blocked even before the video runs`() {
        assertTrue(AdBlocker.shouldBlockNavigation("voe.sx", "www.popads.net", isRedirect = true, videoStarted = false))
    }
}
