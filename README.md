# KoshVista

KoshVista is an open-source, offline-first Android personal-finance app in development. The intended release tracks accounts, cash, spending, investments, fixed income, liabilities, source-linked imports, and encrypted Google Drive recovery. The six numbered documents in this repository define the product and engineering contract.

## Current build

This checkout is under active implementation. A passing debug build is not a certified release or proof of backup recovery. Institution-specific statement formats require redacted fixtures before they can be described as supported.

The current local build supports dated manual ledger entries, category budgets, fixed deposits, investment trades at recorded cost, CSV statement review, and passphrase-encrypted local backup. PDF and image files can be read with on-device OCR for text preview, but OCR text is not converted into posted financial records. Institution-specific parsing, cloud backup, and signed release validation remain in progress.

Open the project in Android Studio or run `./gradlew :app:assembleDebug :app:testDebugUnitTest` with Android SDK 36 installed. The app never needs banking passwords, PINs, or OTPs.

Google sign-in is compiled behind an optional `KOSHVISTA_GOOGLE_WEB_CLIENT_ID` Gradle property, supplied when building with `-PKOSHVISTA_GOOGLE_WEB_CLIENT_ID=...`. Do not add local configuration to Git. The client must be registered for this Android package and the signing certificate. Until configured, the debug build offers a clearly labelled local development vault; release builds require Google sign-in configuration before they can hold records. Settings supports a passphrase-encrypted local backup and restore into an empty vault for the same owner identity. The local archive currently has a 100 MB size limit. Google Drive consent and tested cloud recovery are not yet implemented.

## Privacy and contributions

Never submit real financial documents, account identifiers, OAuth credentials, signing keys, recovery secrets, or vault archives in issues or pull requests. Use synthetic or thoroughly redacted fixtures. Report security problems privately to the repository owner rather than posting exploit details in a public issue.

The code is licensed under Apache-2.0; see [LICENSE](LICENSE).
