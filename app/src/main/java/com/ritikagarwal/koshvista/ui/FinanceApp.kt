package com.ritikagarwal.koshvista.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ritikagarwal.koshvista.core.EntryKind
import com.ritikagarwal.koshvista.backup.LocalVaultBackup
import com.ritikagarwal.koshvista.core.Money
import com.ritikagarwal.koshvista.core.LedgerMath
import com.ritikagarwal.koshvista.data.AccountBalance
import com.ritikagarwal.koshvista.data.AccountEntity
import com.ritikagarwal.koshvista.data.LedgerRepository
import com.ritikagarwal.koshvista.data.ImportJobEntity
import com.ritikagarwal.koshvista.data.ImportCandidateEntity
import com.ritikagarwal.koshvista.data.TransactionEntity
import com.ritikagarwal.koshvista.data.FixedDepositEntity
import com.ritikagarwal.koshvista.data.BudgetEntity
import com.ritikagarwal.koshvista.data.PositionSummary
import com.ritikagarwal.koshvista.data.VaultDatabase
import com.ritikagarwal.koshvista.imports.ImportRepository
import com.ritikagarwal.koshvista.imports.DocumentTextReader
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab { Home, Activity, Import, Wealth, Settings }
private enum class Editor { Account, Expense, Income, CashExpense, Transfer, FixedDeposit, Budget, Trade }

