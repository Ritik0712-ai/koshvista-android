# KoshVista

KoshVista is an open-source, offline-first Android personal-finance app in development. The intended release tracks accounts, cash, spending, investments, fixed income, liabilities, source-linked imports, and encrypted Google Drive recovery. The six numbered documents in this repository define the product and engineering contract.

## Current build

This checkout is under active implementation. A passing debug build is not a certified release or proof of backup recovery. Institution-specific statement formats require redacted fixtures before they can be described as supported.

Open the project in Android Studio or run `./gradlew :app:assembleDebug :app:testDebugUnitTest` with Android SDK 36 installed. The app never needs banking passwords, PINs, or OTPs.

## Privacy and contributions

Never submit real financial documents, account identifiers, OAuth credentials, signing keys, recovery secrets, or vault archives in issues or pull requests. Use synthetic or thoroughly redacted fixtures. Report security problems privately to the repository owner rather than posting exploit details in a public issue.

The code is licensed under Apache-2.0; see [LICENSE](LICENSE).
