package com.kadraa.novka

import com.kadraa.novka.domain.AmountFinder
import com.kadraa.novka.domain.BudgetCalculator
import com.kadraa.novka.domain.Candidate
import com.kadraa.novka.domain.CategoryGuesser
import com.kadraa.novka.domain.Csv
import com.kadraa.novka.domain.CsvImporter
import com.kadraa.novka.domain.CurrencyConverter
import com.kadraa.novka.domain.Direction
import com.kadraa.novka.domain.DuplicateMatcher
import com.kadraa.novka.domain.Frequency
import com.kadraa.novka.domain.Match
import com.kadraa.novka.domain.MerchantRuleInfo
import com.kadraa.novka.domain.Money
import com.kadraa.novka.domain.ParseResult
import com.kadraa.novka.domain.ParsedPayment
import com.kadraa.novka.domain.PaymentKind
import com.kadraa.novka.domain.PromptMode
import com.kadraa.novka.domain.RaiffeisenParser
import com.kadraa.novka.domain.RecurrenceCalculator
import com.kadraa.novka.domain.Source
import com.kadraa.novka.domain.WalletParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MoneyTest {
    @Test fun decimalsPerCurrency() {
        assertEquals(0, Money.decimals("JPY"))
        assertEquals(2, Money.decimals("BAM"))
        assertEquals(3, Money.decimals("KWD"))
    }

    @Test fun parsesLocalAndEnglishStyles() {
        assertEquals(123456L, Money.parseLoose("1.234,56", "BAM"))
        assertEquals(123456L, Money.parseLoose("1,234.56", "BAM"))
        assertEquals(9300L, Money.parseLoose("93.00", "EUR"))
        assertEquals(9300L, Money.parseLoose("93,00", "EUR"))
        assertEquals(123400L, Money.parseLoose("1.234", "BAM"))
        assertEquals(1234L, Money.parseLoose("1.234", "KWD"))
        assertEquals(1250L, Money.parseLoose("12,5", "BAM"))
        assertEquals(1500L, Money.parseLoose("1 500", "JPY"))
        assertEquals(-500L, Money.parseLoose("-5", "BAM"))
        assertEquals(100000000L, Money.parseLoose("1.000.000", "BAM"))
        assertNull(Money.parseLoose("abc", "BAM"))
        assertNull(Money.parseLoose("", "BAM"))
    }

    @Test fun formats() {
        assertEquals("1,234.50 KM", Money.format(123450, "BAM", Locale.US))
        assertEquals("500 JPY", Money.format(500, "JPY", Locale.US))
        assertEquals("1.500 KWD", Money.format(1500, "KWD", Locale.US))
        assertEquals("12.50", Money.plain(1250, "BAM"))
    }

    @Test fun findsAmountsWithCurrency() {
        assertEquals(listOf(9300L to "EUR"), AmountFinder.findAll("karticom *4321. Iznos 93.00 EUR. 20260101 10:00:00.").map { it.minor to it.currency })
        assertEquals(listOf(1250L to "BAM"), AmountFinder.findAll("12,50 KM with Visa ••1234").map { it.minor to it.currency })
        assertEquals(listOf(500L to "USD"), AmountFinder.findAll("$5.00 with Mastercard •••• 4321").map { it.minor to it.currency })
        assertEquals(listOf(5000L to "BAM"), AmountFinder.findAll("ATM 50,00 KM").map { it.minor to it.currency })
        assertTrue(AmountFinder.findAll("Vas kod je 123456").isEmpty())
    }
}

class ParserTest {
    private val wallet = WalletParser()
    private val bank = RaiffeisenParser()

    private fun payment(result: ParseResult): ParsedPayment = (result as ParseResult.Payment).payment

    @Test fun walletPurchase() {
        val p = payment(wallet.parse("BINGO 142 SARAJEVO", "BAM 23.40 with Visa ••4321"))
        assertEquals(ParsedPayment(2340, "BAM", "BINGO 142 SARAJEVO", "4321", Direction.OUT, PaymentKind.PURCHASE), p)
    }

    @Test fun walletLocalisedPurchase() {
        val p = payment(wallet.parse("Konzum", "12,50 KM s karticom Mastercard •••• 1234"))
        assertEquals(1250L, p.amountMinor)
        assertEquals("BAM", p.currency)
        assertEquals("1234", p.cardLast4)
    }

