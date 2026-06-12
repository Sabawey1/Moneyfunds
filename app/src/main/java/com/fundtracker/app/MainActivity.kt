package com.fundtracker.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Theme ----------
private val Green = Color(0xFF1B8A5A)
private val GreenDark = Color(0xFF0E5C3A)
private val Red = Color(0xFFD64545)
private val Ink = Color(0xFF14201B)
private val Muted = Color(0xFF6B7C74)
private val CardBg = Color(0xFFF4F8F5)

private val catColors = listOf(
    Color(0xFF1B8A5A), Color(0xFF3D7EFF), Color(0xFFFF8A3D), Color(0xFF9B5DE5),
    Color(0xFFE5446D), Color(0xFF00B4D8), Color(0xFFF2C14E), Color(0xFF6B7C74),
    Color(0xFF2A9D8F), Color(0xFFE76F51)
)

// ---------- Display model ----------
data class DisplayAccount(
    val id: String, val name: String, val type: String,
    val ids: List<String>, val balance: Double?,
    val hasOverride: Boolean, val implicit: Boolean
)

fun buildAccounts(ctx: Context, allTxns: List<Txn>): List<DisplayAccount> {
    // latest (date,balance) per raw SMS id
    val smsInfo = HashMap<String, Pair<Long, Double>>()
    allTxns.filter { !it.manual && it.account != null && it.balance != null }.forEach {
        val cur = smsInfo[it.account]
        if (cur == null || it.date > cur.first) smsInfo[it.account!!] = it.date to it.balance!!
    }
    val allRawIds = allTxns.filter { !it.manual && it.account != null }.map { it.account!! }.toSet()
    val registered = AccountRegistry.registered(ctx)
    val covered = registered.flatMap { it.linkedIds }.toSet()

    val out = ArrayList<DisplayAccount>()
    registered.forEach { a ->
        val smsBal = a.linkedIds.mapNotNull { smsInfo[it] }.maxByOrNull { it.first }?.second
        out.add(
            DisplayAccount(
                a.id, a.name, a.type, a.linkedIds,
                balance = a.manualBalance ?: smsBal,
                hasOverride = a.manualBalance != null && smsBal != null,
                implicit = false
            )
        )
    }
    (allRawIds - covered).sorted().forEach { rid ->
        out.add(
            DisplayAccount("raw:$rid", "•• $rid", "Bank", listOf(rid),
                balance = smsInfo[rid]?.second, hasOverride = false, implicit = true)
        )
    }
    return out
}

/** Make an implicit account real so we can edit/link it; returns its registry id. */
fun materialize(ctx: Context, da: DisplayAccount): String =
    if (!da.implicit) da.id
    else AccountRegistry.add(ctx, da.name, da.type, da.ids, null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green, onPrimary = Color.White,
                    secondary = GreenDark, background = Color.White,
                    surface = Color.White, onSurface = Ink
                )
            ) { AppRoot() }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (!hasPermission) PermissionGate { launcher.launch(Manifest.permission.READ_SMS) }
    else MainScreen()
}

