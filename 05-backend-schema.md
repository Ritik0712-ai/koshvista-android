# KoshVista — Local Backend and Backup Schema

**Status:** Logical schema for Room/SQLCipher implementation  
**Companion:** [Technical requirements](02-technical-requirements.md), [PRD](01-product-requirements.md)

## 1. Storage model and conventions

There is **no hosted application database** in the release architecture. Room/SQLCipher on each Android device is the transactional source of truth; the user's Google Drive holds versioned encrypted backups of that source. This file is the complete backend data contract for the app, including ownership and authentication metadata.

SQLite types are `TEXT`, `INTEGER` and `BLOB`. IDs are lowercase UUID strings (`TEXT`), except `owners.owner_id`, which is the stable Google subject identifier. Every owner-scoped table includes `owner_id TEXT NOT NULL`, `id TEXT PRIMARY KEY`, `created_at_ms INTEGER NOT NULL`, and `updated_at_ms INTEGER NOT NULL`, unless noted. Every owner-scoped parent has `UNIQUE(owner_id,id)`, and child references use **composite foreign keys** `(owner_id, parent_id)` so a row cannot reference another owner's data. Enable `PRAGMA foreign_keys=ON` and use Room migrations; no destructive fallback migration in production.

Money uses `INTEGER` signed **minor units** (paise for INR) and `currency_code TEXT NOT NULL` (ISO 4217). Decimal quantities/prices/rates use canonical decimal `TEXT`, parsed with `BigDecimal`; SQLite/Java floating point is prohibited for financial calculations. UTC epoch milliseconds are the machine time; `local_date TEXT` is `YYYY-MM-DD` as read or confirmed from the source. Enums below are constrained strings, validated in code and with `CHECK` where feasible. Nullable columns are explicitly marked `?`; all others are required. A field ending `_id` refers to an owner-matched parent unless stated otherwise.

## 2. Identity, access and configuration

### `owners`

`owner_id TEXT PRIMARY KEY`; `email_display TEXT?`; `display_name TEXT?`; `locale_tag TEXT NOT NULL DEFAULT 'en-IN'`; `base_currency TEXT NOT NULL DEFAULT 'INR'`; `created_at_ms INTEGER`; `updated_at_ms INTEGER`; `vault_schema_version INTEGER`; `state TEXT CHECK(active|locked|pending_deletion)`. Email is display data, never an identity key. No bank credentials, Google password or backup passphrase column exists.

### `local_sessions`

`owner_id TEXT PRIMARY KEY REFERENCES owners`; `last_identity_verified_at_ms INTEGER?`; `last_unlock_at_ms INTEGER?`; `lock_timeout_seconds INTEGER NOT NULL`; `drive_consent_state TEXT CHECK(not_requested|granted|denied|revoked|expired)`; `drive_account_hint TEXT?`; `active INTEGER CHECK(0|1)`. At most one row may have `active=1` (partial unique index). OAuth tokens remain in platform-controlled storage and are **not** stored here or backed up.

### `owner_settings`

Common columns; `key TEXT`; `value_json TEXT`; `UNIQUE(owner_id,key)`. Settings include theme, default period, notification choices and format preferences. Sensitive settings are not plain DataStore values. DataStore may cache non-sensitive UI preferences, but this table is authoritative for backup/restore.

## 3. Accounts and ledger

### `accounts`

Common columns; `type TEXT CHECK(bank|cash|credit_card|broker_cash|asset|liability)`; `name TEXT`; `institution_name TEXT?`; `masked_identifier TEXT?`; `currency_code TEXT`; `opening_balance_minor INTEGER`; `opening_local_date TEXT`; `status TEXT CHECK(active|archived)`; `sort_order INTEGER`; `color_token TEXT?`; `notes_cipher_ref TEXT?`. Each owner can have multiple cash accounts; one may be marked default via `is_default_cash INTEGER CHECK(0|1)` and a partial unique owner index. Current balance is computed from opening balance plus posted rows; no independently mutable `current_balance` column.

### `categories`

Common columns; `name TEXT`; `kind TEXT CHECK(expense|income|both)`; `parent_id TEXT?`; `icon_key TEXT?`; `color_token TEXT?`; `is_system INTEGER`; `is_archived INTEGER`; `sort_order INTEGER`. `parent_id` has owner-matched self-FK. `UNIQUE(owner_id,kind,name)` for active names is enforced via normalised name/index or repository validation.

