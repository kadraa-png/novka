@file:OptIn(ExperimentalMaterial3Api::class)

package com.kadraa.novka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kadraa.novka.R
import com.kadraa.novka.data.Account
import com.kadraa.novka.data.AccountWithBalance
import com.kadraa.novka.data.Budget
import com.kadraa.novka.data.Captured
import com.kadraa.novka.data.CardMapping
import com.kadraa.novka.data.Category
import com.kadraa.novka.data.Debt
import com.kadraa.novka.data.DebtPayment
import com.kadraa.novka.data.Goal
import com.kadraa.novka.data.RecurringRule
import com.kadraa.novka.data.Settings
import com.kadraa.novka.data.Tx
import com.kadraa.novka.domain.CategoryKind
import com.kadraa.novka.domain.CurrencyConverter
import com.kadraa.novka.domain.Money
import com.kadraa.novka.domain.TxType
import com.kadraa.novka.domain.fold
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

@Composable
fun t(id: Int, vararg args: Any): String = stringResource(id, *args)

/** Everything the screens read, collected once at the top of the app. */
data class AppData(
    val settings: Settings,
    val accounts: List<AccountWithBalance>,
    val categories: List<Category>,
    val txs: List<Tx>,
    val budgets: List<Budget>,
    val recurring: List<RecurringRule>,
    val rates: Map<String, Double>,
    val goals: List<Goal>,
    val debts: List<Debt>,
    val debtPayments: List<DebtPayment>,
    val captured: List<Captured>,
    val cards: List<CardMapping>,
) {
    val main: String get() = settings.mainCurrency
    val categoryById: Map<Long, Category> by lazy { categories.associateBy { it.id } }
    val accountById: Map<Long, AccountWithBalance> by lazy { accounts.associateBy { it.account.id } }
    val activeAccounts: List<AccountWithBalance> by lazy { accounts.filter { !it.account.archived } }
    val pairs: Map<String, List<Tx>> by lazy { txs.filter { it.transferPairId != null }.groupBy { it.transferPairId!! } }

    /** Value in the main currency at today's rate, or null when no rate is known. */
    fun toMain(amount: Long, currency: String): Long? =
        CurrencyConverter.rate(currency, main, rates)?.let { CurrencyConverter.convert(amount, currency, main, it) }

    val totalBalance: Long by lazy { activeAccounts.sumOf { toMain(it.balance, it.account.currency) ?: 0L } }

    /** A category together with its sub-categories. */
    fun family(categoryId: Long): Set<Long> = categories.filter { it.id == categoryId || it.parentId == categoryId }.map { it.id }.toSet()

    fun topLevel(categoryId: Long?): Category? = categoryById[categoryId]?.let { categoryById[it.parentId] ?: it }
}

@Composable
fun rememberAppData(vm: MainViewModel): AppData? {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val txs by vm.txs.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val rates by vm.rates.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val payments by vm.debtPayments.collectAsStateWithLifecycle()
    val captured by vm.captured.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val current = settings ?: return null
    return remember(current, accounts, categories, txs, budgets, recurring, rates, goals, debts, payments, captured, cards) {
        AppData(current, accounts, categories, txs, budgets, recurring, rates, goals, debts, payments, captured, cards)
    }
}

// ---- Dates ----

val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
fun LocalDate.endMillis(): Long = plusDays(1).startMillis() - 1
fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
fun signed(amount: Long, currency: String): String = (if (amount > 0) "+" else "") + Money.format(amount, currency)

// ---- Small pieces ----

/** A category's icon on a tint of its own colour. [icon] is a key from [categoryIcons]. */
@Composable
fun CategoryBadge(icon: String, colour: Long, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    IconBadge(categoryIcon(icon), size, Color(colour).copy(alpha = 0.24f).compositeOver(scheme.surface), scheme.onSurface)
}

@Composable
fun IconBadge(
    icon: ImageVector,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(Modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector = Icons.Rounded.Inbox) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(R.string.back)) } },
                actions = actions,
            )
        },
        floatingActionButton = fab,
        content = content,
    )
}

