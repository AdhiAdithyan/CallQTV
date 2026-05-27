package com.softland.callqtv.utils

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Parses [PreferenceHelper.product_license_end] values from the license server.
 * TV and phone builds must use the same rules — the server may return yyyy-MM-dd, dd-MM-yyyy, etc.
 */
object LicenseDateUtils {

    private val JAVA_TIME_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT),
    )

    private val LEGACY_FORMATS = listOf(
        "yyyy-MM-dd",
        "dd-MM-yyyy",
        "yyyy/MM/dd",
        "dd/MM/yyyy",
        "MMM dd, yyyy",
        "dd MMM yyyy",
    )

    fun parseLicenseEndDate(raw: String?): LocalDate? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val datePart = trimmed.split(" ").firstOrNull()?.trim().orEmpty()
        if (datePart.isBlank()) return null

        for (formatter in JAVA_TIME_FORMATS) {
            try {
                return LocalDate.parse(datePart, formatter)
            } catch (_: Exception) {
            }
        }

        for (pattern in LEGACY_FORMATS) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.ROOT).parse(datePart) ?: continue
                return parsed.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun daysUntilExpiry(raw: String?): Int? {
        val end = parseLicenseEndDate(raw) ?: return null
        return ChronoUnit.DAYS.between(LocalDate.now(), end).toInt()
    }

    /** License is valid through the end date (inclusive). */
    fun isLicenseValid(raw: String?): Boolean {
        val days = daysUntilExpiry(raw) ?: return false
        return days >= 0
    }

    /** User-facing end date for settings (matches TV clock format). */
    fun formatLicenseEndDateForDisplay(
        raw: String?,
        pattern: String = "dd-MM-yyyy",
    ): String? {
        val end = parseLicenseEndDate(raw) ?: return null
        return end.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT))
    }
}
