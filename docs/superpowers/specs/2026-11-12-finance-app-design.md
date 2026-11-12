# Personal Finance App for Android: Design

Date: 2026-11-12
Status: awaiting review

## 1. Purpose

A personal finance app for Android that tracks money across several accounts
and currencies, captures payments automatically from Google Wallet and
Raiffeisen Mobile Banking (Bosnia and Herzegovina) notifications, and shows
where money goes through graphs, monthly totals and budgets.

It is built for the owner's own phone first, to a standard fit for publishing
on the Play Store later.

Working name: **Ledger** (package `com.kadraa.ledger`). The name can change
before publishing without affecting the design.

### Success criteria

- A tap-to-pay purchase appears in the app within seconds, without opening it,
  and is categorised with one tap from a notification.
- The same purchase reported by both Wallet and Raiffeisen is recorded once.
- The home screen answers "how much do I have, and how much did I spend and
  earn this month" at a glance.
- Balances and monthly totals are always consistent with the transaction list.
- No financial data leaves the phone unless the user exports it.

### Decisions made with the user

| Topic | Decision |
|---|---|
| Audience | Own phone first, polished for possible Play Store release |
| Stack | Native Kotlin with Jetpack Compose |
| Money organisation | Multiple accounts, each with its own currency, with transfers |
| Currencies | All ISO 4217 currencies; rates fetched online and cached |
| Capture behaviour | Save immediately, then ask the user to confirm a guessed category |
| Capture sources | Google Wallet and Raiffeisen Mobile Banking (BiH) |
| Duplicates across sources | Merged automatically into one transaction |
| Extras in version 1 | Budgets, recurring transactions, backup and export, app lock |
| Look | Material You, following system light/dark theme |

### Assumptions

- Data is local only. There is no user account and no cloud sync.
- English is the only interface language in version 1. Raiffeisen notifications
  are parsed in Bosnian regardless of interface language.
- Capture only sees what the two apps notify about. Anything else is entered by
  hand or as a recurring transaction.

## 2. Architecture

Single Android app module. Minimum SDK 26 (Android 8.0), target SDK current
stable. Kotlin, Jetpack Compose, Material 3 with dynamic colour.

Three layers, as packages within the module:

- **ui**: Compose screens. Each screen has a ViewModel exposing a single
  immutable state object as a `StateFlow`, and receiving events.
- **domain**: plain Kotlin with no Android imports. Contains models and the
  logic listed below. Everything here is unit-testable on the JVM.
- **data**: Room database and DAOs, repositories, the exchange-rate client, and
  DataStore for settings.

Libraries: Hilt (dependency injection), Room (database), WorkManager
(background jobs), DataStore (settings), Navigation Compose, Vico (charts),
AndroidX Biometric (app lock), Ktor client with kotlinx.serialization (rates),
kotlinx-datetime.

Domain units, each with one job:

| Unit | Job |
|---|---|
| `NotificationParser` (one per source) | Raw notification text to a `ParsedPayment`, or a parse failure |
| `DuplicateMatcher` | Decide whether a parsed payment matches an existing transaction |
| `CategoryGuesser` | Merchant name to a ranked list of likely categories |
| `CurrencyConverter` | Convert minor-unit amounts between currencies at a given rate |
| `BudgetCalculator` | Spent, remaining and daily allowance for a budget in a month |
| `RecurrenceCalculator` | Next due dates for a recurring rule |
| `BalanceCalculator` | Account and total balances from opening balance and transactions |

Background work (WorkManager):

- Daily exchange-rate refresh, requiring a network connection.
- Daily recurring-transaction check, which creates any transactions now due,
  including ones missed while the phone was off.

## 3. Data model

Room tables:

| Table | Fields |
|---|---|
| `Account` | id, name, type (cash, bank, savings, card), currency code, opening balance, colour, archived |
| `Category` | id, name, icon, colour, kind (income, expense), parent id (nullable), archived |
| `Transaction` | id, account id, amount, currency code, rate to main currency, type (income, expense, transfer), category id (nullable), merchant, note, timestamp, source (manual, wallet, raiffeisen, recurring), needs review, transfer pair id (nullable), also seen by (nullable source) |
| `MerchantRule` | id, normalised merchant, category id, confirmation count |
| `CardMapping` | card last four digits, account id |
| `Budget` | id, category id, monthly limit, currency code |
| `RecurringRule` | id, template (account, amount, type, category, merchant, note), frequency, interval, next due date, end date (nullable), active |
| `ExchangeRate` | currency code, rate against base, date fetched |
| `CapturedNotification` | id, source, raw text, posted time, status (parsed, merged, failed, ignored), transaction id (nullable) |