@Composable
fun <T> Dropdown(label: String, items: List<T>, selected: T?, text: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(text).orEmpty(), onValueChange = {}, readOnly = true, singleLine = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(text(item)) }, onClick = { onSelect(item); open = false })
            }
        }
    }
}

@Composable
fun AmountField(value: String, onChange: (String) -> Unit, label: String, currency: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.all { it.isDigit() || it in ".,- " }) onChange(new) },
        label = { Text(label) }, suffix = { Text(Money.symbol(currency)) }, singleLine = true,
        isError = value.isNotBlank() && Money.parseLoose(value, currency) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun DateButton(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier, onClear: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(Icons.Rounded.CalendarToday, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(if (date != null) "$label: ${formatDate(date)}" else label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(t(R.string.ok)) }
            },
            dismissButton = {
                if (onClear != null && date != null) {
                    TextButton(onClick = { onClear(); open = false }) { Text(t(R.string.clear)) }
                } else {
                    TextButton(onClick = { open = false }) { Text(t(R.string.cancel)) }
                }
            },
        ) { DatePicker(state) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t(R.string.cancel)) } },
    )
}

/** A dialog holding a short form, with Save, Cancel and an optional Delete. */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        },
        confirmButton = { TextButton(onClick = { onSave(); onDismiss() }, enabled = saveEnabled) { Text(t(R.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = { onDelete(); onDismiss() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(t(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(t(R.string.cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColourPicker(selected: Long, onPick: (Long) -> Unit) {
    FlowRow {
        com.kadraa.novka.domain.DefaultCategories.colours.forEach { colour ->
            val picked = colour == selected
            Box(
                Modifier.size(48.dp).clip(CircleShape).selectable(picked, role = Role.RadioButton) { onPick(colour) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(36.dp).background(Color(colour), CircleShape)
                        .then(if (picked) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    // The swatch is a stored data colour, so the tick picks whichever of black or white reads on it.
                    if (picked) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = if (Color(colour).luminance() > 0.4f) Color.Black else Color.White)
                }
            }
        }
    }
}

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    kind: CategoryKind,
    onPick: (Category?) -> Unit,
    onDismiss: () -> Unit,
    allowNone: Boolean = true,
    topLevelOnly: Boolean = false,
) {
    val usable = categories.filter { it.kind == kind && !it.archived }
    val parents = usable.filter { it.parentId == null || usable.none { p -> p.id == it.parentId } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.category)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                parents.forEach { parent ->
                    item(key = parent.id) { CategoryPickRow(parent, indent = false) { onPick(parent); onDismiss() } }
                    if (!topLevelOnly) {
                        items(usable.filter { it.parentId == parent.id }, key = { it.id }) { child ->
                            CategoryPickRow(child, indent = true) { onPick(child); onDismiss() }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t(R.string.cancel)) } },
        dismissButton = { if (allowNone) TextButton(onClick = { onPick(null); onDismiss() }) { Text(t(R.string.none)) } },
    )
}

@Composable
private fun CategoryPickRow(category: Category, indent: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(category.name, style = if (indent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium) },
        leadingContent = { CategoryBadge(category.icon, category.colour, if (indent) 32.dp else 40.dp) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(start = if (indent) 24.dp else 0.dp),
    )
}

private val commonCurrencies = listOf("BAM", "EUR", "USD", "GBP", "CHF", "RSD", "TRY", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "CAD", "AUD", "JPY")

@Composable
fun CurrencyPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        val codes = Currency.getAvailableCurrencies().sortedBy { it.currencyCode }
        (commonCurrencies.mapNotNull { code -> codes.firstOrNull { it.currencyCode == code } } + codes.filter { it.currencyCode !in commonCurrencies })
            .map { it.currencyCode to it.displayName }
    }
    val shown = all.filter { (code, name) -> query.isBlank() || fold(code).contains(fold(query)) || fold(name).contains(fold(query)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.currency)) },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it }, label = { Text(t(R.string.search)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.first }) { (code, name) ->
                        ListItem(
                            headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Text(code, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onPick(code); onDismiss() },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t(R.string.cancel)) } },
    )
}

@Composable
fun typeLabel(type: TxType): String = t(
    when (type) {
        TxType.EXPENSE -> R.string.expense
        TxType.INCOME -> R.string.income
        TxType.TRANSFER -> R.string.transfer
    }
)

@Composable
fun TxRow(tx: Tx, data: AppData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val category = data.categoryById[tx.categoryId]
    val account = data.accountById[tx.accountId]?.account
    val other: Account? = tx.transferPairId?.let { pair -> data.pairs[pair]?.firstOrNull { it.id != tx.id } }?.let { data.accountById[it.accountId]?.account }
    val title = when {
        tx.type == TxType.TRANSFER -> if (tx.amount < 0) "${account?.name.orEmpty()} → ${other?.name.orEmpty()}" else "${other?.name.orEmpty()} → ${account?.name.orEmpty()}"
        tx.merchant.isNotBlank() -> tx.merchant
        category != null -> category.name
        tx.note.isNotBlank() -> tx.note
        else -> typeLabel(tx.type)
    }
    val details = buildList {
        if (tx.type == TxType.TRANSFER) add(t(R.string.transfer))
        if (tx.type != TxType.TRANSFER && tx.merchant.isNotBlank()) add(category?.name ?: t(R.string.uncategorised))
        if (tx.type != TxType.TRANSFER) account?.let { add(it.name) }
        if (tx.pendingAuth) add(t(R.string.awaiting_confirmation))
        if (tx.needsReview) add(t(R.string.needs_review))
    }
    val supporting: (@Composable () -> Unit)? = if (details.isEmpty()) null else {
        {
            Text(
                details.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (tx.needsReview) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting,
        leadingContent = {
            when {
                tx.type == TxType.TRANSFER -> IconBadge(Icons.Rounded.SwapHoriz)
                category != null -> CategoryBadge(category.icon, category.colour)
                else -> IconBadge(Icons.AutoMirrored.Rounded.HelpOutline)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    signed(tx.amount, tx.currency), style = MaterialTheme.typography.titleMedium,
                    color = if (tx.type == TxType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (tx.originalAmount != null && tx.originalCurrency != null) {
                    Text(Money.format(tx.originalAmount, tx.originalCurrency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun FullScreenMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Pieces shared by the list and settings screens ----

@Composable
fun AddFab(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(label) })
}

/** The scrolling body of a sub-screen: centred, capped in width, with room for a floating button. */
@Composable
fun ScreenColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 96.dp), content = content)
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected, onClick = { onSelect(value) }, shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** A form row that opens a picker: label above, current value below. */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    OutlinedCard(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.extraSmall) {
        ListItem(
            overlineContent = { Text(label) },
            headlineContent = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = leading,
            trailingContent = { if (enabled) Icon(Icons.Rounded.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
fun CategoryField(category: Category?, onClick: () -> Unit) {
    PickerField(t(R.string.category), category?.name ?: t(R.string.uncategorised), onClick) {
        if (category != null) CategoryBadge(category.icon, category.colour) else IconBadge(Icons.Rounded.Category)
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, text: String? = null, enabled: Boolean = true) {
    val supporting: (@Composable () -> Unit)? = text?.let { { Text(it) } }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting,
        trailingContent = { Switch(checked, null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit, text: String? = null) {
    val supporting: (@Composable () -> Unit)? = text?.let { { Text(it) } }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting,
        leadingContent = { RadioButton(selected, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
    )
}

/** A stored swatch colour as a soft container that works on light and dark surfaces. */
@Composable
fun tinted(colour: Long): Color = Color(colour).copy(alpha = 0.24f).compositeOver(MaterialTheme.colorScheme.surface)
