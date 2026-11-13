@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kadraa.novka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kadraa.novka.R
import com.kadraa.novka.data.Account
import com.kadraa.novka.data.Category
import com.kadraa.novka.data.Debt
import com.kadraa.novka.data.Goal
import com.kadraa.novka.data.RecurringRule
import com.kadraa.novka.domain.AccountType
import com.kadraa.novka.domain.CategoryKind
import com.kadraa.novka.domain.DebtDirection
import com.kadraa.novka.domain.DefaultCategories
import com.kadraa.novka.domain.Frequency
import com.kadraa.novka.domain.Money
import com.kadraa.novka.domain.TxType
import java.time.LocalDate
import java.util.Currency
import kotlin.math.abs

private val transparentItem @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun TrailingAmount(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

// ---- Accounts ----

@Composable
fun AccountTypeChips(selected: AccountType, onSelect: (AccountType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccountType.entries.forEach { option ->
            FilterChip(
                selected = selected == option, onClick = { onSelect(option) }, label = { Text(accountTypeLabel(option)) },
                leadingIcon = { Icon(accountIcon(option), null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
fun AccountsScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var deleting by remember { mutableStateOf<Account?>(null) }
    val nextColour = DefaultCategories.colours[data.accounts.size % DefaultCategories.colours.size]

    SubScreen(
        t(R.string.accounts), onBack,
        fab = { AddFab(t(R.string.add_account)) { editing = Account(name = "", type = AccountType.BANK, currency = data.main, colour = nextColour) } },
    ) { padding ->
        ScreenColumn(padding) {
            if (data.accounts.isEmpty()) EmptyState(t(R.string.no_accounts), Icons.Rounded.AccountBalanceWallet)
            else SectionCard {
                Column {
                    data.accounts.forEach { row ->
                        val account = row.account
                        ListItem(
                            headlineContent = { Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(listOfNotNull(accountTypeLabel(account.type), if (account.archived) t(R.string.archived) else null).joinToString(" · "))
                            },
                            leadingContent = { IconBadge(accountIcon(account.type), container = tinted(account.colour), tint = MaterialTheme.colorScheme.onSurface) },
                            trailingContent = { TrailingAmount(Money.format(row.balance, account.currency)) },
                            colors = transparentItem,
                            modifier = Modifier.alpha(if (account.archived) 0.6f else 1f).clickable { editing = account },
                        )
                    }
                }
            }
        }
    }

    editing?.let { account ->
        val canDelete = account.id != 0L && (account.archived || data.activeAccounts.size > 1)
        AccountDialog(account, vm, onDelete = if (canDelete) ({ deleting = account }) else null) { editing = null }
    }
    deleting?.let { account ->
        ConfirmDialog(
            t(R.string.delete_account_title, account.name), t(R.string.delete_account_text), t(R.string.delete),
            onConfirm = { vm.run { removeAccount(account.id, null) } }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun AccountDialog(account: Account, vm: MainViewModel, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val new = account.id == 0L
    var name by remember { mutableStateOf(account.name) }
    var type by remember { mutableStateOf(account.type) }
    var currency by remember { mutableStateOf(account.currency) }
    var balance by remember { mutableStateOf(if (new) "" else Money.plain(account.openingBalance, account.currency)) }
    var colour by remember { mutableStateOf(account.colour) }
    var archived by remember { mutableStateOf(account.archived) }
    var pickCurrency by remember { mutableStateOf(false) }
    val parsed = if (balance.isBlank()) 0L else Money.parseLoose(balance, currency)

    FormDialog(
        title = t(if (new) R.string.add_account else R.string.edit_account), onDismiss = onDismiss,
        onSave = {
            vm.run { saveAccount(account.copy(name = name.trim(), type = type, currency = currency, openingBalance = parsed!!, colour = colour, archived = archived)) }
        },
        saveEnabled = name.isNotBlank() && parsed != null, onDelete = onDelete,
    ) {
        OutlinedTextField(name, { name = it }, label = { Text(t(R.string.account_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AccountTypeChips(type) { type = it }
        // The currency of an account with history cannot change: its transactions are stored in it.
        PickerField(t(R.string.currency), "$currency · ${Currency.getInstance(currency).displayName}", { pickCurrency = true }, enabled = new)
        AmountField(balance, { balance = it }, t(R.string.starting_balance), currency)
        ColourPicker(colour) { colour = it }
        if (account.archived) SwitchRow(t(R.string.archived), archived, { archived = it })
    }
    if (pickCurrency) CurrencyPickerDialog(onPick = { currency = it }, onDismiss = { pickCurrency = false })
}

// ---- Categories ----

@Composable
fun CategoriesScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val usable = remember(data.categories, kind) { data.categories.filter { it.kind == kind && !it.archived } }
    val parents = remember(usable) { usable.filter { it.parentId == null || usable.none { p -> p.id == it.parentId } } }
    val children = remember(usable) { usable.filter { it.parentId != null }.groupBy { it.parentId } }

    SubScreen(
        t(R.string.categories), onBack,
        fab = { AddFab(t(R.string.add_category)) { editing = Category(name = "", icon = "category", colour = DefaultCategories.colours[0], kind = kind) } },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 840.dp),
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            ) {
                item {
                    Segmented(
                        listOf(CategoryKind.EXPENSE to t(R.string.expense), CategoryKind.INCOME to t(R.string.income)), kind, { kind = it },
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
                parents.forEach { parent ->
                    item(key = parent.id) { CategoryRow(parent, indent = false) { editing = parent } }
                    items(children[parent.id].orEmpty(), key = { it.id }) { child -> CategoryRow(child, indent = true) { editing = child } }
                }
            }
        }
    }

    editing?.let { category ->
        CategoryDialog(
            category, vm, parents = parents.filter { it.id != category.id && it.parentId == null }, hasChildren = children[category.id].orEmpty().isNotEmpty(),
            onDelete = if (category.id != 0L) ({ deleting = category }) else null,
        ) { editing = null }
    }
    deleting?.let { category ->
        ConfirmDialog(
            t(R.string.delete_category_title, category.name), t(R.string.delete_category_text), t(R.string.delete),
            onConfirm = { vm.run { removeCategory(category.id, moveTo = null, archiveInstead = false) } }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, indent: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(category.name, style = if (indent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium) },
        leadingContent = { CategoryBadge(category.icon, category.colour, if (indent) 32.dp else 40.dp) },
        modifier = Modifier.clickable(onClick = onClick).padding(start = if (indent) 24.dp else 0.dp),
    )
}

@Composable
private fun CategoryDialog(category: Category, vm: MainViewModel, parents: List<Category>, hasChildren: Boolean, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(category.name) }
    var icon by remember { mutableStateOf(category.icon) }
    var colour by remember { mutableStateOf(category.colour) }
    var parentId by remember { mutableStateOf(category.parentId) }
    val none = t(R.string.no_parent)

    FormDialog(
        title = t(if (category.id == 0L) R.string.add_category else R.string.edit_category), onDismiss = onDismiss,
        onSave = { vm.run { saveCategory(category.copy(name = name.trim(), icon = icon, colour = colour, parentId = parentId)) } },
        saveEnabled = name.isNotBlank(), onDelete = onDelete,
    ) {
        OutlinedTextField(name, { name = it }, label = { Text(t(R.string.category_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        // A group cannot itself be put inside another group.
        if (!hasChildren) {
            Dropdown<Category?>(
                t(R.string.parent_category), listOf<Category?>(null) + parents, parents.firstOrNull { it.id == parentId },
                text = { it?.name ?: none }, onSelect = { parentId = it?.id },
            )
        }
        LazyVerticalGrid(GridCells.Adaptive(48.dp), Modifier.fillMaxWidth().height(192.dp)) {
            items(categoryIcons.entries.toList(), key = { it.key }) { (key, vector) ->
                val picked = key == icon
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (picked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(picked, role = Role.RadioButton) { icon = key },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(vector, null, tint = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ColourPicker(colour) { colour = it }
    }
}

// ---- Recurring ----

@Composable
fun frequencyLabel(frequency: Frequency): String = t(
    when (frequency) {
        Frequency.DAILY -> R.string.freq_daily
        Frequency.WEEKLY -> R.string.freq_weekly
        Frequency.MONTHLY -> R.string.freq_monthly
        Frequency.YEARLY -> R.string.freq_yearly
    }
)

@Composable
fun RecurringScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<RecurringRule?>(null) }
    var adding by remember { mutableStateOf(false) }

    SubScreen(t(R.string.recurring), onBack, fab = { if (data.activeAccounts.isNotEmpty()) AddFab(t(R.string.add_recurring)) { adding = true } }) { padding ->
        ScreenColumn(padding) {
            if (data.recurring.isEmpty()) EmptyState(t(R.string.no_recurring), Icons.Rounded.Autorenew)
            else SectionCard {
                Column {
                    data.recurring.forEach { rule ->
                        val category = data.categoryById[rule.categoryId]
                        val currency = data.accountById[rule.accountId]?.account?.currency ?: data.main
                        ListItem(
                            headlineContent = { Text(rule.merchant.ifBlank { category?.name ?: typeLabel(rule.type) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    if (rule.active) "${frequencyLabel(rule.frequency)} · ${t(R.string.next_on, formatDate(LocalDate.parse(rule.nextDue)))}"
                                    else t(R.string.paused),
                                )
                            },
                            leadingContent = { if (category != null) CategoryBadge(category.icon, category.colour) else IconBadge(Icons.Rounded.Autorenew) },
                            trailingContent = { TrailingAmount(signed(rule.amount, currency)) },
                            colors = transparentItem,
                            modifier = Modifier.alpha(if (rule.active) 1f else 0.6f).clickable { editing = rule },
                        )
                    }
                }
            }
        }
    }

    if (adding) RecurringDialog(null, data, vm) { adding = false }
    editing?.let { rule -> RecurringDialog(rule, data, vm) { editing = null } }
}

@Composable
private fun RecurringDialog(rule: RecurringRule?, data: AppData, vm: MainViewModel, onDismiss: () -> Unit) {
    val accounts = data.activeAccounts.map { it.account }
    var type by remember { mutableStateOf(rule?.type ?: TxType.EXPENSE) }
    var accountId by remember { mutableStateOf(rule?.accountId ?: accounts.firstOrNull { it.id == data.settings.lastAccountId }?.id ?: accounts.first().id) }
    val account = accounts.firstOrNull { it.id == accountId } ?: data.accountById[accountId]?.account
    val currency = account?.currency ?: data.main
    var amount by remember { mutableStateOf(rule?.let { Money.plain(abs(it.amount), currency) }.orEmpty()) }
    var categoryId by remember { mutableStateOf(rule?.categoryId) }
    var merchant by remember { mutableStateOf(rule?.merchant.orEmpty()) }
    var frequency by remember { mutableStateOf(rule?.frequency ?: Frequency.MONTHLY) }
    var interval by remember { mutableStateOf((rule?.interval ?: 1).toString()) }
    var start by remember { mutableStateOf(rule?.let { LocalDate.parse(it.nextDue) } ?: LocalDate.now()) }
    var end by remember { mutableStateOf(rule?.endDate?.let(LocalDate::parse)) }
    var active by remember { mutableStateOf(rule?.active ?: true) }
    var pickCategory by remember { mutableStateOf(false) }

    val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val category = data.categoryById[categoryId]?.takeIf { it.kind == kind }
    val parsed = Money.parseLoose(amount, currency)
    val every = interval.toIntOrNull()
    val frequencyLabels = Frequency.entries.associateWith { frequencyLabel(it) }

    FormDialog(
        title = t(if (rule == null) R.string.add_recurring else R.string.edit_recurring), onDismiss = onDismiss,
        onSave = {
            vm.run {
                val fresh = newRecurring(accountId, parsed!!, type, category?.id, merchant.trim(), frequency, every!!, start, end)
                saveRecurring(if (rule == null) fresh else fresh.copy(id = rule.id, note = rule.note, active = active))
            }
        },
        saveEnabled = parsed != null && parsed > 0 && every != null && every in 1..365 && account != null,
        onDelete = if (rule != null) ({ vm.run { dao.deleteRecurring(rule.id) } }) else null,
    ) {
        Segmented(listOf(TxType.EXPENSE to t(R.string.expense), TxType.INCOME to t(R.string.income)), type, { type = it })
        AmountField(amount, { amount = it }, t(R.string.amount), currency)
        Dropdown(t(R.string.account), accounts, account, text = { it.name }, onSelect = { accountId = it.id })
        CategoryField(category) { pickCategory = true }
        OutlinedTextField(merchant, { merchant = it }, label = { Text(t(R.string.merchant)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                interval, { new -> if (new.length <= 3 && new.all(Char::isDigit)) interval = new }, label = { Text(t(R.string.every)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp),
            )
            Dropdown(t(R.string.repeats), Frequency.entries, frequency, text = { frequencyLabels.getValue(it) }, onSelect = { frequency = it }, modifier = Modifier.weight(1f))
        }
        DateButton(t(if (rule == null) R.string.starts else R.string.next_date), start, onPick = { start = it }, modifier = Modifier.fillMaxWidth())
        DateButton(t(R.string.ends), end, onPick = { end = it }, modifier = Modifier.fillMaxWidth(), onClear = { end = null })
        if (rule != null) SwitchRow(t(R.string.active), active, { active = it })
    }

    if (pickCategory) CategoryPickerDialog(data.categories, kind, onPick = { categoryId = it?.id }, onDismiss = { pickCategory = false })
}

// ---- Savings goals ----

@Composable
fun GoalsScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Goal?>(null) }

    SubScreen(t(R.string.goals), onBack, fab = { AddFab(t(R.string.add_goal)) { editing = Goal(name = "", target = 0, currency = data.main) } }) { padding ->
        ScreenColumn(padding) {
            if (data.goals.isEmpty()) EmptyState(t(R.string.no_goals), Icons.Rounded.Flag)
            data.goals.forEach { goal ->
                val saved = goal.accountId?.let { data.accountById[it]?.balance } ?: goal.saved
                val fraction = if (goal.target > 0) (saved.toFloat() / goal.target).coerceIn(0f, 1f) else 0f
                SectionCard(Modifier.padding(bottom = 8.dp).clickable { editing = goal }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(categoryIcon(goal.icon))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                goal.deadline?.let {
                                    Text(t(R.string.by_date, formatDate(LocalDate.parse(it))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${Money.format(saved, goal.currency)} / ${Money.format(goal.target, goal.currency)}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    editing?.let { goal -> GoalDialog(goal, data, vm) { editing = null } }
}

@Composable
private fun GoalDialog(goal: Goal, data: AppData, vm: MainViewModel, onDismiss: () -> Unit) {
    val accounts = data.activeAccounts.map { it.account }
    var name by remember { mutableStateOf(goal.name) }
    var accountId by remember { mutableStateOf(goal.accountId) }
    val account = accounts.firstOrNull { it.id == accountId }
    // A goal tied to an account is measured in that account's currency.
    val currency = account?.currency ?: goal.currency
    var target by remember { mutableStateOf(if (goal.target > 0) Money.plain(goal.target, goal.currency) else "") }
    var saved by remember { mutableStateOf(if (goal.saved != 0L) Money.plain(goal.saved, goal.currency) else "") }
    var deadline by remember { mutableStateOf(goal.deadline?.let(LocalDate::parse)) }
    val parsedTarget = Money.parseLoose(target, currency)
    val parsedSaved = if (saved.isBlank()) 0L else Money.parseLoose(saved, currency)
    val manual = t(R.string.goal_manual)

    FormDialog(
        title = t(if (goal.id == 0L) R.string.add_goal else R.string.edit_goal), onDismiss = onDismiss,
        onSave = {
            vm.run {
                dao.putGoal(goal.copy(name = name.trim(), target = parsedTarget!!, currency = currency, deadline = deadline?.toString(), accountId = account?.id, saved = parsedSaved ?: 0L))
            }
        },
        saveEnabled = name.isNotBlank() && parsedTarget != null && parsedTarget > 0 && parsedSaved != null,
        onDelete = if (goal.id != 0L) ({ vm.run { dao.deleteGoal(goal.id) } }) else null,
    ) {
        OutlinedTextField(name, { name = it }, label = { Text(t(R.string.goal_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AmountField(target, { target = it }, t(R.string.goal_target), currency)
        Dropdown<Account?>(t(R.string.goal_progress_from), listOf<Account?>(null) + accounts, account, text = { it?.name ?: manual }, onSelect = { accountId = it?.id })
        if (account == null) AmountField(saved, { saved = it }, t(R.string.goal_saved), currency)
        DateButton(t(R.string.deadline), deadline, onPick = { deadline = it }, modifier = Modifier.fillMaxWidth(), onClear = { deadline = null })
    }
}

// ---- Debts and loans ----

@Composable
fun DebtsScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Debt?>(null) }
    val paid = remember(data.debtPayments) { data.debtPayments.groupBy { it.debtId }.mapValues { (_, rows) -> rows.sumOf { it.amount } } }
    val newDebt = { Debt(person = "", direction = DebtDirection.I_OWE, amount = 0, currency = data.main, createdAt = System.currentTimeMillis()) }

    SubScreen(t(R.string.debts), onBack, fab = { AddFab(t(R.string.add_debt)) { editing = newDebt() } }) { padding ->
        ScreenColumn(padding) {
            if (data.debts.isEmpty()) EmptyState(t(R.string.no_debts), Icons.Rounded.Handshake)
            else SectionCard {
                Column {
                    data.debts.forEach { debt ->
                        val left = (debt.amount - (paid[debt.id] ?: 0L)).coerceAtLeast(0)
                        ListItem(
                            headlineContent = { Text(debt.person, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        t(if (debt.direction == DebtDirection.I_OWE) R.string.i_owe else R.string.owes_me),
                                        if (debt.settled) t(R.string.settled) else debt.dueDate?.let { t(R.string.by_date, formatDate(LocalDate.parse(it))) },
                                    ).joinToString(" · "),
                                )
                            },
                            leadingContent = { IconBadge(Icons.Rounded.Handshake) },
                            trailingContent = { TrailingAmount(Money.format(left, debt.currency)) },
                            colors = transparentItem,
                            modifier = Modifier.alpha(if (debt.settled) 0.6f else 1f).clickable { editing = debt },
                        )
                    }
                }
            }
        }
    }

    editing?.let { debt -> DebtDialog(debt, paid[debt.id] ?: 0L, vm) { editing = null } }
}

@Composable
private fun DebtDialog(debt: Debt, paid: Long, vm: MainViewModel, onDismiss: () -> Unit) {
    val new = debt.id == 0L
    var person by remember { mutableStateOf(debt.person) }
    var direction by remember { mutableStateOf(debt.direction) }
    var amount by remember { mutableStateOf(if (debt.amount > 0) Money.plain(debt.amount, debt.currency) else "") }
    var note by remember { mutableStateOf(debt.note) }
    var due by remember { mutableStateOf(debt.dueDate?.let(LocalDate::parse)) }
    var payment by remember { mutableStateOf("") }
    val parsed = Money.parseLoose(amount, debt.currency)
    val parsedPayment = if (payment.isBlank()) 0L else Money.parseLoose(payment, debt.currency)

    FormDialog(
        title = t(if (new) R.string.add_debt else R.string.edit_debt), onDismiss = onDismiss,
        onSave = {
            vm.run {
                val id = dao.putDebt(debt.copy(person = person.trim(), direction = direction, amount = parsed!!, note = note.trim(), dueDate = due?.toString(), settled = paid >= parsed))
                if (parsedPayment != null && parsedPayment > 0) addDebtPayment(id, parsedPayment)
            }
        },
        saveEnabled = person.isNotBlank() && parsed != null && parsed > 0 && parsedPayment != null,
        onDelete = if (!new) ({ vm.run { removeDebt(debt.id) } }) else null,
    ) {
        Segmented(listOf(DebtDirection.I_OWE to t(R.string.i_owe), DebtDirection.OWED_TO_ME to t(R.string.owes_me)), direction, { direction = it })
        OutlinedTextField(person, { person = it }, label = { Text(t(R.string.person)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AmountField(amount, { amount = it }, t(R.string.amount), debt.currency)
        OutlinedTextField(note, { note = it }, label = { Text(t(R.string.note)) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
        DateButton(t(R.string.due_date), due, onPick = { due = it }, modifier = Modifier.fillMaxWidth(), onClear = { due = null })
        if (!new) {
            Text(t(R.string.paid_so_far, Money.format(paid, debt.currency)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountField(payment, { payment = it }, t(R.string.record_payment), debt.currency)
        }
    }
}
