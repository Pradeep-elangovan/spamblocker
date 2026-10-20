package com.pradeep.spamblocker.domain

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

data class ParsedNumber(
    /** E.164 (+9198...), or null when the number is hidden / cannot be parsed. */
    val e164: String?,
    /** National significant number: no country code, no trunk 0. */
    val national: String?,
    /** Human friendly form for the UI. */
    val display: String,
    val regions: Set<String>,
    /** true = mobile, false = landline, null = unknown. */
    val isMobile: Boolean?,
)

/** Normalises whatever the dialer hands us (+91, 0, bare 10 digits...) into one canonical form. */
object NumberParser {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val geocoder: PhoneNumberOfflineGeocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }

    fun parse(raw: String?): ParsedNumber {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ParsedNumber(null, null, "Hidden number", emptySet(), null)
        return try {
            val n = util.parse(text, "IN")
            val e164 = util.format(n, PhoneNumberFormat.E164)
            val display = util.format(n, PhoneNumberFormat.INTERNATIONAL)
            val mobile = when (util.getNumberType(n)) {
                PhoneNumberType.MOBILE -> true
                PhoneNumberType.FIXED_LINE -> false
                else -> null
            }
            val regions = if (n.countryCode == 91) {
                runCatching {
                    IndiaRegions.toStates(geocoder.getDescriptionForNumber(n, Locale.ENGLISH))
                }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            ParsedNumber(e164, n.nationalNumber.toString(), display, regions, mobile)
        } catch (e: NumberParseException) {
            val digits = text.filter { it.isDigit() }
            ParsedNumber(null, digits.ifEmpty { null }, text, emptySet(), null)
        }
    }

    /** Loads libphonenumber + geocoder data so the first real call isn't slow. */
    fun warmUp() {
        runCatching { parse("+919824012345") }
    }
}
