# Handoff

Written 2026-11-12, part-way through the first build of the app.

## Where things stand

Updated 2026-11-13. The app builds, all screens exist, and it runs on the owner's
phone with their real data. The 38 domain unit tests pass. The owner tapped through
every screen on the phone and reported that everything works; nothing else has been
tested on a device, and there are no UI tests.

| Area | Files |
|---|---|
| Domain logic | `domain/*.kt` (unit-tested) |
| Database, repository, settings, backup, rates | `data/*.kt` |
| Notification capture | `capture/*.kt` |
| Background jobs, widget | `work/Workers.kt`, `widget/BalanceWidget.kt` |
| App shell, lock screen, navigation | `NovkaApp.kt`, `ui/MainActivity.kt` |
| Shared UI pieces | `ui/Theme.kt`, `ui/Common.kt`, `ui/Icons.kt`, `ui/Charts.kt` |
| Onboarding | `ui/Onboarding.kt` |
| Tabs | `ui/HomeScreen.kt`, `ui/TransactionsScreen.kt`, `ui/InsightsScreen.kt`, `ui/BudgetsScreen.kt`, `ui/MoreScreen.kt` |
| Add and edit sheet | `ui/TxEditor.kt` |
| Accounts, categories, recurring, goals, debts | `ui/ManageScreens.kt` |
| Capture, currencies, security, backup, privacy | `ui/SettingsScreens.kt` |
| Strings | `res/values/strings.xml` (English), `res/values-bs/strings.xml` (Bosnian) |

### Still owed

1. **Tests:** `BackupCodec` round trip and wrong password; Room DAO and migration
   tests; Compose UI tests.
2. **Device check of the parsers.** See "Notification formats" below.
3. **Not exercised on a device:** backup and restore, CSV export and import, changing
   the main currency, recurring rules falling due, budget alerts.
4. **Onboarding has no PIN step.** The PIN is set under More, Security.
5. **The transactions list filters** by type, account, category and "needs review".
   `TxFilter` also has date and source fields; the list honours them (Insights sets
   the dates) but has no controls for them.
6. **Deleting a category** always uncategorises its transactions. The repository can
   also reassign or archive (`removeCategory`), but the screen does not offer it.
7. **Spec update** and a **privacy policy document** for the repository. The in-app
   text is `privacy_text` in the strings files.

## Installing on the owner's phone

The phone holds real data and has the app lock on. Rules that keep it safe:

- Install with `adb install -r` only. Never uninstall: that deletes the data.
- Install the **release** build. It is shrunk and optimised, and is signed with the
  debug key (`~/.android/debug.keystore`) so it updates in place. Debug builds feel
  slow. If that keystore is lost, the app can no longer be updated without an
  uninstall; replace the signing setup before publishing anywhere.
- The release build is not debuggable, so `adb run-as` cannot read its data. The
  in-app Backup screen is the way to get a copy.
- Xiaomi shows an "Install via USB" prompt on the phone that must be tapped.
- After installing, `adb shell cmd package compile -m speed -f com.kadraa.novka`
  precompiles the app.

## Building

There is no system-wide JDK or Android SDK on the development machine. Both were
installed under the home folder:

| Tool | Location |
|---|---|
| JDK 17 (Temurin) | `~/Android/jdk-17` |
| Android SDK (platform 35, build-tools 34 and 35) | `~/Android/Sdk` |
| Gradle 8.10.2 (only used to create the wrapper) | `~/Android/gradle-8.10.2` |

`local.properties` (not committed) must contain `sdk.dir=/home/<you>/Android/Sdk`.

```sh
export JAVA_HOME=~/Android/jdk-17
./gradlew :app:testDebugUnitTest      # domain unit tests
./gradlew :app:assembleDebug          # debug APK in app/build/outputs/apk/debug
./gradlew :app:assembleRelease --max-workers=1   # optimised APK in app/build/outputs/apk/release
```

Run one Kotlin compile at a time (`--max-workers=1`, and not debug and release in the
same command): two at once run out of memory here. The release build takes about
13 minutes because of R8.

On a new machine: install any JDK 17, install the Android command-line tools, then
`sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`.

The development machine has 4 cores and about 5 GB of RAM, so `gradle.properties`
caps Gradle at 1.5 GB, two workers, and runs the Kotlin compiler in-process. Loosen
these on a larger machine. The first build took several minutes.

Versions: AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01,
Room 2.6.1. minSdk 26, target and compile SDK 35.

## Test phone

Xiaomi 2209116AG, Android 13, connected over USB with debugging on. Installed and
relevant:

- Google Wallet: `com.google.android.apps.walletnfcrel`
- Raiffeisen BiH: `ba.raiffeisenbank.mobile.prod`
- Default SMS app: Google Messages, `com.google.android.apps.messaging`

## Notification formats

Only one real message has been seen, read from the phone with
`adb shell dumpsys notification --noredact`. It was an SMS from sender "Raiffeisen",
shown by Google Messages, not by the banking app:

