package com.kadraa.novka.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kadraa.novka.domain.AccountType
import com.kadraa.novka.domain.CaptureStatus
import com.kadraa.novka.domain.CategoryKind
import com.kadraa.novka.domain.DebtDirection
import com.kadraa.novka.domain.Frequency
import com.kadraa.novka.domain.Source
import com.kadraa.novka.domain.TxType
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalance: Long = 0,
    val colour: Long,
    val archived: Boolean = false,
)

data class AccountWithBalance(@Embedded val account: Account, val balance: Long)

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val colour: Long,
    val kind: CategoryKind,
    val parentId: Long? = null,
    val archived: Boolean = false,
    /** Stable name of a built-in category, used by merchant keyword guessing. */
    val key: String? = null,
)

/**
 * [amount] is signed and always in the account's currency: negative leaves the account.
 * A payment made in another currency keeps what was actually charged in the original fields.
 * [mainAmount] is [amount] in the main currency at [rate], fixed on the day of the transaction.
 */
@Serializable
@Entity(tableName = "transactions", indices = [Index("accountId"), Index("timestamp"), Index("categoryId")])
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Long,
    val currency: String,
    val rate: Double,
    val mainAmount: Long,
    val type: TxType,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val timestamp: Long,
    val source: Source = Source.MANUAL,
    val needsReview: Boolean = false,
    val transferPairId: String? = null,
    val alsoSeenBy: Source? = null,
    val cardLast4: String? = null,
    val pendingAuth: Boolean = false,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
)

@Serializable
@Entity(tableName = "merchant_rules", indices = [Index("merchant")])
data class MerchantRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val categoryId: Long,
    val count: Int,
)

@Serializable
@Entity(tableName = "card_mappings")
data class CardMapping(@PrimaryKey val cardLast4: String, val accountId: Long)

/** [monthlyLimit] is in the main currency. */
@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val monthlyLimit: Long,
)

/** [amount] is signed like [Tx.amount]. Dates are ISO yyyy-MM-dd. */
@Serializable
@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Long,
    val type: TxType,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val frequency: Frequency,
    val interval: Int = 1,
    val anchorDay: Int,
    val nextDue: String,
    val endDate: String? = null,
    val active: Boolean = true,
)

/** Units of [code] per one euro. */
@Serializable
@Entity(tableName = "exchange_rates")
data class ExchangeRate(@PrimaryKey val code: String, val rate: Double, val fetchedAt: Long, val manual: Boolean = false)

@Entity(tableName = "captured_notifications")
data class Captured(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: Source,
    val title: String,
    val text: String,
    val postedAt: Long,
    val status: CaptureStatus,
    val txId: Long? = null,
)

/** With [accountId] set, progress is that account's balance; otherwise [saved] is adjusted by hand. */
@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "flag",
    val target: Long,
    val currency: String,
    val deadline: String? = null,
    val accountId: Long? = null,
    val saved: Long = 0,
)

@Serializable
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val direction: DebtDirection,
    val amount: Long,
    val currency: String,
    val note: String = "",
    val createdAt: Long,
    val dueDate: String? = null,
    val settled: Boolean = false,
)

@Serializable
@Entity(tableName = "debt_payments", indices = [Index("debtId")])
data class DebtPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amount: Long,
    val timestamp: Long,
)
