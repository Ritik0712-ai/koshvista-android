# KoshVista — App Flow and Interaction Specification

**Status:** Build-ready navigation contract  
**Applies to:** Android phone UI, including Realme GT2 / Android 14  
**Source of truth for data semantics:** [PRD](01-product-requirements.md) and [schema](05-backend-schema.md)

## 1. Global navigation and interaction rules

The authenticated shell has five bottom destinations: **Home**, **Activity**, **Import**, **Wealth**, **Settings**. Home has a prominent **Add** action; Activity and Wealth can also open it. Add opens a sheet with **Expense**, **Income**, **Transfer**, **Cash expense**, **Trade**, **FD/Bond**, and **Account**. The current tab retains its scroll/filter state when the user switches tabs. Android Back closes a dialog/sheet first, then returns to the previous screen; Back from a tab root exits to the device launcher after preserving pending local writes. Deep links into a specific record require an unlocked, matching owner vault.

All money screens show currency and an `as of` date. Every chart supports tap-to-filter and an accessible text summary. All writes are local transactions first; a backup is queued afterwards. A green “Saved” message means **saved on the phone**. Cloud status is shown separately as “Backed up,” “Waiting for connection,” or “Needs attention.” No screen should label an unsynced write “cloud saved.”

### Global states

| State | UI and action |
|---|---|
| Loading | Skeletons for local queries; progress with page/row counts for import; cancel remains visible where safe. |
| Offline | Small banner, local functions remain active; Backup page shows queued changes. |
| No permission | Explain why a permission is requested and provide **Grant access** and **Not now**; no dead-end screen. |
| Recoverable error | Plain-language reason, **Retry**, and a safe path back. Raw stack traces never shown. |
| Destructive action | Name the affected records, require confirmation, and show undo when technically possible. |
| Account switch | Lock and clear sensitive screen state before showing another owner's data. |
| Repeated tap | Disable commit button while processing; idempotency key prevents duplicate entry/import. |

### Search, filters and time selection

Search is available from Activity, Wealth and Import history. Filters are chips with an active-count indicator and **Clear**. Date presets are **This month**, **Last month**, **3 months**, **Year**, and **Custom**. Custom date validation rejects end before start. Switching tabs preserves filters; **Reset** explicitly restores defaults. Empty filtered results say “No records match these filters” and offer **Clear filters**, distinct from first-use empty states.

## 2. Entry, identity and recovery

### S01 Launch and vault check

- **Entry:** App icon or supported deep link. Check local schema, active owner, lock timeout, and available backups without blocking the first local frame on network.
- **Routes:** No owner → S02. Existing locked owner → S07. Valid unlocked session → S08. Migration needed → progress and then appropriate route.
- **Error:** Corrupt local vault offers **Restore from backup** (S06) or **Export diagnostics**; never silently resets data.

### S02 Welcome

- **Content:** Product purpose, offline/private processing, backup requirement, zero-fee explanation and **Continue with Google**.
- **Actions:** **Continue with Google** → S03; **Privacy details** → explanatory sheet; Android Back exits.
- **Error:** Network unavailable explains that a new identity needs connection; **Retry**. No misleading local-only onboarding path because cloud backup is a core requirement.

### S03 Google sign-in

- **Action:** System Credential Manager account selector. On success store stable Google subject ID and compare it with any local owner vault.
- **Routes:** Existing local owner → S07; no local vault but Drive granted/backup found → S05; new user → S04.
- **Cancel/error:** Return to S02 with **Try again**. A selected account with a different subject never opens the old owner's vault.

### S04 Drive authorisation

- **Content:** Explain app-created backup folder, file-limited access, encrypted archives, and that sign-in alone does not grant Drive access.
- **Actions:** **Allow Drive backup** → system OAuth consent; success → S05; **Not now** → S08 with persistent “Backup setup incomplete” banner and S27 shortcut; **Learn more** → details sheet.
- **Error:** Permission denied/revoked shows **Retry** and **Continue locally**. Do not loop consent prompts.

### S05 Recovery setup / backup discovery

- **New owner:** **Create recovery passphrase** (enter/confirm, strength guidance) → generate and display one-time recovery key; **I saved it** requires re-entry of a short verification segment → create encrypted vault → S08. **Back** returns to S04 without uploading an unprotected archive.
- **Existing Drive backup:** Show latest validated backup metadata and **Restore** → S06, or **Start empty** with a strong warning that a backup exists. Wrong account/unsupported archive is not offered as restorable.
- **Error:** Mismatched confirmation remains on page; device key generation error offers retry.