### `merchants`

Common columns; `display_name TEXT`; `normalised_key TEXT`; `default_category_id TEXT?`; `logo_local_ref TEXT?`; `is_archived INTEGER`. `UNIQUE(owner_id,normalised_key)`; category FK owner-matched. Store no remote tracking identifier.

### `transfer_groups`

Common columns; `transfer_kind TEXT CHECK(internal|cash_withdrawal|cash_deposit|broker_funding|repayment|fx)`; `status TEXT CHECK(complete|needs_review)`; `note TEXT?`; `fx_rate_decimal TEXT?`; `fee_transaction_id TEXT?`. Exactly two posted sides are enforced by a domain transaction at commit, with a verification query; deletion/undo reverses or removes both sides atomically. Cross-currency transfer requires both amounts and a recorded FX rate.

### `transactions`

Common columns; `account_id TEXT`; `posted_at_ms INTEGER?`; `local_date TEXT`; `description TEXT`; `normalised_description TEXT?`; `merchant_id TEXT?`; `category_id TEXT?`; `amount_minor INTEGER`; `currency_code TEXT`; `kind TEXT CHECK(expense|income|transfer|refund|adjustment|fee|trade_cash)`; `status TEXT CHECK(posted|pending_review|void)`; `transfer_group_id TEXT?`; `source_document_id TEXT?`; `import_job_id TEXT?`; `source_row_key TEXT?`; `source_fingerprint TEXT?`; `refund_of_transaction_id TEXT?`; `note TEXT?`; `is_user_edited INTEGER`; `version INTEGER`. Every FK is owner-matched. A bank-provided stable source key is unique per owner/account when non-null; a fuzzy fingerprint is **not** unique because two legitimate transactions may share date/amount/description. `amount_minor != 0` unless a documented zero-value reconciliation adjustment is permitted. Refund and transfer categories must follow ledger rules in the TRD.

### `transaction_splits`

Common columns; `transaction_id TEXT`; `category_id TEXT`; `amount_minor INTEGER`; `memo TEXT?`; `position INTEGER`. Owner-matched FKs. A transaction with splits has no independently counted parent category; signed split amounts must total the parent signed amount exactly, enforced in repository transaction and test fixtures.

### `transaction_links`

Common columns; `from_transaction_id TEXT`; `to_transaction_id TEXT`; `kind TEXT CHECK(refund|duplicate_candidate|investment_funding|reconciliation_match)`; `confidence_decimal TEXT?`; `confirmed_by_user INTEGER`. `UNIQUE(owner_id,from_transaction_id,to_transaction_id,kind)`; no self-link. Useful where one-to-one `refund_of` is insufficient.

### `account_balance_observations`

Common columns; `account_id TEXT`; `observed_local_date TEXT`; `balance_minor INTEGER`; `currency_code TEXT`; `source_document_id TEXT?`; `source_label TEXT?`; `confidence_decimal TEXT?`. This is statement evidence and does not silently rewrite the computed balance. Index by owner/account/date descending.

### `reconciliations`

Common columns; `account_id TEXT`; `observation_id TEXT`; `computed_balance_minor INTEGER`; `difference_minor INTEGER`; `status TEXT CHECK(matched|unresolved|adjusted)`; `resolved_transaction_id TEXT?`; `reviewed_at_ms INTEGER?`. An adjustment needs an explicit linked transaction and audit event.

### `budgets`

Common columns; `name TEXT`; `category_id TEXT?`; `account_id TEXT?`; `period TEXT CHECK(monthly|weekly|custom)`; `start_local_date TEXT`; `end_local_date TEXT?`; `limit_minor INTEGER`; `currency_code TEXT`; `alert_threshold_decimal TEXT`; `status TEXT CHECK(active|paused|archived)`. Limits are positive. “Actual” is calculated from posted expense splits/rows, excluding transfers.

### `recurring_rules`

Common columns; `merchant_id TEXT?`; `category_id TEXT?`; `account_id TEXT?`; `pattern_json TEXT`; `expected_amount_minor INTEGER?`; `currency_code TEXT?`; `next_expected_local_date TEXT?`; `status TEXT CHECK(suggested|accepted|paused|dismissed)`; `source_transaction_ids_json TEXT`. Suggested patterns do not create posted transactions.

## 4. Sources and import pipeline

### `source_documents`