@Composable
fun PermissionGate(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Green), Alignment.Center) {
            Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Welcome to FundTracker", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text(
            "We read your bank text messages right here on your phone to put your " +
                    "money in one place. Nothing ever leaves your device — the app can't " +
                    "even go online.",
            textAlign = TextAlign.Center, color = Muted
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onGrant, shape = RoundedCornerShape(14.dp)) {
            Text("Let's go", modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

// ---------- helpers ----------
private fun egp(v: Double): String = "EGP ${"%,.2f".format(v)}"
private fun fmtDate(ms: Long): String =
    SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(ms))
private fun monthKey(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "${c.get(Calendar.YEAR)}-${"%02d".format(c.get(Calendar.MONTH) + 1)}"
}
private fun monthLabel(key: String): String {
    val (y, m) = key.split("-").map { it.toInt() }
    val c = Calendar.getInstance().apply { set(Calendar.YEAR, y); set(Calendar.MONTH, m - 1) }
    return SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(c.time)
}
private fun acctLabel(name: String) =
    if (name.all { it.isDigit() } && name.isNotEmpty()) "•• $name" else name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var refreshKey by remember { mutableStateOf(0) }
    var showSheet by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }

    val scan = remember(refreshKey) { SmsParser.scan(context) }
    val manual = remember(refreshKey) { ManualStore.manualTxns(context) }
    val allTxns = remember(refreshKey) { (scan.txns + manual).sortedByDescending { it.date } }
    val accounts = remember(refreshKey, allTxns) { buildAccounts(context, allTxns) }

    val months = remember(allTxns) {
        listOf("All") + allTxns.map { monthKey(it.date) }.distinct().sortedDescending()
    }
    var selectedMonth by remember { mutableStateOf("All") }
    val filtered = remember(allTxns, selectedMonth) {
        if (selectedMonth == "All") allTxns else allTxns.filter { monthKey(it.date) == selectedMonth }
    }

    if (showAccounts) {
        BackHandler { showAccounts = false }
        AccountsScreen(context, accounts, onBack = { showAccounts = false },
            onChanged = { refreshKey++ })
        return
    }

    Scaffold(
        containerColor = Color.White,
        floatingActionButton = {
            if (tab == 0 || tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showSheet = true },
                    containerColor = Green, contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add") }
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val items = listOf(
                    Triple(0, Icons.Default.Home, "Home"),
                    Triple(1, Icons.Default.Receipt, "Activity"),
                    Triple(2, Icons.Default.PieChart, "Insights"),
                    Triple(3, Icons.Default.Tune, "Setup")
                )
                items.forEach { (i, icon, label) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = {
                            if (i == 3 && scan.missed.isNotEmpty())
                                BadgedBox(badge = { Badge { Text("${scan.missed.size}") } }) { Icon(icon, null) }
                            else Icon(icon, null)
                        },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Green, selectedTextColor = Green, indicatorColor = CardBg
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 })
                    .togetherWith(fadeOut(tween(160)))
            }, label = "tabs", modifier = Modifier.padding(padding)
        ) { t ->
            when (t) {
                0 -> HomeTab(accounts, filtered, selectedMonth, months,
                    onMonth = { selectedMonth = it }, onManage = { showAccounts = true })
                1 -> ActivityTab(filtered, selectedMonth, months,
                    onMonth = { selectedMonth = it },
                    onDelete = { ManualStore.deleteManual(context, it); refreshKey++ })
                2 -> InsightsTab(filtered, selectedMonth, months, onMonth = { selectedMonth = it })
                3 -> SetupTab(scan.missed, onChanged = { refreshKey++ })
            }
        }
    }

    if (showSheet) {
        AddSheet(context, accounts, onDismiss = { showSheet = false },
            onSaved = { showSheet = false; refreshKey++ })
    }
}