```
<code> je lozinka za potvrdu zaduzenja vaseg racuna u korist <merchant> karticom *<last4>. Iznos <amount> EUR. <yyyyMMdd HH:mm:ss>.
```

This is a confirmation-code message for an online card payment, sent before the
payment completes. Consequences already built in:

- Raiffeisen is read from SMS apps (when the sender is "Raiffeisen") as well as from
  the banking app. See `Sources.identify` in `capture/CaptureProcessor.kt`.
- Such a message is saved as an expense marked `pendingAuth` and `needsReview`. If the
  bank later reports the same amount within 15 minutes, the two are joined.
- The code itself is replaced with dots before anything is stored
  (`RaiffeisenParser.redact`).

Every other format in `RaiffeisenParser` and `WalletParser` is an educated guess: no
Wallet notification and no banking-app notification has been observed. The test
fixtures for those are invented. Once the app runs, make a tap-to-pay purchase and a
card payment, look at More, Capture, debug list, and correct the parsers and tests from
what actually arrives.

## Changes from the spec

Agreed with the owner after the spec was written:

- Added to version 1: savings goals, debts and loans, a home-screen widget, CSV import.
- Interface in English and Bosnian, following the phone's language.

Made during the build; the owner has not reviewed these:

- **No Hilt.** Dependencies are created by hand in `NovkaApp`. Fewer build steps on a
  small machine.
- **No Vico.** Charts are drawn on a Compose `Canvas` in `ui/Charts.kt`.
- **No Ktor.** Rates are fetched with `HttpURLConnection`. Providers:
  `open.er-api.com` first, the `@fawazahmed0/currency-api` CDN as fallback. Both are
  free and need no key.
- **One view model** (`MainViewModel`) shared by all screens, instead of one per screen.
- **Category icons are Material icons, not emoji** (owner's request, 2026-11-13).
  `Category.icon` and `Goal.icon` hold a key such as `shopping_cart`; `ui/Icons.kt`
  maps keys to vectors and is the list an icon picker should offer. This added the
  `material-icons-extended` dependency, which makes debug builds larger; release
  builds strip the unused icons.
- **A transaction's amount is always in its account's currency.** A payment made in
  another currency is converted, and what was actually charged is kept in
  `originalAmount` / `originalCurrency`. The spec said to save it in the paid currency,
  which would have broken account balances.
- **Budgets are in the main currency**; they have no currency of their own.
- **Payment prompts have three buttons**, not four: the two likeliest categories and
  "Other". Android shows at most three actions on a notification.
- **Recurring rules cover income and expenses only**, not transfers.
- **The widget hides amounts** while the app lock is on.
- A transaction saved when no exchange rate is known gets rate 0 and is completed the
  next time rates are fetched (`Repository.repairMissingRates`).

## Design rules

The owner wants every screen to follow Material Design 3, with no emoji anywhere and
quick animations. The screens still to be written should keep to what the existing
ones do:

- Colours only from `MaterialTheme.colorScheme` (dynamic colour on Android 12+, the
  teal scheme in `ui/Theme.kt` below that). The only stored colours are the category
  and account swatches. Shapes from `MaterialTheme.shapes`, text styles from
  `MaterialTheme.typography`; no hand-set font sizes or weights.
- Icons from `Icons.Rounded` (outlined for unselected navigation items). Use
  `CategoryBadge` / `IconBadge` from `ui/Common.kt` for leading icons.
- Rows are `ListItem`s; sections are grouped in `SectionCard` (`ui/HomeScreen.kt`).
- Animation durations and easing come from `Motion` in `ui/Theme.kt` (100 to 200 ms).
  Screen transitions are set once on the `NavHost` in `ui/MainActivity.kt`.
- Navigation bar below 600dp of width, navigation rail above. Scrolling content is
  capped at 840dp wide.
- The widget takes its colours from `res/values*/colors.xml`, which follow the
  wallpaper on Android 12+.

## Code map

```
app/src/main/java/com/kadraa/novka/
  NovkaApp.kt        creates the database, repository and capture processor
  domain/            plain Kotlin, no Android imports, unit-tested
    Money.kt           minor-unit maths, loose number parsing, AmountFinder
    Parsers.kt         WalletParser, RaiffeisenParser
    Capture.kt         DuplicateMatcher, CategoryGuesser
    Calculators.kt     currency conversion, budgets, recurrence
    Csv.kt             CSV reading, writing and statement import
    DefaultCategories.kt  built-in categories (en and bs) and merchant keywords
  data/              Room entities and DAO, Repository, settings, backup, rates
  capture/           notification listener, processing pipeline, prompt notifications
  work/              daily exchange-rate and recurring-transaction jobs
  widget/            home-screen balance widget
  ui/                Compose screens
```

Money is whole minor units in a `Long` everywhere. Balances are never stored; they are
computed from the opening balance plus the account's transactions.

## Working with Claude Code in this repository

The owner's Claude Code setup has a "GateGuard" hook that rejects the first write to
each new directory and the first shell command of a session until some facts are
stated. It adds a retry to each new directory; it is not a sign that anything is wrong
with the project.
