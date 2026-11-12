package com.kadraa.novka.domain

private val cardRegex = Regex("""(?:[•·*•]{1,}|[.]{2,}|[xX]{2,})\s?(\d{4})(?!\d)""")

private fun containsAny(text: String, vararg words: String) = words.any { text.contains(it) }

/** Google Wallet tap-to-pay notifications: the title is the merchant, the text has amount and card. */
class WalletParser : NotificationParser {
    private val genericTitles = setOf("google wallet", "google pay", "wallet", "gpay", "google novcanik")

    override fun parse(title: String, text: String): ParseResult {
        val folded = fold("$title\n$text")
        val amount = AmountFinder.findAll(text).firstOrNull()
            ?: AmountFinder.findAll(title).firstOrNull()
            ?: return ParseResult.Ignored
        val card = cardRegex.find(text)?.groupValues?.get(1) ?: cardRegex.find(title)?.groupValues?.get(1)

        val kind = when {
            containsAny(folded, "declined", "odbijen", "nije uspjel", "failed", "neuspjes") -> PaymentKind.DECLINED
            containsAny(folded, "refund", "povrat") -> PaymentKind.REFUND
            else -> PaymentKind.PURCHASE
        }
        val merchant = title.trim().takeIf { it.isNotEmpty() && fold(it) !in genericTitles && AmountFinder.findAll(it).isEmpty() }
            ?: Regex("""(?:\bat|\bkod|\bu)\s+([^.,\n]{2,40})""").find(text)?.groupValues?.get(1)?.trim()

        return ParseResult.Payment(
            ParsedPayment(
                amountMinor = amount.minor,
                currency = amount.currency,
                merchant = merchant,
                cardLast4 = card,
                direction = if (kind == PaymentKind.REFUND) Direction.IN else Direction.OUT,
                kind = kind,
            )
        )
    }
}

/**
 * Raiffeisen Bank BiH messages, which arrive both from the banking app and as SMS. Wording is
 * Bosnian and varies by message type, so this reads keywords rather than one fixed template.
 */
class RaiffeisenParser : NotificationParser {
    private val merchantPatterns = listOf(
        Regex("""u korist\s+(.+?)\s+kartic""", RegexOption.IGNORE_CASE),
        Regex("""prodajnom mjestu:?\s+(.+?)(?:[.,;]|\s+iznos|\s+dana|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:mjesto|lokacija|trgovac):?\s+(.+?)(?:[.,;]|\s+iznos|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bkod\s+(.+?)(?:[.,;]|\s+u iznosu|\s+iznos|\s+dana|$)""", RegexOption.IGNORE_CASE),
    )
    private val balanceWords = Regex("""(raspoloziv\w*|stanje|saldo)\D{0,25}$""")

    override fun parse(title: String, text: String): ParseResult {
        val folded = fold(text)
        val amounts = AmountFinder.findAll(text)
        if (amounts.isEmpty()) return ParseResult.Ignored

        val afterIznos = folded.indexOf("iznos").takeIf { it >= 0 }
        val amount = amounts.firstOrNull { afterIznos != null && it.start >= afterIznos }
            ?: amounts.firstOrNull { !balanceWords.containsMatchIn(folded.substring(0, it.start)) }
            ?: amounts.first()

        val card = cardRegex.find(text)?.groupValues?.get(1)
            ?: Regex("""kartic\w*\s+(?:br\.?\s*)?(\d{4})(?!\d)""").find(folded)?.groupValues?.get(1)

        val kind = when {
            containsAny(folded, "odbijen", "neuspjes", "nije uspjel", "declined") -> PaymentKind.DECLINED
            containsAny(folded, "lozinka za potvrdu", "kod za potvrdu", "lozinka za autorizaciju") -> PaymentKind.PENDING_AUTH
            containsAny(folded, "povrat", "storno", "refund") -> PaymentKind.REFUND
            containsAny(folded, "bankomat", " atm", "isplata gotovine", "podizanje gotovine") -> PaymentKind.ATM
            containsAny(folded, "uplata", "priliv", "odobren", "primili ste", "u korist vaseg racuna") -> PaymentKind.INCOMING
            containsAny(folded, "zaduzen", "placanj", "kupovin", "trosak", "transakcij", "isplata", "kartic") || card != null ->
                PaymentKind.PURCHASE
            else -> return ParseResult.Failed
        }

        val merchant = merchantPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 2..60 }
        }

        val incoming = kind == PaymentKind.REFUND || kind == PaymentKind.INCOMING
        return ParseResult.Payment(
            ParsedPayment(
                amountMinor = amount.minor,
                currency = amount.currency,
                merchant = merchant,
                cardLast4 = card,
                direction = if (incoming) Direction.IN else Direction.OUT,
                kind = kind,
            )
        )
    }

    companion object {
        private val codeBefore = Regex("""\b\d{4,8}\b(?=\s+je\s+(?:lozinka|kod|sifra))""", RegexOption.IGNORE_CASE)
        private val codeAfter = Regex("""((?:lozinka|kod|sifra|code|otp)\s*(?:je|is)?:?\s*)\d{4,8}\b""", RegexOption.IGNORE_CASE)

        /** Removes one-time passwords so they are never written to the database. */
        fun redact(text: String): String =
            codeAfter.replace(codeBefore.replace(text, "••••••")) { it.groupValues[1] + "••••••" }
    }
}
