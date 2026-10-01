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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.ritikagarwal.koshvista.core.EntryKind
import com.ritikagarwal.koshvista.core.Money
import com.ritikagarwal.koshvista.data.AccountBalance
import com.ritikagarwal.koshvista.data.AccountEntity
import com.ritikagarwal.koshvista.data.LedgerRepository
import com.ritikagarwal.koshvista.data.ImportJobEntity
import com.ritikagarwal.koshvista.data.ImportCandidateEntity
import com.ritikagarwal.koshvista.data.TransactionEntity
import com.ritikagarwal.koshvista.data.VaultDatabase
import com.ritikagarwal.koshvista.imports.ImportRepository
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab { Home, Activity, Import, Wealth, Settings }
private enum class Editor { Account, Expense, Income, CashExpense, Transfer }

@Composable
fun FinanceApp(ownerId: String, database: VaultDatabase, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(ownerId, database) { LedgerRepository(ownerId, database) }
    val importRepository = remember(ownerId, database) { ImportRepository(ownerId, database, context) }
    val accounts by repository.accounts.collectAsState(emptyList())
    val balances by repository.balances.collectAsState(emptyList())
    val categories by repository.categories.collectAsState(emptyList())
    val importJobs by importRepository.history.collectAsState(emptyList())
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
    var importAccountId by remember { mutableStateOf("") }
    var selectedImportId by remember { mutableStateOf<String?>(null) }
    var activityDay by remember { mutableStateOf<String?>(null) }
    var activityCategory by remember { mutableStateOf<String?>(null) }
    var categoryFilterActive by remember { mutableStateOf(false) }
    val filteredActivity = remember(repository, activityDay, activityCategory, categoryFilterActive) {
        when {
            activityDay != null -> database.vaultDao().transactionsForDay(ownerId, activityDay!!)
            categoryFilterActive -> database.vaultDao().transactionsForCategory(ownerId, activityCategory, monthStart, today)
            else -> repository.recentTransactions
        }
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
            Tab.Home -> HomeContent(balances, income, expense, dailySpending, categoryTotals,
                categories.associate { it.id to it.name },
                onAccount = { selectedAccount = it }, onAccountAdd = { editor = Editor.Account },
                onCashSpend = { editor = Editor.CashExpense },
                onDay = { activityDay = it; categoryFilterActive = false; tab = Tab.Activity },
                onCategory = { activityCategory = it; activityDay = null; categoryFilterActive = true; tab = Tab.Activity },
                Modifier.padding(padding))
            Tab.Activity -> ActivityContent(visibleTransactions, accounts, { editor = Editor.Expense },
                filterLabel = activityDay ?: if (categoryFilterActive) activityCategory?.let { id -> categories.find { it.id == id }?.name } ?: "Uncategorised" else null,
                onClearFilter = { activityDay = null; activityCategory = null; categoryFilterActive = false },
                Modifier.padding(padding))
            Tab.Import -> ImportContent(accounts, importJobs, importAccountId, selectedImportId, importRepository,
                onAccount = { importAccountId = it }, onPick = { filePicker.launch(arrayOf("text/*", "application/csv")) },
                onSelectJob = { selectedImportId = it }, onCommit = { id -> scope.launch {
                    try { val count = importRepository.commit(id); snackbar.showSnackbar("Saved $count transactions on this phone") }
                    catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Could not save import") }
                } }, modifier = Modifier.padding(padding))
            Tab.Wealth -> WealthContent(balances, Modifier.padding(padding))
            Tab.Settings -> Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text("Cloud backup is not configured in this build. Records currently remain on this phone.")
                Button(onClick = onSignOut) { Text("Lock and sign out") }
            }
        }
    }

    if (addMenu) AlertDialog(onDismissRequest = { addMenu = false }, title = { Text("Add") },
        text = { Column { listOf(
            Editor.Expense to "Expense", Editor.Income to "Income", Editor.Transfer to "Transfer",
            Editor.CashExpense to "Cash expense", Editor.Account to "Account",
        ).forEach { (choice, label) -> TextButton(onClick = { editor = choice; addMenu = false }) { Text(label) } } } },
        confirmButton = { TextButton(onClick = { addMenu = false }) { Text("Close") } })

    selectedAccount?.let { account -> AlertDialog(onDismissRequest = { selectedAccount = null },
        title = { Text(account.name) },
        text = { Column { Text(formatMoney(account.balanceMinor, account.currencyCode), style = MaterialTheme.typography.headlineMedium)
            Text("Balance from opening amount and posted entries") } },
        confirmButton = { TextButton(onClick = { selectedAccount = null }) { Text("Close") } }) }

    editor?.let { current -> when (current) {
        Editor.Account -> AccountEditor({ editor = null }) { type, name, amount -> scope.launch {
            try {
                repository.addAccount(type, name, "INR", Money.parse(amount, "INR"), LocalDate.now())
                editor = null; snackbar.showSnackbar("Account saved on this phone")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Account could not be saved") }
        } }
        Editor.Transfer -> TransferEditor(accounts, { editor = null }) { from, to, amount -> scope.launch {
            try {
                repository.transfer(from, to, Money.parse(amount, "INR"), LocalDate.now())
                editor = null; snackbar.showSnackbar("Transfer saved on this phone")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Transfer could not be saved") }
        } }
        else -> TransactionEditor(accounts, categories.filter { it.kind == if (current == Editor.Income) "income" else "expense" }
            .map { it.id to it.name }, current, { editor = null }) { account, amount, description, category -> scope.launch {
            try {
                val entered = Money.parse(amount, "INR")
                require(entered.minor > 0)
                val kind = if (current == Editor.Income) EntryKind.INCOME else EntryKind.EXPENSE
                repository.addTransaction(account, kind, if (kind == EntryKind.EXPENSE) -entered else entered,
                    LocalDate.now(), description, category)
                editor = null; snackbar.showSnackbar("Transaction saved on this phone")
            } catch (error: Exception) { snackbar.showSnackbar(error.message ?: "Transaction could not be saved") }
        } }
    } }
}

@Composable
private fun HomeContent(
    balances: List<AccountBalance>, income: Long, expense: Long,
    dailySpending: List<com.ritikagarwal.koshvista.data.DailySpend>,
    categoryTotals: List<com.ritikagarwal.koshvista.data.CategoryTotal>, categoryNames: Map<String, String>,
    onAccount: (AccountBalance) -> Unit, onAccountAdd: () -> Unit, onCashSpend: () -> Unit,
    onDay: (String) -> Unit, onCategory: (String?) -> Unit,
    modifier: Modifier,
) {
    val assets = balances.filter { it.type !in setOf("liability", "credit_card") }.sumOf { it.balanceMinor }
    val liabilities = -balances.filter { it.type in setOf("liability", "credit_card") }.sumOf { it.balanceMinor }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your finances", style = MaterialTheme.typography.headlineMedium) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
            Text("Net worth", style = MaterialTheme.typography.titleMedium)
            Text(formatMoney(assets - liabilities, "INR"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("Assets ${formatMoney(assets, "INR")}   Liabilities ${formatMoney(liabilities, "INR")}")
        } } }
        item { DailySpendingCard(dailySpending, onDay) }
        item { CategorySpendingCard(categoryTotals, categoryNames, onCategory) }
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
private fun ActivityContent(transactions: List<TransactionEntity>, accounts: List<AccountEntity>, onAdd: () -> Unit,
    filterLabel: String?, onClearFilter: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Activity", style = MaterialTheme.typography.headlineMedium) }
        if (filterLabel != null) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Showing $filterLabel")
            TextButton(onClick = onClearFilter) { Text("Clear filter") }
        } }
        if (transactions.isEmpty()) item { Column {
            Text("No transactions yet. Add an expense or income to begin your ledger.")
            Button(onClick = onAdd) { Text("Add expense") }
        } }
        items(transactions, key = { it.id }) { transaction -> Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(transaction.description, fontWeight = FontWeight.SemiBold)
                    Text("${transaction.localDate} · ${accounts.find { it.id == transaction.accountId }?.name ?: "Account"}",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(formatMoney(transaction.amountMinor, transaction.currencyCode))
            }
        } }
    }
}

