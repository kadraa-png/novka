package com.kadraa.novka.domain

enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class Source { MANUAL, WALLET, RAIFFEISEN, RECURRING, IMPORT }

enum class AccountType { CASH, BANK, SAVINGS, CARD }

enum class CategoryKind { EXPENSE, INCOME }

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class PromptMode { ALWAYS, UNTIL_CONFIDENT, NEVER }

enum class DebtDirection { I_OWE, OWED_TO_ME }

enum class CaptureStatus { PARSED, MERGED, FAILED, IGNORED }

enum class Direction { OUT, IN }

enum class PaymentKind { PURCHASE, REFUND, ATM, INCOMING, DECLINED, PENDING_AUTH }

/** What a parser extracted from one notification. [amountMinor] is always positive. */
data class ParsedPayment(
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val cardLast4: String?,
    val direction: Direction,
    val kind: PaymentKind,
)

sealed interface ParseResult {
    data class Payment(val payment: ParsedPayment) : ParseResult

    /** Not a payment at all (advert, login code). Never shown to the user. */
    data object Ignored : ParseResult

    /** Looks like a payment but could not be read. Listed under "Couldn't read". */
    data object Failed : ParseResult
}

interface NotificationParser {
    fun parse(title: String, text: String): ParseResult
}