Common columns; `kind TEXT CHECK(bank_statement|broker_statement|portfolio_screenshot|fd_certificate|bond_document|receipt|other)`; `display_name TEXT`; `mime_type TEXT`; `size_bytes INTEGER`; `sha256_hex TEXT`; `encrypted_file_ref TEXT`; `original_created_at_ms INTEGER?`; `institution_hint TEXT?`; `page_count INTEGER?`; `retention_state TEXT CHECK(retained|user_removed)`; `imported_at_ms INTEGER?`. `UNIQUE(owner_id,sha256_hex)` may map an identical file to one stored blob while allowing multiple import attempts. File bytes are encrypted outside SQLite in app-private storage and included in full backups.

### `document_links`

Common columns; `document_id TEXT`; `target_type TEXT CHECK(transaction|account|trade|snapshot|fixed_income|liability)`; `target_id TEXT`; `page_number INTEGER?`; `region_json TEXT?`; `source_excerpt_cipher_ref TEXT?`. `target_id` ownership is checked by a typed repository because SQLite cannot make one FK point to several tables. Source excerpt is sensitive.

### `import_jobs`

Common columns; `document_id TEXT`; `adapter_key TEXT`; `adapter_version TEXT`; `detected_institution TEXT?`; `selected_account_id TEXT?`; `status TEXT CHECK(staged|processing|review|committing|completed|failed|cancelled)`; `started_at_ms INTEGER`; `finished_at_ms INTEGER?`; `pages_processed INTEGER`; `rows_detected INTEGER`; `accepted_count INTEGER`; `duplicate_count INTEGER`; `rejected_count INTEGER`; `error_code TEXT?`; `idempotency_key TEXT`; `UNIQUE(owner_id,idempotency_key)`. Commit is atomic and retry-safe. Staged jobs are never included as posted totals.

### `import_candidates`

Common columns; `import_job_id TEXT`; `candidate_type TEXT CHECK(transaction|trade|position|fixed_income|balance)`; `source_row_key TEXT?`; `source_location_json TEXT?`; `extracted_json TEXT`; `edited_json TEXT?`; `confidence_decimal TEXT`; `validation_errors_json TEXT`; `decision TEXT CHECK(unreviewed|accepted|rejected|duplicate|linked)`; `linked_record_id TEXT?`; `reviewed_at_ms INTEGER?`. Candidate JSON is encrypted by SQLCipher. Exact field normalisation and validation are adapter-versioned; the UI highlights differences between extracted and edited values.

## 5. Investments, fixed income and liabilities

### `instruments`

Common columns; `asset_class TEXT CHECK(equity|mutual_fund|etf|bond|government_security|gold|crypto|other)`; `name TEXT`; `symbol TEXT?`; `isin TEXT?`; `exchange TEXT?`; `currency_code TEXT`; `identity_status TEXT CHECK(confirmed|needs_review)`; `UNIQUE(owner_id,isin)` when ISIN is present. Names/symbols are not assumed globally unique.

### `investment_trades`

Common columns; `broker_account_id TEXT`; `instrument_id TEXT`; `side TEXT CHECK(buy|sell|reinvest|other)`; `trade_local_date TEXT`; `quantity_decimal TEXT`; `unit_price_decimal TEXT`; `fees_minor INTEGER`; `tax_minor INTEGER`; `gross_minor INTEGER`; `net_minor INTEGER`; `currency_code TEXT`; `funding_transaction_id TEXT?`; `source_document_id TEXT?`; `import_job_id TEXT?`; `external_trade_id TEXT?`; `status TEXT CHECK(confirmed|needs_review|void)`. Broker account is `accounts.type=broker_cash` or a linked investment account representation. Enforce positive quantity and price with BigDecimal validation. `UNIQUE(owner_id,broker_account_id,external_trade_id)` when a stable ID exists.

### `position_snapshots`

Common columns; `broker_account_id TEXT`; `observed_at_ms INTEGER`; `local_date TEXT`; `source_document_id TEXT?`; `import_job_id TEXT?`; `total_value_minor INTEGER?`; `currency_code TEXT`; `status TEXT CHECK(confirmed|needs_review)`; `snapshot_kind TEXT CHECK(holdings|valuation_only)`. One screenshot or broker export can produce one snapshot with many lines. A snapshot is not a trade.

### `position_snapshot_lines`

