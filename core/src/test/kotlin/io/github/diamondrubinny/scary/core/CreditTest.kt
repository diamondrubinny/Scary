package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.brand.Credit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreditTest {
    @Test
    fun theCreditReadsExactlyAsAgreed() {
        assertEquals("Powered by SCARY · Open source · github.com/diamondrubinny/Scary", Credit.LINE)
        assertEquals("Powered by SCARY · Open source", Credit.SHORT)
        assertTrue(Credit.LINE.startsWith(Credit.SHORT))
        assertEquals("https://github.com/diamondrubinny/Scary", Credit.URL)
        assertTrue(Credit.LINE.endsWith(Credit.URL.removePrefix("https://")))
    }

    @Test
    fun theNoticeFileCarriesTheCredit() {
        // Tests run from the core module directory; the NOTICE file is at the repository root.
        val notice = File("../NOTICE").readText()
        assertTrue(notice.contains("Powered by SCARY · Open source · ${Credit.URL}"))
        assertTrue(File("../LICENSE").readText().contains("Apache License"))
    }
}
