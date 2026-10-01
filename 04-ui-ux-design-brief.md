# KoshVista — UI/UX Design Brief

**Audience:** Android UI designer or coding agent  
**Product behavior:** [PRD](01-product-requirements.md) and [app flow](03-app-flow.md)  
**Target form factor:** Phone-first native Android; first device Realme GT2 / Android 14

## 1. Design intent

KoshVista should feel like a calm financial instrument: precise, private, confident and visually rich. The interface is led by **real data and clear charts**, not decoration. It should make a user's entire position understandable in seconds while still exposing the source of every number. The personality is modern and editorial, with restrained colour and generous space; avoid the neon trading-terminal look or a generic fintech gradient.

The name is a working name. Do not bake a logo wordmark into raster assets until the public name is cleared.

## 2. Visual language

### Palette (design tokens)

| Token | Light | Dark | Use |
|---|---|---|---|
| `surface` | `#F6F5F1` | `#111713` | Page background |
| `card` | `#FFFFFF` | `#1B241F` | Cards/sheets |
| `ink` | `#17241C` | `#F2F6F1` | Primary text |
| `muted` | `#5B6A60` | `#A8B8AD` | Secondary text |
| `primary` | `#176B4B` | `#6AD6A2` | Primary buttons, active tab |
| `primary-soft` | `#DDEFE4` | `#224634` | Selection, chart fill |
| `income` | `#178A65` | `#6AD6A2` | Positive flow |
| `expense` | `#C65B50` | `#FF998B` | Negative flow |
| `warning` | `#A86A17` | `#F1BD67` | Review/pending |
| `info` | `#396EA8` | `#8FBFFF` | Links, neutral alerts |
| `divider` | `#DFE5DE` | `#36453B` | Boundaries |

Use one semantic meaning for each colour, and always pair it with a sign, label or icon. Credit/debit meaning must remain clear in monochrome and colour-blind modes. Chart series use the semantic colours plus distinct patterns or markers where series overlap. The palette is a starting token set; contrast must be verified against WCAG/Android accessibility targets before implementation freeze.

### Typography

- **Primary:** Material 3's available sans-serif family (Roboto/system), so all scripts and weights render reliably without a font download.
- **Display numbers:** Tabular figures for balances, axis labels and tables. Large total: 32–40 sp; account total: 24–28 sp.
- **Hierarchy:** Screen heading 24 sp semibold, section 18–20 sp semibold, body 14–16 sp, metadata no smaller than 12 sp.
- **Currency:** Use the correct locale and grouping; keep currency symbol and amount together. Negative amounts use a visible minus sign; never depend on red alone.
- **Dynamic type:** Respect system font scaling. Cards grow vertically; amounts wrap or shrink only within a defined accessible range.

## 3. Layout and navigation

Use an 8 dp spacing grid, 16–20 dp phone margins, 12–16 dp card inner padding, and clear section gaps of 24–32 dp. Keep action rows and touch targets at least 48 dp tall. Use rounded cards (16–20 dp), quiet borders and very subtle elevation. Avoid dense dashboard tiles that require users to decode unlabeled icons.

The bottom bar has **Home, Activity, Import, Wealth, Settings** with labels always visible. A prominent **Add** action opens the shared entry sheet. Each screen has one obvious primary action. Detailed flows and button behaviors are in [03-app-flow.md](03-app-flow.md); the UI must use the same labels and routing.

On compact phones, charts stack in a single column. On wider phones/tablets, dashboard cards can form two columns, but order remains net worth → cash flow → spending → wealth → upcoming dates. Use edge-to-edge system bars with correct inset handling. In landscape, detail screens may show source and extracted data side by side. This is a native layout, not a website viewport scaled into an APK.

## 4. Dashboard composition

1. **Header:** greeting or neutral “Your finances,” current period selector, profile lock indicator and backup status. Do not put account numbers here.
2. **Financial position card:** net worth, assets, liabilities, change versus prior comparable period, as-of timestamp. If valuations are stale, show “Last valued [date]”; if incomplete, show “Partial total.”
3. **Cash and accounts strip:** cash-on-hand always visible; bank and credit balances horizontally scroll or use a concise list. Cash card has one-tap Cash expense.
4. **Cash-flow card:** income and expense bars, net flow value, selectable month. Tap opens matched transactions.
5. **Spending card:** daily histogram and category breakdown; allow toggle between histogram and category list with exact totals.
6. **Wealth card:** allocation and net-worth trend; badge any snapshot-only holdings or unvalued assets.
7. **Upcoming card:** FD/bond maturity, credit due date and recurring bills, ordered by date.
8. **Insights card:** at most three actionable observations; link to records, not a black-box score.

Prioritise the most useful cards based on available data, but keep a stable default order. If one card has no data, give a small relevant action rather than removing the section and shifting everything unpredictably.

## 5. Chart design and interaction

