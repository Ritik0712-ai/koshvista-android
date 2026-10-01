# KoshVista — Product Requirements Document

**Status:** Specification for approval and implementation  
**Product:** Native Android personal-finance app, distributed as a signed APK  
**First test device:** Realme GT2, Android 14  
**Working name:** KoshVista; public branding requires a separate availability check  
**Related documents:** [Technical requirements](02-technical-requirements.md), [App flows](03-app-flow.md), [Design brief](04-ui-ux-design-brief.md), [Data schema](05-backend-schema.md), [Implementation plan](06-implementation-plan.md)

## 1. Overview

KoshVista gives a person a trustworthy, visual view of their money: bank accounts, cash, income, expenses, investments, portfolios, fixed deposits (FDs), bonds, liabilities, and net worth. It imports bank statements and investment documents, extracts structured records on the device, detects duplicates, and asks for review only when evidence is uncertain. It works offline and automatically backs up encrypted data to the signed-in user's Google Drive when connectivity permits.

The product is **read-only with respect to banks and brokers**: it tracks money but never initiates transfers, trades, or financial-product purchases. It never requests banking passwords, PINs, or OTPs. “Real time” means newly entered or imported records immediately update the app; live bank balances and exchange prices depend on external data that cannot be assumed free or available.

## 2. Users and problem

### Primary user

An India-based individual who uses several banks and investment services, holds some cash, and wants the least possible bookkeeping. The initial owner uses a Realme GT2. The architecture must isolate each later user's records and backup under their own identity.

### Secondary users

- A person tracking a household's accounts and investments on one phone.
- A user with intermittent connectivity who still needs a complete local view.
- A user restoring financial history after uninstalling or changing phones.

### Problem statement

Financial information is scattered across bank PDFs, broker apps, investment screenshots, FD certificates, bond documents, and cash. Manual entry is repetitive; existing screenshots do not explain transaction history; a simple expense graph does not show the whole financial position. Users need one dependable ledger, clear visual explanations, and a recoverable copy of their data without mandatory fees.

## 3. Product principles and constraints

1. **Evidence before automation:** Every imported value records its source. Low-confidence extraction requires review.
2. **Financial maths is deterministic:** Balances, interest, totals, and chart aggregates derive from explicit records and formulas; an AI model cannot author a final amount without validation.
3. **Minimal effort:** Reuse categorisation corrections, recognise repeat imports, and make cash entry fast.
4. **Private by default:** Local processing, encrypted local storage and backup, narrow permissions, no advertising or data sale.
5. **No required payment:** No subscription, paid API, hosted server, market-data licence, or billing-dependent feature in the release contract. Capacity and third-party terms must be monitored and surfaced.
6. **Readable data visuals:** Every chart has a date range, units, accessible labels, and a route to underlying records.
7. **Recoverability:** The release cannot be called ready until a real uninstall/reinstall restore succeeds.

## 4. Core capabilities and acceptance requirements

| Area | Required behavior | Acceptance evidence |
|---|---|---|
| Identity | Google sign-in; local biometric/device-credential unlock; separate Drive authorisation; sign-out and account switch | The app never opens another user's local records after account switch. |
| Accounts | Bank, cash, credit card, broker cash and other asset/liability accounts; opening balance and currency | Each account shows a dated balance and history. |
| Ledger | Income, expense, transfer, refund, adjustment, split transaction, merchant, category, notes, attachment | Transfers between owned accounts do not inflate income or spending. |
| Cash | Dedicated cash account, quick cash spend/receive, ATM withdrawal and cash deposit transfer | Cash changes appear in balances and charts without double counting. |
| Import | PDF, CSV, image and screenshot selection; document type detection; statement/holding/FD/bond extraction; review and commit | Reimporting the same or overlapping bank statement does not duplicate accepted transactions. |
| Investments | Broker accounts, instruments, positions, trades where documented, dated holding/valuation snapshots, allocation | A snapshot is labelled as such; missing acquisition cost is not invented. |
| FDs and bonds | Principal, rate, term, payout, maturity, status, linked institution and source, projected cash flows | Estimated values are marked; maturity reminders come from confirmed terms. |
| Liabilities | Credit-card balances and other manually/imported liabilities, repayments, due dates | Net worth subtracts outstanding liability balances. |
| Visual dashboard | Cash flow, spending histogram, category mix, net-worth series, asset allocation, maturity timeline | Chart totals reconcile to the filtered underlying records. |
| Insights | Recurring payments, category suggestions, anomalies and period comparisons | Insight text points to source records and can be dismissed/corrected. |
| Backup | Versioned encrypted Drive backup, automatic retry, restore, last-success status, recovery key | Restore is validated on a clean install and identifies an incomplete/corrupt backup. |
| Data control | Search, edit, undo where possible, export, delete account data and Drive backups separately | User can obtain and remove their records without contacting a developer. |