### S06 Restore wizard

- **Steps:** Select version → enter recovery passphrase → download → authenticate/decrypt/verify → preview record counts and date span → **Restore data** → stage/migrate/atomic swap → S08.
- **Actions:** **Older versions**, **Retry download**, **Cancel** before swap, **Replace local data** only after typed confirmation if data already exists.
- **Error:** Wrong passphrase, corrupt archive, insufficient storage, disconnected network, owner mismatch or unsupported future schema each get a specific message. Existing local vault remains intact after any failed restore.
- **Empty:** “No backup found in this account”; **Check another account** → S03, **Create new vault** → S05.

### S07 App unlock

- **Action:** Biometric/device credential prompt; success returns to requested screen or S08. **Use device PIN** invokes Android system credential. **Switch Google account** locks vault and routes S03.
- **Error:** Cancel stays locked; too many failures follow OS lockout. Offline unlock of an existing verified owner remains available.

## 3. Home and analytics

### S08 Home dashboard

- **Content:** Net worth, cash-on-hand, bank totals, liabilities, income/expense this period, backup status, upcoming maturities, spending histogram, category mix, net-worth line and investment allocation. Each card shows date/source or estimate where relevant.
- **Actions:** **Add** → add sheet; account card → S12; cash card → S13; net-worth/chart card → S09; spending bar/category segment → S10 with applied filters; maturity item → S23; backup badge → S27; **See all insights** → S11; **Import** → S18.
- **Empty:** Guided cards for **Add an account**, **Add cash**, **Import a statement**, **Import investments**. Charts show an explanatory zero-data illustration, never fake values.
- **Error:** One failed aggregate shows an error on that card and **Retry**; other cards stay usable. Missing FX rate labels affected value “Not valued.”

### S09 Analytics overview

- **Content:** Date-range selector, account/category filters, income/expense bars, daily histogram, net-worth trend, asset allocation, cash-flow breakdown and textual summaries.
- **Actions:** Tap point/segment → S10; **Compare periods** toggles comparison; **Export chart data** creates a user-selected CSV; **Reset filters** restores defaults.
- **Empty/error:** Clear cause (no activity/no valuation/filtered empty) with relevant entry action; chart query failure permits retry.

### S10 Chart drill-down

- **Content:** Chart title, selected series/period, formula/legend, transaction or holding list, total and source filters.
- **Actions:** Row → S15 or S22; **Change period/filter** returns updated drill-down; **Back to chart** restores S09/S08 state.
- **Empty:** No matching records; **Clear filters**. **Error:** Invalid saved chart link returns to S09.

### S11 Insights and alerts

- **Content:** Recurring payment suggestions, unusually high spending, budget warnings, upcoming FD/bond maturity and backup problems, each with source and timestamp.
- **Actions:** **View records** → S10/S15/S23/S27; **Dismiss** marks an insight dismissed; **Correct category** → S15; **Set reminder** → S24.
- **Empty:** “Nothing needs attention”; still show recent comparison summary. An uncertain suggestion is labelled “Suggestion.”

## 4. Accounts, activity and cash

### S12 Accounts list

- **Content:** Sections for Bank, Cash, Credit, Broker cash, Other assets and Liabilities; balance and as-of date for each.
- **Actions:** Row → S13; **Add account** → S14; **Manage order** saves display order; **Hide zero balances** toggles view only.
- **Empty:** **Add your first account**. **Error:** Data query retry, never substitute zero for missing balance.

### S13 Account detail, including cash

- **Content:** Balance trend, posted transactions, pending/review items, institution metadata, reconciliation status.
- **Actions:** **Add expense/income** → S16 preselected account; **Transfer** → S17; **Import statement** → S18 preselected account; **Edit account** → S14; **View transaction** → S15; **Reconcile** opens statement-balance comparison; cash account has **Cash spend** shortcut.
- **Empty:** “No activity yet” with Add and Import actions. **Error:** Account deleted/not owned returns S12.

### S14 Account editor

