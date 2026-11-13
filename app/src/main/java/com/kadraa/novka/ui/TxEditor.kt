@file:OptIn(ExperimentalMaterial3Api::class)

package com.kadraa.novka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kadraa.novka.R
import com.kadraa.novka.data.Account
import com.kadraa.novka.data.TxDraft
import com.kadraa.novka.domain.CaptureStatus
import com.kadraa.novka.domain.CategoryKind
import com.kadraa.novka.domain.Money
import com.kadraa.novka.domain.TxType
import java.time.Instant
import kotlin.math.abs

/** Bottom sheet for adding or editing an expense, income or transfer. */
@Composable
fun TxEditorSheet(editing: Editing, data: AppData, vm: MainViewModel) = key(editing) {
    val tx = editing.tx
    val legs = tx?.transferPairId?.let { data.pairs[it] }.orEmpty()
    val outLeg = legs.firstOrNull { it.amount < 0 }
    val inLeg = legs.firstOrNull { it.id != outLeg?.id }
    val baseTime = remember { tx?.timestamp ?: System.currentTimeMillis() }

    var type by remember { mutableStateOf(tx?.type ?: editing.type) }
    var fromId by remember {
        val preferred = listOf(data.settings.lastAccountId, data.settings.defaultAccountId).firstOrNull { id -> data.activeAccounts.any { it.account.id == id } }
        mutableStateOf(outLeg?.accountId ?: tx?.accountId ?: preferred ?: data.activeAccounts.firstOrNull()?.account?.id)
    }
    var toId by remember { mutableStateOf(inLeg?.accountId) }
    var amount by remember { mutableStateOf((outLeg ?: tx)?.let { Money.plain(abs(it.amount), it.currency) }.orEmpty()) }
    var categoryId by remember { mutableStateOf(tx?.categoryId) }
    var merchant by remember { mutableStateOf(tx?.merchant.orEmpty()) }
    var note by remember { mutableStateOf(tx?.note ?: editing.note) }
    var date by remember { mutableStateOf(baseTime.toDate()) }
    var pickCategory by remember { mutableStateOf(false) }

    // An archived account stays selectable while editing a transaction that already uses it.
    val accounts: List<Account> = data.accounts.map { it.account }.filter { !it.archived || it.id == fromId || it.id == toId }
    val from = accounts.firstOrNull { it.id == fromId }
    val to = accounts.firstOrNull { it.id == toId }
    val currency = from?.currency ?: data.main
    val parsed = Money.parseLoose(amount, currency)
    val category = data.categoryById[categoryId]
    val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val valid = parsed != null && parsed > 0 && from != null && (type != TxType.TRANSFER || (to != null && to.id != from.id))

    fun close() {
        vm.editing = null
    }

    fun save() {
        val timestamp = if (date == baseTime.toDate()) baseTime else date.atTime(Instant.ofEpochMilli(baseTime).atZone(zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        val draft = TxDraft(
            id = tx?.id ?: 0, type = type, accountId = from!!.id, toAccountId = to?.id.takeIf { type == TxType.TRANSFER }, amount = parsed!!,
            categoryId = categoryId.takeIf { type != TxType.TRANSFER && category?.kind == kind },
            merchant = if (type == TxType.TRANSFER) "" else merchant, note = note, timestamp = timestamp,
        )
        vm.run {
            val id = saveTx(draft)
            editing.capturedId?.let { dao.setCapturedStatus(it, CaptureStatus.PARSED.name, id) }
        }
        close()
    }

    val deletedText = t(R.string.transaction_deleted)
    val undoText = t(R.string.undo)
    val focus = remember { FocusRequester() }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(t(if (tx == null) R.string.new_transaction else R.string.edit_transaction), style = MaterialTheme.typography.titleLarge)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val types = TxType.entries
                types.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = type == option, onClick = { type = option }, shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    ) { Text(typeLabel(option), maxLines = 1) }
                }
            }

            AmountField(amount, { amount = it }, t(R.string.amount), currency, Modifier.focusRequester(focus))

            Dropdown(
                t(if (type == TxType.TRANSFER) R.string.from_account else R.string.account), accounts, from,
                text = { "${it.name} (${Money.symbol(it.currency)})" }, onSelect = { fromId = it.id },
            )

            if (type == TxType.TRANSFER) {
                Dropdown(
                    t(R.string.to_account), accounts.filter { it.id != fromId }, to,
                    text = { "${it.name} (${Money.symbol(it.currency)})" }, onSelect = { toId = it.id },
                )
            } else {
                OutlinedCard(onClick = { pickCategory = true }, shape = MaterialTheme.shapes.extraSmall) {
                    ListItem(
                        overlineContent = { Text(t(R.string.category)) },
                        headlineContent = { Text(category?.takeIf { it.kind == kind }?.name ?: t(R.string.uncategorised)) },
                        leadingContent = {
                            if (category != null && category.kind == kind) CategoryBadge(category.icon, category.colour) else IconBadge(Icons.Rounded.Category)
                        },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                OutlinedTextField(merchant, { merchant = it }, label = { Text(t(R.string.merchant)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(note, { note = it }, label = { Text(t(R.string.note)) }, maxLines = 3, modifier = Modifier.fillMaxWidth())

            DateButton(t(R.string.date), date, onPick = { date = it }, modifier = Modifier.fillMaxWidth())

            if (tx?.alsoSeenBy != null) {
                TextButton(onClick = { vm.run { splitMerged(tx.id) }; close() }) { Text(t(R.string.split_merged)) }
            }

            Row(Modifier.fillMaxWidth()) {
                if (tx != null) {
                    TextButton(
                        onClick = { vm.deleteWithUndo(tx.id, deletedText, undoText); close() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(t(R.string.delete)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = ::close) { Text(t(R.string.cancel)) }
                Button(onClick = ::save, enabled = valid, modifier = Modifier.padding(start = 8.dp)) { Text(t(R.string.save)) }
            }
        }
    }

    LaunchedEffect(Unit) { if (tx == null) runCatching { focus.requestFocus() } }

    if (pickCategory) {
        CategoryPickerDialog(data.categories, kind, onPick = { categoryId = it?.id }, onDismiss = { pickCategory = false })
    }
}