@Composable
private fun ImportContent(
    accounts: List<AccountEntity>, jobs: List<ImportJobEntity>, accountId: String,
    selectedJobId: String?, repository: ImportRepository,
    onAccount: (String) -> Unit, onPick: () -> Unit, onSelectJob: (String?) -> Unit,
    onCommit: (String) -> Unit, modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
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
}

@Composable
private fun WealthContent(balances: List<AccountBalance>, modifier: Modifier) {
    val assets = balances.filter { it.type == "asset" || it.type == "broker_cash" }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Wealth", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Broker cash and other assets", style = MaterialTheme.typography.titleMedium) }
        if (assets.isEmpty()) item { Text("No investment assets recorded yet.") }
        items(assets) { account -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween) { Text(account.name); Text(formatMoney(account.balanceMinor, account.currencyCode)) } } }
    }
}

@Composable
private fun PlainContent(title: String, description: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description)
    }
}

@Composable
private fun AccountEditor(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var type by remember { mutableStateOf("bank") }
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("0") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add account") }, text = { Column {
        listOf("bank", "cash", "credit_card", "broker_cash", "asset", "liability").forEach {
            FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.replace('_', ' ')) })
        }
        OutlinedTextField(name, { name = it }, label = { Text("Account name") })
        OutlinedTextField(opening, { opening = it }, label = { Text("Opening balance in INR") })
    } }, confirmButton = { Button(onClick = { onSave(type, name, opening) }, enabled = name.isNotBlank()) { Text("Save account") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TransactionEditor(
    accounts: List<AccountEntity>, categories: List<Pair<String, String>>, kind: Editor,
    onDismiss: () -> Unit, onSave: (String, String, String, String?) -> Unit,
) {
    val options = if (kind == Editor.CashExpense) accounts.filter { it.type == "cash" } else accounts
    var accountId by remember { mutableStateOf(options.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (kind == Editor.Income) "Add income" else "Add expense") }, text = { Column {
        if (options.isEmpty()) Text("Add an account first to record this transaction.")
        options.forEach { FilterChip(selected = accountId == it.id, onClick = { accountId = it.id }, label = { Text(it.name) }) }
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount in INR") })
        OutlinedTextField(description, { description = it }, label = { Text("Description") })
        categories.forEach { (id, name) -> FilterChip(selected = categoryId == id, onClick = { categoryId = id }, label = { Text(name) }) }
    } }, confirmButton = { Button(onClick = { onSave(accountId, amount, description, categoryId) },
        enabled = accountId.isNotBlank() && amount.isNotBlank() && description.isNotBlank()) { Text("Save transaction") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TransferEditor(accounts: List<AccountEntity>, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var from by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var to by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Transfer") }, text = { Column {
        Text("From")
        accounts.forEach { FilterChip(selected = from == it.id, onClick = { from = it.id }, label = { Text(it.name) }) }
        Text("To")
        accounts.forEach { FilterChip(selected = to == it.id, onClick = { to = it.id }, label = { Text(it.name) }) }
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount in INR") })
    } }, confirmButton = { Button(onClick = { onSave(from, to, amount) },
        enabled = from.isNotBlank() && to.isNotBlank() && from != to && amount.isNotBlank()) { Text("Save transfer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

internal fun formatMoney(minor: Long, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    val amount = BigDecimal.valueOf(minor).movePointLeft(currency.defaultFractionDigits)
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply { this.currency = currency }.format(amount)
}
