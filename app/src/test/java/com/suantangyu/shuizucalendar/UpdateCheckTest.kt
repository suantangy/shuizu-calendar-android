package com.suantangyu.shuizucalendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun compareVersions_basic() {
        assertEquals(1, compareVersions("2.0.2", "2.0.1"))
        assertEquals(-1, compareVersions("2.0.1", "2.0.2"))
        assertEquals(0, compareVersions("2.0.1", "2.0.1"))
        assertEquals(1, compareVersions("2.1.0", "2.0.9"))
        assertEquals(1, compareVersions("2.0.10", "2.0.9"))
        assertEquals(0, compareVersions("2.0", "2.0.0"))
    }

    @Test
    fun parseReleaseTag_basic() {
        assertEquals("2.0.1", parseReleaseTag("v2.0.1-24"))
        assertEquals("2.0.1", parseReleaseTag("v2.0.1"))
        assertEquals("2.0.10", parseReleaseTag("v2.0.10-3"))
        assertNull(parseReleaseTag(""))
        assertNull(parseReleaseTag("v"))
        assertNull(parseReleaseTag("nightly"))
    }
}
