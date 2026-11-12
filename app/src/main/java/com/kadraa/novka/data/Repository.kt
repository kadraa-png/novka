package com.kadraa.novka.data

import androidx.room.withTransaction
import com.kadraa.novka.domain.AccountType
import com.kadraa.novka.domain.BudgetCalculator
import com.kadraa.novka.domain.CategoryGuesser
import com.kadraa.novka.domain.CategoryKind
import com.kadraa.novka.domain.Csv
import com.kadraa.novka.domain.CurrencyConverter
import com.kadraa.novka.domain.DefaultCategories
import com.kadraa.novka.domain.Frequency
import com.kadraa.novka.domain.ImportedRow
import com.kadraa.novka.domain.MerchantRuleInfo
import com.kadraa.novka.domain.RecurrenceCalculator
import com.kadraa.novka.domain.Source
import com.kadraa.novka.domain.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/** What the editor hands over. [amount] is positive and in the account's currency. */
data class TxDraft(
    val id: Long = 0,
    val type: TxType = TxType.EXPENSE,
    val accountId: Long,
    val toAccountId: Long? = null,
    val amount: Long,
    /** For a transfer between currencies: what arrived. Null converts at the current rate. */
    val toAmount: Long? = null,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val timestamp: Long,
)

data class BudgetAlert(val budgetId: Long, val categoryName: String, val level: Int, val spent: Long, val limit: Long)

