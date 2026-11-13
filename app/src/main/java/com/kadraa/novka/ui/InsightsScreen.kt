package com.kadraa.novka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kadraa.novka.R
import com.kadraa.novka.domain.Money
import com.kadraa.novka.domain.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Period { WEEK, MONTH, YEAR }

private fun periodRange(period: Period, today: LocalDate, offset: Int): Pair<LocalDate, LocalDate> = when (period) {
    Period.WEEK -> today.with(DayOfWeek.MONDAY).plusWeeks(offset.toLong()).let { it to it.plusDays(6) }
    Period.MONTH -> YearMonth.from(today).plusMonths(offset.toLong()).let { it.atDay(1) to it.atEndOfMonth() }
    Period.YEAR -> LocalDate.of(today.year + offset, 1, 1).let { it to it.withDayOfYear(it.lengthOfYear()) }
}

private data class Share(val categoryId: Long?, val name: String, val amount: Long, val colour: Color)

private class InsightsView(
    val buckets: List<Bucket>,
    val balanceBuckets: List<Bucket>,
    val spent: Long,
    val earned: Long,
    val previousSpent: Long,
    val previousEarned: Long,
    val shares: List<Share>,
)

private fun buildView(
    data: AppData, period: Period, start: LocalDate, end: LocalDate, previous: Pair<LocalDate, LocalDate>, today: LocalDate,
    uncategorised: String, otherLabel: String, muted: Color, otherColour: Color,
): InsightsView {
    val flows = data.txs.filter { it.type != TxType.TRANSFER }
    val inPeriod = flows.filter { it.timestamp in start.startMillis()..end.endMillis() }
    val locale = Locale.getDefault()

    val spans: List<Triple<String, LocalDate, LocalDate>> = when (period) {
        Period.YEAR -> (0L..11L).map { start.plusMonths(it) }.map { Triple(it.month.getDisplayName(TextStyle.SHORT, locale), it, YearMonth.from(it).atEndOfMonth()) }
        Period.MONTH -> generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { Triple(it.dayOfMonth.toString(), it, it) }.toList()
        Period.WEEK -> (0L..6L).map { start.plusDays(it) }.map { Triple(it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), it, it) }
    }
    val buckets = spans.map { (label, from, to) ->
        val range = from.startMillis()..to.endMillis()
        val rows = inPeriod.filter { it.timestamp in range }
        // Balance at the end of the bucket: today's total with everything that happened later taken back out.
        val later = flows.filter { it.timestamp > range.last }.sumOf { it.mainAmount }
        Bucket(label, rows.filter { it.type == TxType.INCOME }.sumOf { it.mainAmount }, -rows.filter { it.type == TxType.EXPENSE }.sumOf { it.mainAmount }, data.totalBalance - later)
    }

    val expenses = inPeriod.filter { it.type == TxType.EXPENSE }
    val ranked = expenses.groupBy { data.topLevel(it.categoryId) }
        .map { (category, rows) -> Share(category?.id, category?.name ?: uncategorised, -rows.sumOf { it.mainAmount }, category?.let { Color(it.colour) } ?: muted) }
        .filter { it.amount > 0 }.sortedByDescending { it.amount }
    val shares = if (ranked.size <= 7) ranked else ranked.take(6) + Share(null, otherLabel, ranked.drop(6).sumOf { it.amount }, otherColour)

    val before = flows.filter { it.timestamp in previous.first.startMillis()..previous.second.endMillis() }
    return InsightsView(
        buckets = buckets,
        balanceBuckets = buckets.filterIndexed { index, _ -> !spans[index].second.isAfter(today) },
        spent = -expenses.sumOf { it.mainAmount },
        earned = inPeriod.filter { it.type == TxType.INCOME }.sumOf { it.mainAmount },
        previousSpent = -before.filter { it.type == TxType.EXPENSE }.sumOf { it.mainAmount },
        previousEarned = before.filter { it.type == TxType.INCOME }.sumOf { it.mainAmount },
        shares = shares,
    )
}

@Composable
fun InsightsScreen(data: AppData, vm: MainViewModel, nav: NavHostController) {
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val (start, end) = periodRange(period, today, offset)
    val main = data.main
    val colors = chartColors()
    val uncategorised = t(R.string.uncategorised)
    val otherLabel = t(R.string.other)
    val view = remember(data.txs, data.categories, data.totalBalance, period, offset, today) {
        buildView(data, period, start, end, periodRange(period, today, offset - 1), today, uncategorised, otherLabel, colors.muted, colors.other)
    }
    val title = when (period) {
        Period.WEEK -> "${formatDate(start)} – ${formatDate(end)}"
        Period.MONTH -> start.format(DateTimeFormatter.ofPattern("LLLL yyyy"))
        Period.YEAR -> start.year.toString()
    }
    val format: (Long) -> String = { Money.format(it, main) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Segmented(
                listOf(Period.WEEK to t(R.string.period_week), Period.MONTH to t(R.string.period_month), Period.YEAR to t(R.string.period_year)),
                period, { period = it; offset = 0 }, Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) { Icon(Icons.Rounded.ChevronLeft, t(R.string.previous)) }
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { offset++ }, enabled = offset < 0) { Icon(Icons.Rounded.ChevronRight, t(R.string.next)) }
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(t(R.string.spent), format(view.spent), Modifier.weight(1f), Icons.Rounded.NorthEast)
                StatTile(t(R.string.earned), format(view.earned), Modifier.weight(1f), Icons.Rounded.SouthWest)
            }
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(t(R.string.net), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(signed(view.earned - view.spent, main), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        t(R.string.previous_period, format(view.previousSpent), format(view.previousEarned)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (view.spent == 0L && view.earned == 0L) {
                EmptyState(t(R.string.nothing_in_period), Icons.Rounded.Insights)
            } else {
                ChartCard(t(R.string.earned_and_spent)) { EarnedSpentChart(view.buckets, t(R.string.earned), t(R.string.spent), format) }

                if (view.shares.isNotEmpty()) {
                    ChartCard(t(R.string.spending_by_category)) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Donut(view.shares.map { Slice(it.name, it.amount, it.colour) }, format(view.spent), t(R.string.spent))
                        }
                        view.shares.forEach { share ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = share.categoryId != null) {
                                        vm.filter = TxFilter(type = TxType.EXPENSE, categoryId = share.categoryId, from = start, to = end)
                                        nav.goToTab("transactions")
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).background(share.colour, CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Text(share.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(
                                    "${share.amount * 100 / view.spent.coerceAtLeast(1)}%", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                Text(format(share.amount), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                if (view.balanceBuckets.size > 1) {
                    ChartCard(t(R.string.total_balance)) { BalanceChart(view.balanceBuckets, format) }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
