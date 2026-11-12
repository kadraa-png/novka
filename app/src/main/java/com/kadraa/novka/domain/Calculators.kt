package com.kadraa.novka.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

object CurrencyConverter {
    /**
     * [rates] holds units of each currency per one unit of a common base.
     * Returns how many units of [to] one unit of [from] buys, or null when a rate is missing.
     */
    fun rate(from: String, to: String, rates: Map<String, Double>): Double? {
        if (from == to) return 1.0
        val a = rates[from] ?: return null
        val b = rates[to] ?: return null
        if (a <= 0.0 || b <= 0.0) return null
        return b / a
    }

    /** Converts minor units of [from] into minor units of [to] at [rate], rounding half-even. */
    fun convert(minor: Long, from: String, to: String, rate: Double): Long {
        if (from == to) return minor
        return BigDecimal.valueOf(minor, Money.decimals(from))
            .multiply(BigDecimal.valueOf(rate))
            .movePointRight(Money.decimals(to))
            .setScale(0, RoundingMode.HALF_EVEN)
            .toLong()
    }
}

data class BudgetStatus(
    val limit: Long,
    val spent: Long,
    val remaining: Long,
    val fraction: Float,
    val dailyAllowance: Long,
    /** 0, 80 or 100: the highest alert threshold reached. */
    val level: Int,
)

object BudgetCalculator {
    fun status(limit: Long, spent: Long, today: LocalDate, month: YearMonth = YearMonth.from(today)): BudgetStatus {
        val remaining = limit - spent
        val daysLeft = when {
            YearMonth.from(today) == month -> month.lengthOfMonth() - today.dayOfMonth + 1
            month.isAfter(YearMonth.from(today)) -> month.lengthOfMonth()
            else -> 0
        }
        val fraction = if (limit <= 0) 0f else (spent.toDouble() / limit).toFloat().coerceAtLeast(0f)
        return BudgetStatus(
            limit = limit,
            spent = spent,
            remaining = remaining,
            fraction = fraction,
            dailyAllowance = if (daysLeft > 0 && remaining > 0) remaining / daysLeft else 0,
            level = when {
                limit <= 0 -> 0
                spent >= limit -> 100
                spent * 100 >= limit * 80 -> 80
                else -> 0
            },
        )
    }
}

object RecurrenceCalculator {
    /**
     * The occurrence after [date]. [anchorDay] is the day of month the rule started on, so a rule
     * set for the 31st falls on the last day of shorter months and returns to the 31st afterwards.
     */
    fun next(date: LocalDate, frequency: Frequency, interval: Int, anchorDay: Int): LocalDate {
        val step = interval.coerceAtLeast(1).toLong()
        return when (frequency) {
            Frequency.DAILY -> date.plusDays(step)
            Frequency.WEEKLY -> date.plusWeeks(step)
            Frequency.MONTHLY -> anchored(date.plusMonths(step), anchorDay)
            Frequency.YEARLY -> anchored(date.plusYears(step), anchorDay)
        }
    }

    data class Due(val dates: List<LocalDate>, val nextDue: LocalDate, val finished: Boolean)

    /** Every occurrence from [nextDue] up to and including [today], including runs that were missed. */
    fun due(
        nextDue: LocalDate,
        today: LocalDate,
        frequency: Frequency,
        interval: Int,
        anchorDay: Int,
        end: LocalDate?,
        max: Int = 400,
    ): Due {
        val dates = mutableListOf<LocalDate>()
        var cursor = nextDue
        while (!cursor.isAfter(today) && (end == null || !cursor.isAfter(end)) && dates.size < max) {
            dates += cursor
            cursor = next(cursor, frequency, interval, anchorDay)
        }
        return Due(dates, cursor, finished = end != null && cursor.isAfter(end))
    }

    private fun anchored(date: LocalDate, anchorDay: Int): LocalDate =
        date.withDayOfMonth(minOf(anchorDay.coerceIn(1, 31), date.lengthOfMonth()))
}