Rules:

1. **Money is stored as whole minor units in a `Long`.** No floating point.
   Each currency carries its own number of decimal places (JPY 0, BAM 2,
   KWD 3), taken from `java.util.Currency`.
2. **Balances are derived, never stored.** Balance equals opening balance plus
   the sum of the account's transactions.
3. **Each transaction stores the exchange rate to the main currency for its own
   date.** Historical totals do not move when rates refresh. If the user changes
   their main currency, stored rates are recalculated from saved historical
   rates where available, otherwise from the current rate.
4. **A transfer is two linked rows** sharing a transfer pair id, one out and one
   in. Transfers are excluded from spent and earned totals.
5. **Deleting an account or category that has transactions** requires choosing
   to reassign the transactions or archive instead. Archived items are hidden
   from pickers but keep their history.

## 4. Notification capture

### Sources

A `NotificationListenerService` receives notifications and passes them to a
registry of sources. Each source declares the package names it accepts and
provides a parser. Notifications from any other package are dropped at once
and never stored.

| Source | Reads | Captures |
|---|---|---|
| Google Wallet | `com.google.android.apps.walletnfcrel`, and Google Play services payment notifications | Tap-to-pay purchases, refunds |
| Raiffeisen Mobile Banking (BiH) | The RMB app package, confirmed from the device during the build | Card payments, ATM withdrawals, incoming payments, transfers |

Each source has its own on/off switch in settings. Adding a bank later means
adding one source with one parser and its tests.

The Raiffeisen parser handles Bosnian wording, amounts in KM / BAM and foreign
currencies, and the local number format (`1.234,56`).

### Pipeline

1. **Receive** the notification and record its raw text in
   `CapturedNotification`.
2. **Parse** it into amount, currency, merchant, card last four or account,
   direction (out or in) and kind (purchase, refund, ATM, incoming, declined).
   Declined payments are marked ignored and go no further.
3. **Match duplicates** (see below).
4. **Resolve the account** from `CardMapping`. An unknown card prompts the user
   once to pick an account, and the answer is remembered. Until answered, the
   transaction goes to the default account.
5. **Save** the transaction with `needs review` set and a guessed category.
6. **Prompt** the user with a notification: merchant, amount, and the guessed
   category, with actions for Yes, the next two most likely categories, and
   Other (opens a picker). Actions work from the lock screen.

Special kinds:

- **Refund**: saved as income, in the category of the original purchase when a
  matching merchant is found.
- **ATM withdrawal**: saved as a transfer from the bank account to the Cash
  account. If no cash account exists, one is created.
- **Incoming payment**: saved as income and prompted for an income category.

### Duplicate handling

Same-source repeats: a notification from the same source with the same amount,
currency, merchant and card within 2 minutes is a re-post of the same
notification and is dropped.

Cross-source merge: a notification from one source matches an existing
transaction from the other source when all of these hold:

- same amount and currency,
- same card last four, or cards mapped to the same account,
- timestamps within 5 minutes.

Merchant names are not compared, because the two apps word them differently.

On a match, no new transaction and no new prompt are created. The second
notification fills in any fields the transaction lacks (merchant detail, card
digits), sets `also seen by`, and is marked merged. Where the two disagree on
amount-related detail, Raiffeisen's values win.

A notification from the same source as the existing transaction is never
cross-merged, so two real purchases of the same amount are both kept. A merged
transaction shows "seen by Wallet and Raiffeisen" with a "these were two
payments" action that splits it back into two.

### Category guessing

In order:

1. A `MerchantRule` for the normalised merchant name: exact match, then fuzzy
   match (case, punctuation, branch numbers and trailing location text
   removed).
2. A built-in keyword table of common merchants and terms.
3. No match: the category is left empty and the prompt offers the user's
   most-used categories.

Every confirmation or correction creates or updates a rule and its
confirmation count. A setting controls prompting: always ask, ask until
confident (default, 3 consistent confirmations), or never ask. A correction
resets the count for that merchant.

### Failure handling

| Situation | Behaviour |
|---|---|
| Parse fails | Raw text kept, listed under "Couldn't read", convertible to a transaction by hand |
| Prompt ignored | Transaction stays in "Needs review", with a badge on Home |
| Listener permission revoked or service stopped | Banner on Home with a button to the system setting |
| Foreign currency | Saved in the paid currency with that day's rate |

A debug screen under capture settings lists recent captured notifications with
raw text and parse result. It is used to tune parsers against real
notifications from the device, since exact wording varies by region and bank
and cannot be verified in advance.

### Permission and disclosure

