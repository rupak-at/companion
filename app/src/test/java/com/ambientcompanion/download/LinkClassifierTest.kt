package com.ambientcompanion.download

import com.ambientcompanion.download.classifier.LinkClassifier
import com.ambientcompanion.download.classifier.LinkType
import com.ambientcompanion.download.share.SharedUrlExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkClassifierTest {
    @Test fun `recognizes provider subdomains`() {
        assertEquals(LinkType.TIKTOK, LinkClassifier.classify("https://vm.tiktok.com/abc")?.type)
        assertEquals(LinkType.INSTAGRAM, LinkClassifier.classify("https://www.instagram.com/reel/abc")?.type)
    }

    @Test fun `does not accept lookalike domains or insecure URLs`() {
        assertEquals(LinkType.UNKNOWN, LinkClassifier.classify("https://tiktok.com.attacker.example/video")?.type)
        assertNull(LinkClassifier.classify("http://www.tiktok.com/video"))
        assertNull(LinkClassifier.classify("https://user:pass@www.tiktok.com/video"))
    }

    @Test fun `extracts a shared URL without trailing punctuation`() {
        assertEquals("https://vm.tiktok.com/abc", SharedUrlExtractor.extract("Try https://vm.tiktok.com/abc)."))
    }
}
