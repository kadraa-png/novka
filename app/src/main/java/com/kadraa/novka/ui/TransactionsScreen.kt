@file:OptIn(ExperimentalMaterial3Api::class)

package com.kadraa.novka.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kadraa.novka.R
import com.kadraa.novka.data.Tx
import com.kadraa.novka.domain.CategoryKind
import com.kadraa.novka.domain.TxType
import com.kadraa.novka.domain.fold

private fun matches(tx: Tx, filter: TxFilter, data: AppData, family: Set<Long>?): Boolean {
    if (filter.type != null && tx.type != filter.type) return false
    if (filter.accountId != null && tx.accountId != filter.accountId) return false
    if (family != null && tx.categoryId !in family) return false
    if (filter.source != null && tx.source != filter.source) return false
    if (filter.reviewOnly && !tx.needsReview) return false
    if (filter.from != null && tx.timestamp < filter.from.startMillis()) return false
    if (filter.to != null && tx.timestamp > filter.to.endMillis()) return false
    // A transfer is two rows; outside a single account's history only the outgoing one is listed.
    if (filter.accountId == null && tx.type == TxType.TRANSFER && tx.amount > 0) return false
    if (filter.query.isNotBlank()) {
        val text = fold(listOf(tx.merchant, tx.note, data.categoryById[tx.categoryId]?.name.orEmpty(), data.accountById[tx.accountId]?.account?.name.orEmpty()).joinToString(" "))
        if (!text.contains(fold(filter.query))) return false
    }
    return true
}

@Composable
fun TransactionsScreen(data: AppData, vm: MainViewModel) {
    val filter = vm.filter
    val shown = remember(data.txs, data.categories, filter) {
        val family = filter.categoryId?.let(data::family)
        data.txs.filter { matches(it, filter, data, family) }
    }
    val days = remember(shown) { shown.groupBy { it.timestamp.toDate() }.toList() }
    val deletedText = t(R.string.transaction_deleted)
    val undoText = t(R.string.undo)
    var accountMenu by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp)) {
            TextField(
                value = filter.query, onValueChange = { vm.filter = filter.copy(query = it) },
                placeholder = { Text(t(R.string.search)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) IconButton(onClick = { vm.filter = filter.copy(query = "") }) { Icon(Icons.Rounded.Close, t(R.string.clear)) }
                },
                singleLine = true, shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )

            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data.txs.any { it.needsReview } || filter.reviewOnly) {
                    FilterChip(filter.reviewOnly, { vm.filter = filter.copy(reviewOnly = !filter.reviewOnly) }, label = { Text(t(R.string.needs_review)) })
                }
                TxType.entries.forEach { type ->
                    FilterChip(filter.type == type, { vm.filter = filter.copy(type = if (filter.type == type) null else type) }, label = { Text(typeLabel(type)) })
                }
                Box {
                    FilterChip(
                        filter.accountId != null, { accountMenu = true },
                        label = { Text(data.accountById[filter.accountId]?.account?.name ?: t(R.string.all_accounts)) },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(accountMenu, { accountMenu = false }) {
                        DropdownMenuItem(text = { Text(t(R.string.all_accounts)) }, onClick = { vm.filter = filter.copy(accountId = null); accountMenu = false })
                        data.accounts.forEach { row ->
                            DropdownMenuItem(
                                text = { Text(row.account.name) }, leadingIcon = { Icon(accountIcon(row.account.type), null) },
                                onClick = { vm.filter = filter.copy(accountId = row.account.id); accountMenu = false },
                            )
                        }
                    }
                }
                FilterChip(
                    filter.categoryId != null, { if (filter.categoryId != null) vm.filter = filter.copy(categoryId = null) else pickCategory = true },
                    label = { Text(data.categoryById[filter.categoryId]?.name ?: t(R.string.category)) },
                )
            }

            if (shown.isEmpty()) {
                if (filter.active) {
                    EmptyState(t(R.string.no_results), Icons.Rounded.SearchOff)
                    TextButton(onClick = { vm.filter = TxFilter() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t(R.string.clear_filters)) }
                } else {
                    EmptyState(t(R.string.no_transactions), Icons.AutoMirrored.Rounded.ReceiptLong)
                }
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                days.forEach { (day, txs) ->
                    item(key = day.toEpochDay().toString()) {
                        Row(
                            Modifier.animateItem(tween(Motion.SHORT), tween(Motion.MEDIUM), tween(Motion.QUICK))
                                .fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 24.dp, bottom = 4.dp),
                        ) {
                            Text(formatDate(day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(
                                signed(txs.filter { it.type != TxType.TRANSFER }.sumOf { it.mainAmount }, data.main),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(txs, key = { it.id }) { tx ->
                        val swipe = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled) vm.deleteWithUndo(tx.id, deletedText, undoText)
                                value != SwipeToDismissBoxValue.Settled
                            },
                        )
                        SwipeToDismissBox(
                            swipe, modifier = Modifier.animateItem(tween(Motion.SHORT), tween(Motion.MEDIUM), tween(Motion.QUICK)),
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                    contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                                ) { Icon(Icons.Rounded.Delete, t(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer) }
                            },
                        ) {
                            Surface { TxRow(tx, data, onClick = { vm.editing = Editing(tx = tx) }) }
                        }
                    }
                }
            }
        }
    }

    if (pickCategory) {
        CategoryPickerDialog(
            data.categories, if (filter.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
            onPick = { picked -> vm.filter = filter.copy(categoryId = picked?.id) }, onDismiss = { pickCategory = false }, allowNone = false,
        )
    }
}
