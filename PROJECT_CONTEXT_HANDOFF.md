# KoshVista — Complete Chat Context Handoff

**Prepared:** 2026-10-01  
**Purpose:** Paste or attach this file in a new Codex chat to continue the project without relying on the old conversation. This is a faithful working summary of the conversation and decisions, not a verbatim export of internal tool messages.  
**Project folder:** `/Volumes/RitikSSD/Projects/Ultimate Expense Tracker/`  
**GitHub repository:** https://github.com/Ritik0712-ai/koshvista-android

## Read this first

The user has explicitly said **“Please wait to start until I tell you to.”** The six specification documents have been written, and the GitHub repository has been created, but **Android app implementation has not started**. Creating this handoff document is the only work authorised by the latest request. Do not scaffold, code, push, create cloud resources, or install anything for the app until the user explicitly instructs you to start. When the user does say to start, aim for the **complete agreed application**, not a throwaway starter or UI-only demo. Engineering phases are an implementation order, not a reduction of final scope.

The user also requires **all project files and generated artefacts to stay inside the project folder above**. Other existing files/tools on the machine may be read, but do not save or modify any file outside that folder unless the user later changes this instruction.

## User's original vision

The user wants a native Android APK that becomes an “ultimate expense tracker” and covers their full personal-finance picture: bank accounts and all credits/debits, expenses, cash, portfolios, investments, fixed deposits (FDs), bonds and overall net worth. They want it highly automated and data-driven, with attractive bar graphs, histograms, pie/allocation charts and other visualisations. They plan to use it for real life, not merely as a portfolio demonstration. They will upload bank-statement PDFs, portfolio screenshots, and FD/bond screenshots or PDFs; the app should extract and organise records with minimal manual work. **All required tools and services must be free: no payments, subscriptions or fees.** Open-source tools are preferred where useful.

The user first asked for discussion and analysis only. After reviewing and accepting the product/technical plan, they asked for six Markdown specification files. They later chose to make the project **open source with a public GitHub repository**. They have not yet authorised implementation.

### Conversation sequence

1. The user described the complete finance-tracking vision and asked to **discuss and plan only** before making anything.
2. The assistant proposed an offline-first native Android design, honest limits on full automation and live feeds, phased engineering, and no mandatory paid service.
3. The user supplied the Realme GT2/Android 14 device, named Indian banks and brokers, said other users would come later, and added chart-heavy design, login, cloud backup after uninstall and a dedicated cash option.
4. The assistant proposed per-user Google sign-in and encrypted Google Drive backup, cash as an account, and source-linked visual dashboards. The user accepted the suggestions.
5. The user requested an A-to-Z technology/deployment/tool list; the assistant selected the stack summarised below.
6. The user asked whether the assistant was ready to build and requested a name. The assistant recommended **KoshVista**; the later repository name `koshvista-android` confirms its use as the working name.
7. The user asked which Codex connectors/plugins would minimise manual work. GitHub was already connected; Google Drive was suggested and then connected. The assistant explained that connectors cannot replace each app user's OAuth consent.
8. The user explicitly said to **wait before starting**, wanted the complete final app rather than a beginner version, and asked for every connector/plugin. The assistant confirmed only GitHub and Google Drive are relevant Codex connectors for the agreed design.
9. The user requested six detailed Markdown specifications and restricted all project file creation to the project folder. The assistant created the six documents listed below; no app code was created.
10. The user asked for GitHub creation choices, corrected the assistant's initial private-repository recommendation, selected a public open-source repository with Apache-2.0 and Android `.gitignore`, and created the URL above.
11. The latest user request supplied that repository URL and asked for one file carrying the conversation context into a new chat. This handoff file fulfils that request; it does not authorise app implementation.

## People, device and intended users

- Initial owner/tester: Ritik Agarwal, GitHub account `Ritik0712-ai`.
- First Android device: **Realme GT2 running Android 14**.
- Initially for Ritik, later able to accommodate other users. Data must be isolated per user.
- India-focused initial formats, INR default, but the model should support currency codes and dated FX values.
- Candidate banks named in discussion: Axis Bank, HDFC Bank, SBI, Kotak, ICICI; prioritise banks the user actually uses when redacted examples arrive.
- Candidate investment apps/brokers: Groww, Zerodha, INDmoney; again, a format is “supported” only after real redacted examples and tests.
- An earlier phrase, “Close the office,” was unclear. It was not converted into a feature requirement.