- **Fields:** Type, display name, institution (optional), currency, opening balance/date, masked last four digits (optional), colour/icon, visibility. Cash is a regular account with cash-specific shortcuts.
- **Actions:** **Save** validates unique owner-visible identity, commits, queues backup, returns S13; **Cancel** discards; **Archive** only if no unresolved links, otherwise explains what must be resolved.
- **Error:** Invalid amount/date or duplicate identity inline; database failure leaves form intact.

### S15 Activity list and transaction detail

- **List:** Search, date/account/category/type/status filters, grouped dates, debit/credit signs, source badge. Row opens detail. **Add** → S16. Bulk selection offers **Categorise**, **Mark transfer**, or **Delete manual entries** with preview and confirmation.
- **Detail:** Amount, account, date, merchant, category, splits, source excerpt/document, confidence, matching transfer/refund, edit history. **Edit** → S16; **Mark transfer** → S17; **Link refund** selects matching original; **View source** → S21; **Delete** confirms and audits. Imported correction preserves source.
- **Empty:** First-use Add/Import; filtered empty Clear filters. **Error:** Missing/deleted item returns to list with notice.

### S16 Transaction editor / quick cash expense

- **Fields:** Type (expense/income/refund/adjustment), amount, account, local date/time, category, merchant/payee, notes, optional attachment and split rows.
- **Actions:** **Save** checks positive entered amount, nonempty account and exact split total; creates signed ledger row, updates charts and queues backup; **Add split** adds row; **Remove split** rebalances only after confirmation; **Attach** uses picker; **Cancel** prompts if dirty.
- **Quick cash:** Opens with Cash + Expense selected and remembered recent category. **Save & add another** creates one entry then resets amount and note. Insufficient cash warns but permits an explicit correction/overdraft choice; it never silently changes balance.
- **Error:** Validation inline; duplicate tap blocked; failed write preserves form.

### S17 Transfer editor

- **Fields:** From, To, positive amount, date/time, optional fee, FX rate if currencies differ, note. The same account cannot be both sides.
- **Actions:** **Save transfer** atomically posts paired ledger rows and optional fee expense; **Cancel** discards; **Swap** exchanges accounts.
- **Special cases:** Bank → Cash = withdrawal; Cash → Bank = deposit; Bank → Broker cash = funding. Matching an imported debit/credit links existing entries rather than creating duplicates.
- **Error:** Missing counterpart prompts **Create destination account** → S14 and returns to S17; failed pair write rolls back both sides.

### S18 Budgets and recurring rules

- **Content:** Monthly/category limits, actual versus planned bars, recurring-pattern suggestions and reminders.
- **Actions:** **Add budget**, **Edit**, **Pause**, **Delete**, **Accept recurring suggestion**, **Ignore**. Save validates period and amount. Budget warnings route to S10.
- **Empty:** “Set a budget to compare planned and actual spending.” Imported recurring candidates are suggestions until accepted.

## 5. Document imports

### S19 Import hub

- **Content:** Four primary cards: **Bank statement**, **Portfolio/trade document**, **FD or bond**, **Other document**. History lists import date, source, status, accepted count and warnings.
- **Actions:** Card → Android picker → S20; history row → S22; **Supported formats** lists only fixture-validated formats; **Retry failed** resumes a retryable job.
- **Empty:** Explain supported PDF/CSV/image inputs and show **Choose a file**. **Error:** Picker cancelled returns unchanged; no broad storage permission request.

### S20 Import setup and processing

- **Setup:** Show file name/size/type, detected institution/document family, optional target account, date range, PDF password field when needed. **Change type/account** is always available.
- **Actions:** **Analyse file** starts staged extraction; **Choose another file** returns picker; **Cancel** removes uncommitted staging. Progress lists pages/rows and can be cancelled safely.
- **Error:** Wrong PDF password asks again without saving it; unsupported/corrupt/oversize file explains limit and offers another file; OCR failure offers retry or manual entry.
- **Success:** Candidate summary → S21. Identical file hash opens existing import with **View prior result**; overlapping but non-identical file continues to dedup review.

### S21 Import review

- **Content:** Accepted/uncertain/duplicate/rejected counts; source preview; list of candidates with confidence and field-level evidence. Tabs filter by status.
- **Actions:** **Edit candidate** opens field form with source side-by-side; **Accept**, **Reject**, **Mark duplicate**, **Link existing**, and **Apply category to similar** affect staged candidates; **Commit accepted** shows account/record totals and commits atomically; **Cancel import** keeps no posted changes.
- **Validation:** Debit/credit direction, date, amount, account/security identity and FD terms require confirmation if uncertain. Duplicate candidates cannot be committed until resolved. Portfolio screenshot commits a dated snapshot, with unknown cost basis shown as unknown.
- **Empty:** If no records found, show source preview and **Try different document type**, **Import manually**, **Report extraction issue** (local diagnostics export). **Error:** Commit failure leaves review intact and posted count zero.