@Composable
fun FinanceApp(ownerId: String, database: VaultDatabase, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(ownerId, database) { LedgerRepository(ownerId, database) }
    val importRepository = remember(ownerId, database) { ImportRepository(ownerId, database, context) }
    val localBackup = remember(context) { LocalVaultBackup(context) }
    val documentReader = remember(context) { DocumentTextReader(context) }
    val accounts by repository.accounts.collectAsState(emptyList())
    val balances by repository.balances.collectAsState(emptyList())
    val categories by repository.categories.collectAsState(emptyList())
    val importJobs by importRepository.history.collectAsState(emptyList())
    val fixedDeposits by repository.fixedDeposits.collectAsState(emptyList())
    val budgets by repository.budgets.collectAsState(emptyList())
    val positions by repository.positions.collectAsState(emptyList())
    val monthStart = remember { LocalDate.now().withDayOfMonth(1).toString() }
    val today = remember { LocalDate.now().toString() }
    val lastSevenStart = remember { LocalDate.now().minusDays(6).toString() }
    val income by remember(repository) { database.vaultDao().totalForKind(ownerId, "income", monthStart, today) }.collectAsState(0L)
    val expense by remember(repository) { database.vaultDao().totalForKind(ownerId, "expense", monthStart, today) }.collectAsState(0L)
    val categoryTotals by remember(repository) { database.vaultDao().categorySpending(ownerId, monthStart, today) }.collectAsState(emptyList())
    val dailySpending by remember(repository) { database.vaultDao().dailySpending(ownerId, lastSevenStart, today) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(Tab.Home) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<AccountBalance?>(null) }
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var importAccountId by remember { mutableStateOf("") }
    var selectedImportId by remember { mutableStateOf<String?>(null) }
    var activityDay by remember { mutableStateOf<String?>(null) }
    var activityCategory by remember { mutableStateOf<String?>(null) }
    var categoryFilterActive by remember { mutableStateOf(false) }
    var activityAccountId by remember { mutableStateOf<String?>(null) }
    var activitySearch by remember { mutableStateOf("") }
    var activityPage by remember { mutableStateOf(0) }
    var backupAction by remember { mutableStateOf<String?>(null) }
    var recoveryPassphrase by remember { mutableStateOf("") }
    var documentPreview by remember { mutableStateOf<String?>(null) }
    val filteredActivity = remember(repository, activityDay, activityCategory, categoryFilterActive,
        activityAccountId, activitySearch, activityPage) {
        database.vaultDao().searchTransactions(ownerId, activityAccountId, activityDay, categoryFilterActive,
            activityCategory, monthStart, today, activitySearch.trim().ifBlank { null }, 50, activityPage * 50)
    }
    val visibleTransactions by filteredActivity.collectAsState(emptyList())
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "Statement.csv"
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readNBytes(20 * 1024 * 1024 + 1) }
                        ?: error("Could not open the selected file")
                }
                selectedImportId = importRepository.stageCsv(importAccountId, name, bytes)
                snackbar.showSnackbar("Review the statement before saving")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not analyse the file") }
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readNBytes(20 * 1024 * 1024 + 1) }
                        ?: error("Could not open document")
                }
                documentPreview = documentReader.read(bytes, mimeType).take(20_000)
                snackbar.showSnackbar("Text extracted on this phone. Review it before recording anything.")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not read document") }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val secret = recoveryPassphrase.toCharArray()
            try {
                val archive = withContext(Dispatchers.IO) { localBackup.create(ownerId, database, secret) }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(archive) }
                    ?: error("Could not write backup file") }
                snackbar.showSnackbar("Encrypted backup saved. Keep the passphrase separately.")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Backup could not be saved") }
            finally { secret.fill('\u0000'); recoveryPassphrase = "" }
        } else recoveryPassphrase = ""
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val secret = recoveryPassphrase.toCharArray()
            try {
                val archive = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use {
                    it.readNBytes(100 * 1024 * 1024 + 1)
                } ?: error("Could not open backup file") }
                withContext(Dispatchers.IO) { localBackup.restore(ownerId, database, secret, archive) }
                snackbar.showSnackbar("Vault restored. Reopen it to see the recovered records.")
                onSignOut()
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Restore failed") }
            finally { secret.fill('\u0000'); recoveryPassphrase = "" }
        } else recoveryPassphrase = ""
    }

    LaunchedEffect(repository) { repository.initialiseOwner(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { NavigationBar { Tab.entries.forEach { destination ->
            NavigationBarItem(selected = tab == destination, onClick = { tab = destination },
                icon = { Text(destination.name.first().toString()) }, label = { Text(destination.name) })
        } } },
        floatingActionButton = { if (tab == Tab.Home || tab == Tab.Activity)
            FloatingActionButton(onClick = { addMenu = true }) { Text("Add") } },
    ) { padding ->
        when (tab) {
            Tab.Home -> HomeContent(balances, income, expense, dailySpending, categoryTotals, budgets,
                categories.associate { it.id to it.name },
                onAccount = { selectedAccount = it }, onAccountAdd = { editor = Editor.Account },
                onCashSpend = { editor = Editor.CashExpense },
                onBudget = { editor = Editor.Budget },
                onDay = { activityDay = it; categoryFilterActive = false; activityPage = 0; tab = Tab.Activity },
                onCategory = { activityCategory = it; activityDay = null; categoryFilterActive = true; activityPage = 0; tab = Tab.Activity },
                Modifier.padding(padding))
            Tab.Activity -> ActivityContent(visibleTransactions, accounts, { editor = Editor.Expense },
                onSelect = { selectedTransaction = it },
                accountId = activityAccountId, onAccount = { activityAccountId = it; activityPage = 0 },
                search = activitySearch, onSearch = { activitySearch = it; activityPage = 0 },
                page = activityPage, onPage = { activityPage = it },
                filterLabel = activityDay ?: if (categoryFilterActive) activityCategory?.let { id -> categories.find { it.id == id }?.name } ?: "Uncategorised" else null,
                onClearFilter = { activityDay = null; activityCategory = null; categoryFilterActive = false; activityPage = 0 },
                Modifier.padding(padding))
            Tab.Import -> ImportContent(accounts, importJobs, importAccountId, selectedImportId, importRepository,
                onAccount = { importAccountId = it }, onPick = { filePicker.launch(arrayOf("text/*", "application/csv")) },
                onReadDocument = { documentPicker.launch(arrayOf("application/pdf", "image/*")) },
                documentPreview = documentPreview, onClearPreview = { documentPreview = null },
                onSelectJob = { selectedImportId = it }, onCommit = { id -> scope.launch {
                    try { val count = importRepository.commit(id); snackbar.showSnackbar("Saved $count transactions on this phone") }
                    catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not save import") }
                } }, modifier = Modifier.padding(padding))
            Tab.Wealth -> WealthContent(balances, fixedDeposits, positions,
                { editor = Editor.FixedDeposit }, { editor = Editor.Trade }, Modifier.padding(padding))
            Tab.Settings -> Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text("Create an encrypted backup file and keep its passphrase separately. Google Drive sync will be available after cloud access is configured.")
                Button(onClick = { backupAction = "create" }) { Text("Create local backup") }
                Button(onClick = { backupAction = "restore" }) { Text("Restore from backup file") }
                Button(onClick = onSignOut) { Text("Lock and sign out") }
            }
        }
    }

    if (addMenu) AlertDialog(onDismissRequest = { addMenu = false }, title = { Text("Add") },
        text = { Column { listOf(
            Editor.Expense to "Expense", Editor.Income to "Income", Editor.Transfer to "Transfer",
            Editor.CashExpense to "Cash expense", Editor.Account to "Account",
            Editor.FixedDeposit to "Fixed deposit",
            Editor.Budget to "Monthly budget",
            Editor.Trade to "Investment trade",
        ).forEach { (choice, label) -> TextButton(onClick = { editor = choice; addMenu = false }) { Text(label) } } } },
        confirmButton = { TextButton(onClick = { addMenu = false }) { Text("Close") } })

    backupAction?.let { action -> AlertDialog(onDismissRequest = { backupAction = null; recoveryPassphrase = "" },
        title = { Text(if (action == "create") "Encrypt your backup" else "Restore your vault") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (action == "create") "Use at least 12 characters. Losing this passphrase means the archive cannot be restored."
                else "Restoration requires the same owner identity and an empty vault. Existing records will not be overwritten.")
            OutlinedTextField(recoveryPassphrase, { recoveryPassphrase = it }, label = { Text("Recovery passphrase") },
                visualTransformation = PasswordVisualTransformation())
        } },
        confirmButton = { Button(onClick = {
            if (action == "create") backupPicker.launch("KoshVista-${LocalDate.now()}.kvbackup")
            else restorePicker.launch(arrayOf("*/*"))
            backupAction = null
        }, enabled = recoveryPassphrase.length >= 12) { Text(if (action == "create") "Choose save location" else "Choose backup file") } },
        dismissButton = { TextButton(onClick = { backupAction = null; recoveryPassphrase = "" }) { Text("Cancel") } }) }

    selectedAccount?.let { account -> AlertDialog(onDismissRequest = { selectedAccount = null },
        title = { Text(account.name) },
        text = { Column { Text(formatMoney(account.balanceMinor, account.currencyCode), style = MaterialTheme.typography.headlineMedium)
            Text("Balance from opening amount and posted entries") } },
        confirmButton = { TextButton(onClick = { selectedAccount = null }) { Text("Close") } }) }

    selectedTransaction?.let { transaction -> AlertDialog(onDismissRequest = { selectedTransaction = null },
        title = { Text(transaction.description) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatMoney(transaction.amountMinor, transaction.currencyCode), style = MaterialTheme.typography.headlineMedium)
            Text("${transaction.localDate} · ${accounts.find { it.id == transaction.accountId }?.name ?: "Account"}")
            Text("Type: ${transaction.kind.replace('_', ' ')}")
            if (transaction.sourceDocumentId != null) Text("Imported from a statement; original source is retained.")
            if (transaction.transferGroupId != null) Text("Both sides of this transfer will be reversed together.")
        } },
        confirmButton = { Button(onClick = { scope.launch {
            try { repository.voidTransaction(transaction.id); selectedTransaction = null; snackbar.showSnackbar("Record reversed") }
            catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not reverse record") }
        } }) { Text("Reverse entry") } },
        dismissButton = { TextButton(onClick = { selectedTransaction = null }) { Text("Close") } }) }

    editor?.let { current -> when (current) {
        Editor.Trade -> TradeEditor(accounts.filter { it.type == "broker_cash" }, positions,
            { editor = null }) { broker, symbol, name, side, quantity, price, fee, date -> scope.launch {
            try {
                repository.recordTrade(broker, symbol, name, side, quantity.toBigDecimal(), price.toBigDecimal(),
                    Money.parse(fee.ifBlank { "0" }, "INR"), LocalDate.parse(date))
                editor = null; snackbar.showSnackbar("Investment trade saved")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not save trade") }
        } }
        Editor.Budget -> BudgetEditor(categories.filter { it.kind == "expense" && !it.isArchived },
            { editor = null }) { categoryId, amount -> scope.launch {
            try {
                repository.setMonthlyBudget(categoryId, Money.parse(amount, "INR"))
                editor = null; snackbar.showSnackbar("Monthly budget saved")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not save budget") }
        } }
        Editor.FixedDeposit -> FixedDepositEditor(accounts, { editor = null }) { source, name, institution, principal, rate, maturity -> scope.launch {
            try {
                repository.createFixedDeposit(source, name, institution, Money.parse(principal, "INR"),
                    rate.trim().toBigDecimal().movePointLeft(2), LocalDate.now(), LocalDate.parse(maturity))
                editor = null; snackbar.showSnackbar("Fixed deposit saved and funded")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not save fixed deposit") }
        } }
        Editor.Account -> AccountEditor({ editor = null }) { type, name, amount, date -> scope.launch {
            try {
                repository.addAccount(type, name, "INR", Money.parse(amount, "INR"), LocalDate.parse(date))
                editor = null; snackbar.showSnackbar("Account saved on this phone")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Account could not be saved") }
        } }
        Editor.Transfer -> TransferEditor(accounts, { editor = null }) { from, to, amount, date -> scope.launch {
            try {
                repository.transfer(from, to, Money.parse(amount, "INR"), LocalDate.parse(date))
                editor = null; snackbar.showSnackbar("Transfer saved on this phone")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Transfer could not be saved") }
        } }
        else -> TransactionEditor(accounts, categories.filter { it.kind == if (current == Editor.Income) "income" else "expense" }
            .map { it.id to it.name }, current, { editor = null }) { account, amount, description, category, date -> scope.launch {
            try {
                val entered = Money.parse(amount, "INR")
                require(entered.minor > 0)
                val kind = if (current == Editor.Income) EntryKind.INCOME else EntryKind.EXPENSE
                repository.addTransaction(account, kind, if (kind == EntryKind.EXPENSE) -entered else entered,
                    LocalDate.parse(date), description, category)
                editor = null; snackbar.showSnackbar("Transaction saved on this phone")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Transaction could not be saved") }
        } }
    } }
}

@Composable
private fun HomeContent(
    balances: List<AccountBalance>, income: Long, expense: Long,
    dailySpending: List<com.ritikagarwal.koshvista.data.DailySpend>,
    categoryTotals: List<com.ritikagarwal.koshvista.data.CategoryTotal>, budgets: List<BudgetEntity>,
    categoryNames: Map<String, String>,
    onAccount: (AccountBalance) -> Unit, onAccountAdd: () -> Unit, onCashSpend: () -> Unit,
    onBudget: () -> Unit,
    onDay: (String) -> Unit, onCategory: (String?) -> Unit,
    modifier: Modifier,
) {
    val assets = balances.filter { it.type !in setOf("liability", "credit_card") }.sumOf { it.balanceMinor }
    val liabilities = -balances.filter { it.type in setOf("liability", "credit_card") }.sumOf { it.balanceMinor }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your finances", style = MaterialTheme.typography.headlineMedium) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
            Text("Recorded net worth", style = MaterialTheme.typography.titleMedium)
            Text(formatMoney(assets - liabilities, "INR"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("Assets ${formatMoney(assets, "INR")}   Liabilities ${formatMoney(liabilities, "INR")}")
            Text("Investment holdings use recorded cost until a valuation is available.", style = MaterialTheme.typography.bodySmall)
        } } }
        item { DailySpendingCard(dailySpending, onDay) }
        item { CategorySpendingCard(categoryTotals, categoryNames, onCategory) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Monthly budgets", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBudget) { Text("Set limit") }
        } }
        if (budgets.isEmpty()) item { Text("Set a category limit to see how this month's spending compares.") }
        items(budgets, key = { it.id }) { budget ->
            val spent = categoryTotals.find { it.categoryId == budget.categoryId }?.totalMinor ?: 0L
            val proportion = (spent.toDouble() / budget.limitMinor).toFloat().coerceIn(0f, 1f)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(categoryNames[budget.categoryId] ?: "Category")
                    Text("${formatMoney(spent, budget.currencyCode)} / ${formatMoney(budget.limitMinor, budget.currencyCode)}")
                }
                LinearProgressIndicator(progress = { proportion }, modifier = Modifier.fillMaxWidth())
                if (spent > budget.limitMinor) Text("Over limit by ${formatMoney(spent - budget.limitMinor, budget.currencyCode)}",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
            Text("This month's cash flow", style = MaterialTheme.typography.titleMedium)
            Text("Income ${formatMoney(income, "INR")}")
            Text("Spending ${formatMoney(-expense, "INR")}")
            Text("Transfers are excluded", style = MaterialTheme.typography.bodySmall)
        } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Accounts", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onAccountAdd) { Text("Add account") }
        } }
        if (balances.isEmpty()) item { Text("Add an account to start tracking your balances and spending.") }
        items(balances, key = { it.id }) { account -> Card(Modifier.fillMaxWidth().clickable { onAccount(account) }) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(account.name, fontWeight = FontWeight.SemiBold); Text(account.type.replace('_', ' ')) }
                Text(formatMoney(if (account.type in setOf("liability", "credit_card")) -account.balanceMinor else account.balanceMinor, account.currencyCode))
            }
        } }
        if (balances.any { it.type == "cash" }) item { Button(onClick = onCashSpend) { Text("Cash expense") } }
    }
}