### Import confidence policy

Each extracted candidate has source page/region, parser version, confidence, and validation result. A document is committed only after required fields pass structural checks; ambiguous dates, debit/credit direction, account identity, security identity, or FD terms enter a review queue. A user correction changes the structured record while preserving original evidence. A PDF may be password protected; the password is used in memory for that import and is not saved.

## 5. User stories

- As a user, I can sign in once and use my records offline, so a weak network does not block daily tracking.
- As a user, I can upload a bank PDF and review only uncertain rows, so statement import is faster than manual entry.
- As a user, I can upload a portfolio screenshot and see a dated snapshot, so I can track assets without pretending the image reveals purchase history.
- As a user, I can add a cash purchase in a few taps, so cash is included in my financial picture.
- As a user, I can mark a bank debit as an ATM withdrawal, so it moves money to cash rather than appearing as spending.
- As a user, I can upload an FD or bond document and see confirmed terms and maturity reminders, so I do not have to re-enter them.
- As a user, I can tap any chart value to see the records behind it, so I trust the visualisations.
- As a user, I can reinstall the app, sign in, supply my recovery key, and restore, so uninstalling does not erase my history.
- As a later user, I see only my own accounts, documents and backups, even if another person used the same phone.

## 6. MVP scope and release contract

Here **MVP** means the smallest **complete, safe public-release candidate**, not a disposable demo. It includes all areas the owner explicitly requested: account and cash ledger, expenses, investments and portfolio snapshots, FDs/bonds, chart-led dashboard, document imports with review, Google sign-in, encrypted Drive backup and tested restore. Support for a named institution requires validated examples of its formats; generic import and manual correction remain available for others.

The first release is complete only when critical end-to-end journeys work: initial setup, bank import, cash transfer, investment import, FD import, backup, clean reinstall and restore. A polished screen without a working data path does not meet scope.

### Features to avoid in version 1

- Trading, bank transfers, bill payment, credit offers, or financial advice presented as a recommendation.
- Unlicensed live market-price feeds and claims of live bank balances without a real authorised feed.
- Scraping bank/broker logins or storing their credentials, PINs, or OTPs.
- Broad SMS-inbox access, full-Drive access, or device-wide file permissions when a file picker suffices.
- A mandatory paid cloud AI API or a server that creates ongoing cost.
- Multi-device simultaneous editing until conflict resolution and security are proven; restore from backup is in scope.
- Public social/community features, ads, gamified scores, or opaque “AI health” scores.

These exclusions preserve the zero-fee and trust requirements; they do not reduce the requested tracking, visualisation, import or backup scope.

## 7. Success metrics and quality targets

Targets are release criteria to measure with test fixtures and dogfooding, not claims of current performance.

| Metric | Target / measurement |
|---|---|
| Core journey completion | 100% of listed critical journeys pass on the Realme GT2 before release. |
| Backup recovery | 100% of clean-install restore tests recover accounts, ledger, assets, documents and settings; corrupt backup is rejected clearly. |
| Ledger integrity | Zero unexplained balance differences in golden test cases for transfers, refunds, split entries and investment cash flows. |
| Duplicate imports | Zero duplicates in the overlapping-statement fixture suite. |
| Import quality | At least 95% of validated rows from explicitly supported sample formats need no correction; report results by format, never as a blanket bank claim. |
| Manual effort | Median reviewed rows per supported statement and taps for cash expense, tracked during owner dogfooding. Goal: cash expense in four taps or fewer after unlock. |
| Visual trust | Every chart aggregate matches the same filtered ledger query and provides drill-down. |
| Cost | Zero required paid services or automatic charges for normal use. |
| Accessibility | All core journeys usable with large text and screen-reader labels; charts have text summaries. |

## 8. Assumptions, dependencies, and unresolved inputs

- Base currency and default locale are INR and India, but records retain an ISO currency code and can represent other currencies.
- The owner will eventually supply **redacted, representative** files from their actual institutions. Until then, institutional coverage cannot be truthfully certified.
- The user must grant Google OAuth consent, keep a recovery secret, and keep enough space in their Drive. A missing/revoked permission or full Drive must create a visible backup alert.
- Public use requires Google OAuth configuration and review of current Android distribution rules. App signing credentials must be owned and backed up by Ritik.
- “Close the office” from the earlier conversation was unclear and has not been converted into a requirement.

## 9. Source notes

- [Android offline-first architecture](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [ML Kit on-device text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [Google Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Android app distribution outside Play](https://developer.android.com/distribute/marketing-tools/alternative-distribution)