## Agreed product behaviour

1. **Native Android app/APK**, not a website wrapped as an app.
2. **Accounts and cash:** Bank, cash, credit-card, broker cash, other assets/liabilities. Cash is a first-class account. ATM withdrawals and cash deposits are transfers between bank and cash, not expenses/income. Money is recorded once.
3. **Ledger:** Income, expenses, transfers, refunds, splits, categories, merchant recognition, search, reconciliation, and source links.
4. **Investment tracking:** Holdings, trades when documented, dated portfolio snapshots, allocation and net-worth views. A screenshot is a snapshot and cannot by itself establish trade history or purchase cost.
5. **FDs/bonds:** Extract terms, track projected interest/coupons/maturity and reminders. Projections are labelled and kept separate from confirmed transactions.
6. **Imports:** PDF/CSV/image/screenshot; on-device extraction; versioned institution adapters; deduplication, confidence scoring, source traceability and a short review queue for uncertain fields. Reimporting overlapping statements must not duplicate entries.
7. **AI and analytics:** AI assists extraction, category suggestions, recurring-pattern and anomaly detection, and evidence-linked explanations. Financial totals, balances and interest use deterministic calculations. No mandatory cloud LLM or paid AI API.
8. **Visual interface:** A chart-led, polished dashboard with income/expense bars, daily-spend histogram, category breakdown, net-worth line, investment allocation, cash view, maturity timeline, filters, drill-down to source records and accessible text summaries.
9. **Identity and recovery:** Google sign-in, local biometric/device-credential lock, separate Drive consent, encrypted local database/documents, automatic queued encrypted Google Drive backups, visible last verified backup, and clean-install restore with a recovery passphrase/key. Backups must survive uninstall if the user retains Google account access and recovery secret.
10. **Offline first:** Local data and charts work without internet. Network return triggers best-effort scheduled backup; Android cannot guarantee an instant background upload at the moment connectivity appears.
11. **Later users:** Each user's stable Google account subject ID owns a separate local vault and Drive backup. No cross-user access. Full concurrent multi-device editing requires a separate conflict-resolution design; the agreed release includes backup/restore, not unsafe simultaneous sync.
12. **Safety and cost:** The app never transfers money, places trades, stores banking passwords/PINs/OTPs, scrapes logins, or assumes a free/unlicensed live market feed. Live market and bank data are not promised merely because the app's own ledger updates in real time.

## Agreed technical approach

- Kotlin, Android Studio, Gradle Kotlin DSL, Jetpack Compose, Material 3, Navigation Compose, ViewModel/StateFlow, Coroutines, Hilt.
- Room/SQLite with SQLCipher for encrypted local storage, after validating exact library-version compatibility. DataStore for non-financial preferences. Integer minor units for money; `BigDecimal` for quantities/prices/rates.
- Vico for standard Compose charts; Compose Canvas for specialised visualisations.
- PdfBox-Android for text PDFs, Android PDF rendering where needed, bundled on-device ML Kit OCR for scans/screenshots, strict CSV parsing for exports.
- Android file/photo pickers, private encrypted document storage, Android Keystore/BiometricPrompt, WorkManager for queued import/backup.
- Google Credential Manager for sign-in; Google Drive API with narrow `drive.file` scope for **user-owned, app-created, visible backup files**. No application-owned server, hosted database, Vercel deployment, Firebase or Supabase is required under the zero-fee release architecture.
- Backup archives are versioned and encrypted before upload. A random data key is wrapped for local access and separately via a user-held recovery passphrase for reinstall. A newly uploaded archive must be verified before being called a successful backup; retain a prior good version.
- No paid service or automatic billing may be required. Google Drive storage/quotas and OAuth/public-distribution requirements need checking at implementation/release time.

## Six completed specification files

All six are local `.md` files in the project folder. They were created on 2026-10-01; local links and all 37 app-flow screen references were checked.

1. [Product Requirements Document](01-product-requirements.md) — vision, users, problems, features, user stories, complete release contract, success metrics and exclusions.
2. [Technical Requirements Document](02-technical-requirements.md) — Android stack, architecture, local backend, auth, imports, Drive API, security and deployment.
3. [Complete App Flow](03-app-flow.md) — 37 numbered screens, navigation, buttons, success/error/empty states and end-to-end journeys.
4. [UI/UX Design Brief](04-ui-ux-design-brief.md) — palette, typography, components, charts, dashboard and accessibility.
5. [Backend and Database Schema](05-backend-schema.md) — owner-scoped Room/SQLCipher schema, relationships, indexes, backup format and ownership rules.
6. [Implementation Plan](06-implementation-plan.md) — setup, identity, data, UI, imports, backup, integrations, testing and signed release gates.