@Composable
private fun BudgetEditor(categories: List<com.ritikagarwal.koshvista.data.CategoryEntity>,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var categoryId by remember { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Monthly budget") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("Choose an expense category. Your limit repeats each calendar month.") }
            items(categories, key = { it.id }) { category ->
                FilterChip(categoryId == category.id, onClick = { categoryId = category.id }, label = { Text(category.name) })
            }
            item { OutlinedTextField(amount, { amount = it }, label = { Text("Monthly limit in INR") }) }
        } },
        confirmButton = { Button(onClick = { onSave(categoryId, amount) }, enabled = categoryId.isNotBlank() && amount.isNotBlank()) { Text("Save limit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ActivityContent(transactions: List<TransactionEntity>, accounts: List<AccountEntity>, onAdd: () -> Unit,
    onSelect: (TransactionEntity) -> Unit,
    accountId: String?, onAccount: (String?) -> Unit,
    search: String, onSearch: (String) -> Unit, page: Int, onPage: (Int) -> Unit,
    filterLabel: String?, onClearFilter: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Activity", style = MaterialTheme.typography.headlineMedium) }
        item { OutlinedTextField(search, onSearch, label = { Text("Search descriptions") }, modifier = Modifier.fillMaxWidth()) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(accountId == null, onClick = { onAccount(null) }, label = { Text("All accounts") }) }
            items(accounts, key = { "filter-${it.id}" }) { account ->
                FilterChip(accountId == account.id, onClick = { onAccount(account.id) }, label = { Text(account.name) })
            }
        } }
        if (filterLabel != null) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Showing $filterLabel")
            TextButton(onClick = onClearFilter) { Text("Clear filter") }
        } }
        if (transactions.isEmpty()) item { Column {
            Text("No transactions yet. Add an expense or income to begin your ledger.")
            Button(onClick = onAdd) { Text("Add expense") }
        } }
        items(transactions, key = { it.id }) { transaction -> Card(Modifier.fillMaxWidth().clickable { onSelect(transaction) }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(transaction.description, fontWeight = FontWeight.SemiBold)
                    Text("${transaction.localDate} · ${accounts.find { it.id == transaction.accountId }?.name ?: "Account"}",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(formatMoney(transaction.amountMinor, transaction.currencyCode))
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Text("Previous") }
            Text("Page ${page + 1}")
            TextButton(onClick = { onPage(page + 1) }, enabled = transactions.size == 50) { Text("Next") }
        } }
    }
}

