package com.kadraa.novka.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Csv {
    fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return listOf(';', '\t', ',').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
    }

    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            when {
                quoted && c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    fun write(rows: List<List<String>>): String = rows.joinToString("\r\n", postfix = "\r\n") { row ->
        row.joinToString(",") { cell ->
            // A leading = + - @ would be run as a formula by spreadsheet apps.
            val safe = if (cell.isNotEmpty() && cell[0] in "=+@" ) "'$cell" else cell
            if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
        }
    }
}

data class ImportedRow(
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String?,
    val category: String?,
    val merchant: String,
    val note: String,
)

data class ImportResult(val rows: List<ImportedRow>, val skipped: Int, val missingColumns: List<String>)

/** Reads bank statements and exports from other finance apps by recognising common column names. */
object CsvImporter {
    private val names = mapOf(
        "date" to listOf("date", "datum", "time", "vrijeme", "booking date", "transaction date", "datum transakcije", "datum knjizenja"),
        "amount" to listOf("amount", "iznos", "value", "vrijednost", "sum", "suma"),
        "debit" to listOf("debit", "duguje", "isplata", "withdrawal", "expense", "rashod"),
        "credit" to listOf("credit", "potrazuje", "uplata", "deposit", "income", "prihod"),
        "currency" to listOf("currency", "valuta"),
        "category" to listOf("category", "kategorija"),
        "merchant" to listOf("merchant", "payee", "trgovac", "primalac", "name", "naziv", "counterparty"),
        "note" to listOf("note", "notes", "description", "opis", "memo", "details", "napomena", "svrha"),
        "type" to listOf("type", "tip", "vrsta"),
    )
    private val dateFormats = listOf(
        "yyyy-MM-dd", "dd.MM.yyyy", "d.M.yyyy", "dd.MM.yyyy.", "d.M.yyyy.", "dd/MM/yyyy", "d/M/yyyy", "MM/dd/yyyy", "yyyy/MM/dd", "dd-MM-yyyy", "yyyyMMdd",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ROOT) }

    fun import(text: String, defaultCurrency: String): ImportResult {
        val table = Csv.parse(text)
        if (table.size < 2) return ImportResult(emptyList(), 0, listOf("date", "amount"))
        val header = table.first().map { fold(it).trim() }
        fun column(kind: String): Int = header.indexOfFirst { it in names.getValue(kind) }
            .takeIf { it >= 0 } ?: header.indexOfFirst { h -> names.getValue(kind).any { h.startsWith(it) } }

        val date = column("date")
        val amount = column("amount")
        val debit = column("debit")
        val credit = column("credit")
        val missing = buildList {
            if (date < 0) add("date")
            if (amount < 0 && debit < 0 && credit < 0) add("amount")
        }
        if (missing.isNotEmpty()) return ImportResult(emptyList(), 0, missing)

        val currency = column("currency")
        val category = column("category")
        val merchant = column("merchant")
        val note = column("note")
        val type = column("type")

        var skipped = 0
        val rows = table.drop(1).mapNotNull { cells ->
            fun cell(index: Int) = cells.getOrNull(index)?.trim().orEmpty()
            val code = cell(currency).uppercase(Locale.ROOT).let { if (it == "KM") "BAM" else it }.takeIf(Money::isCurrency)
            val parsedDate = parseDate(cell(date))
            var value = if (amount >= 0) Money.parseLoose(stripCurrency(cell(amount)), code ?: defaultCurrency) else null
            if (value == null) {
                val out = Money.parseLoose(stripCurrency(cell(debit)), code ?: defaultCurrency)
                val incoming = Money.parseLoose(stripCurrency(cell(credit)), code ?: defaultCurrency)
                value = when {
                    out != null && out != 0L -> -kotlin.math.abs(out)
                    incoming != null && incoming != 0L -> kotlin.math.abs(incoming)
                    else -> null
                }
            } else if (value > 0 && fold(cell(type)).let { t -> listOf("expense", "rashod", "debit", "trosak").any(t::contains) }) {
                value = -value
            }
            if (parsedDate == null || value == null || value == 0L) {
                skipped++
                return@mapNotNull null
            }
            ImportedRow(parsedDate, value, code, cell(category).ifEmpty { null }, cell(merchant), cell(note))
        }
        return ImportResult(rows, skipped, emptyList())
    }

    private fun stripCurrency(text: String) = text.replace(Regex("[A-Za-z$€£¥]"), "").trim()

    fun parseDate(text: String): LocalDate? {
        val t = text.trim()
        if (t.isEmpty()) return null
        runCatching { return LocalDateTime.parse(t.replace(' ', 'T')).toLocalDate() }
        val head = t.substringBefore(' ').substringBefore('T')
        for (candidate in listOf(t, head)) {
            for (format in dateFormats) {
                runCatching { return LocalDate.parse(candidate, format) }
            }
        }
        return null
    }
}