    @Test fun walletRefundAndDeclined() {
        assertEquals(PaymentKind.REFUND, payment(wallet.parse("Zara", "Refund of €20.00 to Visa ••4321")).kind)
        assertEquals(Direction.IN, payment(wallet.parse("Zara", "Refund of €20.00 to Visa ••4321")).direction)
        assertEquals(PaymentKind.DECLINED, payment(wallet.parse("Zara", "€20.00 payment declined, Visa ••4321")).kind)
    }

    @Test fun walletIgnoresAdverts() {
        assertEquals(ParseResult.Ignored, wallet.parse("Google Wallet", "Add a card to pay with your phone"))
    }

    @Test fun bankConfirmationCodeMessage() {
        val text = "135790 je lozinka za potvrdu zaduzenja vaseg racuna u korist Example Store karticom *4321. Iznos 93.00 EUR. 20260101 10:00:00."
        val p = payment(bank.parse("Raiffeisen", text))
        assertEquals(ParsedPayment(9300, "EUR", "Example Store", "4321", Direction.OUT, PaymentKind.PENDING_AUTH), p)
    }

    @Test fun bankRedactsCodes() {
        val text = "135790 je lozinka za potvrdu zaduzenja. Iznos 93.00 EUR."
        assertFalse(RaiffeisenParser.redact(text).contains("135790"))
        assertFalse(RaiffeisenParser.redact("Vasa lozinka je: 55123").contains("55123"))
        assertTrue(RaiffeisenParser.redact(text).contains("93.00 EUR"))
    }

    @Test fun bankCardPurchaseWithBalance() {
        val p = payment(bank.parse("Raiffeisen", "Placanje karticom *4321 na prodajnom mjestu BINGO 142 SARAJEVO. Iznos 23,40 KM. Raspolozivo stanje 1.540,10 KM."))
        assertEquals(2340L, p.amountMinor)
        assertEquals("BINGO 142 SARAJEVO", p.merchant)
        assertEquals(PaymentKind.PURCHASE, p.kind)
    }

    @Test fun bankSkipsBalanceWhenItComesFirst() {
        val p = payment(bank.parse("Raiffeisen", "Raspolozivo stanje 1.540,10 KM. Kupovina karticom *4321 kod Konzum 5,00 KM"))
        assertEquals(500L, p.amountMinor)
    }

    @Test fun bankAtmIncomingDeclined() {
        assertEquals(PaymentKind.ATM, payment(bank.parse("", "Isplata na bankomat karticom *4321. Iznos 100,00 KM.")).kind)
        val incoming = payment(bank.parse("", "Uplata na vas racun. Iznos 1.850,00 KM."))
        assertEquals(PaymentKind.INCOMING, incoming.kind)
        assertEquals(Direction.IN, incoming.direction)
        assertEquals(185000L, incoming.amountMinor)
        assertEquals(PaymentKind.DECLINED, payment(bank.parse("", "Transakcija karticom *4321 odbijena. Iznos 10,00 KM.")).kind)
    }

    @Test fun bankIgnoresMessagesWithoutAmounts() {
        assertEquals(ParseResult.Ignored, bank.parse("Raiffeisen", "Postovani, nova verzija aplikacije je dostupna."))
        assertEquals(ParseResult.Ignored, bank.parse("Raiffeisen", "123456 je vas kod za prijavu."))
    }
}

class DuplicateMatcherTest {
    private val payment = ParsedPayment(2340, "BAM", "Bingo", "4321", Direction.OUT, PaymentKind.PURCHASE)
    private fun candidate(source: Source, ago: Long, merchant: String = "BINGO 142", seen: Source? = null, pending: Boolean = false, card: String? = "4321") =
        Candidate(7, source, seen, 2340, "BAM", card, 1, 1_000_000 - ago, merchant, pending)

    @Test fun sameSourceRepostIsDropped() {
        assertEquals(Match.Repost(7), DuplicateMatcher.match(payment, Source.WALLET, 1, 1_000_000, listOf(candidate(Source.WALLET, 30_000, "Bingo"))))
    }

    @Test fun otherSourceWithinFiveMinutesMerges() {
        assertEquals(Match.Merge(7), DuplicateMatcher.match(payment, Source.RAIFFEISEN, 1, 1_000_000, listOf(candidate(Source.WALLET, 4 * 60_000))))
    }