@Composable
fun MonthBar(months: List<String>, selected: String, onMonth: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(months) { m ->
            val isSel = m == selected
            Surface(shape = RoundedCornerShape(20.dp), color = if (isSel) Green else CardBg,
                modifier = Modifier.clickable { onMonth(m) }) {
                Text(if (m == "All") "All time" else monthLabel(m),
                    color = if (isSel) Color.White else Ink,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

// ---------- HOME ----------
@Composable
fun HomeTab(
    accounts: List<DisplayAccount>, filtered: List<Txn>,
    selectedMonth: String, months: List<String>,
    onMonth: (String) -> Unit, onManage: () -> Unit
) {
    val netWorth = accounts.sumOf { it.balance ?: 0.0 }
    val income = filtered.filter { it.type == TxnType.CREDIT }.sumOf { it.amount }
    val spent = filtered.filter { it.type == TxnType.DEBIT }.sumOf { it.amount }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Green).padding(20.dp)) {
                Text("Hey there 👋", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text("Here's your money", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Text("Total across all accounts", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                AnimatedCounter(netWorth)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    FlowStat("Came in", income, Modifier.weight(1f), selectedMonth, true)
                    FlowStat("Went out", spent, Modifier.weight(1f), selectedMonth, false)
                }
            }
        }
        item { MonthBar(months, selectedMonth, onMonth) }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Your accounts", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                Row(Modifier.clickable { onManage() }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, tint = Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Manage", color = Green, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (accounts.isEmpty()) {
            item {
                EmptyHint("No accounts yet. Your bank texts will fill these in automatically, " +
                        "or tap Manage to add cash or a wallet.")
            }
        }
        items(accounts) { acc -> HomeAccountCard(acc) }
        if (accounts.size > 1) {
            item {
                Text("Two accounts that are really the same money? Tap Manage to link them " +
                        "so your total stays accurate.",
                    color = Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
fun AnimatedCounter(value: Double) {
    val animated by animateFloatAsState(value.toFloat(),
        animationSpec = tween(700, easing = FastOutSlowInEasing), label = "bal")
    Text(egp(animated.toDouble()), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun FlowStat(label: String, value: Double, modifier: Modifier, month: String, isUp: Boolean) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.15f)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isUp) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(label + if (month == "All") "" else " this month",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(egp(value), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun HomeAccountCard(acc: DisplayAccount) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Green.copy(alpha = 0.15f)),
                    Alignment.Center) {
                    Icon(typeIcon(acc.type), null, tint = GreenDark, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(acc.name, fontWeight = FontWeight.SemiBold, color = Ink)
                    val sub = if (acc.ids.size > 1) "${acc.type} · ${acc.ids.size} linked"
                    else acc.type
                    Text(sub, fontSize = 12.sp, color = Muted)
                }
            }
            Text(if (acc.balance != null) egp(acc.balance) else "—",
                fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}

fun typeIcon(type: String) = when (type) {
    "Card" -> Icons.Default.CreditCard
    "Cash" -> Icons.Default.Payments
    "Wallet" -> Icons.Default.AccountBalanceWallet
    else -> Icons.Default.AccountBalance
}

// ---------- ACCOUNTS MANAGEMENT ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(context: Context, accounts: List<DisplayAccount>,
                   onBack: () -> Unit, onChanged: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var editBalanceOf by remember { mutableStateOf<DisplayAccount?>(null) }
    var renameOf by remember { mutableStateOf<DisplayAccount?>(null) }
    var linkFrom by remember { mutableStateOf<DisplayAccount?>(null) }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Manage accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Ink)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true },
                containerColor = Green, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("New account") })
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Add cash or wallet accounts, fix a balance, or link an account and its " +
                        "debit card together so they count once.", color = Muted, fontSize = 14.sp)
            }
            items(accounts) { acc ->
                ManageAccountCard(
                    acc,
                    onRename = { renameOf = acc },
                    onBalance = { editBalanceOf = acc },
                    onLink = { linkFrom = acc },
                    onUnlink = { rawId ->
                        if (!acc.implicit) { AccountRegistry.unlinkId(context, acc.id, rawId); onChanged() }
                    },
                    onUseBank = {
                        if (!acc.implicit) { AccountRegistry.setBalance(context, acc.id, null); onChanged() }
                    },
                    onDelete = {
                        if (!acc.implicit) { AccountRegistry.remove(context, acc.id); onChanged() }
                    }
                )
            }
        }
    }

    if (showAdd) AddAccountDialog(
        onDismiss = { showAdd = false },
        onSave = { name, type, bal ->
            AccountRegistry.add(context, name, type, emptyList(), bal)
            showAdd = false; onChanged()
        })

    renameOf?.let { acc ->
        TextEditDialog("Rename account", acc.name, "Account name",
            onDismiss = { renameOf = null }) { newName ->
            val id = materialize(context, acc)
            AccountRegistry.rename(context, id, newName); renameOf = null; onChanged()
        }
    }

    editBalanceOf?.let { acc ->
        NumberEditDialog("Set balance", acc.balance ?: 0.0, "Balance (EGP)",
            onDismiss = { editBalanceOf = null }) { v ->
            val id = materialize(context, acc)
            AccountRegistry.setBalance(context, id, v); editBalanceOf = null; onChanged()
        }
    }

    linkFrom?.let { from ->
        LinkDialog(from, accounts.filter { it.id != from.id },
            onDismiss = { linkFrom = null }) { into ->
            val targetId = materialize(context, into)
            val sourceIds = from.ids
            AccountRegistry.addLinkedIds(context, targetId, sourceIds)
            if (!from.implicit) AccountRegistry.remove(context, from.id)
            linkFrom = null; onChanged()
        }
    }
}

