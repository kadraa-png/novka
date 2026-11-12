# Novka

A personal finance app for Android: accounts in any currency, automatic capture of
payments from Google Wallet and Raiffeisen (BiH) notifications, budgets, recurring
transactions, savings goals, debts, graphs, backup and an app lock. Interface in
English and Bosnian. All data stays on the phone.

**Status: work in progress. The project does not compile yet.** The domain logic,
database, notification capture, background jobs and widget are written; about half of
the screens and the string resources are still missing.

- [HANDOFF.md](HANDOFF.md): what is done, what is left, and how to build.
- [Design spec](docs/superpowers/specs/2026-11-12-finance-app-design.md): the agreed design.

Native Kotlin, Jetpack Compose, Room. Package `com.kadraa.novka`, minimum Android 8.0.
