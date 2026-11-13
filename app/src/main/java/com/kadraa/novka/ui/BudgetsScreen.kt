package com.kadraa.novka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kadraa.novka.R
import com.kadraa.novka.data.Budget
import com.kadraa.novka.domain.CategoryKind
import com.kadraa.novka.domain.Money

@Composable
fun BudgetsScreen(data: AppData, vm: MainViewModel) {
    val main = data.main
    val lines = remember(data.txs, data.budgets, data.categories) { budgetLines(data).sortedByDescending { it.status.fraction } }
    var editing by remember { mutableStateOf<Budget?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
            Text(t(R.string.tab_budgets), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
            Text(
                t(R.string.budgets_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
            if (lines.isEmpty()) {
                EmptyState(t(R.string.no_budgets), Icons.Rounded.PieChart)
            } else {
                SectionCard {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        lines.forEach { line ->
                            Column(Modifier.clickable { editing = data.budgets.firstOrNull { it.id == line.budgetId } }.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                BudgetProgress(line, main)
                                Text(
                                    if (line.status.remaining >= 0) {
                                        t(R.string.budget_left, Money.format(line.status.remaining, main), Money.format(line.status.dailyAllowance, main))
                                    } else {
                                        t(R.string.budget_over, Money.format(-line.status.remaining, main))
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 56.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            FilledTonalButton(
                onClick = { editing = Budget(categoryId = 0, monthlyLimit = 0) }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(t(R.string.add_budget))
            }
        }
    }

    editing?.let { budget -> BudgetDialog(budget, data, vm) { editing = null } }
}

@Composable
private fun BudgetDialog(budget: Budget, data: AppData, vm: MainViewModel, onDismiss: () -> Unit) {
    var categoryId by remember { mutableStateOf(budget.categoryId.takeIf { it != 0L }) }
    var limit by remember { mutableStateOf(if (budget.monthlyLimit > 0) Money.plain(budget.monthlyLimit, data.main) else "") }
    var pickCategory by remember { mutableStateOf(false) }
    val parsed = Money.parseLoose(limit, data.main)
    val category = data.categoryById[categoryId]

    FormDialog(
        title = t(if (budget.id == 0L) R.string.add_budget else R.string.edit_budget),
        onDismiss = onDismiss,
        onSave = { vm.run { dao.putBudget(budget.copy(categoryId = categoryId!!, monthlyLimit = parsed!!)); changed() } },
        saveEnabled = category != null && parsed != null && parsed > 0,
        onDelete = if (budget.id != 0L) ({ vm.run { dao.deleteBudget(budget.id); changed() } }) else null,
    ) {
        CategoryField(category) { pickCategory = true }
        AmountField(limit, { limit = it }, t(R.string.monthly_limit), data.main)
    }

    if (pickCategory) {
        CategoryPickerDialog(data.categories, CategoryKind.EXPENSE, onPick = { categoryId = it?.id }, onDismiss = { pickCategory = false }, allowNone = false)
    }
}