@Composable
fun ManageAccountCard(
    acc: DisplayAccount, onRename: () -> Unit, onBalance: () -> Unit,
    onLink: () -> Unit, onUnlink: (String) -> Unit, onUseBank: () -> Unit, onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(typeIcon(acc.type), null, tint = GreenDark, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(acc.name, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(acc.type, fontSize = 12.sp, color = Muted)
                    }
                }
                Text(if (acc.balance != null) egp(acc.balance) else "—",
                    fontWeight = FontWeight.Bold, color = Ink)
            }
            if (acc.ids.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Linked numbers", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    acc.ids.forEach { rid ->
                        AssistChip(onClick = { if (acc.ids.size > 1) onUnlink(rid) },
                            label = { Text("•• $rid") },
                            trailingIcon = {
                                if (acc.ids.size > 1)
                                    Icon(Icons.Default.Close, "unlink", modifier = Modifier.size(16.dp))
                            })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniBtn(Icons.Default.Edit, "Rename", onRename)
                MiniBtn(Icons.Default.Tune, "Balance", onBalance)
                MiniBtn(Icons.Default.Link, "Link", onLink)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (acc.hasOverride) MiniBtn(Icons.Default.Sync, "Use bank balance", onUseBank)
                if (!acc.implicit) MiniBtn(Icons.Default.Delete, "Remove", onDelete, danger = true)
            }
        }
    }
}

@Composable
fun MiniBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
            onClick: () -> Unit, danger: Boolean = false) {
    Surface(shape = RoundedCornerShape(10.dp),
        color = if (danger) Red.copy(alpha = 0.1f) else Green.copy(alpha = 0.1f),
        modifier = Modifier.clickable { onClick() }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (danger) Red else GreenDark, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 13.sp, color = if (danger) Red else GreenDark)
        }
    }
}

@Composable
fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, String, Double?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Cash") }
    var bal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = name.isNotBlank(),
                onClick = { onSave(name, type, bal.toDoubleOrNull()) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New account") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Cash") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cash", "Wallet", "Bank", "Card").forEach {
                        FilterChip(type == it, { type = it }, label = { Text(it) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(bal, { bal = it }, label = { Text("Starting balance (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}

@Composable
fun TextEditDialog(title: String, initial: String, label: String,
                   onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text(label) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    )
}

@Composable
fun NumberEditDialog(title: String, initial: Double, label: String,
                     onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (initial != 0.0) initial.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { text.toDoubleOrNull()?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    )
}

@Composable
fun LinkDialog(from: DisplayAccount, others: List<DisplayAccount>,
               onDismiss: () -> Unit, onPick: (DisplayAccount) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Link \"${from.name}\" with…") },
        text = {
            Column {
                Text("Pick the account that's really the same money. They'll merge into one " +
                        "and count once.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                others.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(typeIcon(o.type), null, tint = GreenDark, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(o.name, color = Ink)
                    }
                }
                if (others.isEmpty()) Text("No other accounts to link.", color = Muted)
            }
        }
    )
}

// ---------- ACTIVITY ----------
@Composable
fun ActivityTab(filtered: List<Txn>, selectedMonth: String, months: List<String>,
                onMonth: (String) -> Unit, onDelete: (Long) -> Unit) {
    var typeFilter by remember { mutableStateOf("All") }
    var catFilter by remember { mutableStateOf("All") }
    val cats = listOf("All") + filtered.map { it.category }.distinct().sorted()
    val shown = filtered.filter {
        (typeFilter == "All" ||
                (typeFilter == "Out" && it.type == TxnType.DEBIT) ||
                (typeFilter == "In" && it.type == TxnType.CREDIT)) &&
                (catFilter == "All" || it.category == catFilter)
    }
    Column {
        MonthBar(months, selectedMonth, onMonth)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "In", "Out").forEach { f ->
                FilterChip(typeFilter == f, { typeFilter = f }, label = { Text(f) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Green, selectedLabelColor = Color.White))
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats) { c -> FilterChip(catFilter == c, { catFilter = c }, label = { Text(c) }) }
        }
        if (shown.isEmpty()) EmptyHint("Nothing here yet for this filter.")
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(shown, key = { it.id }) { t ->
                TxnRow(t, onDelete = if (t.manual) ({ onDelete(t.id) }) else null)
            }
        }
    }
}

