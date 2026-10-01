# KoshVista — End-to-End Implementation Plan

**Goal:** Deliver the complete signed Android app defined in the [PRD](01-product-requirements.md), [technical requirements](02-technical-requirements.md), [app flow](03-app-flow.md), [design brief](04-ui-ux-design-brief.md) and [schema](05-backend-schema.md). Phases are engineering order and review gates; the release target includes every in-scope product capability.

## Working rules

- All project files, fixtures, build outputs and documentation stay under `/Volumes/RitikSSD/Projects/Ultimate Expense Tracker/` unless the user later changes this instruction. External tools may read installed SDKs from their existing locations; do not create project artefacts elsewhere.
- No real bank statement, broker screenshot, signing key, OAuth token, recovery passphrase or Google account secret is committed to Git. Redact fixtures and test the redaction.
- Before the first commit, verify repo-level Git email and set author/committer to `Ritik Agarwal <ritikagarwal2468@gmail.com>`; use Ritik's own `gh` credentials. Never add a `Co-Authored-By` trailer.
- Do not claim an institution format, cloud restore, APK installation or public release is complete without the corresponding test evidence.
- Keep the app free of required paid services and automatic billing. Review third-party terms and quotas before enabling each integration.

## Phase 1 — Setup and release foundation

**Work**

1. Freeze the working package ID and app label; check name/package availability before public distribution. Record min SDK 26, test target Realme GT2/Android 14, target/compile SDK and dependency versions available at build time.
2. Create a Kotlin/Compose/Gradle Android project with packages/modules defined in the TRD. Add formatting, static analysis, unit-test runner, instrumentation-test runner and reproducible debug/release variants.
3. Add theme tokens, typography, navigation shell, empty/error/loading components and accessibility conventions.
4. Use the owner's **public GitHub repository**. Add a project README, explicit open-source licence (Apache-2.0 recommended), contribution/security guidance and an Android-specific `.gitignore` before the first push. Protect signing material and sample data; ignore rules are a guardrail, so scan the staged diff as well. Establish local debug builds first.

**Deliverables:** Buildable app skeleton, dependency/licence inventory, theme preview, navigation map, public project README and green base checks. **Gate:** Debug APK installs and launches on an emulator and the Realme GT2 when connected; first public push contains no financial documents or secrets.

## Phase 2 — Identity, security and cloud consent

**Work**

1. Configure Google Identity and Drive OAuth for an Android package/signing fingerprint. Implement Credential Manager sign-in and stable subject-based ownership.
2. Implement local app lock, biometric/device credential fallback, session expiry, account switch and owner isolation.
3. Implement device key wrapping, recovery-passphrase setup/confirmation and no-backdoor recovery model.
4. Build Drive permission screen and status handling; sign-in and Drive authorisation are separate actions.

**Deliverables:** Complete S01–S07 and S29–S32 identity/security flows; tests for wrong owner, revocation, offline unlock and lost recovery secret. **Gate:** A second Google account cannot read the first owner's vault or backup, and no OAuth token or passphrase appears in logs or local exports.

**External dependency:** The owner must complete Google's own sign-in/consent when presented. A public release may also require Google OAuth verification; this cannot be bypassed by a connector.

## Phase 3 — Database and finance rules

**Work**

1. Implement the [schema](05-backend-schema.md) in Room/SQLCipher, owner-scoped DAOs, migrations and encrypted document storage. Confirm exact Room/SQLCipher compatibility with a migration/open/close test.
2. Implement account, transaction, split, refund, paired-transfer and reconciliation domain services using integer minor units and `BigDecimal` for quantities/rates.
3. Implement investment trade and snapshot semantics, FD/bond contracts and projected schedules, liability terms, budgets, recurring rules and reminders.
4. Build seed-free test fixtures covering bank, cash, credit card, broker funding, refunds, FX, FD interest and liabilities.

**Deliverables:** Repository APIs and meaningful tests; no screen contains financial formulas. **Gate:** Every golden case reconciles to a known total, transfer pairs are atomic, chart queries cannot double-count cash withdrawal or investment funding, and migrations retain records.

## Phase 4 — Core UI and visual analytics

**Work**

1. Implement all screen routes and state contracts in [03-app-flow.md](03-app-flow.md): Home, Activity, accounts, cash, Wealth, Import, Settings and detail/edit screens.
2. Build Vico bar/line charts and Compose Canvas specialised visuals according to [04-ui-ux-design-brief.md](04-ui-ux-design-brief.md).
3. Make chart segments open filtered source records. Add date/account/category filters, accessible summaries, dark theme, dynamic type and compact/wide layouts.
4. Add manual expense/income/transfer/cash entry and account management so every domain remains usable even when a document format is unsupported.

**Deliverables:** Complete local app navigation and data-driven dashboard, all empty/loading/error states, cash shortcuts and chart drill-down. **Gate:** Chart totals equal repository queries for the same filters; core journeys work offline, with large text and TalkBack.

## Phase 5 — Import and on-device intelligence

**Work**

