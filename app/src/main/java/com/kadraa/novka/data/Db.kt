package com.kadraa.novka.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface NovkaDao {
    // Accounts. Balances are derived here and never stored.
    @Query(
        "SELECT a.*, a.openingBalance + COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.accountId = a.id), 0) AS balance " +
            "FROM accounts a ORDER BY a.archived, a.id"
    )
    fun observeAccounts(): Flow<List<AccountWithBalance>>

    @Query(
        "SELECT a.*, a.openingBalance + COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.accountId = a.id), 0) AS balance " +
            "FROM accounts a ORDER BY a.archived, a.id"
    )
    suspend fun accountsWithBalance(): List<AccountWithBalance>

    @Query("SELECT * FROM accounts ORDER BY id") suspend fun accounts(): List<Account>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: Long): Account?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccount(account: Account): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccounts(list: List<Account>)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: Long)
    @Query("DELETE FROM accounts") suspend fun clearAccounts()

    // Categories
    @Query("SELECT * FROM categories ORDER BY id") fun observeCategories(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun categories(): List<Category>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategory(category: Category): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategories(list: List<Category>)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: Long)
    @Query("UPDATE categories SET parentId = NULL WHERE parentId = :id") suspend fun orphanChildren(id: Long)
    @Query("DELETE FROM categories") suspend fun clearCategories()

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC") fun observeTxs(): Flow<List<Tx>>
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC") suspend fun txs(): List<Tx>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun tx(id: Long): Tx?
    @Query("SELECT * FROM transactions WHERE transferPairId = :pair") suspend fun txsOfPair(pair: String): List<Tx>
    @Query("SELECT * FROM transactions WHERE timestamp >= :since ORDER BY timestamp DESC") suspend fun txsSince(since: Long): List<Tx>
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to") suspend fun txsBetween(from: Long, to: Long): List<Tx>
    @Query("SELECT * FROM transactions WHERE rate = 0") suspend fun txsWithoutRate(): List<Tx>
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId") suspend fun countForAccount(accountId: Long): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId") suspend fun countForCategory(categoryId: Long): Int
    @Query("UPDATE transactions SET accountId = :to WHERE accountId = :from") suspend fun moveTxsToAccount(from: Long, to: Long)
    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from") suspend fun moveTxsToCategory(from: Long, to: Long?)
    @Query(
        "SELECT categoryId FROM transactions WHERE categoryId IS NOT NULL AND type = :type " +
            "GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT :limit"
    )
    suspend fun mostUsedCategories(type: String, limit: Int): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTx(tx: Tx): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTxs(list: List<Tx>)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTx(id: Long)
    @Query("DELETE FROM transactions") suspend fun clearTxs()

    // Merchant rules
    @Query("SELECT * FROM merchant_rules") suspend fun rules(): List<MerchantRule>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRule(rule: MerchantRule): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRules(list: List<MerchantRule>)
    @Query("DELETE FROM merchant_rules WHERE merchant = :merchant AND categoryId != :keep") suspend fun deleteOtherRules(merchant: String, keep: Long)
    @Query("DELETE FROM merchant_rules WHERE categoryId = :categoryId") suspend fun deleteRulesForCategory(categoryId: Long)
    @Query("DELETE FROM merchant_rules") suspend fun clearRules()

    // Card mappings
    @Query("SELECT * FROM card_mappings") fun observeCards(): Flow<List<CardMapping>>
    @Query("SELECT * FROM card_mappings") suspend fun cards(): List<CardMapping>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCard(card: CardMapping)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCards(list: List<CardMapping>)
    @Query("DELETE FROM card_mappings WHERE cardLast4 = :card") suspend fun deleteCard(card: String)
    @Query("DELETE FROM card_mappings WHERE accountId = :accountId") suspend fun deleteCardsForAccount(accountId: Long)
    @Query("DELETE FROM card_mappings") suspend fun clearCards()

    // Budgets
    @Query("SELECT * FROM budgets") fun observeBudgets(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<Budget>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudget(budget: Budget): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgets(list: List<Budget>)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: Long)
    @Query("DELETE FROM budgets WHERE categoryId = :categoryId") suspend fun deleteBudgetsForCategory(categoryId: Long)
    @Query("DELETE FROM budgets") suspend fun clearBudgets()

    // Recurring
    @Query("SELECT * FROM recurring_rules ORDER BY nextDue") fun observeRecurring(): Flow<List<RecurringRule>>
    @Query("SELECT * FROM recurring_rules") suspend fun recurring(): List<RecurringRule>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecurring(rule: RecurringRule): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecurringAll(list: List<RecurringRule>)
    @Query("DELETE FROM recurring_rules WHERE id = :id") suspend fun deleteRecurring(id: Long)
    @Query("DELETE FROM recurring_rules WHERE accountId = :accountId") suspend fun deleteRecurringForAccount(accountId: Long)
    @Query("DELETE FROM recurring_rules") suspend fun clearRecurring()

    // Exchange rates
    @Query("SELECT * FROM exchange_rates") fun observeRates(): Flow<List<ExchangeRate>>
    @Query("SELECT * FROM exchange_rates") suspend fun rates(): List<ExchangeRate>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRates(list: List<ExchangeRate>)
    @Query("DELETE FROM exchange_rates") suspend fun clearRates()

    // Captured notifications
    @Query("SELECT * FROM captured_notifications ORDER BY postedAt DESC LIMIT 200") fun observeCaptured(): Flow<List<Captured>>
    @Insert suspend fun putCaptured(captured: Captured): Long
    @Query("UPDATE captured_notifications SET status = :status, txId = :txId WHERE id = :id")
    suspend fun setCapturedStatus(id: Long, status: String, txId: Long?)
    @Query("DELETE FROM captured_notifications WHERE id NOT IN (SELECT id FROM captured_notifications ORDER BY postedAt DESC LIMIT 200)")
    suspend fun pruneCaptured()
    @Query("DELETE FROM captured_notifications WHERE id = :id") suspend fun deleteCaptured(id: Long)
    @Query("DELETE FROM captured_notifications") suspend fun clearCaptured()

    // Goals
    @Query("SELECT * FROM goals ORDER BY id") fun observeGoals(): Flow<List<Goal>>
    @Query("SELECT * FROM goals") suspend fun goals(): List<Goal>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGoal(goal: Goal): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGoals(list: List<Goal>)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: Long)
    @Query("UPDATE goals SET accountId = NULL WHERE accountId = :accountId") suspend fun unlinkGoals(accountId: Long)
    @Query("DELETE FROM goals") suspend fun clearGoals()

    // Debts
    @Query("SELECT * FROM debts ORDER BY settled, createdAt DESC") fun observeDebts(): Flow<List<Debt>>
    @Query("SELECT * FROM debts") suspend fun debts(): List<Debt>
    @Query("SELECT * FROM debts WHERE id = :id") suspend fun debt(id: Long): Debt?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDebt(debt: Debt): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDebts(list: List<Debt>)
    @Query("DELETE FROM debts WHERE id = :id") suspend fun deleteDebt(id: Long)
    @Query("DELETE FROM debts") suspend fun clearDebts()

    @Query("SELECT * FROM debt_payments ORDER BY timestamp DESC") fun observeDebtPayments(): Flow<List<DebtPayment>>
    @Query("SELECT * FROM debt_payments") suspend fun debtPayments(): List<DebtPayment>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_payments WHERE debtId = :debtId") suspend fun paidForDebt(debtId: Long): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDebtPayment(payment: DebtPayment): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDebtPayments(list: List<DebtPayment>)
    @Query("DELETE FROM debt_payments WHERE debtId = :debtId") suspend fun deletePaymentsForDebt(debtId: Long)
    @Query("DELETE FROM debt_payments") suspend fun clearDebtPayments()
}

@Database(
    entities = [
        Account::class, Category::class, Tx::class, MerchantRule::class, CardMapping::class, Budget::class,
        RecurringRule::class, ExchangeRate::class, Captured::class, Goal::class, Debt::class, DebtPayment::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NovkaDb : RoomDatabase() {
    abstract fun dao(): NovkaDao

    companion object {
        // Later versions add Room migrations here. Destructive migration is never enabled.
        fun create(context: Context): NovkaDb =
            Room.databaseBuilder(context, NovkaDb::class.java, "novka.db").build()
    }
}