    @Test fun otherSourceTooLateIsNew() {
        assertEquals(Match.None, DuplicateMatcher.match(payment, Source.RAIFFEISEN, 1, 1_000_000, listOf(candidate(Source.WALLET, 6 * 60_000))))
    }

    @Test fun twoRealPurchasesFromSameSourceAreKept() {
        assertEquals(Match.None, DuplicateMatcher.match(payment, Source.WALLET, 1, 1_000_000, listOf(candidate(Source.WALLET, 3 * 60_000, "Bingo"))))
    }

    @Test fun alreadyMergedIsNotMergedAgain() {
        assertEquals(Match.None, DuplicateMatcher.match(payment, Source.RAIFFEISEN, 1, 1_000_000, listOf(candidate(Source.WALLET, 60_000, seen = Source.RAIFFEISEN))))
    }

    @Test fun differentAmountNeverMatches() {
        assertEquals(Match.None, DuplicateMatcher.match(payment.copy(amountMinor = 2341), Source.RAIFFEISEN, 1, 1_000_000, listOf(candidate(Source.WALLET, 1000))))
    }

    @Test fun differentCardAndAccountDoesNotMerge() {
        assertEquals(Match.None, DuplicateMatcher.match(payment.copy(cardLast4 = "1111"), Source.RAIFFEISEN, 2, 1_000_000, listOf(candidate(Source.WALLET, 1000))))
    }

    @Test fun bankConfirmationFollowsCodeMessage() {
        val pending = candidate(Source.RAIFFEISEN, 8 * 60_000, "Example Store", pending = true)
        assertEquals(Match.ConfirmsPending(7), DuplicateMatcher.match(payment.copy(merchant = "EXAMPLE"), Source.RAIFFEISEN, 1, 1_000_000, listOf(pending)))
    }
}

class CategoryGuesserTest {
    @Test fun normalises() {
        assertEquals("bingo", CategoryGuesser.normalise("BINGO 142 SARAJEVO"))
        assertEquals("bingo", CategoryGuesser.normalise("Bingo d.o.o."))
        assertEquals("cevabdzinica zeljo", CategoryGuesser.normalise("Ćevabdžinica Željo"))
    }

    @Test fun learnedRuleBeatsKeyword() {
        val rules = listOf(MerchantRuleInfo("bingo", 99, 2))
        assertEquals(listOf(99L, 5L, 1L), CategoryGuesser.guess("BINGO 142 SARAJEVO", rules, mapOf("groceries" to 5L), listOf(1L, 2L)))
    }

    @Test fun fuzzyRuleMatchesBranch() {
        val rules = listOf(MerchantRuleInfo("konzum", 42, 1))
        assertEquals(42L, CategoryGuesser.guess("Konzum Maxi K104", rules, emptyMap(), emptyList()).first())
    }

    @Test fun keywordThenMostUsed() {
        assertEquals(listOf(8L, 1L, 2L), CategoryGuesser.guess("OMV 2231", emptyList(), mapOf("fuel" to 8L), listOf(1L, 2L, 3L)))
        assertEquals(listOf(1L, 2L, 3L), CategoryGuesser.guess("Nepoznato", emptyList(), mapOf("fuel" to 8L), listOf(1L, 2L, 3L)))
        assertFalse(CategoryGuesser.hasBasis("Nepoznato", emptyList()))
    }

    @Test fun promptModes() {
        assertTrue(CategoryGuesser.shouldPrompt(PromptMode.UNTIL_CONFIDENT, MerchantRuleInfo("a", 1, 2)))
        assertFalse(CategoryGuesser.shouldPrompt(PromptMode.UNTIL_CONFIDENT, MerchantRuleInfo("a", 1, 3)))
        assertTrue(CategoryGuesser.shouldPrompt(PromptMode.ALWAYS, MerchantRuleInfo("a", 1, 9)))
        assertFalse(CategoryGuesser.shouldPrompt(PromptMode.NEVER, null))
    }
}