1. Implement Android file selection, encrypted staging, type/size/hash checks, text PDF extraction, scanned PDF rendering, on-device OCR and CSV parsing.
2. Implement versioned adapter interface, bank transaction parser, brokerage holding/trade parser and FD/bond term parser. Begin with generic formats, then certify specific institutions only from redacted real samples.
3. Add candidate confidence, source highlights, duplicate detection, overlapping-statement handling, user review and atomic commit/undo.
4. Add local merchant/category rules and feedback learning, recurring-pattern detection and insight evidence links. All suggestions remain editable and traceable.

**Deliverables:** S19–S22 import flow, adapter fixture corpus, supported-format registry, import diagnostics and review queue. **Gate:** An identical file and an overlapping statement create no duplicate posted records in fixtures; uncertain financial fields require a user decision; a failed commit posts zero partial rows.

**External dependency:** Redacted examples from the owner's actual banks/brokers are needed to certify those formats. No importer will request bank login credentials.

## Phase 6 — Encrypted Drive backup and restore

**Work**

1. Build a consistent snapshot archive containing the database and encrypted source files. Encrypt it with a random data key wrapped for both local use and recovery-passphrase restoration.
2. Add app-created Drive folder and file operations with `drive.file`, resumable uploads, retries, quota/revocation handling and version retention.
3. Verify newly uploaded archives before marking “Backed up”; retain a previous verified version.
4. Implement restore-version selection, download/decrypt/integrity check, staged schema migration and atomic replacement of local data. Add a visible last-verified timestamp and pending-change count.

**Deliverables:** S31/S06 complete backup/restore flows, redacted local diagnostic export, tests for wrong passphrase, corruption, insufficient storage, offline retry, revoked permission and quota exceeded. **Gate:** A real clean-install restore on the Realme GT2 recovers ledger, accounts, assets, documents, settings and chart totals exactly. No plaintext financial file is uploaded.

## Phase 7 — Integrations and institution coverage

**Work**

1. Test and label adapters for owner-provided Axis/HDFC/SBI/Kotak/ICICI and Groww/Zerodha/INDmoney formats as samples become available. Record exact file variants and extraction score per variant.
2. Confirm Google sign-in/Drive authorisation under the release signing certificate and public OAuth configuration.
3. Evaluate whether any optional notification-based bank alert import is reliable and permission-appropriate; if included, keep it provisional until reconciled against a statement.
4. Do not add a paid or unlicensed live-price source. Snapshot valuations show source date. Add a price adapter only after permissions, terms and no-fee status are verified.

**Deliverables:** Institution support matrix with fixtures and known limitations, integration smoke tests and privacy/permission review. **Gate:** No institution is labelled supported without passing its fixture suite; revoked/expired Google grants recover without losing local data.

## Phase 8 — Testing and hardening

**Work**

1. Unit tests for finance formulas, parser decisions, account ownership, duplicate detection, exact decimals and chart aggregation.
2. Instrumented Android tests for onboarding, manual transactions, import review, cash withdrawal, investment/FD details, backup and restore.
3. Test adversarial PDFs, very large files, network loss, low disk, app kill during import/upload, clock/time-zone change, account switch and schema upgrade.
4. Review privacy, licences, accessibility, performance and battery behavior; profile real statement imports on the Realme GT2.
5. Dogfood with redacted and then personally controlled real data. Compare statement balances and holdings manually for a representative period.

**Deliverables:** Test report mapped to [PRD success metrics](01-product-requirements.md), defect log and fixed critical findings. **Gate:** No critical data-loss, cross-owner leak, incorrect financial total, broken restore or unreviewed high-uncertainty import remains.

## Phase 9 — Signed APK, distribution and final polish

**Work**

1. Finalise icon/wordmark after name check, privacy explanation, licence notices, accessibility copy, screenshots and support instructions.
2. Generate a Ritik-owned signing key in a secure location, maintain an offline recovery copy, sign the APK and verify its certificate fingerprint matches Google OAuth configuration.
3. Install/update the signed APK on the Realme GT2; run all critical journeys and clean-install restore using the release build.
4. Publish through a no-fee channel selected by the owner when distribution is authorised. Keep release notes, checksum and known format limitations with the APK. Wider public release is a separate compliance/cost decision.

**Deliverables:** Signed release APK, install/update instructions, integrity checksum, supported-format list, privacy/licence documentation and final verification report. **Gate:** The installed release build—not merely a debug build—passes import, cash, wealth, chart and recovery journeys.

## Completion definition

The project is complete when all in-scope features in the PRD and every screen/state in the app-flow document work in the signed Android release, cloud restore has been demonstrated after reinstalling, supported file formats are fixture-tested, and zero paid services are required. Any omitted requirement or unverified external integration must be called out explicitly rather than described as finished.

## Items that require owner participation

These are limited to account/security actions that an agent cannot perform on the owner's behalf: Google OAuth consent, keeping the recovery key and APK signing key safe, choosing/authorising public distribution, and supplying redacted representative institution files. The engineering work around them can proceed independently once implementation is authorised.