### S22 Import result and source viewer

- **Result:** Source name/type, adapter version, import time, accepted/rejected/duplicate counts, linked records, warnings and backup status. **View records** opens S15/S25/S23; **View source** opens encrypted document viewer; **Undo import** previews linked edits and requires confirmation.
- **Viewer:** Page navigation/zoom; tap highlighted extracted region to open candidate/record; **Close** returns. PDFs remain in app-private storage.
- **Error:** Missing source file retains structured records but shows “Original unavailable”; corrupt source offers a fresh import.

## 6. Wealth, fixed income and liabilities

### S23 Wealth overview

- **Content:** Total valued assets, liabilities, allocation chart, broker cards, holdings, FD/bond maturities, dated snapshot badges and unvalued assets.
- **Actions:** **Import portfolio** → S19; **Add trade** → S25; **Add FD/Bond** → S26; holding → S24; liability → S28; allocation segment → S10.
- **Empty:** Add/import actions for holdings and fixed income; net-worth chart explains missing history.

### S24 Instrument/holding detail

- **Content:** Identifier, quantity, latest dated value, source, acquisition cost if known, trade history, snapshot history and valuation chart. Unknown cost basis disables profit percentage and says why.
- **Actions:** **Add trade** → S25; **Add snapshot** opens position form; **View source** → S22; **Edit instrument metadata**; **Delete snapshot** with confirmation.
- **Error:** Valuation unavailable shows last known value with date rather than implying a live quote.

### S25 Trade / position editor

- **Trade fields:** Broker account, instrument, buy/sell, trade date, quantity, unit price, fees/taxes, currency, optional funding transaction and source.
- **Snapshot fields:** Broker, instrument, quantity, observed price/value, observation date, source. Snapshot is not a trade.
- **Actions:** **Save trade/snapshot** validates exact decimals and ownership; **Link funding** searches ledger; **Cancel** discards.
- **Error:** Sell quantity above known holdings warns and requests explanation; missing acquisition data remains unknown. No automatic brokerage order is sent.

### S26 FD / bond editor and detail

- **Fields:** Product type, institution, account, principal/face value, start/issue date, maturity date, annual rate, compounding/payout schedule, interest type, optional identifier, tax/treatment notes, source and confidence.
- **Actions:** **Save** validates dates and terms, creates projected schedule; **Confirm terms** moves uncertain import to confirmed; **Record payout** posts a linked ledger income entry; **Set reminder**; **Mark matured/redeemed** prompts for actual proceeds; **Edit** recalculates projections with an audit event.
- **Empty:** No schedule when terms missing; ask for the missing fields. **Error:** Unsupported rate convention flags projection unavailable rather than producing a guessed return.

### S27 Reminders and maturity calendar

- **Content:** Chronological FD/bond maturities, interest payouts, credit-card due dates and accepted recurring bills.
- **Actions:** **Open item** → S26/S28/S15; **Snooze** adjusts notification only; **Mark done** records completion; **Notification settings** opens system prompt when necessary.
- **Empty:** “No upcoming dates”; **Add FD/Bond**. Denied notification permission keeps in-app calendar available.

### S28 Liability detail/editor

- **Fields:** Credit card/loan/other liability, institution, opening balance/date, statement/due dates, interest or minimum payment (optional), linked payment account.
- **Actions:** **Save**, **Record payment** (transfer), **Import statement**, **Set reminder**, **Archive when settled**. Imported charges/refunds follow ledger rules.
- **Error:** Missing interest terms suppress projections; liability never becomes a negative asset row by accident.

## 7. Settings, protection and ownership

### S29 Settings root

- **Rows/actions:** **Profile** → S30; **Backup & restore** → S31; **Security** → S32; **Categories & rules** → S33; **Currency & format** → S34; **Export data** → S35; **Import history** → S19; **About & licences** → S36; **Sign out** locks and routes S02/S03; **Delete my data** → S37.
- **Content:** Signed-in account, app version and backup status. Error rows show a retry action rather than a false “healthy” indicator.