class CalculatorTest {
    @Test fun convertsAcrossDecimalPlaces() {
        val rates = mapOf("EUR" to 1.0, "BAM" to 1.95583, "JPY" to 160.0, "KWD" to 0.33)
        assertEquals(1.95583, CurrencyConverter.rate("EUR", "BAM", rates)!!, 1e-9)
        assertEquals(18189L, CurrencyConverter.convert(9300, "EUR", "BAM", 1.95583))
        assertEquals(1600L, CurrencyConverter.convert(1000, "EUR", "JPY", 160.0))
        assertEquals(3300L, CurrencyConverter.convert(1000, "EUR", "KWD", 0.33))
        assertEquals(625L, CurrencyConverter.convert(1000, "JPY", "EUR", 1 / 160.0))
        assertEquals(500L, CurrencyConverter.convert(500, "BAM", "BAM", 1.0))
        assertNull(CurrencyConverter.rate("EUR", "XXX", rates))
    }

    @Test fun budgetStatus() {
        val s = BudgetCalculator.status(limit = 30000, spent = 12000, today = LocalDate.of(2026, 11, 21))
        assertEquals(18000L, s.remaining)
        assertEquals(1800L, s.dailyAllowance)
        assertEquals(0, s.level)
        assertEquals(80, BudgetCalculator.status(10000, 8000, LocalDate.of(2026, 11, 1)).level)
        val over = BudgetCalculator.status(10000, 12000, LocalDate.of(2026, 11, 30))
        assertEquals(100, over.level)
        assertEquals(0L, over.dailyAllowance)
        assertEquals(-2000L, over.remaining)
    }

    @Test fun monthlyRecurrenceKeepsMonthEnd() {
        val jan31 = LocalDate.of(2027, 1, 31)
        val feb = RecurrenceCalculator.next(jan31, Frequency.MONTHLY, 1, 31)
        assertEquals(LocalDate.of(2027, 2, 28), feb)
        assertEquals(LocalDate.of(2027, 3, 31), RecurrenceCalculator.next(feb, Frequency.MONTHLY, 1, 31))
        assertEquals(LocalDate.of(2028, 2, 29), RecurrenceCalculator.next(LocalDate.of(2027, 2, 28), Frequency.YEARLY, 1, 29))
    }

    @Test fun missedRunsAreAllDue() {
        val due = RecurrenceCalculator.due(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 11, 12), Frequency.MONTHLY, 1, 15, null)
        assertEquals(3, due.dates.size)
        assertEquals(LocalDate.of(2026, 11, 15), due.nextDue)
        assertFalse(due.finished)
    }

    @Test fun recurrenceStopsAtEndDate() {
        val due = RecurrenceCalculator.due(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), Frequency.WEEKLY, 1, 1, LocalDate.of(2026, 11, 10))
        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8)), due.dates)
        assertTrue(due.finished)
    }

    @Test fun nothingDueBeforeDate() {
        assertTrue(RecurrenceCalculator.due(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 12), Frequency.DAILY, 1, 1, null).dates.isEmpty())
    }
}

class CsvTest {
    @Test fun roundTripsQuotedCells() {
        val rows = listOf(listOf("a", "b,c", "say \"hi\""), listOf("1", "", "line\nbreak"))
        assertEquals(rows, Csv.parse(Csv.write(rows), ','))
    }

    @Test fun neutralisesFormulas() {
        assertTrue(Csv.write(listOf(listOf("=SUM(A1)"))).startsWith("'="))
    }

    @Test fun importsSemicolonStatement() {
        val text = "Datum;Opis;Iznos;Valuta\n12.11.2026;BINGO 142;-23,40;BAM\n13.11.2026;Plata;1.850,00;KM\nloše;x;y;BAM\n"
        val result = CsvImporter.import(text, "EUR")
        assertEquals(2, result.rows.size)
        assertEquals(1, result.skipped)
        assertEquals(-2340L, result.rows[0].amountMinor)
        assertEquals(LocalDate.of(2026, 11, 12), result.rows[0].date)
        assertEquals("BINGO 142", result.rows[0].note)
        assertEquals(185000L, result.rows[1].amountMinor)
        assertEquals("BAM", result.rows[1].currency)
    }

    @Test fun importsDebitCreditColumns() {
        val text = "date,description,debit,credit\n2026-11-12,Rent,500.00,\n2026-11-13,Salary,,2000.00\n"
        val rows = CsvImporter.import(text, "EUR").rows
        assertEquals(listOf(-50000L, 200000L), rows.map { it.amountMinor })
    }

    @Test fun reportsMissingColumns() {
        assertEquals(listOf("date", "amount"), CsvImporter.import("foo,bar\n1,2\n", "EUR").missingColumns)
    }
}