@Composable
private fun ImportContent(
    accounts: List<AccountEntity>, jobs: List<ImportJobEntity>, accountId: String,
    selectedJobId: String?, repository: ImportRepository,
    onAccount: (String) -> Unit, onPick: () -> Unit,
    onReadDocument: () -> Unit, documentPreview: String?, onClearPreview: () -> Unit,
    onSelectJob: (String?) -> Unit,
    onCommit: (String) -> Unit, modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ImportCandidateEntity?>(null) }
    val selectedJob = jobs.find { it.id == selectedJobId }
    val candidateFlow = remember(repository, selectedJobId) { selectedJobId?.let(repository::candidates) }
    val candidates by candidateFlow?.collectAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Import", style = MaterialTheme.typography.headlineMedium) }
        if (selectedJob == null) {
            item { Text("Choose the account for a bank CSV statement. You'll review every uncertain row before it enters your ledger.") }
            if (accounts.isEmpty()) item { Text("Add a bank account on Home before importing a statement.") }
            items(accounts.filter { it.type == "bank" || it.type == "credit_card" }) { account ->
                FilterChip(selected = accountId == account.id, onClick = { onAccount(account.id) }, label = { Text(account.name) })
            }
            item { Button(onClick = onPick, enabled = accountId.isNotBlank()) { Text("Choose CSV statement") } }
            item { Button(onClick = onReadDocument) { Text("Read PDF or image on device") } }
            if (documentPreview != null) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Extracted text preview", style = MaterialTheme.typography.titleMedium)
                Text("This is unverified text. It has not been added to your accounts.", style = MaterialTheme.typography.bodySmall)
                Text(documentPreview.ifBlank { "No readable text detected." }, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onClearPreview) { Text("Clear preview") }
            } } }
            item { Text("Import history", style = MaterialTheme.typography.titleLarge) }
            if (jobs.isEmpty()) item { Text("No statements imported yet.") }
            items(jobs, key = { it.id }) { job -> Card(Modifier.fillMaxWidth().clickable { onSelectJob(job.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Statement · ${job.status}", fontWeight = FontWeight.SemiBold)
                    Text("${job.acceptedCount} saved · ${job.duplicateCount} duplicates")
                }
            } }
        } else {
            item { Text("Review statement", style = MaterialTheme.typography.titleLarge) }
            item { Text("${candidates.count { it.decision == "accepted" }} ready · ${candidates.count { it.decision == "unreviewed" }} need a decision · ${candidates.count { it.decision == "duplicate" }} duplicates") }
            if (selectedJob.status == "completed") item { Text("Saved ${selectedJob.acceptedCount} transactions on this phone.") }
            items(candidates, key = { it.id }) { candidate -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(candidate.description.ifBlank { "Row ${candidate.sourceRow}" }, fontWeight = FontWeight.SemiBold)
                    Text("Row ${candidate.sourceRow} · ${candidate.localDate ?: "Date missing"} · ${candidate.amountMinor?.let { formatMoney(it, candidate.currencyCode) } ?: "Amount missing"}")
                    Text(candidate.reviewReasons.ifBlank { "Ready" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Decision: ${candidate.decision}")
                    if (selectedJob.status == "review") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = candidate }) { Text("Edit") }
                        if (candidate.localDate != null && candidate.amountMinor != null && candidate.amountMinor != 0L && candidate.description.isNotBlank()) {
                            TextButton(onClick = { scope.launch {
                                try { repository.decide(candidate.id, "accepted") } catch (error: Exception) { message = error.message }
                            } }) { Text("Accept") }
                        }
                        TextButton(onClick = { scope.launch {
                            try { repository.decide(candidate.id, "rejected") } catch (error: Exception) { message = error.message }
                        } }) { Text("Reject") }
                        TextButton(onClick = { scope.launch {
                            try { repository.decide(candidate.id, "duplicate") } catch (error: Exception) { message = error.message }
                        } }) { Text("Duplicate") }
                    }
                }
            } }
            if (message != null) item { Text(message.orEmpty(), color = MaterialTheme.colorScheme.error) }
            if (selectedJob.status == "review") item { Button(onClick = { onCommit(selectedJob.id) },
                enabled = candidates.isNotEmpty() && candidates.none { it.decision == "unreviewed" }) {
                Text("Save ${candidates.count { it.decision == "accepted" }} transactions")
            } }
            item { TextButton(onClick = { onSelectJob(null) }) { Text("Back to imports") } }
        }
    }
    editing?.let { candidate ->
        var date by remember(candidate.id) { mutableStateOf(candidate.localDate.orEmpty()) }
        var description by remember(candidate.id) { mutableStateOf(candidate.description) }
        var signedAmount by remember(candidate.id) { mutableStateOf(candidate.amountMinor?.let {
            BigDecimal.valueOf(it).movePointLeft(2).toPlainString()
        }.orEmpty()) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Edit statement row ${candidate.sourceRow}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter a negative amount for money out, positive for money in.")
                OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") })
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                OutlinedTextField(signedAmount, { signedAmount = it }, label = { Text("Signed amount in INR") })
            } },
            confirmButton = { Button(onClick = { scope.launch {
                try { repository.editCandidate(candidate.id, date, description, signedAmount); editing = null }
                catch (error: Exception) { message = error.message ?: "Could not edit row" }
            } }) { Text("Save changes") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
}

@Composable
private fun WealthContent(balances: List<AccountBalance>, deposits: List<FixedDepositEntity>,
    positions: List<PositionSummary>, onAddDeposit: () -> Unit, onTrade: () -> Unit, modifier: Modifier) {
    val depositAccountIds = deposits.mapTo(mutableSetOf()) { it.assetAccountId }
    val investmentAccountIds = positions.mapTo(mutableSetOf()) { it.instrument.assetAccountId }
    val assets = balances.filter { (it.type == "asset" || it.type == "broker_cash") && it.id !in depositAccountIds && it.id !in investmentAccountIds }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Wealth", style = MaterialTheme.typography.headlineMedium) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Investments", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onTrade) { Text("Add trade") }
        } }
        if (positions.isEmpty()) item { Text("No holdings yet. Add a broker cash account, then record a buy trade.") }
        items(positions, key = { it.instrument.id }) { holding -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${holding.instrument.symbol} · ${holding.instrument.name}", fontWeight = FontWeight.SemiBold)
                Text("${holding.quantity.stripTrailingZeros().toPlainString()} units")
                Text("Cost basis ${formatMoney(holding.costBasisMinor, holding.instrument.currencyCode)}")
                Text("Market value is unavailable until a dated valuation is recorded.", style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fixed deposits", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onAddDeposit) { Text("Add") }
        } }
        if (deposits.isEmpty()) item { Text("No fixed deposits yet. Add one to track its principal and estimated maturity.") }
        items(deposits, key = { it.id }) { deposit ->
            val projected = LedgerMath.simpleMaturity(Money(deposit.principalMinor, deposit.currencyCode),
                deposit.annualRateDecimal.toBigDecimal(), LocalDate.parse(deposit.startLocalDate), LocalDate.parse(deposit.maturityLocalDate))
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(deposit.name, fontWeight = FontWeight.SemiBold)
                Text("${deposit.institutionName} · Matures ${deposit.maturityLocalDate}")
                Text("Principal ${formatMoney(deposit.principalMinor, deposit.currencyCode)}")
                Text("Estimated maturity ${formatMoney(projected.minor, projected.currencyCode)}",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Simple interest estimate; actual proceeds may differ.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item { Text("Broker cash and other assets", style = MaterialTheme.typography.titleMedium) }
        if (assets.isEmpty()) item { Text("No investment assets recorded yet.") }
        items(assets) { account -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween) { Text(account.name); Text(formatMoney(account.balanceMinor, account.currencyCode)) } } }
    }
}

