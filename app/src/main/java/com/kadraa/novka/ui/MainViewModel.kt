package com.kadraa.novka.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kadraa.novka.NovkaApp
import com.kadraa.novka.capture.Notifier
import com.kadraa.novka.data.Repository
import com.kadraa.novka.data.Settings
import com.kadraa.novka.data.Tx
import com.kadraa.novka.domain.Source
import com.kadraa.novka.domain.TxType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the transaction editor is working on: an existing row, or a new one of [type]. */
data class Editing(val tx: Tx? = null, val type: TxType = TxType.EXPENSE, val note: String = "", val capturedId: Long? = null)

data class TxFilter(
    val query: String = "",
    val type: TxType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val source: Source? = null,
    val reviewOnly: Boolean = false,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val active get() = this != TxFilter()
}

data class Snack(val text: String, val action: String? = null, val onAction: () -> Unit = {})

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo: Repository = (app as NovkaApp).repo

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val settings: StateFlow<Settings?> = repo.settings.flow.map<Settings, Settings?> { it }.state(null)
    val accounts = repo.dao.observeAccounts().state(emptyList())
    val categories = repo.dao.observeCategories().state(emptyList())
    val txs = repo.dao.observeTxs().state(emptyList())
    val budgets = repo.dao.observeBudgets().state(emptyList())
    val recurring = repo.dao.observeRecurring().state(emptyList())
    val rates = repo.dao.observeRates().map { list -> list.associate { it.code to it.rate } }.state(emptyMap())
    val goals = repo.dao.observeGoals().state(emptyList())
    val debts = repo.dao.observeDebts().state(emptyList())
    val debtPayments = repo.dao.observeDebtPayments().state(emptyList())
    val captured = repo.dao.observeCaptured().state(emptyList())
    val cards = repo.dao.observeCards().state(emptyList())

    val snacks = MutableSharedFlow<Snack>(extraBufferCapacity = 4)

    var editing by mutableStateOf<Editing?>(null)
    var filter by mutableStateOf(TxFilter())

    var locked by mutableStateOf(false)
    var lockChecked = false
    var backgroundedAt = 0L

    fun run(block: suspend Repository.() -> Unit): Job = viewModelScope.launch {
        runCatching { repo.block() }.onFailure { snacks.tryEmit(Snack(it.message ?: it.javaClass.simpleName)) }
    }

    fun say(text: String) {
        snacks.tryEmit(Snack(text))
    }

    fun deleteWithUndo(id: Long, deletedText: String, undoText: String) = run {
        val removed = deleteTx(id)
        Notifier.cancelPrompt(getApplication(), id)
        if (removed.isNotEmpty()) snacks.tryEmit(Snack(deletedText, undoText) { this@MainViewModel.run { restoreTxs(removed) } })
    }

    fun openTx(id: Long) = viewModelScope.launch {
        repo.dao.tx(id)?.let { editing = Editing(tx = it) }
    }
}
