package com.kadraa.novka.ui

import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kadraa.novka.R
import com.kadraa.novka.capture.NovkaNotificationListener
import com.kadraa.novka.data.BackupCodec
import com.kadraa.novka.data.BackupException
import com.kadraa.novka.data.CardMapping
import com.kadraa.novka.data.Settings
import com.kadraa.novka.domain.CaptureStatus
import com.kadraa.novka.domain.CsvImporter
import com.kadraa.novka.domain.CurrencyConverter
import com.kadraa.novka.domain.PromptMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

private val transparentItem @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

private val sectionTitle = Modifier.padding(horizontal = 16.dp)

// ---- Automatic capture ----

@Composable
fun CaptureScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = data.settings
    var listenerOn by remember { mutableStateOf(NovkaNotificationListener.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { listenerOn = NovkaNotificationListener.isEnabled(context) }
    var showAll by remember { mutableStateOf(false) }
    val failed = remember(data.captured) { data.captured.filter { it.status == CaptureStatus.FAILED } }
    val accounts = data.activeAccounts.map { it.account }
    fun update(change: (Settings) -> Settings) = vm.run { settings.update(change) }

    SubScreen(t(R.string.capture), onBack) { padding ->
        ScreenColumn(padding) {
            SectionCard(container = if (listenerOn) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.tertiaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(if (listenerOn) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationsOff, null)
                        Text(t(if (listenerOn) R.string.capture_enabled else R.string.capture_off_title), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(t(R.string.capture_disclosure), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(
                        onClick = { context.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.align(Alignment.End),
                    ) { Text(t(R.string.open_settings)) }
                }
            }

            SectionTitle(t(R.string.capture_sources), sectionTitle)
            SectionCard {
                Column {
                    SwitchRow("Google Wallet", s.walletEnabled, { on -> update { it.copy(walletEnabled = on) } })
                    SwitchRow("Raiffeisen", s.raiffeisenEnabled, { on -> update { it.copy(raiffeisenEnabled = on) } }, text = t(R.string.raiffeisen_text))
                }
            }

            SectionTitle(t(R.string.prompt_mode), sectionTitle)
            SectionCard {
                Column {
                    RadioRow(t(R.string.prompt_always), s.promptMode == PromptMode.ALWAYS, { update { it.copy(promptMode = PromptMode.ALWAYS) } })
                    RadioRow(
                        t(R.string.prompt_until_confident), s.promptMode == PromptMode.UNTIL_CONFIDENT, { update { it.copy(promptMode = PromptMode.UNTIL_CONFIDENT) } },
                        text = t(R.string.prompt_until_confident_text),
                    )
                    RadioRow(t(R.string.prompt_never), s.promptMode == PromptMode.NEVER, { update { it.copy(promptMode = PromptMode.NEVER) } })
                }
            }

            if (data.cards.isNotEmpty()) {
                SectionTitle(t(R.string.cards), sectionTitle)
                SectionCard {
                    Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        data.cards.forEach { card ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dropdown(
                                    t(R.string.card_ending, card.cardLast4), accounts, accounts.firstOrNull { it.id == card.accountId }, text = { it.name },
                                    onSelect = { vm.run { dao.putCard(CardMapping(card.cardLast4, it.id)) } }, modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { vm.run { dao.deleteCard(card.cardLast4) } }) { Icon(Icons.Rounded.Delete, t(R.string.delete)) }
                            }
                        }
                    }
                }
            }

            if (failed.isNotEmpty()) {
                SectionTitle(t(R.string.could_not_read), sectionTitle)
                SectionCard {
                    Column {
                        failed.forEach { item ->
                            ListItem(
                                headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(item.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                                trailingContent = { IconButton(onClick = { vm.run { dao.deleteCaptured(item.id) } }) { Icon(Icons.Rounded.Delete, t(R.string.delete)) } },
                                colors = transparentItem,
                                modifier = Modifier.clickable { vm.editing = Editing(note = item.text, capturedId = item.id) },
                            )
                        }
                    }
                }
            }

            if (data.captured.isNotEmpty()) {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(t(if (showAll) R.string.hide_captured else R.string.show_captured))
                }
                if (showAll) {
                    SectionCard {
                        Column {
                            data.captured.take(50).forEach { item ->
                                ListItem(
                                    overlineContent = { Text("${item.status.name} · ${item.source.name} · ${formatDate(item.postedAt.toDate())}") },
                                    headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(item.text) },
                                    colors = transparentItem,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Currencies ----

@Composable
fun CurrenciesScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    val main = data.main
    var pickMain by remember { mutableStateOf(false) }
    var pendingMain by remember { mutableStateOf<String?>(null) }
    var pickManual by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf<String?>(null) }
    val updated = t(R.string.rates_updated)
    val failed = t(R.string.rates_failed)
    val foreign = remember(data.accounts, main) { data.accounts.map { it.account.currency }.distinct().filter { it != main } }
    val fetchedAt = data.settings.ratesFetchedAt

    SubScreen(t(R.string.currencies), onBack) { padding ->
        ScreenColumn(padding) {
            SectionCard {
                ListItem(
                    overlineContent = { Text(t(R.string.main_currency)) },
                    headlineContent = { Text("$main · ${Currency.getInstance(main).displayName}") },
                    supportingContent = { Text(t(R.string.main_currency_hint)) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    colors = transparentItem, modifier = Modifier.clickable { pickMain = true },
                )
            }

            SectionTitle(t(R.string.exchange_rates), sectionTitle)
            SectionCard {
                Column {
                    ListItem(
                        headlineContent = { Text(if (fetchedAt == 0L) t(R.string.rates_missing) else t(R.string.rates_from, formatDate(fetchedAt.toDate()))) },
                        trailingContent = { FilledTonalButton(onClick = { vm.run { vm.say(if (refreshRates()) updated else failed) } }) { Text(t(R.string.refresh)) } },
                        colors = transparentItem,
                    )
                    foreign.forEach { code ->
                        val rate = CurrencyConverter.rate(main, code, data.rates)
                        ListItem(
                            headlineContent = { Text(if (rate == null) t(R.string.no_rate, code) else "1 $main = ${String.format(Locale.getDefault(), "%.4f", rate)} $code") },
                            trailingContent = { Icon(Icons.Rounded.Edit, t(R.string.set_rate)) },
                            colors = transparentItem, modifier = Modifier.clickable { manual = code },
                        )
                    }
                }
            }
            OutlinedButton(onClick = { pickManual = true }, modifier = Modifier.padding(16.dp)) { Text(t(R.string.set_rate)) }
        }
    }

    if (pickMain) CurrencyPickerDialog(onPick = { if (it != main) pendingMain = it }, onDismiss = { pickMain = false })
    if (pickManual) CurrencyPickerDialog(onPick = { if (it != main) manual = it }, onDismiss = { pickManual = false })
    pendingMain?.let { code ->
        ConfirmDialog(
            t(R.string.change_main_title, code), t(R.string.change_main_text), t(R.string.ok),
            onConfirm = { vm.run { changeMainCurrency(code) } }, onDismiss = { pendingMain = null },
        )
    }
    manual?.let { code ->
        var text by remember(code) { mutableStateOf("") }
        val value = text.replace(',', '.').toDoubleOrNull()
        FormDialog(
            title = t(R.string.set_rate), onDismiss = { manual = null }, onSave = { vm.run { setManualRate(code, value!!) } },
            saveEnabled = value != null && value > 0,
        ) {
            OutlinedTextField(
                text, { text = it }, label = { Text("1 $main =") }, suffix = { Text(code) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---- Security ----

@Composable
fun SecurityScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    val s = data.settings
    var hasPin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { hasPin = vm.repo.settings.hasPin() }
    var pinDialog by remember { mutableStateOf(false) }
    var lockAfterPin by remember { mutableStateOf(false) }
    fun update(change: (Settings) -> Settings) = vm.run { settings.update(change) }

    SubScreen(t(R.string.security), onBack) { padding ->
        ScreenColumn(padding) {
            SectionCard {
                Column {
                    SwitchRow(
                        t(R.string.app_lock), s.lockEnabled,
                        { on ->
                            // The lock is useless without a PIN, so turning it on the first time sets one.
                            if (on && hasPin != true) { lockAfterPin = true; pinDialog = true } else update { it.copy(lockEnabled = on) }
                        },
                        text = t(R.string.app_lock_text),
                    )
                    ListItem(
                        headlineContent = { Text(t(if (hasPin == true) R.string.change_pin else R.string.set_pin)) },
                        leadingContent = { Icon(Icons.Rounded.Password, null) },
                        colors = transparentItem, modifier = Modifier.clickable { lockAfterPin = false; pinDialog = true },
                    )
                    SwitchRow(t(R.string.use_biometrics), s.biometric, { on -> update { it.copy(biometric = on) } }, enabled = s.lockEnabled)
                    SwitchRow(
                        t(R.string.hide_amounts), s.hideAmountOnLockScreen, { on -> update { it.copy(hideAmountOnLockScreen = on) } },
                        text = t(R.string.hide_amounts_text),
                    )
                }
            }

            SectionTitle(t(R.string.lock_after), sectionTitle)
            SectionCard {
                Column {
                    listOf(0 to R.string.lock_immediately, 30 to R.string.lock_30s, 60 to R.string.lock_1m, 300 to R.string.lock_5m).forEach { (seconds, label) ->
                        RadioRow(t(label), s.lockDelaySeconds == seconds, { update { it.copy(lockDelaySeconds = seconds) } })
                    }
                }
            }
        }
    }

    if (pinDialog) {
        var pin by remember { mutableStateOf("") }
        var repeat by remember { mutableStateOf("") }
        val enable = lockAfterPin
        FormDialog(
            title = t(if (hasPin == true) R.string.change_pin else R.string.set_pin), onDismiss = { pinDialog = false },
            onSave = {
                hasPin = true
                vm.run {
                    settings.setPin(pin)
                    if (enable) settings.update { it.copy(lockEnabled = true) }
                }
            },
            saveEnabled = pin.length >= 4 && pin == repeat,
        ) {
            listOf(Triple(pin, R.string.new_pin, { v: String -> pin = v }), Triple(repeat, R.string.repeat_pin, { v: String -> repeat = v })).forEach { (value, label, set) ->
                OutlinedTextField(
                    value, { new -> if (new.all(Char::isDigit) && new.length <= 12) set(new) }, label = { Text(t(label)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(t(R.string.pin_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Backup and export ----

@Composable
fun BackupScreen(data: AppData, vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var password by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val accounts = data.activeAccounts.map { it.account }
    var importInto by remember { mutableStateOf(accounts.firstOrNull()) }
    val today = LocalDate.now()

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.run {
            val file = snapshot()
            val bytes = withContext(Dispatchers.Default) { BackupCodec.encode(file, password.ifEmpty { null }) }
            withContext(Dispatchers.IO) { resolver.openOutputStream(uri)!!.use { it.write(bytes) } }
            vm.say(context.getString(R.string.backup_saved))
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.run { pending = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes() } } }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.run {
            val text = exportCsv(0, Long.MAX_VALUE)
            withContext(Dispatchers.IO) { resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
            vm.say(context.getString(R.string.csv_saved))
        }
    }
    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val account = importInto
        if (uri != null && account != null) vm.run {
            val text = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8) }
            val result = CsvImporter.import(text, account.currency)
            if (result.missingColumns.isNotEmpty()) {
                vm.say(context.getString(R.string.csv_missing_columns, result.missingColumns.joinToString()))
            } else {
                vm.say(context.getString(R.string.csv_imported, importRows(account.id, result.rows), result.skipped))
            }
        }
    }

    SubScreen(t(R.string.backup), onBack) { padding ->
        ScreenColumn(padding) {
            SectionTitle(t(R.string.backup_title), sectionTitle)
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t(R.string.backup_text), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        password, { password = it }, label = { Text(t(R.string.backup_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { createBackup.launch("novka-$today.novka") }) { Text(t(R.string.create_backup)) }
                        OutlinedButton(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text(t(R.string.restore_backup)) }
                    }
                }
            }

            SectionTitle(t(R.string.csv_title), sectionTitle)
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t(R.string.csv_text), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { saveCsv.launch("novka-$today.csv") }) { Text(t(R.string.export_csv)) }
                    Dropdown(t(R.string.import_into), accounts, importInto, text = { it.name }, onSelect = { importInto = it })
                    OutlinedButton(onClick = { openCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, enabled = importInto != null) {
                        Text(t(R.string.import_csv))
                    }
                }
            }
        }
    }

    pending?.let { bytes ->
        val encrypted = remember(bytes) { BackupCodec.isEncrypted(bytes) }
        var filePassword by remember(bytes) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(t(R.string.restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(t(R.string.restore_text))
                    if (encrypted) {
                        OutlinedTextField(
                            filePassword, { filePassword = it }, label = { Text(t(R.string.backup_password)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !encrypted || filePassword.isNotEmpty(),
                    onClick = {
                        pending = null
                        vm.run {
                            try {
                                restore(withContext(Dispatchers.Default) { BackupCodec.decode(bytes, filePassword.ifEmpty { null }) })
                                vm.say(context.getString(R.string.restore_done))
                            } catch (e: BackupException) {
                                vm.say(
                                    context.getString(
                                        when (e.reason) {
                                            BackupException.Reason.NEEDS_PASSWORD, BackupException.Reason.WRONG_PASSWORD -> R.string.restore_wrong_password
                                            BackupException.Reason.TOO_NEW -> R.string.restore_too_new
                                            BackupException.Reason.CORRUPT -> R.string.restore_corrupt
                                        }
                                    )
                                )
                            }
                        }
                    },
                ) { Text(t(R.string.restore_backup)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(t(R.string.cancel)) } },
        )
    }
}

// ---- Privacy ----

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    SubScreen(t(R.string.privacy), onBack) { padding ->
        ScreenColumn(padding) {
            Text(t(R.string.privacy_text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}