@Composable
private fun TradeEditor(brokers: List<AccountEntity>, positions: List<PositionSummary>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String) -> Unit) {
    var broker by remember { mutableStateOf(brokers.firstOrNull()?.id.orEmpty()) }
    var side by remember { mutableStateOf("buy") }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Investment trade") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (brokers.isEmpty()) item { Text("Add a broker cash account first.") }
            items(brokers, key = { it.id }) { account -> FilterChip(broker == account.id,
                onClick = { broker = account.id }, label = { Text(account.name) }) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(side == "buy", onClick = { side = "buy" }, label = { Text("Buy") })
                FilterChip(side == "sell", onClick = { side = "sell" }, label = { Text("Sell") })
            } }
            items(positions, key = { it.instrument.id }) { holding -> FilterChip(symbol == holding.instrument.symbol,
                onClick = { symbol = holding.instrument.symbol; name = holding.instrument.name },
                label = { Text("${holding.instrument.symbol} · ${holding.quantity.stripTrailingZeros().toPlainString()} held") }) }
            item { OutlinedTextField(symbol, { symbol = it }, label = { Text("Symbol") }) }
            item { OutlinedTextField(name, { name = it }, label = { Text("Instrument name") }) }
            item { OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }) }
            item { OutlinedTextField(price, { price = it }, label = { Text("Unit price in INR") }) }
            item { OutlinedTextField(fee, { fee = it }, label = { Text("Fees in INR") }) }
            item { OutlinedTextField(date, { date = it }, label = { Text("Trade date YYYY-MM-DD") }) }
        } },
        confirmButton = { Button(onClick = { onSave(broker, symbol, name, side, quantity, price, fee, date) },
            enabled = broker.isNotBlank() && symbol.isNotBlank() && name.isNotBlank() && runCatching {
                LocalDate.parse(date); quantity.toBigDecimal() > BigDecimal.ZERO && price.toBigDecimal() > BigDecimal.ZERO &&
                    Money.parse(fee, "INR").minor >= 0
            }.getOrDefault(false)) { Text("Save trade") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun FixedDepositEditor(accounts: List<AccountEntity>, onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit) {
    val sources = accounts.filter { it.type in setOf("bank", "cash", "broker_cash") }
    var source by remember { mutableStateOf(sources.firstOrNull()?.id.orEmpty()) }
    var name by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var maturity by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add fixed deposit") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("Fund from") }
            items(sources, key = { it.id }) { account -> FilterChip(source == account.id,
                onClick = { source = account.id }, label = { Text(account.name) }) }
            item { OutlinedTextField(name, { name = it }, label = { Text("Deposit name") }) }
            item { OutlinedTextField(institution, { institution = it }, label = { Text("Institution") }) }
            item { OutlinedTextField(principal, { principal = it }, label = { Text("Principal in INR") }) }
            item { OutlinedTextField(rate, { rate = it }, label = { Text("Annual rate %") }) }
            item { OutlinedTextField(maturity, { maturity = it }, label = { Text("Maturity date YYYY-MM-DD") }) }
        } },
        confirmButton = { Button(onClick = { onSave(source, name, institution, principal, rate, maturity) },
            enabled = source.isNotBlank() && name.isNotBlank() && institution.isNotBlank() && principal.isNotBlank() && rate.isNotBlank() && maturity.isNotBlank()) { Text("Save deposit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PlainContent(title: String, description: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description)
    }
}

@Composable
private fun AccountEditor(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var type by remember { mutableStateOf("bank") }
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("0") }
    var openingDate by remember { mutableStateOf(LocalDate.now().toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add account") }, text = { LazyColumn {
        items(listOf("bank", "cash", "credit_card", "broker_cash", "asset", "liability")) {
            FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.replace('_', ' ')) })
        }
        item { OutlinedTextField(name, { name = it }, label = { Text("Account name") }) }
        item { OutlinedTextField(opening, { opening = it }, label = { Text("Opening balance in INR") }) }
        item { OutlinedTextField(openingDate, { openingDate = it }, label = { Text("Opening date YYYY-MM-DD") }) }
    } }, confirmButton = { Button(onClick = { onSave(type, name, opening, openingDate) },
        enabled = name.isNotBlank() && runCatching { LocalDate.parse(openingDate); Money.parse(opening, "INR") }.isSuccess) { Text("Save account") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TransactionEditor(
    accounts: List<AccountEntity>, categories: List<Pair<String, String>>, kind: Editor,
    onDismiss: () -> Unit, onSave: (String, String, String, String?, String) -> Unit,
) {
    val options = if (kind == Editor.CashExpense) accounts.filter { it.type == "cash" } else accounts
    var accountId by remember { mutableStateOf(options.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (kind == Editor.Income) "Add income" else "Add expense") }, text = { LazyColumn {
        if (options.isEmpty()) item { Text("Add an account first to record this transaction.") }
        items(options, key = { it.id }) { FilterChip(selected = accountId == it.id, onClick = { accountId = it.id }, label = { Text(it.name) }) }
        item { OutlinedTextField(amount, { amount = it }, label = { Text("Amount in INR") }) }
        item { OutlinedTextField(description, { description = it }, label = { Text("Description") }) }
        item { OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") }) }
        items(categories, key = { it.first }) { (id, name) -> FilterChip(selected = categoryId == id, onClick = { categoryId = id }, label = { Text(name) }) }
    } }, confirmButton = { Button(onClick = { onSave(accountId, amount, description, categoryId, date) },
        enabled = accountId.isNotBlank() && description.isNotBlank() && runCatching {
            LocalDate.parse(date); Money.parse(amount, "INR").minor > 0
        }.getOrDefault(false)) { Text("Save transaction") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TransferEditor(accounts: List<AccountEntity>, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var from by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var to by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Transfer") }, text = { LazyColumn {
        item { Text("From") }
        items(accounts, key = { "from-${it.id}" }) { FilterChip(selected = from == it.id, onClick = { from = it.id }, label = { Text(it.name) }) }
        item { Text("To") }
        items(accounts, key = { "to-${it.id}" }) { FilterChip(selected = to == it.id, onClick = { to = it.id }, label = { Text(it.name) }) }
        item { OutlinedTextField(amount, { amount = it }, label = { Text("Amount in INR") }) }
        item { OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") }) }
    } }, confirmButton = { Button(onClick = { onSave(from, to, amount, date) },
        enabled = from.isNotBlank() && to.isNotBlank() && from != to && runCatching {
            LocalDate.parse(date); Money.parse(amount, "INR").minor > 0
        }.getOrDefault(false)) { Text("Save transfer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

internal fun formatMoney(minor: Long, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    val amount = BigDecimal.valueOf(minor).movePointLeft(currency.defaultFractionDigits)
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply { this.currency = currency }.format(amount)
}