@Composable
fun TxnRow(t: Txn, onDelete: (() -> Unit)?) {
    val idx = (t.category.hashCode() and 0xFFFF) % catColors.size
    val color = catColors[idx]
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), Alignment.Center) {
            Icon(iconFor(t.category), null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchant ?: t.category, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
            Text(buildString {
                append(t.category)
                if (t.manual) append(" · added by you")
                t.account?.let { append(" · ${acctLabel(it)}") }
                append(" · ${fmtDate(t.date)}")
            }, fontSize = 12.sp, color = Muted, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text((if (t.type == TxnType.DEBIT) "-" else "+") + egp(t.amount),
            color = if (t.type == TxnType.DEBIT) Red else Green, fontWeight = FontWeight.Bold)
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, "delete", tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
    HorizontalDivider(color = CardBg)
}

fun iconFor(cat: String) = when (cat) {
    "Food" -> Icons.Default.Restaurant
    "Transport" -> Icons.Default.DirectionsCar
    "Shopping" -> Icons.Default.ShoppingBag
    "Bills" -> Icons.Default.Receipt
    "Entertainment" -> Icons.Default.Movie
    "ATM" -> Icons.Default.LocalAtm
    "InstaPay" -> Icons.Default.Bolt
    "Card Payment" -> Icons.Default.CreditCard
    "Transfer" -> Icons.Default.SwapHoriz
    "Salary" -> Icons.Default.Savings
    else -> Icons.Default.Payments
}

// ---------- INSIGHTS ----------
@Composable
fun InsightsTab(filtered: List<Txn>, selectedMonth: String, months: List<String>,
                onMonth: (String) -> Unit) {
    val debits = filtered.filter { it.type == TxnType.DEBIT }
    val total = debits.sumOf { it.amount }
    val byCat = debits.groupBy { it.category }
        .mapValues { it.value.sumOf { t -> t.amount } }.toList().sortedByDescending { it.second }
    val topMerchants = debits.filter { it.merchant != null }
        .groupBy { it.merchant!! }.mapValues { it.value.sumOf { t -> t.amount } }
        .toList().sortedByDescending { it.second }.take(5)

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { MonthBar(months, selectedMonth, onMonth) }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You spent ${if (selectedMonth == "All") "in total" else "in " + monthLabel(selectedMonth)}",
                        color = Muted)
                    Spacer(Modifier.height(12.dp))
                    DonutChart(byCat, total)
                    Spacer(Modifier.height(8.dp))
                    Text(egp(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("across ${debits.size} expenses", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Text("Where it went", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
        }
        items(byCat) { (cat, amt) ->
            val frac = if (total > 0) (amt / total).toFloat() else 0f
            val idx = (cat.hashCode() and 0xFFFF) % catColors.size
            CategoryBar(cat, amt, frac, catColors[idx])
        }
        if (topMerchants.isNotEmpty()) {
            item {
                Text("Top places", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items(topMerchants) { (m, amt) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.SpaceBetween) {
                    Text(m, color = Ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(egp(amt), fontWeight = FontWeight.SemiBold, color = Ink)
                }
            }
        }
        if (debits.isEmpty()) item { EmptyHint("No spending to break down for this period yet.") }
    }
}

@Composable
fun DonutChart(data: List<Pair<String, Double>>, total: Double) {
    val anim by animateFloatAsState(if (total > 0) 1f else 0f, tween(800), label = "donut")
    Canvas(Modifier.size(160.dp)) {
        val stroke = 34f
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        if (total <= 0) {
            drawArc(CardBg, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            return@Canvas
        }
        var start = -90f
        data.forEachIndexed { i, (_, amt) ->
            val sweep = (amt / total).toFloat() * 360f * anim
            drawArc(catColors[i % catColors.size], start, sweep, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable
fun CategoryBar(cat: String, amount: Double, fraction: Float, color: Color) {
    val anim by animateFloatAsState(fraction, tween(600, easing = FastOutSlowInEasing), label = "bar")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(iconFor(cat), null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(cat, color = Ink, fontWeight = FontWeight.Medium)
            }
            Text("${egp(amount)}  ·  ${"%.0f".format(fraction * 100)}%", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardBg)) {
            Box(Modifier.fillMaxWidth(anim).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

// ---------- SETUP ----------
@Composable
fun SetupTab(missed: List<RawMsg>, onChanged: () -> Unit) {
    val context = LocalContext.current
    var section by remember { mutableStateOf(0) }
    Column {
        TabRow(section, containerColor = Color.White, contentColor = Green) {
            Tab(section == 0, { section = 0 }, text = { Text("Keywords") })
            Tab(section == 1, { section = 1 }, text = { Text("Missed (${missed.size})") })
        }
        if (section == 0) RulesContent(context, onChanged) else MissedContent(missed)
    }
}

@Composable
fun RulesContent(context: Context, onChanged: () -> Unit) {
    var newWord by remember { mutableStateOf("") }
    var isDebit by remember { mutableStateOf(true) }
    var version by remember { mutableStateOf(0) }
    val debitWords = remember(version) { RulesStore.extraDebitWords(context).toList() }
    val creditWords = remember(version) { RulesStore.extraCreditWords(context).toList() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Teach the app new words", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
            Text("If a bank message gets missed, add the exact word it uses (Arabic or English) " +
                    "and the app will catch it from now on.", color = Muted, fontSize = 14.sp)
        }
        item {
            OutlinedTextField(newWord, { newWord = it },
                label = { Text("Word, e.g. \"راتب\" or \"salary\"") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(isDebit, { isDebit = true }, label = { Text("Money out") })
                FilterChip(!isDebit, { isDebit = false }, label = { Text("Money in") })
            }
            Spacer(Modifier.height(8.dp))
            Button(enabled = newWord.isNotBlank(), onClick = {
                RulesStore.addWord(context, newWord, isDebit); newWord = ""; version++; onChanged()
            }) { Text("Add word") }
        }
        if (debitWords.isNotEmpty()) {
            item { Text("Money-out words", fontWeight = FontWeight.SemiBold, color = Ink) }
            items(debitWords) { w -> KeywordRow(w) { RulesStore.removeWord(context, w, true); version++; onChanged() } }
        }
        if (creditWords.isNotEmpty()) {
            item { Text("Money-in words", fontWeight = FontWeight.SemiBold, color = Ink) }
            items(creditWords) { w -> KeywordRow(w) { RulesStore.removeWord(context, w, false); version++; onChanged() } }
        }
    }
}

@Composable
fun KeywordRow(word: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(word, color = Ink)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remove", tint = Muted) }
    }
}

@Composable
fun MissedContent(missed: List<RawMsg>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("These texts mention money but the app wasn't sure how to read them. Spot the " +
                    "keyword they use and add it under Keywords.", color = Muted, fontSize = 14.sp)
        }
        items(missed) { m ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(12.dp)) {
                    Text(m.sender, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GreenDark)
                    Spacer(Modifier.height(4.dp))
                    Text(m.body, fontSize = 13.sp, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(fmtDate(m.date), fontSize = 11.sp, color = Muted)
                }
            }
        }
        if (missed.isEmpty()) item { EmptyHint("All good — every money text was read correctly.") }
    }
}

// ---------- Add sheet ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(context: Context, accounts: List<DisplayAccount>,
             onDismiss: () -> Unit, onSaved: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isExpense by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var pickedId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "Cash") }
    var category by remember { mutableStateOf("Other") }
    var note by remember { mutableStateOf("") }

    val cats = listOf("Food", "Transport", "Shopping", "Bills", "Entertainment",
        "ATM", "InstaPay", "Card Payment", "Transfer", "Salary", "Other")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("Add a transaction", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                SegBtn("Spent", isExpense, Modifier.weight(1f)) { isExpense = true }
                SegBtn("Received", !isExpense, Modifier.weight(1f)) { isExpense = false }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (EGP)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("Account", color = Muted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.isEmpty()) {
                    FilterChip(true, {}, label = { Text("Cash") })
                }
                accounts.forEach { a ->
                    FilterChip(pickedId == a.id, { pickedId = a.id }, label = { Text(a.name) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Category", color = Muted, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c -> FilterChip(category == c, { category = c }, label = { Text(c) }) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        val target = accounts.firstOrNull { it.id == pickedId }
                        // figure out a stable account + its registry id for balance adjustment
                        val accountName: String
                        val accountId: String
                        if (target == null) {
                            accountId = AccountRegistry.add(context, "Cash", "Cash", emptyList(), 0.0)
                            accountName = "Cash"
                        } else {
                            accountId = materialize(context, target)
                            accountName = target.name
                        }
                        ManualStore.addManual(context, amt,
                            if (isExpense) TxnType.DEBIT else TxnType.CREDIT,
                            accountName, category, note, System.currentTimeMillis())
                        val base = target?.balance ?: 0.0
                        val delta = if (isExpense) -amt else amt
                        AccountRegistry.setBalance(context, accountId, base + delta)
                        onSaved()
                    }
                },
                enabled = amount.toDoubleOrNull() != null,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) { Text("Save", modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}

@Composable
fun SegBtn(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable { onClick() }, shape = RoundedCornerShape(12.dp),
        color = if (selected) Green else CardBg) {
        Text(label, color = if (selected) Color.White else Ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, color = Muted, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center)
}