Common columns; `snapshot_id TEXT`; `instrument_id TEXT`; `quantity_decimal TEXT`; `observed_unit_price_decimal TEXT?`; `observed_value_minor INTEGER?`; `cost_basis_minor INTEGER?`; `currency_code TEXT`; `confidence_decimal TEXT`; `UNIQUE(owner_id,snapshot_id,instrument_id)` unless a source has genuinely separate lots, in which case create distinct line keys. Unknown cost basis remains `NULL`, never zero.

### `valuations`

Common columns; `instrument_id TEXT`; `valued_at_ms INTEGER`; `unit_price_decimal TEXT`; `currency_code TEXT`; `source_type TEXT CHECK(imported_snapshot|manual|permitted_feed)`; `source_document_id TEXT?`; `is_stale INTEGER`; `confidence_decimal TEXT?`. `UNIQUE(owner_id,instrument_id,valued_at_ms,source_type)`. No “live” badge unless a permitted feed exists and age threshold is met.

### `fixed_income_contracts`

Common columns; `product_type TEXT CHECK(fd|bond|government_security|other)`; `name TEXT`; `institution_name TEXT`; `linked_account_id TEXT?`; `instrument_id TEXT?`; `principal_minor INTEGER`; `currency_code TEXT`; `start_local_date TEXT`; `maturity_local_date TEXT`; `annual_rate_decimal TEXT?`; `interest_method TEXT CHECK(simple|compound|coupon|floating|unknown)`; `compounds_per_year INTEGER?`; `payout_frequency TEXT CHECK(at_maturity|monthly|quarterly|half_yearly|yearly|custom|unknown)`; `expected_maturity_minor INTEGER?`; `actual_redemption_minor INTEGER?`; `identifier_masked TEXT?`; `source_document_id TEXT?`; `terms_status TEXT CHECK(confirmed|needs_review|incomplete)`; `lifecycle TEXT CHECK(active|matured|redeemed|cancelled)`. Projections are recalculated from confirmed terms and retain a formula version.

### `fixed_income_cashflows`

Common columns; `contract_id TEXT`; `due_local_date TEXT`; `kind TEXT CHECK(interest|coupon|principal|tax|other)`; `projected_minor INTEGER?`; `actual_minor INTEGER?`; `currency_code TEXT`; `actual_transaction_id TEXT?`; `projection_formula_version TEXT?`; `status TEXT CHECK(projected|posted|skipped|needs_review)`. Projected rows do not enter actual income/net worth cash balances.

### `liability_terms`

Common columns; `account_id TEXT UNIQUE per owner`; `liability_type TEXT CHECK(credit_card|loan|other)`; `statement_day INTEGER?`; `due_day INTEGER?`; `annual_rate_decimal TEXT?`; `minimum_payment_minor INTEGER?`; `linked_payment_account_id TEXT?`; `terms_status TEXT CHECK(confirmed|incomplete)`. The authoritative outstanding amount is the linked liability account balance, not this metadata.

### `fx_rates`

Common columns; `base_currency TEXT`; `quote_currency TEXT`; `rate_decimal TEXT`; `as_of_local_date TEXT`; `source_type TEXT CHECK(manual|imported|permitted_feed)`; `source_document_id TEXT?`; `UNIQUE(owner_id,base_currency,quote_currency,as_of_local_date,source_type)`. Conversion uses the latest eligible rate at or before the valuation date and labels its age.

## 6. Reminders, suggestions and audit

### `reminders`

Common columns; `target_type TEXT CHECK(fixed_income|liability|recurring|custom)`; `target_id TEXT?`; `title TEXT`; `due_at_ms INTEGER`; `notify_at_ms INTEGER?`; `state TEXT CHECK(pending|snoozed|done|cancelled)`; `notification_id INTEGER?`; `note TEXT?`. A type-aware ownership lookup validates `target_id`. Notification permission denial does not delete the reminder.

### `insights`

Common columns; `kind TEXT CHECK(recurring|anomaly|period_comparison|budget|maturity|backup)`; `title TEXT`; `explanation TEXT`; `evidence_record_ids_json TEXT`; `computed_at_ms INTEGER`; `confidence_decimal TEXT?`; `state TEXT CHECK(active|dismissed|expired)`; `rule_version TEXT`. No insight is a posted transaction or investment recommendation.

### `categorisation_rules`

