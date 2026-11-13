# Novka

A private, offline personal finance app for Android. Novka tracks accounts in any
currency, picks up card payments from your notifications, and keeps every bit of it
on your phone.

[Download the latest APK](https://github.com/kadraa-png/novka/releases/latest) ·
[Website](https://kadraa.space) ·
[Buy me a coffee](https://ko-fi.com/kadraa)

## Features

- **Accounts in any currency**, with transfers between them. Exchange rates are
  downloaded and cached; each transaction keeps the rate of its own day.
- **Automatic capture** of payments from Google Wallet and Raiffeisen (BiH)
  notifications. A captured payment is saved straight away with a guessed category
  for you to confirm, and the same payment seen from two sources is merged into one.
- **Budgets** per category, with alerts.
- **Recurring transactions**, **savings goals** and **debts**.
- **Insights**: graphs of spending and income over time and by category.
- **Backup and restore** to a file you choose, optionally protected by a password,
  plus CSV export and import.
- **App lock** with a PIN and fingerprint.
- **Home screen widget** showing your balance.
- **Material You** design that follows your wallpaper colours and light or dark theme.
- Interface in **English and Bosnian**.

## Privacy

There is no account, no server and no analytics. The only thing Novka downloads is a
list of exchange rates, and nothing about you or your money is sent with that
request. The full policy is in [PRIVACY.md](PRIVACY.md) and inside the app under
More, Privacy.

## Install

1. Download `novka-<version>.apk` from the
   [releases page](https://github.com/kadraa-png/novka/releases/latest).
2. Open it on your phone and allow installing from that source when Android asks.
3. For automatic capture, grant notification access when the app asks during setup.

Novka needs Android 8.0 or newer.

## Build from source

You need JDK 17 and the Android SDK (platform 35). Put the SDK path in
`local.properties` as `sdk.dir=/path/to/Android/Sdk`, then:

```sh
./gradlew :app:testDebugUnitTest                   # domain unit tests
./gradlew :app:assembleDebug                       # debug APK
./gradlew :app:assembleRelease --max-workers=1     # optimised APK
```

APKs are written to `app/build/outputs/apk/`.

## Tech

Native Kotlin, Jetpack Compose with Material 3, Room, DataStore, WorkManager,
Navigation Compose and Vico for charts. Money is stored as whole minor units in a
`Long`; balances are always derived, never stored.

- [HANDOFF.md](HANDOFF.md): developer notes on what is done, what is still owed and
  how the pieces fit together.
- [Design spec](docs/superpowers/specs/2026-11-12-finance-app-design.md): the
  original design.
- [CHANGELOG.md](CHANGELOG.md): what changed in each version.

## Support

Novka is free. If it is useful to you, you can
[buy me a coffee on Ko-fi](https://ko-fi.com/kadraa). Bug reports and ideas are
welcome in the [issue tracker](https://github.com/kadraa-png/novka/issues).

## License

[GPL-3.0](LICENSE). Copyright © 2026 kadraa.