The PRD uses “MVP” only because the user requested that section; it defines it as the smallest **complete and safe release candidate containing all user-requested core areas**, not a beginner version. The implementation plan is phased for engineering order, but the user wants the complete final app.

## GitHub repository and current status

- Public repository: **https://github.com/Ritik0712-ai/koshvista-android**.
- The GitHub form selected owner `Ritik0712-ai`, name `koshvista-android`, Public visibility, README off, Android `.gitignore`, Apache License 2.0, and blank Copilot jumpstart. The screenshot originally had “Private Android app” in its description; Codex advised replacing it with an open-source description before creation. The final saved description has not been verified.
- Read-only verification through GitHub CLI confirmed the repository exists, is **PUBLIC**, uses `main`, and currently contains `.gitignore` and `LICENSE`. The `LICENSE` file begins with **Apache License, Version 2.0**. GitHub's `licenseInfo` field was `null` immediately after creation, so GitHub's automatic licence badge/detection has not been confirmed.
- The local project folder currently contains the six specs and this handoff. It is **not yet a Git repository** and has not been connected to the remote. No app source, APK, signing key or cloud resource has been created.
- Because the remote already contains `.gitignore` and `LICENSE`, incorporate its initial commit rather than overwriting it when local Git is set up. Extend the Android ignore rules for private financial files, credentials, signing keys and local build artefacts before any public push. Scan the staged diff as a second control.
- A public repository does not mean public user data. Never commit real or insufficiently redacted financial statements/screenshots, OAuth secrets, recovery keys, signing keys, local databases or backup archives. Documentation and code can be public; each app user's data remains private.

## Git identity and publication rules

These user-provided `AGENTS.md` instructions apply to **every repository**:

- All commits and pushes use author and committer **`Ritik Agarwal <ritikagarwal2468@gmail.com>`**, the email linked to GitHub account `Ritik0712-ai`.
- Push with Ritik's own `gh` credentials. Never author, co-author or push as Codex, a bot or a placeholder.
- Never append a `Co-Authored-By:` trailer.
- Before the first commit in this unfamiliar repo, check `git config user.email` and correct a stale local override.

Do not create a PR or publish a release merely to mark progress; do so when implementation and review warrant it. Keep public commits free of secrets and personal data.

## Connectors and required user actions

- The **GitHub** Codex connector is connected. The **Google Drive** Codex connector was installed/connected after it was suggested. Verify each connection again in a new chat before using it; connector status can change.
- The Google Drive *Codex connector* is for development assistance. It does **not** grant the finished app access to a user's Drive. The app needs its own Google OAuth client, sign-in and each user's explicit Drive authorisation.
- No Vercel, Supabase, Firebase or other Codex plugin is required by the agreed architecture. Android SDK/Studio and in-app libraries are local build dependencies, not connectors.
- When implementation is authorised, Ritik must eventually approve his own Google OAuth consent, retain a recovery secret and signing key, install/test the APK on the Realme GT2, and supply redacted representative bank/broker documents for institution-specific parser certification. The agent can handle engineering work around these unavoidable owner actions.

## Important course corrections from this chat

1. Initial plan was discussion only; no app code should be created until explicit start instruction.
2. The app must include **cash as a dedicated account** and a **visual, data-driven interface**.
3. Cloud backup, login/authorisation and restore after uninstall are core requirements.
4. The owner wants the **complete final app**, not a beginner/starter version. Do not mislabel a partial UI as complete.
5. The GitHub repository is **public and open source**, correcting an earlier private-repository suggestion. The TRD and implementation plan were updated accordingly.
6. The latest request is only to create this context file for a future chat. App implementation is still on hold.

## Suggested first message for a new chat

> Read `PROJECT_CONTEXT_HANDOFF.md` and the six linked specifications in `/Volumes/RitikSSD/Projects/Ultimate Expense Tracker/`. Treat them as the current KoshVista project context. The public repository is `https://github.com/Ritik0712-ai/koshvista-android`. Follow the Git identity and project-folder rules. Do not begin app implementation until I explicitly say to start.
