package com.kadraa.novka.domain

import kotlin.math.abs

/** An existing transaction, reduced to what duplicate matching needs. */
data class Candidate(
    val id: Long,
    val source: Source,
    val alsoSeenBy: Source?,
    val amountAbs: Long,
    val currency: String,
    val cardLast4: String?,
    val accountId: Long,
    val timestamp: Long,
    val merchant: String,
    val pendingAuth: Boolean,
)

sealed interface Match {
    data object None : Match

    /** The same notification posted again. Drop it. */
    data class Repost(val id: Long) : Match

    /** The other app reporting the same payment. Fill gaps in the existing transaction. */
    data class Merge(val id: Long) : Match

    /** The bank confirming a payment first seen as a confirmation-code message. */
    data class ConfirmsPending(val id: Long) : Match
}

object DuplicateMatcher {
    const val REPOST_WINDOW_MS = 2 * 60_000L
    const val MERGE_WINDOW_MS = 5 * 60_000L
    const val PENDING_WINDOW_MS = 15 * 60_000L

    fun match(
        payment: ParsedPayment,
        source: Source,
        accountId: Long?,
        timestamp: Long,
        candidates: List<Candidate>,
    ): Match {
        val sameMoney = candidates.filter { it.amountAbs == payment.amountMinor && it.currency == payment.currency }
        val merchant = CategoryGuesser.normalise(payment.merchant.orEmpty())

        for (c in sameMoney.filter { it.source == source }) {
            val gap = abs(timestamp - c.timestamp)
            val sameCard = c.cardLast4 == payment.cardLast4
            if (gap <= REPOST_WINDOW_MS && sameCard && CategoryGuesser.normalise(c.merchant) == merchant) {
                return Match.Repost(c.id)
            }
            if (c.pendingAuth && payment.kind != PaymentKind.PENDING_AUTH && gap <= PENDING_WINDOW_MS &&
                (sameCard || c.cardLast4 == null || payment.cardLast4 == null)
            ) {
                return Match.ConfirmsPending(c.id)
            }
        }

        // Merchant names are not compared: the two apps word them differently.
        val other = sameMoney.firstOrNull { c ->
            c.source != source && c.alsoSeenBy == null &&
                (c.source == Source.WALLET || c.source == Source.RAIFFEISEN) &&
                abs(timestamp - c.timestamp) <= MERGE_WINDOW_MS &&
                sameCardOrAccount(c, payment.cardLast4, accountId)
        }
        return if (other != null) Match.Merge(other.id) else Match.None
    }

    private fun sameCardOrAccount(c: Candidate, card: String?, accountId: Long?): Boolean = when {
        card != null && c.cardLast4 != null -> card == c.cardLast4 || accountId == c.accountId
        else -> accountId == null || accountId == c.accountId
    }
}

data class MerchantRuleInfo(val merchant: String, val categoryId: Long, val count: Int)

object CategoryGuesser {
    const val CONFIDENT_AFTER = 3

    private val locations = setOf(
        "sarajevo", "mostar", "tuzla", "zenica", "banja", "luka", "bihac", "brcko", "ilidza", "travnik",
        "ba", "bih", "bosna", "doo", "dd", "d", "o",
    )

    /** "BINGO 142 SARAJEVO" and "Bingo d.o.o." both become "bingo". */
    fun normalise(merchant: String): String {
        val tokens = fold(merchant)
            .replace(Regex("[^a-z0-9& ]"), " ")
            .split(' ')
            .filter { it.isNotBlank() && !it.all(Char::isDigit) }
        val trimmed = tokens.dropLastWhile { it in locations }
        return (trimmed.ifEmpty { tokens }).joinToString(" ")
    }

    /** Category ids, most likely first, without repeats. */
    fun guess(
        merchant: String,
        rules: List<MerchantRuleInfo>,
        categoryIdByKey: Map<String, Long>,
        mostUsed: List<Long>,
        limit: Int = 3,
    ): List<Long> {
        val name = normalise(merchant)
        val ranked = mutableListOf<Long>()
        if (name.isNotEmpty()) {
            rules.filter { it.merchant == name }.sortedByDescending { it.count }.mapTo(ranked) { it.categoryId }
            rules.filter { it.merchant != name && fuzzy(it.merchant, name) }
                .sortedByDescending { it.count }.mapTo(ranked) { it.categoryId }
            DefaultCategories.keywordKey(name)?.let { key -> categoryIdByKey[key]?.let(ranked::add) }
        }
        ranked += mostUsed
        return ranked.distinct().take(limit)
    }

    /** True when a learned rule or a built-in keyword produced the first guess. */
    fun hasBasis(merchant: String, rules: List<MerchantRuleInfo>): Boolean {
        val name = normalise(merchant)
        if (name.isEmpty()) return false
        return rules.any { it.merchant == name || fuzzy(it.merchant, name) } || DefaultCategories.keywordKey(name) != null
    }

    fun ruleFor(merchant: String, rules: List<MerchantRuleInfo>): MerchantRuleInfo? {
        val name = normalise(merchant)
        return rules.filter { it.merchant == name }.maxByOrNull { it.count }
    }

    fun shouldPrompt(mode: PromptMode, rule: MerchantRuleInfo?): Boolean = when (mode) {
        PromptMode.ALWAYS -> true
        PromptMode.NEVER -> false
        PromptMode.UNTIL_CONFIDENT -> (rule?.count ?: 0) < CONFIDENT_AFTER
    }

    private fun fuzzy(a: String, b: String): Boolean {
        if (a.length < 4 || b.length < 4) return false
        if (a.startsWith(b) || b.startsWith(a)) return true
        val first = a.substringBefore(' ')
        return first.length >= 4 && first == b.substringBefore(' ')
    }
}
