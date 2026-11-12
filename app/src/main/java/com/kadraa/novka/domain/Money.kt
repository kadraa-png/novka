package com.kadraa.novka.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Money is held as whole minor units in a Long. Nothing here uses floating point for amounts. */
object Money {
    fun isCurrency(code: String): Boolean = runCatching { Currency.getInstance(code) }.isSuccess

    fun decimals(code: String): Int {
        val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2)
        return if (digits < 0) 2 else digits
    }

    fun symbol(code: String): String = if (code == "BAM") "KM" else code

    fun toMinor(value: BigDecimal, code: String): Long =
        value.movePointRight(decimals(code)).setScale(0, RoundingMode.HALF_EVEN).longValueExact()

    fun toMajor(minor: Long, code: String): BigDecimal = BigDecimal.valueOf(minor, decimals(code))

    fun format(minor: Long, code: String, locale: Locale = Locale.getDefault()): String =
        formatNumber(minor, code, locale) + " " + symbol(code)

    fun formatNumber(minor: Long, code: String, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.minimumFractionDigits = decimals(code)
        format.maximumFractionDigits = decimals(code)
        return format.format(toMajor(minor, code))
    }

    /** Plain form for edit fields, e.g. 1234.50. */
    fun plain(minor: Long, code: String): String = toMajor(minor, code).toPlainString()

    /**
     * Reads a number written in either the local style (1.234,56) or the English style
     * (1,234.56). Returns null when the text is not a number.
     */
    fun parseLoose(text: String, code: String): Long? {
        var s = text.trim().replace(Regex("[\\s ']"), "")
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("-") || s.startsWith("−")) {
            negative = true
            s = s.drop(1)
        } else if (s.startsWith("+")) {
            s = s.drop(1)
        }
        if (s.isEmpty() || !s.all { it.isDigit() || it == '.' || it == ',' }) return null
        if (!s.first().isDigit()) s = "0$s"

        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decimalIndex = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastDot < 0 && lastComma < 0 -> -1
            else -> {
                val index = maxOf(lastDot, lastComma)
                val separator = s[index]
                val trailing = s.length - index - 1
                val repeated = s.count { it == separator } > 1
                // "1.234" is one thousand two hundred, unless the currency has three decimals.
                if (repeated || (trailing == 3 && decimals(code) != 3)) -1 else index
            }
        }
        val whole = (if (decimalIndex < 0) s else s.substring(0, decimalIndex)).filter { it.isDigit() }
        val fraction = if (decimalIndex < 0) "" else s.substring(decimalIndex + 1)
        if (whole.isEmpty() || !fraction.all { it.isDigit() }) return null
        val value = runCatching { BigDecimal(if (fraction.isEmpty()) whole else "$whole.$fraction") }.getOrNull()
            ?: return null
        val minor = runCatching { toMinor(value, code) }.getOrNull() ?: return null
        return if (negative) -minor else minor
    }
}

/** Lower-cases and strips accents so that "Ćevabdžinica" and "cevabdzinica" compare equal. */
fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('đ', 'd').replace('Đ', 'D')
        .lowercase(Locale.ROOT)

/** Finds amounts that have a currency next to them, such as "93.00 EUR", "KM 12,50" or "$5". */
object AmountFinder {
    data class Found(val minor: Long, val currency: String, val start: Int)

    private val symbols = mapOf("KM" to "BAM", "$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY")
    private const val CUR = """(KM|km|[A-Z]{3}|[$€£¥])"""
    private const val NUM = """(\d{1,3}(?:[.,\s ]\d{3})+(?:[.,]\d{1,3})?|\d+(?:[.,]\d{1,3})?)"""
    private val regex = Regex("""(?<![A-Za-z\d])$CUR?\s?$NUM\s?$CUR?(?![A-Za-z\d])""")

    fun findAll(text: String): List<Found> = regex.findAll(text).mapNotNull { match ->
        val before = currencyOf(match.groupValues[1])
        val after = currencyOf(match.groupValues[3])
        val currency = after ?: before ?: return@mapNotNull null
        val minor = Money.parseLoose(match.groupValues[2], currency) ?: return@mapNotNull null
        if (minor <= 0) null else Found(minor, currency, match.range.first)
    }.toList()

    private fun currencyOf(token: String): String? {
        if (token.isEmpty()) return null
        symbols[token.uppercase(Locale.ROOT)]?.let { return it }
        return token.takeIf { it.length == 3 && Money.isCurrency(it) }
    }
}