class Repository(
    private val db: NovkaDb,
    val settings: SettingsStore,
    private val rateProviders: List<RateProvider>,
) {
    val dao: NovkaDao = db.dao()

    /** Called after any write that changes balances or totals (widget refresh, budget alerts). */
    var onChanged: suspend () -> Unit = {}

    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---- Rates ----

    suspend fun rateMap(): Map<String, Double> = dao.rates().associate { it.code to it.rate }

    suspend fun refreshRates(): Boolean {
        val fetched = rateProviders.firstNotNullOfOrNull { it.fetch() } ?: return false
        val now = System.currentTimeMillis()
        val manual = dao.rates().filter { it.manual && it.code !in fetched }
        dao.putRates(fetched.map { ExchangeRate(it.key, it.value, now) } + ExchangeRate("EUR", 1.0, now) + manual)
        settings.update { it.copy(ratesFetchedAt = now) }
        repairMissingRates()
        return true
    }

    /** A rate typed in by the user for a currency the services do not list: units per one main-currency unit. */
    suspend fun setManualRate(code: String, perMainUnit: Double) {
        val rates = rateMap()
        val main = settings.current().mainCurrency
        val mainPerEuro = rates[main] ?: 1.0
        dao.putRates(
            listOfNotNull(
                ExchangeRate(code, perMainUnit * mainPerEuro, System.currentTimeMillis(), manual = true),
                if (main !in rates) ExchangeRate(main, 1.0, System.currentTimeMillis(), manual = true) else null,
            )
        )
        repairMissingRates()
    }

    /** Rate and main-currency value for an amount today. Rate 0 means no rate is known yet. */
    suspend fun mainOf(amount: Long, currency: String): Pair<Double, Long> =
        toMain(amount, currency, settings.current().mainCurrency, rateMap())

    suspend fun changed() = onChanged()

    private fun toMain(amount: Long, currency: String, main: String, rates: Map<String, Double>): Pair<Double, Long> {
        val rate = CurrencyConverter.rate(currency, main, rates) ?: return 0.0 to 0L
        return rate to CurrencyConverter.convert(amount, currency, main, rate)
    }

    /** Transactions saved while no rate was known carry rate 0 and are completed here. */
    private suspend fun repairMissingRates() {
        val pending = dao.txsWithoutRate()
        if (pending.isEmpty()) return
        val rates = rateMap()
        val main = settings.current().mainCurrency
        dao.putTxs(pending.mapNotNull { tx ->
            val (rate, mainAmount) = toMain(tx.amount, tx.currency, main, rates)
            if (rate > 0) tx.copy(rate = rate, mainAmount = mainAmount) else null
        })
        onChanged()
    }

    suspend fun convert(amount: Long, from: String, to: String): Long? {
        val rate = CurrencyConverter.rate(from, to, rateMap()) ?: return null
        return CurrencyConverter.convert(amount, from, to, rate)
    }

    // ---- Onboarding ----

    suspend fun seedCategories(bosnian: Boolean) = db.withTransaction {
        if (dao.categories().isNotEmpty()) return@withTransaction
        val ids = mutableMapOf<String, Long>()
        DefaultCategories.all.forEachIndexed { index, default ->
            val parentIndex = DefaultCategories.all.indexOfFirst { it.key == (default.parent ?: default.key) }
            ids[default.key] = dao.putCategory(
                Category(
                    name = if (bosnian) default.bs else default.en,
                    icon = default.icon,
                    colour = DefaultCategories.colours[(if (default.kind == CategoryKind.INCOME) index else parentIndex) % DefaultCategories.colours.size],
                    kind = default.kind,
                    parentId = default.parent?.let(ids::get),
                    key = default.key,
                )
            )
        }
    }

    suspend fun completeOnboarding(mainCurrency: String, accountName: String, type: AccountType, balance: Long, bosnian: Boolean) {
        seedCategories(bosnian)
        val id = dao.putAccount(Account(name = accountName, type = type, currency = mainCurrency, openingBalance = balance, colour = 0xFF4DB6AC))
        settings.update { it.copy(onboarded = true, mainCurrency = mainCurrency, defaultAccountId = id, lastAccountId = id) }
        onChanged()
    }

    // ---- Transactions ----

    suspend fun saveTx(draft: TxDraft): Long {
        val id = db.withTransaction {
            val existing = if (draft.id != 0L) dao.tx(draft.id) else null
            val account = dao.account(draft.accountId) ?: error("Account not found")
            val main = settings.current().mainCurrency
            val rates = rateMap()

            if (draft.type == TxType.TRANSFER) {
                val to = dao.account(draft.toAccountId ?: error("Transfer needs a destination")) ?: error("Account not found")
                existing?.let { deleteInternal(it) }
                val pair = UUID.randomUUID().toString()
                val arrived = draft.toAmount
                    ?: CurrencyConverter.rate(account.currency, to.currency, rates)?.let { CurrencyConverter.convert(draft.amount, account.currency, to.currency, it) }
                    ?: draft.amount
                val (outRate, outMain) = toMain(-draft.amount, account.currency, main, rates)
                val (inRate, inMain) = toMain(arrived, to.currency, main, rates)
                val outId = dao.putTx(
                    Tx(accountId = account.id, amount = -draft.amount, currency = account.currency, rate = outRate, mainAmount = outMain,
                        type = TxType.TRANSFER, note = draft.note, timestamp = draft.timestamp, transferPairId = pair,
                        source = existing?.source ?: Source.MANUAL)
                )
                dao.putTx(
                    Tx(accountId = to.id, amount = arrived, currency = to.currency, rate = inRate, mainAmount = inMain,
                        type = TxType.TRANSFER, note = draft.note, timestamp = draft.timestamp, transferPairId = pair,
                        source = existing?.source ?: Source.MANUAL)
                )
                outId
            } else {
                if (existing?.transferPairId != null) deleteInternal(existing)
                val base = existing?.takeIf { it.transferPairId == null }
                val signed = if (draft.type == TxType.EXPENSE) -draft.amount else draft.amount
                // Editing keeps the rate of the day the transaction happened.
                val keepRate = base != null && base.currency == account.currency && base.rate > 0
                val rate = if (keepRate) base!!.rate else toMain(signed, account.currency, main, rates).first
                val mainAmount = if (rate > 0) CurrencyConverter.convert(signed, account.currency, main, rate) else 0
                val amountChanged = base != null && abs(base.amount) != draft.amount
                val saved = (base ?: Tx(accountId = account.id, amount = 0, currency = account.currency, rate = 0.0, mainAmount = 0, type = draft.type, timestamp = 0)).copy(
                    accountId = account.id, amount = signed, currency = account.currency, rate = rate, mainAmount = mainAmount,
                    type = draft.type, categoryId = draft.categoryId, merchant = draft.merchant.trim(), note = draft.note.trim(),
                    timestamp = draft.timestamp, needsReview = false, pendingAuth = false,
                    originalAmount = if (amountChanged || base?.currency != account.currency) null else base?.originalAmount,
                    originalCurrency = if (amountChanged || base?.currency != account.currency) null else base?.originalCurrency,
                )
                val savedId = dao.putTx(saved)
                if (base != null && base.source in listOf(Source.WALLET, Source.RAIFFEISEN)) {
                    draft.categoryId?.let { learn(saved.merchant, it) }
                    saved.cardLast4?.let { dao.putCard(CardMapping(it, account.id)) }
                }
                savedId
            }
        }
        settings.update { it.copy(lastAccountId = draft.accountId) }
        onChanged()
        return id
    }

    private suspend fun deleteInternal(tx: Tx): List<Tx> {
        val rows = tx.transferPairId?.let { dao.txsOfPair(it) } ?: listOf(tx)
        rows.forEach { dao.deleteTx(it.id) }
        return rows
    }

    /** Returns the removed rows so the caller can offer undo with [restoreTxs]. */
    suspend fun deleteTx(id: Long): List<Tx> {
        val removed = db.withTransaction { dao.tx(id)?.let { deleteInternal(it) } ?: emptyList() }
        onChanged()
        return removed
    }

    suspend fun restoreTxs(rows: List<Tx>) {
        dao.putTxs(rows)
        onChanged()
    }

    /** Confirms or corrects the category of a captured payment and remembers it for the merchant. */
    suspend fun setCategory(txId: Long, categoryId: Long) {
        db.withTransaction {
            val tx = dao.tx(txId) ?: return@withTransaction
            dao.putTx(tx.copy(categoryId = categoryId, needsReview = false))
            learn(tx.merchant, categoryId)
        }
        onChanged()
    }

    private suspend fun learn(merchant: String, categoryId: Long) {
        val name = CategoryGuesser.normalise(merchant)
        if (name.isEmpty()) return
        // A correction drops what was learned before, so the count starts again.
        dao.deleteOtherRules(name, categoryId)
        val rule = dao.rules().firstOrNull { it.merchant == name && it.categoryId == categoryId }
        dao.putRule(rule?.copy(count = rule.count + 1) ?: MerchantRule(merchant = name, categoryId = categoryId, count = 1))
    }

    suspend fun ruleInfos(): List<MerchantRuleInfo> = dao.rules().map { MerchantRuleInfo(it.merchant, it.categoryId, it.count) }

    /** Undoes a cross-source merge when the two notifications were in fact two payments. */
    suspend fun splitMerged(txId: Long) {
        db.withTransaction {
            val tx = dao.tx(txId) ?: return@withTransaction
            val other = tx.alsoSeenBy ?: return@withTransaction
            dao.putTx(tx.copy(alsoSeenBy = null))
            dao.putTx(tx.copy(id = 0, source = other, alsoSeenBy = null, needsReview = true))
        }
        onChanged()
    }

    // ---- Accounts and categories ----

    suspend fun saveAccount(account: Account): Long {
        val id = dao.putAccount(account)
        if (settings.current().defaultAccountId == 0L) settings.update { it.copy(defaultAccountId = id) }
        onChanged()
        return id
    }

    /** With transactions and no [moveTo], the account is archived and keeps its history. */
    suspend fun removeAccount(id: Long, moveTo: Long?) {
        db.withTransaction {
            val account = dao.account(id) ?: return@withTransaction
            val used = dao.countForAccount(id) > 0
            when {
                used && moveTo == null -> dao.putAccount(account.copy(archived = true))
                else -> {
                    if (moveTo != null) dao.moveTxsToAccount(id, moveTo)
                    dao.deleteCardsForAccount(id)
                    dao.deleteRecurringForAccount(id)
                    dao.unlinkGoals(id)
                    dao.deleteAccount(id)
                }
            }
        }
        val current = settings.current()
        if (current.defaultAccountId == id || current.lastAccountId == id) {
            val fallback = moveTo ?: dao.accounts().firstOrNull { !it.archived && it.id != id }?.id ?: 0
            settings.update { it.copy(defaultAccountId = if (it.defaultAccountId == id) fallback else it.defaultAccountId, lastAccountId = fallback) }
        }
        onChanged()
    }

    suspend fun saveCategory(category: Category): Long = dao.putCategory(category)

    suspend fun removeCategory(id: Long, moveTo: Long?, archiveInstead: Boolean) {
        db.withTransaction {
            val category = dao.categories().firstOrNull { it.id == id } ?: return@withTransaction
            if (archiveInstead) {
                dao.putCategory(category.copy(archived = true))
            } else {
                dao.moveTxsToCategory(id, moveTo)
                dao.orphanChildren(id)
                dao.deleteRulesForCategory(id)
                dao.deleteBudgetsForCategory(id)
                dao.deleteCategory(id)
            }
        }
        onChanged()
    }

    suspend fun changeMainCurrency(newMain: String) {
        val old = settings.current().mainCurrency
        if (old == newMain) return
        if (rateMap()[newMain] == null) refreshRates()
        val rates = rateMap()
        val factor = CurrencyConverter.rate(old, newMain, rates)
        db.withTransaction {
            // Each stored rate is carried over through today's rate between the old and new main currency.
            dao.putTxs(dao.txs().map { tx ->
                val rate = when {
                    tx.currency == newMain -> 1.0
                    factor != null && tx.rate > 0 -> tx.rate * factor
                    else -> CurrencyConverter.rate(tx.currency, newMain, rates) ?: 0.0
                }
                tx.copy(rate = rate, mainAmount = if (rate > 0) CurrencyConverter.convert(tx.amount, tx.currency, newMain, rate) else 0)
            })
            if (factor != null) {
                dao.putBudgets(dao.budgets().map { it.copy(monthlyLimit = CurrencyConverter.convert(it.monthlyLimit, old, newMain, factor)) })
            }
        }
        settings.update { it.copy(mainCurrency = newMain, budgetAlerts = emptySet()) }
        onChanged()
    }

    // ---- Budgets ----

    private fun monthRange(month: YearMonth): Pair<Long, Long> =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    /** Budgets that crossed 80% or 100% this month and have not been announced yet. */
    suspend fun newBudgetAlerts(today: LocalDate = LocalDate.now()): List<BudgetAlert> {
        val budgets = dao.budgets()
        if (budgets.isEmpty()) return emptyList()
        val month = YearMonth.from(today)
        val (from, to) = monthRange(month)
        val txs = dao.txsBetween(from, to).filter { it.type == TxType.EXPENSE }
        val categories = dao.categories()
        val shown = settings.current().budgetAlerts
        val alerts = budgets.mapNotNull { budget ->
            val ids = categories.filter { it.id == budget.categoryId || it.parentId == budget.categoryId }.map { it.id }.toSet()
            val spent = -txs.filter { it.categoryId in ids }.sumOf { it.mainAmount }
            val status = BudgetCalculator.status(budget.monthlyLimit, spent, today)
            val key = "${budget.id}:$month:${status.level}"
            if (status.level == 0 || key in shown) return@mapNotNull null
            BudgetAlert(budget.id, categories.firstOrNull { it.id == budget.categoryId }?.name.orEmpty(), status.level, spent, budget.monthlyLimit) to key
        }
        if (alerts.isNotEmpty()) {
            settings.update { s -> s.copy(budgetAlerts = s.budgetAlerts.filter { it.contains(":$month:") }.toSet() + alerts.map { it.second }) }
        }
        return alerts.map { it.first }
    }

    // ---- Recurring ----

    /** Creates every transaction that has fallen due, including ones missed while the phone was off. */
    suspend fun runRecurring(today: LocalDate = LocalDate.now()): Int {
        var created = 0
        db.withTransaction {
            val main = settings.current().mainCurrency
            val rates = rateMap()
            for (rule in dao.recurring().filter { it.active }) {
                val account = dao.account(rule.accountId) ?: continue
                val due = RecurrenceCalculator.due(
                    LocalDate.parse(rule.nextDue), today, rule.frequency, rule.interval, rule.anchorDay, rule.endDate?.let(LocalDate::parse),
                )
                if (due.dates.isEmpty() && !due.finished) continue
                val (rate, _) = toMain(rule.amount, account.currency, main, rates)
                dao.putTxs(due.dates.map { date ->
                    Tx(
                        accountId = account.id, amount = rule.amount, currency = account.currency, rate = rate,
                        mainAmount = if (rate > 0) CurrencyConverter.convert(rule.amount, account.currency, main, rate) else 0,
                        type = rule.type, categoryId = rule.categoryId, merchant = rule.merchant, note = rule.note,
                        timestamp = date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(), source = Source.RECURRING,
                    )
                })
                created += due.dates.size
                dao.putRecurring(rule.copy(nextDue = due.nextDue.toString(), active = !due.finished))
            }
        }
        if (created > 0) onChanged()
        return created
    }

    suspend fun saveRecurring(rule: RecurringRule) {
        dao.putRecurring(rule)
        runRecurring()
    }

    fun newRecurring(accountId: Long, amount: Long, type: TxType, categoryId: Long?, merchant: String, frequency: Frequency, interval: Int, start: LocalDate, end: LocalDate?) =
        RecurringRule(
            accountId = accountId, amount = if (type == TxType.EXPENSE) -abs(amount) else abs(amount), type = type, categoryId = categoryId,
            merchant = merchant, frequency = frequency, interval = interval, anchorDay = start.dayOfMonth, nextDue = start.toString(), endDate = end?.toString(),
        )

    // ---- Debts ----

    suspend fun addDebtPayment(debtId: Long, amount: Long) = db.withTransaction {
        val debt = dao.debt(debtId) ?: return@withTransaction
        dao.putDebtPayment(DebtPayment(debtId = debtId, amount = amount, timestamp = System.currentTimeMillis()))
        dao.putDebt(debt.copy(settled = dao.paidForDebt(debtId) >= debt.amount))
    }

    suspend fun removeDebt(id: Long) = db.withTransaction {
        dao.deletePaymentsForDebt(id)
        dao.deleteDebt(id)
    }

    // ---- CSV ----

    suspend fun exportCsv(from: Long, to: Long): String {
        val accounts = dao.accounts().associateBy { it.id }
        val categories = dao.categories().associateBy { it.id }
        val header = listOf("date", "account", "type", "category", "amount", "currency", "merchant", "note", "source")
        val rows = dao.txsBetween(from, to).sortedBy { it.timestamp }.map { tx ->
            listOf(
                Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDateTime().withNano(0).toString(),
                accounts[tx.accountId]?.name.orEmpty(),
                tx.type.name.lowercase(),
                categories[tx.categoryId]?.name.orEmpty(),
                com.kadraa.novka.domain.Money.plain(tx.amount, tx.currency),
                tx.currency, tx.merchant, tx.note, tx.source.name.lowercase(),
            )
        }
        return Csv.write(listOf(header) + rows)
    }

    /** Returns how many rows were added. Rows already present in the account are skipped. */
    suspend fun importRows(accountId: Long, rows: List<ImportedRow>): Int {
        var added = 0
        db.withTransaction {
            val account = dao.account(accountId) ?: return@withTransaction
            val main = settings.current().mainCurrency
            val rates = rateMap()
            val categories = dao.categories().associateBy { com.kadraa.novka.domain.fold(it.name) }
            val seen = dao.txs().filter { it.accountId == accountId }
                .map { Triple(Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(), it.amount, it.merchant) }.toMutableSet()
            val batch = rows.mapNotNull { row ->
                var amount = row.amountMinor
                var original: Long? = null
                if (row.currency != null && row.currency != account.currency) {
                    val rate = CurrencyConverter.rate(row.currency, account.currency, rates) ?: return@mapNotNull null
                    original = abs(amount)
                    amount = CurrencyConverter.convert(amount, row.currency, account.currency, rate)
                }
                val merchant = row.merchant.ifBlank { row.note }
                if (!seen.add(Triple(row.date, amount, merchant))) return@mapNotNull null
                val (rate, mainAmount) = toMain(amount, account.currency, main, rates)
                Tx(
                    accountId = accountId, amount = amount, currency = account.currency, rate = rate, mainAmount = mainAmount,
                    type = if (amount < 0) TxType.EXPENSE else TxType.INCOME,
                    categoryId = row.category?.let { categories[com.kadraa.novka.domain.fold(it)]?.id },
                    merchant = merchant, note = if (row.merchant.isBlank()) "" else row.note,
                    timestamp = row.date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(), source = Source.IMPORT,
                    originalAmount = original, originalCurrency = if (original != null) row.currency else null,
                )
            }
            dao.putTxs(batch)
            added = batch.size
        }
        onChanged()
        return added
    }

    // ---- Backup ----

    suspend fun snapshot(): BackupFile = db.withTransaction {
        BackupFile(
            createdAt = System.currentTimeMillis(), settings = settings.current(),
            accounts = dao.accounts(), categories = dao.categories(), transactions = dao.txs(), rules = dao.rules(),
            cards = dao.cards(), budgets = dao.budgets(), recurring = dao.recurring(), rates = dao.rates(),
            goals = dao.goals(), debts = dao.debts(), debtPayments = dao.debtPayments(),
        )
    }

    /** Replaces everything in one database transaction, so a failure leaves current data untouched. */
    suspend fun restore(file: BackupFile) {
        db.withTransaction {
            dao.clearAccounts(); dao.clearCategories(); dao.clearTxs(); dao.clearRules(); dao.clearCards(); dao.clearBudgets()
            dao.clearRecurring(); dao.clearRates(); dao.clearGoals(); dao.clearDebts(); dao.clearDebtPayments(); dao.clearCaptured()
            dao.putAccounts(file.accounts); dao.putCategories(file.categories); dao.putTxs(file.transactions); dao.putRules(file.rules)
            dao.putCards(file.cards); dao.putBudgets(file.budgets); dao.putRecurringAll(file.recurring); dao.putRates(file.rates)
            dao.putGoals(file.goals); dao.putDebts(file.debts); dao.putDebtPayments(file.debtPayments)
        }
        // The app lock belongs to this phone, so it is not taken from the file.
        settings.update { now ->
            file.settings.copy(
                onboarded = true, lockEnabled = now.lockEnabled, biometric = now.biometric,
                lockDelaySeconds = now.lockDelaySeconds, theme = now.theme,
            )
        }
        onChanged()
    }
}