Common columns; `match_type TEXT CHECK(merchant|narrative|account|custom)`; `match_value TEXT`; `category_id TEXT`; `priority INTEGER`; `source TEXT CHECK(user|learned)`; `is_active INTEGER`; `last_applied_at_ms INTEGER?`. User rules outrank learned suggestions. Rules must not silently recategorise historical corrected rows without preview.

### `audit_events`

Common columns; `entity_type TEXT`; `entity_id TEXT`; `event_type TEXT`; `occurred_at_ms INTEGER`; `actor TEXT CHECK(user|import|system)`; `before_hash TEXT?`; `after_hash TEXT?`; `details_json TEXT?`; `source_job_id TEXT?`. Store minimal non-secret change metadata; sensitive before/after values remain in encrypted vault only if needed for undo. Index by owner/entity/time.

## 7. Backup and local sync metadata

### `backup_jobs`

Common columns; `trigger TEXT CHECK(local_write|manual|periodic|restore_safety)`; `status TEXT CHECK(queued|running|retry|verified|failed|cancelled)`; `enqueued_at_ms INTEGER`; `started_at_ms INTEGER?`; `finished_at_ms INTEGER?`; `attempt_count INTEGER`; `next_retry_at_ms INTEGER?`; `error_code TEXT?`; `content_revision INTEGER`; `remote_version_id TEXT?`; `UNIQUE(owner_id,content_revision)` for coalesced ordinary jobs. Each local commit increments an owner content revision; a newer revision supersedes older queued snapshots, never a verified remote version.

### `backup_versions`

Common columns; `drive_file_id TEXT`; `manifest_file_id TEXT?`; `archive_format_version INTEGER`; `vault_schema_version INTEGER`; `content_revision INTEGER`; `created_remote_at_ms INTEGER?`; `verified_at_ms INTEGER?`; `archive_size_bytes INTEGER`; `archive_sha256_hex TEXT`; `encrypted_owner_binding BLOB`; `record_counts_json TEXT?`; `state TEXT CHECK(uploaded|verified|corrupt|deleted)`; `UNIQUE(owner_id,drive_file_id)`. This is local cache of remote metadata; restore can rebuild it by listing app-owned Drive files. Owner binding is checked after authenticated decryption.

### Archive envelope and manifest

The **unencrypted authenticated header** contains only `format_version`, `cipher`, `kdf`, `salt`, `nonce` and wrapped-key metadata required to decrypt; it is authenticated as AEAD associated data and contains no account names or amounts. The **encrypted manifest** contains `vault_schema_version`, `owner_subject_hash`, `snapshot_created_at_ms`, `content_revision`, `database_file_name`, `document_entries[{id,path,sha256,size}]`, `record_counts`, `compression` and `integrity_hashes`. Each uploaded archive uses fresh nonces; old versions are immutable.

## 8. Indexes and database invariants

Create these indexes in addition to primary keys, owner-scoped unique constraints and FK indexes:

| Table | Index |
|---|---|
| `transactions` | `(owner_id,account_id,local_date DESC,id)`; `(owner_id,category_id,local_date DESC)`; `(owner_id,merchant_id,local_date DESC)`; partial unique `(owner_id,account_id,source_row_key)` where key non-null; `(owner_id,source_fingerprint)` nonunique. |
| `accounts` | `(owner_id,status,sort_order)`; partial unique `(owner_id)` where `type='cash' AND is_default_cash=1`. |
| `source_documents` | unique `(owner_id,sha256_hex)`; `(owner_id,kind,created_at_ms DESC)`. |
| `import_jobs` | `(owner_id,status,started_at_ms DESC)`; unique idempotency key. |
| `import_candidates` | `(owner_id,import_job_id,decision)`; `(owner_id,source_row_key)`. |
| `investment_trades` | `(owner_id,instrument_id,trade_local_date DESC)`; `(owner_id,broker_account_id,trade_local_date DESC)`. |
| `position_snapshots` | `(owner_id,broker_account_id,observed_at_ms DESC)`. |
| `fixed_income_contracts` | `(owner_id,lifecycle,maturity_local_date)`; `(owner_id,institution_name)`. |
| `fixed_income_cashflows` / `reminders` | `(owner_id,status,due_local_date)` / `(owner_id,state,due_at_ms)`. |
| `backup_jobs` / `backup_versions` | `(owner_id,status,enqueued_at_ms)` / `(owner_id,state,verified_at_ms DESC)`. |

