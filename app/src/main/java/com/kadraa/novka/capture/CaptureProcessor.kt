package com.kadraa.novka.capture

import com.kadraa.novka.data.Account
import com.kadraa.novka.data.Captured
import com.kadraa.novka.data.Repository
import com.kadraa.novka.data.Tx
import com.kadraa.novka.data.TxDraft
import com.kadraa.novka.domain.AccountType
import com.kadraa.novka.domain.Candidate
import com.kadraa.novka.domain.CaptureStatus
import com.kadraa.novka.domain.CategoryGuesser
import com.kadraa.novka.domain.CategoryKind
import com.kadraa.novka.domain.Direction
import com.kadraa.novka.domain.DuplicateMatcher
import com.kadraa.novka.domain.Match
import com.kadraa.novka.domain.NotificationParser
import com.kadraa.novka.domain.ParseResult
import com.kadraa.novka.domain.ParsedPayment
import com.kadraa.novka.domain.PaymentKind
import com.kadraa.novka.domain.PromptMode
import com.kadraa.novka.domain.RaiffeisenParser
import com.kadraa.novka.domain.Source
import com.kadraa.novka.domain.TxType
import com.kadraa.novka.domain.WalletParser
import com.kadraa.novka.domain.fold
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/** Which apps are read, and how their notifications are recognised. */
object Sources {
    const val WALLET = "com.google.android.apps.walletnfcrel"
    const val PLAY_SERVICES = "com.google.android.gms"
    val raiffeisenApps = setOf("ba.raiffeisenbank.mobile.prod", "ba.raiffeisenbank.mobile")
    private val smsApps = setOf(
        "com.google.android.apps.messaging", "com.android.mms", "com.android.messaging", "com.samsung.android.messaging",
    )

    data class Identified(val source: Source, /** Keep nothing unless a payment is read. */ val strict: Boolean)

    fun identify(packageName: String, title: String, defaultSmsApp: String?): Identified? = when {
        packageName == WALLET -> Identified(Source.WALLET, strict = false)
        packageName == PLAY_SERVICES -> Identified(Source.WALLET, strict = true)
        packageName in raiffeisenApps -> Identified(Source.RAIFFEISEN, strict = false)
        (packageName in smsApps || packageName == defaultSmsApp) && fold(title).contains("raiffeisen") ->
            Identified(Source.RAIFFEISEN, strict = false)
        else -> null
    }
}

/** A saved payment the user should be asked about. */
data class Prompt(val txId: Long, val merchant: String, val amount: Long, val currency: String, val guesses: List<Pair<Long, String>>)

class CaptureProcessor(private val repo: Repository, private val cashAccountName: () -> String) {
    private val mutex = Mutex()
    private val parsers: Map<Source, NotificationParser> = mapOf(Source.WALLET to WalletParser(), Source.RAIFFEISEN to RaiffeisenParser())
    private val dao get() = repo.dao