Notification access is optional and requested during onboarding on a dedicated
screen stating which apps are read (each enabled source by name), what is
extracted, and that nothing leaves the phone. The app is fully usable without
it. A privacy policy is included in the app and repository for Play Store
submission.

## 5. Screens

Bottom navigation with five tabs and a central add button.

**Home.** Total balance in the main currency; account cards; this month's
spent, earned and net; "Needs review" card when captures are waiting; budgets
nearest their limit; upcoming recurring items; recent transactions.

**Transactions.** History grouped by day. Search, and filters for account,
category, type, date range and source. Tap to edit; swipe to delete with undo.

**Insights.** Period switcher (week, month, year, custom).
- Spent against earned over time, as bars by day, week or month.
- Category breakdown as a donut with a ranked list; a category opens its
  transactions and trend.
- Total balance over time as a line.
- Comparison with the previous period.

**Budgets.** Per-category monthly progress with amount left and a daily
allowance for the days remaining. Notifications at 80% and 100%.

**More.** Accounts, categories, recurring transactions, capture (source
switches, card mapping, "Couldn't read", debug), currencies and main currency,
backup and export, app lock, theme, privacy policy, about.

**Add transaction.** Bottom sheet: Expense, Income or Transfer; amount keypad;
category grid; account, date, note. Last-used account and today's date are
pre-filled.

**Categories.** About 60 defaults under parent groups: Food, Transport,
Housing, Bills, Health, Shopping, Entertainment, Travel, Education, Family,
Pets, Gifts, Fees and taxes; and income categories: salary, freelance, gifts,
refunds, interest, sales. Users can add, rename, recolour, re-icon, regroup and
archive categories.

**Onboarding.** Main currency (BAM suggested from locale), first account with
current balance, optional notification capture with disclosure, optional app
lock.

## 6. Currencies and exchange rates

- The currency list comes from `java.util.Currency.getAvailableCurrencies()`,
  with name, symbol and decimal places, searchable in the picker.
- Rates come from a free, key-less public rates service, accessed through a
  `RateProvider` interface with a primary and a fallback provider, so a
  provider can be replaced without touching the rest of the app. The primary
  must cover BAM; the provider is selected and verified at implementation.
- Rates are refreshed daily and cached in `ExchangeRate`. Only currency codes
  are sent; no user data.
- When rates are unavailable, the last cached rates are used and the UI shows
  their age. A currency with no cached rate asks the user to enter one.

## 7. Security and privacy

- App lock with biometrics via the system prompt and a PIN fallback. The PIN is
  stored as a salted hash. Lock delay is configurable: immediately, 1 minute,
  5 minutes.
- `FLAG_SECURE` hides content in the recent-apps view and blocks screenshots
  while lock is enabled.
- Lock-screen category prompts show merchant and amount by default, with a
  setting to hide the amount.
- The database is in app-private storage. Android auto backup is disabled for
  it.
- The only network permission use is the rates request.

## 8. Backup and export

- **Backup**: a single file containing all tables and settings, written through
  the system file picker to a location the user chooses. Optional password
  encryption (AES-GCM, key derived with PBKDF2). The file carries a schema
  version.
- **Restore**: validates the file and version, asks for confirmation, then
  replaces current data in one database transaction. A failed restore leaves
  existing data untouched.
- **CSV export**: transactions for a chosen date range, with account, category,
  amount, currency, merchant, note, date and source.

## 9. Error handling

- Database changes between versions use Room migrations with migration tests.
  Destructive migration is never enabled.
- Background jobs retry with backoff and never block the UI.
- Any multi-row write (transfers, merges, restore, reassign on delete) runs in
  one database transaction.
- User-facing errors say what happened and what to do next.

## 10. Testing

- **Unit (JVM)**: both parsers against a library of sample notification texts;
  duplicate matching; category guessing and rule learning; currency conversion
  and rounding across 0, 2 and 3 decimal currencies; budget maths; recurrence
  dates including month ends and missed runs; balance calculation.
- **Database (instrumented)**: DAO queries, transactional writes, migrations.
- **UI (Compose tests)**: add a transaction, confirm a captured payment, create
  a budget, back up and restore.
- **Device**: real Wallet and Raiffeisen notifications checked through the
  debug screen, with parser patterns and sample texts updated from what is
  observed.

Domain logic is written test-first.

## 11. Environment

The development machine has `adb` and Node but no JDK, Gradle or Android SDK.
The implementation plan starts by installing JDK 17, the Android command-line
tools and required SDK packages, and creating the Gradle project with a
wrapper.

## 12. Out of scope for version 1

Cloud sync; shared or multi-user accounts; CSV import; investment and loan
tracking; home-screen widgets; interface languages other than English; banks
other than Raiffeisen BiH; iOS.