Room migrations must preserve every ledger row and verify counts/checksums before migration completes. Repository transaction boundaries enforce paired transfers, split sums, import atomicity, owner match and backup revision increments. Foreign-key failures are never recovered by silently dropping rows. A periodic integrity check compares account calculations with statement observations and backup manifests; discrepancies become review tasks.

### Relationship and foreign-key map

Every listed child relationship is a composite `(owner_id, child_fk) → (owner_id, parent.id)` foreign key with `ON DELETE RESTRICT` for posted financial records. Non-financial drafts may use `CASCADE` only as noted. Optional fields enforce the FK when non-null.

| Child table | Parent relationship(s) |
|---|---|
| `local_sessions`, `owner_settings` | `owners` via `owner_id` |
| `accounts` | `owners` |
| `categories` | `owners`; optional `parent_id → categories` |
| `merchants` | `owners`; optional `default_category_id → categories` |
| `transfer_groups` | `owners`; optional `fee_transaction_id → transactions` |
| `transactions` | `accounts`; optional `merchant_id → merchants`, `category_id → categories`, `transfer_group_id → transfer_groups`, `source_document_id → source_documents`, `import_job_id → import_jobs`, `refund_of_transaction_id → transactions` |
| `transaction_splits` | `transaction_id → transactions`, `category_id → categories`; may cascade only when an unposted draft transaction is discarded |
| `transaction_links` | `from_transaction_id` and `to_transaction_id → transactions` |
| `account_balance_observations` | `account_id → accounts`; optional `source_document_id → source_documents` |
| `reconciliations` | `account_id → accounts`, `observation_id → account_balance_observations`; optional `resolved_transaction_id → transactions` |
| `budgets` | optional `category_id → categories`, `account_id → accounts` |
| `recurring_rules` | optional `merchant_id → merchants`, `category_id → categories`, `account_id → accounts` |
| `source_documents` | `owners` |
| `document_links` | `document_id → source_documents`; polymorphic target validated by typed repository |
| `import_jobs` | `document_id → source_documents`; optional `selected_account_id → accounts` |
| `import_candidates` | `import_job_id → import_jobs`; may cascade when an uncommitted job is cancelled |
| `instruments` | `owners` |
| `investment_trades` | `broker_account_id → accounts`, `instrument_id → instruments`; optional `funding_transaction_id → transactions`, `source_document_id → source_documents`, `import_job_id → import_jobs` |
| `position_snapshots` | `broker_account_id → accounts`; optional `source_document_id → source_documents`, `import_job_id → import_jobs` |
| `position_snapshot_lines` | `snapshot_id → position_snapshots`, `instrument_id → instruments` |
| `valuations` | `instrument_id → instruments`; optional `source_document_id → source_documents` |
| `fixed_income_contracts` | optional `linked_account_id → accounts`, `instrument_id → instruments`, `source_document_id → source_documents` |
| `fixed_income_cashflows` | `contract_id → fixed_income_contracts`; optional `actual_transaction_id → transactions` |
| `liability_terms` | `account_id → accounts`; optional `linked_payment_account_id → accounts` |
| `fx_rates` | optional `source_document_id → source_documents` |
| `reminders`, `insights`, `audit_events` | Polymorphic/evidence record IDs checked by typed repository and owner query |
| `categorisation_rules` | `category_id → categories` |
| `backup_jobs`, `backup_versions` | `owners` |

Each FK column has a child-side index beginning with `owner_id`; compound search indexes above may satisfy this when their leftmost fields match. Owner ID must be present in every join condition, not merely filtered after a join. The app uses archival/void statuses for posted financial rows rather than cascading deletions through a user's history.

## 9. Ownership, permissions and deletion rules

- The active Google subject is the only owner whose vault can be opened after local unlock. All owner-scoped DAO methods require `owner_id`; no unfiltered `SELECT *` is exposed to UI code.
- A selected source file is copied into that owner's encrypted app-private area and cannot be queried by another owner. Document link targets are checked for ownership before insert.
- Drive access is scoped to the signed-in Google user and app-created files. A downloaded archive with a different authenticated owner binding is rejected.
- Signing out locks data; deleting local data removes that owner's database rows, encrypted files, cached keys and session. Deleting Drive backups is a separate, explicitly requested remote action with reported result.
- Other users later get separate vaults and Drive folders. There is no shared household role, admin override or cross-user read permission in the release.