### S30 Profile and account switch

- **Content:** Google display identity, stable internal owner status (not the raw subject ID), Drive consent status.
- **Actions:** **Switch account** confirms, locks and routes S03; **Reconnect Drive** → S04; **Sign out** clears session tokens and locks; local records remain encrypted until separately deleted.
- **Error:** Identity token expired prompts reauth online, while existing owner can still unlock locally if policy permits.

### S31 Backup and restore

- **Content:** Last verified backup time/version, pending change count, storage/permission status, recovery-key health and list of validated versions.
- **Actions:** **Back up now** queues expedited eligible work; **Restore** → S06; **Check backup** verifies latest; **Change recovery passphrase** rewraps key after old proof; **Manage versions** shows retention and explicit delete; **Reconnect Drive** → S04.
- **States:** Offline/queued, uploading progress, verifying, success, permission revoked, quota exceeded, wrong account, recovery key missing, corrupt version. Every failed state has a next action. Never mark an upload alone as verified.

### S32 Security

- **Actions:** Toggle biometric/device-lock use, choose auto-lock timeout, view recovery guidance, **Lock now**, **Review active owner**. Changing lock settings requires device credential.
- **Error:** Device lacks biometrics → device credential remains. Lock remains enforced for sensitive records.

### S33 Categories and learned rules

- **Content:** System/user categories, merchant mapping rules, import suggestion history.
- **Actions:** **Add**, **Rename**, **Merge**, **Archive**, **Delete rule**, **Reset suggestion**. Merge previews affected records; system categories cannot be deleted while referenced.
- **Empty:** Defaults are provided. A new custom category appears immediately in transaction editor.

### S34 Currency, date and appearance

- **Actions:** Choose base currency, number/date format, theme (system/light/dark), chart motion reduction and default dashboard period; **Save** updates display without changing original transaction amounts.
- **Error:** Missing FX data shows unavailable converted totals and the currencies affected.

### S35 Export

- **Options:** Transactions CSV, portfolio CSV, full encrypted archive, local diagnostic summary. Select date range and destination with Android document picker.
- **Actions:** **Export** generates and hands a file to selected destination; **Cancel** deletes temporary output. Plain CSV requires a privacy warning and device unlock.
- **Error:** Insufficient storage, destination unavailable or write failure preserves source data and offers retry.

### S36 About, privacy and licences

- **Content:** App/version, privacy summary, open-source acknowledgements, backup model, supported formats and support/diagnostic instructions.
- **Actions:** **View licence**, **Copy version**, **Export diagnostics**. No financial data is sent automatically.

### S37 Delete data

- **Choices:** **Delete this device's data**, **Delete Drive backups**, or **Delete both**. Show what each means, verify active identity and device unlock, then require typed confirmation.
- **Success:** Display separate local and Drive outcomes. If Drive is offline/revoked, do not claim remote deletion; offer retry and preserve a deletion job only if the owner explicitly chooses it.
- **Error:** Partial deletion is reported precisely. Signed-out state follows successful local deletion.

## 8. End-to-end route examples

1. **First use:** S01 → S02 → S03 → S04 → S05 → S08 → S12/S14 → S19/S20/S21/S22 → S08.
2. **Bank statement:** S08 or S13 → S19 → S20 → S21 → S22 → S15; import commit queues S31 backup.
3. **Cash withdrawal:** S15 imported bank debit → S17 match/choose Cash → S13 Cash → S08 updated charts.
4. **Cash purchase:** S08 Add → Cash expense/S16 → S13 Cash or S15 → S08 updated histogram.
5. **Portfolio screenshot:** S23 → S19 → S20 → S21 → S22 → S24 dated holding snapshot.
6. **FD certificate:** S23 → S19 → S20 → S21 → S26 confirm terms → S27 maturity reminder.
7. **Phone reinstall:** S01 → S02 → S03 → S04 → S05 backup found → S06 → S08 and S31 verified backup.

## 9. Accessibility and copy rules

Buttons use verbs such as “Save transfer” and “Commit 32 accepted records,” not generic “Done” when an action moves money data. Destructive actions state counts and owner. Text and icons never rely on colour alone. Each chart exposes a spoken title, period, unit, trend summary and a “View records” action. All touch targets meet Android guidance; large text must reflow without truncating amounts or consent language.
