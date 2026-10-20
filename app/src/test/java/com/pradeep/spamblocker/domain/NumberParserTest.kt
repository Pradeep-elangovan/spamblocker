package com.pradeep.spamblocker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberParserTest {

    @Test fun differentFormsNormaliseToTheSameE164() {
        val forms = listOf("+918056123456", "08056123456", "8056123456", "+91 80561 23456", "918056123456")
        forms.forEach { assertEquals(it, "+918056123456", NumberParser.parse(it).e164) }
    }

    @Test fun nationalNumberHasNoCountryCodeOrTrunkZero() {
        assertEquals("8056123456", NumberParser.parse("+918056123456").national)
        assertEquals("8056123456", NumberParser.parse("08056123456").national)
    }

    @Test fun hiddenNumber() {
        val p = NumberParser.parse(null)
        assertNull(p.e164)
        assertEquals("Hidden number", p.display)
        assertNull(NumberParser.parse("").e164)
    }

    @Test fun mobileVersusLandline() {
        assertEquals(true, NumberParser.parse("+919824012345").isMobile)
        assertEquals(false, NumberParser.parse("+917926301234").isMobile)
    }

    @Test fun indianLandlineResolvesToAState() {
        // 079 is Ahmedabad.
        assertTrue(NumberParser.parse("+917926301234").regions.contains("Gujarat"))
        // 011 is Delhi.
        assertTrue(NumberParser.parse("+911123456789").regions.contains("Delhi"))
    }
}
