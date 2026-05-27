package com.softland.callqtv.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LicenseDateUtilsTest {

    @Test
    fun parseLicenseEndDate_yyyyMmDd() {
        assertEquals(LocalDate.of(2026, 12, 31), LicenseDateUtils.parseLicenseEndDate("2026-12-31"))
    }

    @Test
    fun parseLicenseEndDate_ddMmYyyy() {
        assertEquals(LocalDate.of(2026, 5, 20), LicenseDateUtils.parseLicenseEndDate("20-05-2026"))
    }

    @Test
    fun parseLicenseEndDate_withTimeSuffix() {
        assertEquals(LocalDate.of(2026, 1, 15), LicenseDateUtils.parseLicenseEndDate("2026-01-15 23:59:59"))
    }

    @Test
    fun isLicenseValid_blank() {
        assertFalse(LicenseDateUtils.isLicenseValid(""))
        assertFalse(LicenseDateUtils.isLicenseValid(null))
    }

    @Test
    fun isLicenseValid_futureDate() {
        val future = LocalDate.now().plusDays(30).toString()
        assertTrue(LicenseDateUtils.isLicenseValid(future))
    }

    @Test
    fun formatLicenseEndDateForDisplay_ddMmYyyy() {
        assertEquals(
            "20-05-2026",
            LicenseDateUtils.formatLicenseEndDateForDisplay("2026-05-20"),
        )
    }
}
