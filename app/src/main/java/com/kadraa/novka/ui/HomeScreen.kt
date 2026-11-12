package com.kadraa.novka.ui

import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import com.kadraa.novka.R
import com.kadraa.novka.capture.NovkaNotificationListener
import com.kadraa.novka.domain.BudgetCalculator
import com.kadraa.novka.domain.Money
import com.kadraa.novka.domain.TxType
import java.time.LocalDate
import java.time.YearMonth

data class BudgetLine(val budgetId: Long, val name: String, val icon: String, val colour: Long, val status: com.kadraa.novka.domain.BudgetStatus)

fun budgetLines(data: AppData, today: LocalDate = LocalDate.now()): List<BudgetLine> {
    val month = YearMonth.from(today)
    val from = month.atDay(1).startMillis()
    val to = month.atEndOfMonth().endMillis()
    val expenses = data.txs.filter { it.type == TxType.EXPENSE && it.timestamp in from..to }
    return data.budgets.mapNotNull { budget ->
        val category = data.categoryById[budget.categoryId] ?: return@mapNotNull null
        val family = data.family(budget.categoryId)
        val spent = -expenses.filter { it.categoryId in family }.sumOf { it.mainAmount }
        BudgetLine(budget.id, category.name, category.icon, category.colour, BudgetCalculator.status(budget.monthlyLimit, spent, today))
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BudgetProgress(line: BudgetLine, main: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${line.icon} ${line.name}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Money.formatNumber(line.status.spent, main)} / ${Money.format(line.status.limit, main)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { line.status.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (line.status.level >= 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun HomeScreen(data: AppData, vm: MainViewModel, nav: NavHostController) {
    val context = LocalContext.current
    var listenerOn by remember { mutableStateOf(NovkaNotificationListener.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { listenerOn = NovkaNotificationListener.isEnabled(context) }

    val main = data.main
    val month = YearMonth.now()
    val monthTxs = remember(data.txs, month) {
        val from = month.atDay(1).startMillis()
        val to = month.atEndOfMonth().endMillis()
        data.txs.filter { it.timestamp in from..to }
    }
    val spent = -monthTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.mainAmount }
    val earned = monthTxs.filter { it.type == TxType.INCOME }.sumOf { it.mainAmount }
    val review = data.txs.filter { it.needsReview }
    val budgets = remember(data.txs, data.budgets, data.categories) { budgetLines(data).sortedByDescending { it.status.fraction }.take(3) }
    val upcoming = data.recurring.filter { it.active }.take(3)
    val ratesAgeDays = if (data.settings.ratesFetchedAt == 0L) null else (System.currentTimeMillis() - data.settings.ratesFetchedAt) / 86_400_000
    val foreign = data.activeAccounts.any { it.account.currency != main }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(t(R.string.total_balance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(data.totalBalance, main), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                if (foreign && (ratesAgeDays == null || ratesAgeDays >= 3)) {
                    Text(
                        if (ratesAgeDays == null) t(R.string.rates_missing) else t(R.string.rates_age, ratesAgeDays.toInt()),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(t(R.string.this_month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(t(R.string.spent), Money.format(spent, main), Modifier.weight(1f))
                    StatTile(t(R.string.earned), Money.format(earned, main), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                StatTile(t(R.string.net), signed(earned - spent, main), Modifier.fillMaxWidth())
            }
        }

        if ((data.settings.walletEnabled || data.settings.raiffeisenEnabled) && !listenerOn) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t(R.string.capture_off_title), fontWeight = FontWeight.SemiBold)
                        Text(t(R.string.capture_off_text), style = MaterialTheme.typography.bodyMedium)
                        Row {
                            TextButton(onClick = { context.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text(t(R.string.open_settings)) }
                            TextButton(onClick = { vm.run { settings.update { it.copy(walletEnabled = false, raiffeisenEnabled = false) } } }) { Text(t(R.string.turn_off)) }
                        }
                    }
                }
            }
        }

        if (review.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t(R.string.needs_review_count, review.size), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.filter = TxFilter(reviewOnly = true); nav.goToTab("transactions") }) { Text(t(R.string.see_all)) }
                        }
                        review.take(3).forEach { tx -> TxRow(tx, data, onClick = { vm.editing = Editing(tx = tx) }) }
                    }
                }
            }
        }

        item {
            SectionTitle(t(R.string.accounts), Modifier.padding(horizontal = 16.dp), t(R.string.manage)) { nav.navigate("accounts") }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.activeAccounts, key = { it.account.id }) { row ->
                    OutlinedCard(Modifier.width(170.dp).clickable { vm.filter = TxFilter(accountId = row.account.id); nav.goToTab("transactions") }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.account.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Money.format(row.balance, row.account.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (budgets.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle(t(R.string.tab_budgets), action = t(R.string.see_all)) { nav.goToTab("budgets") }
                    budgets.forEach { BudgetProgress(it, main) }
                }
            }
        }

        if (upcoming.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle(t(R.string.upcoming), action = t(R.string.manage)) { nav.navigate("recurring") }
                    upcoming.forEach { rule ->
                        val currency = data.accountById[rule.accountId]?.account?.currency ?: main
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(
                                rule.merchant.ifBlank { data.categoryById[rule.categoryId]?.name ?: typeLabel(rule.type) },
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(formatDate(LocalDate.parse(rule.nextDue)), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                            Text(signed(rule.amount, currency), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        item { SectionTitle(t(R.string.recent), Modifier.padding(horizontal = 16.dp), t(R.string.see_all)) { vm.filter = TxFilter(); nav.goToTab("transactions") } }
        val recent = data.txs.filter { it.type != TxType.TRANSFER || it.amount < 0 }.take(8)
        if (recent.isEmpty()) item { EmptyState(t(R.string.no_transactions)) }
        items(recent, key = { it.id }) { tx -> TxRow(tx, data, onClick = { vm.editing = Editing(tx = tx) }) }
    }
}
