package io.github.dreadnought0.feedclean.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokLinksTest {

    @Test
    fun extractsLinkFromMixedText() {
        val text = "Look at this one! https://vm.tiktok.com/ZMabc123/ so many ads."
        assertEquals(listOf("https://vm.tiktok.com/ZMabc123/"), TikTokLinks.extract(text))
    }

    @Test
    fun extractsEveryAllowedHostInOrderWithoutDuplicates() {
        val text = """
            first https://www.tiktok.com/@example_creator/video/7300000000000000001?is_from_webapp=1,
            then (https://vt.tiktok.com/ZSxyz/) and https://m.tiktok.com/v/123.html.
            again https://vm.tiktok.com/ZMabc/ https://vm.tiktok.com/ZMabc/ and https://tiktok.com/t/ZTdef/
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.tiktok.com/@example_creator/video/7300000000000000001?is_from_webapp=1",
                "https://vt.tiktok.com/ZSxyz/",
                "https://m.tiktok.com/v/123.html",
                "https://vm.tiktok.com/ZMabc/",
                "https://tiktok.com/t/ZTdef/",
            ),
            TikTokLinks.extract(text),
        )
    }

    @Test
    fun ignoresNonTikTokAndLookalikeHosts() {
        val text = "https://example.com/@someone https://tiktok.com.evil.example/x " +
            "https://eviltiktok.com/@a https://sub.vm.tiktok.com/ZM1/ https://user@vm.tiktok.com/ZM2/ ftp://vm.tiktok.com/x"
        assertEquals(emptyList<String>(), TikTokLinks.extract(text))
    }

    @Test
    fun hostMatchIsCaseInsensitiveAndBlankTextIsEmpty() {
        assertEquals(listOf("HTTPS://VM.TIKTOK.COM/ZMabc/"), TikTokLinks.extract("HTTPS://VM.TIKTOK.COM/ZMabc/"))
        assertEquals(emptyList<String>(), TikTokLinks.extract(null))
        assertEquals(emptyList<String>(), TikTokLinks.extract("   "))
        assertEquals(emptyList<String>(), TikTokLinks.extract("no links here"))
    }

    @Test
    fun handleFromUrlParsesAndNormalizes() {
        assertEquals("some.creator_1", TikTokLinks.handleFromUrl("https://www.tiktok.com/@Some.Creator_1/video/123"))
        assertEquals("abc", TikTokLinks.handleFromUrl("/@abc/video/1?lang=en"))
        assertEquals("abc", TikTokLinks.handleFromUrl("https://www.tiktok.com/@abc"))
        assertEquals("abc", TikTokLinks.handleFromUrl("https://www.tiktok.com/@abc?_r=1"))
    }

    @Test
    fun handleFromUrlRejectsMissingOrInvalidHandles() {
        assertNull(TikTokLinks.handleFromUrl("https://vm.tiktok.com/ZMabc/"))
        assertNull(TikTokLinks.handleFromUrl("https://www.tiktok.com/login"))
        assertNull(TikTokLinks.handleFromUrl("https://www.tiktok.com/@a/video/1"))
        assertNull(TikTokLinks.handleFromUrl("https://www.tiktok.com/@" + "x".repeat(25) + "/video/1"))
        assertNull(TikTokLinks.handleFromUrl("https://www.tiktok.com/@bad-handle/video/1"))
        assertNull(TikTokLinks.handleFromUrl(null))
    }

    @Test
    fun resolvableOnlyForHttpsOnAllowedHostsAndDefaultPort() {
        assertTrue(TikTokLinks.isResolvable("https://vm.tiktok.com/ZMabc/"))
        assertFalse(TikTokLinks.isResolvable("http://vm.tiktok.com/ZMabc/"))
        assertFalse(TikTokLinks.isResolvable("https://vm.tiktok.com:8443/ZMabc/"))
        assertFalse(TikTokLinks.isResolvable("https://example.com/ZMabc/"))
        assertFalse(TikTokLinks.isResolvable("https://user@vm.tiktok.com/ZMabc/"))
        assertFalse(TikTokLinks.isResolvable("not a url"))
    }

    @Test
    fun shortLinkDetection() {
        assertTrue(TikTokLinks.isShortLink("https://vm.tiktok.com/ZMabc/"))
        assertTrue(TikTokLinks.isShortLink("https://vt.tiktok.com/ZSabc/"))
        assertTrue(TikTokLinks.isShortLink("https://www.tiktok.com/t/ZTabc/"))
        assertFalse(TikTokLinks.isShortLink("https://www.tiktok.com/@abc/video/1"))
        assertFalse(TikTokLinks.isShortLink("https://example.com/t/abc"))
    }
}