- Every chart displays title, period, unit, legend and accessible summary. Axes have readable tick density and honest zero baseline for bars.
- **Bar graphs:** monthly cash flow and category comparisons. Negative values extend below baseline or use a clearly separate series.
- **Histogram:** daily spending frequency/amount distribution, with a clearly named measurement; do not use “histogram” for an unrelated time-series bar chart.
- **Line/area:** net worth and account balance over time; mark missing valuations with gaps, not fabricated interpolation.
- **Donut/pie:** asset allocation only when the number of categories is manageable; show a labelled list with exact percentages alongside it. For many categories, use sorted bars instead.
- **Calendar heatmap:** spending intensity by day, with explicit legend and tap-to-day detail.
- **Timeline:** FD/bond maturity and projected payouts, visually distinguishing projected and posted events.
- Tap/long-press reveals a tooltip containing exact value/date and **View records**. A selected bar/segment persists while drilling down and returning.
- Motion lasts roughly 150–250 ms and follows reduced-motion settings. Animate transitions, never animate numbers in a way that obscures the actual value.
- Thousands of points are aggregated at the chosen zoom; raw records remain available in drill-down.

## 6. Components

| Component | Required behavior |
|---|---|
| Account card | Name/type, masked identifier if available, balance, as-of date; tap opens detail. |
| Money row | Signed amount, merchant/payee, date, account, category and source badge. |
| Status badge | “Imported,” “Needs review,” “Snapshot,” “Estimated,” “Backup pending,” or “Backed up”; text plus icon. |
| Primary button | Solid primary colour, verb-first label; disabled only with visible reason. |
| Secondary button | Outlined or text, never visually confused with destructive action. |
| Review card | Source excerpt next to editable fields and confidence reason; accept/reject actions. |
| File import card | File type, institution detection, progress, supported-format note and selected account. |
| Filter chip | Selected state, clear affordance and readable count of active filters. |
| Toast/snackbar | Short confirmation with Undo only when rollback is valid; backup state remains separate. |
| Confirmation dialog | Names the action and affected count; destructive button is explicit. |
| Empty state | One-sentence explanation plus the next useful action; no fabricated example finances. |
| Error state | Human cause, preserved work, Retry/Change file/Review options; no raw exception. |

## 7. Key journeys and microcopy

### Import

Use a step indicator **Choose → Analyse → Review → Save**. Show page/row progress and the original source. Review groups “Ready,” “Needs your decision,” and “Already recorded.” The commit button includes the exact count: “Save 28 transactions.” A final result shows accepted, skipped and unresolved counts. Avoid saying “AI handled everything” if decisions remain.

### Backup

Display **Last verified backup: [time]** and **[n] changes waiting**. During transfer say “Uploading encrypted backup,” then “Checking backup.” If the network fails, say “Your changes are safe on this phone; cloud backup will retry.” If the recovery key is missing, use a persistent high-priority prompt before the user trusts cloud recovery.

### Wealth

Distinguish **Current holding from [date]**, **Trade history**, **Estimated maturity value**, and **Confirmed proceeds**. Never label an imported screenshot value “live.” Unknown cost basis is shown as “Purchase history needed” with an Import trade statement action.

### Cash

Use the everyday language **Cash in hand**. “Withdraw to cash” and “Deposit cash” are transfer shortcuts. The cash account retains a visible balance and recent expenses; cash is never an invisible category.

## 8. Accessibility and trust

TalkBack must announce amount sign, currency, date, account and status in a consistent order. Provide chart data as a readable table/summary. Buttons have descriptive content descriptions; touch areas reach 48 dp. Support high contrast and reduced motion. Permission screens explain exact access and an alternative route. Lock the app on background according to user setting; do not place sensitive amounts in notifications or app-switcher previews unless the user opts in.

## 9. Visual references and anti-patterns

Use **Material 3** as the interaction and accessibility reference, **Android's JetLagged Compose graph example** as a technical drawing reference, and Vico's chart guide as a chart-capability reference. These are principles, not templates to copy. [Material Design 3](https://m3.material.io/), [Compose graphics](https://developer.android.com/develop/ui/compose/graphics/draw/overview), [Vico](https://github.com/patrykandpatrick/vico)

Avoid a trading terminal, fake live tickers, crowded rainbow pie charts, oversized decorative gradients, a mandatory onboarding slideshow, and any chart that cannot be explained through its source records.

## 10. Design acceptance checklist

- All screens and states in the [flow document](03-app-flow.md) have light/dark designs and large-text behavior.
- Dashboard remains useful with zero, one and many accounts, including a cash-only user.
- Charts reconcile to underlying filtered records and include text alternatives.
- Import review makes source and uncertain fields visible without excessive navigation.
- Offline and backup-pending states cannot be mistaken for successful cloud backup.
- Financial data, documents and account identifiers are never displayed to the wrong signed-in owner.