    suspend fun process(source: Source, strict: Boolean, title: String, rawText: String, postedAt: Long): Prompt? = mutex.withLock {
        val text = RaiffeisenParser.redact(rawText)
        val result = parsers.getValue(source).parse(title, text)
        if (strict && (result !is ParseResult.Payment || result.payment.cardLast4 == null)) return null

        suspend fun record(status: CaptureStatus, txId: Long? = null) {
            dao.putCaptured(Captured(source = source, title = title, text = text, postedAt = postedAt, status = status, txId = txId))
            dao.pruneCaptured()
        }

        val payment = when (result) {
            ParseResult.Ignored -> { record(CaptureStatus.IGNORED); return null }
            ParseResult.Failed -> { record(CaptureStatus.FAILED); return null }
            is ParseResult.Payment -> result.payment
        }
        if (payment.kind == PaymentKind.DECLINED) { record(CaptureStatus.IGNORED); return null }

        val settings = repo.settings.current()
        val accounts = dao.accounts().filter { !it.archived }
        val mapped = payment.cardLast4?.let { card -> dao.cards().firstOrNull { it.cardLast4 == card }?.accountId }
        val account = accounts.firstOrNull { it.id == mapped }
            ?: accounts.firstOrNull { it.id == settings.defaultAccountId }
            ?: accounts.firstOrNull()
            ?: run { record(CaptureStatus.FAILED); return null }

        val candidates = dao.txsSince(postedAt - DuplicateMatcher.PENDING_WINDOW_MS - 60_000)
            .filter { it.source == Source.WALLET || it.source == Source.RAIFFEISEN }
            .map {
                Candidate(it.id, it.source, it.alsoSeenBy, it.originalAmount ?: abs(it.amount), it.originalCurrency ?: it.currency,
                    it.cardLast4, it.accountId, it.timestamp, it.merchant, it.pendingAuth)
            }
        when (val match = DuplicateMatcher.match(payment, source, mapped, postedAt, candidates)) {
            is Match.Repost -> { record(CaptureStatus.IGNORED, match.id); return null }
            is Match.Merge -> { fill(match.id, payment, alsoSeenBy = source); record(CaptureStatus.MERGED, match.id); return null }
            is Match.ConfirmsPending -> { fill(match.id, payment, alsoSeenBy = null); record(CaptureStatus.MERGED, match.id); return null }
            Match.None -> Unit
        }

        // The account is charged in its own currency; what was paid abroad is kept alongside.
        var amount = payment.amountMinor
        var converted = true
        if (payment.currency != account.currency) {
            val value = repo.convert(amount, payment.currency, account.currency)
                ?: run { repo.refreshRates(); repo.convert(amount, payment.currency, account.currency) }
            if (value != null) amount = value else converted = false
        }

        if (payment.kind == PaymentKind.ATM) {
            val cash = accounts.firstOrNull { it.type == AccountType.CASH && it.currency == account.currency }
                ?: Account(name = cashAccountName(), type = AccountType.CASH, currency = account.currency, colour = 0xFF1BAF7A)
                    .let { it.copy(id = repo.saveAccount(it)) }
            val id = repo.saveTx(TxDraft(type = TxType.TRANSFER, accountId = account.id, toAccountId = cash.id, amount = amount, timestamp = postedAt))
            record(CaptureStatus.PARSED, id)
            return null
        }

        val incoming = payment.direction == Direction.IN
        val type = if (incoming) TxType.INCOME else TxType.EXPENSE
        val merchant = payment.merchant.orEmpty()
        val categories = dao.categories().filter { !it.archived }
        val rules = repo.ruleInfos()
        val kind = if (incoming) CategoryKind.INCOME else CategoryKind.EXPENSE
        val guesses = CategoryGuesser.guess(
            merchant, rules, categories.mapNotNull { c -> c.key?.let { it to c.id } }.toMap(), dao.mostUsedCategories(type.name, 3), limit = 6,
        ).mapNotNull { id -> categories.firstOrNull { it.id == id && it.kind == kind } }

        var category = if (CategoryGuesser.hasBasis(merchant, rules)) guesses.firstOrNull()?.id else null
        if (payment.kind == PaymentKind.REFUND) {
            val name = CategoryGuesser.normalise(merchant)
            category = dao.txs().firstOrNull { it.type == TxType.EXPENSE && name.isNotEmpty() && CategoryGuesser.normalise(it.merchant) == name }?.categoryId
                ?: categories.firstOrNull { it.key == "refunds" }?.id
        }
        val ask = payment.kind != PaymentKind.REFUND && settings.promptMode != PromptMode.NEVER &&
            (category == null || CategoryGuesser.shouldPrompt(settings.promptMode, CategoryGuesser.ruleFor(merchant, rules)))

        val signed = if (incoming) amount else -amount
        val (rate, mainAmount) = repo.mainOf(signed, account.currency)
        val foreign = payment.currency != account.currency
        val id = dao.putTx(
            Tx(
                accountId = account.id, amount = signed, currency = account.currency, rate = rate, mainAmount = mainAmount, type = type,
                categoryId = category, merchant = merchant, timestamp = postedAt, source = source,
                needsReview = ask || category == null || !converted, cardLast4 = payment.cardLast4,
                pendingAuth = payment.kind == PaymentKind.PENDING_AUTH,
                originalAmount = if (foreign) payment.amountMinor else null, originalCurrency = if (foreign) payment.currency else null,
            )
        )
        record(CaptureStatus.PARSED, id)
        repo.changed()
        if (!ask) return null
        val ordered = (listOfNotNull(categories.firstOrNull { it.id == category }) + guesses).distinctBy { it.id }
        Prompt(id, merchant, payment.amountMinor, payment.currency, ordered.map { it.id to "${it.icon} ${it.name}" })
    }

    private suspend fun fill(txId: Long, payment: ParsedPayment, alsoSeenBy: Source?) {
        val tx = dao.tx(txId) ?: return
        dao.putTx(
            tx.copy(
                merchant = tx.merchant.ifBlank { payment.merchant.orEmpty() },
                cardLast4 = tx.cardLast4 ?: payment.cardLast4,
                alsoSeenBy = alsoSeenBy ?: tx.alsoSeenBy,
                pendingAuth = if (alsoSeenBy == null) false else tx.pendingAuth,
            )
        )
    }
}
