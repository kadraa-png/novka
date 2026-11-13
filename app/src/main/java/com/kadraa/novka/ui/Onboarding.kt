package com.kadraa.novka.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kadraa.novka.R
import com.kadraa.novka.capture.NovkaNotificationListener
import com.kadraa.novka.domain.AccountType
import com.kadraa.novka.domain.Money
import java.util.Currency
import java.util.Locale

private const val STEPS = 3

@Composable
fun accountTypeLabel(type: AccountType): String = t(
    when (type) {
        AccountType.CASH -> R.string.type_cash
        AccountType.BANK -> R.string.type_bank
        AccountType.CARD -> R.string.type_card
        AccountType.SAVINGS -> R.string.type_savings
    }
)

@Composable
fun Onboarding(vm: MainViewModel) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var currency by rememberSaveable { mutableStateOf("BAM") }
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    val defaultName = t(R.string.cash)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var type by rememberSaveable { mutableStateOf(AccountType.CASH) }
    var balance by rememberSaveable { mutableStateOf("") }
    var listenerOn by rememberSaveable { mutableStateOf(NovkaNotificationListener.isEnabled(context)) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { listenerOn = NovkaNotificationListener.isEnabled(context) }
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val parsedBalance = if (balance.isBlank()) 0L else Money.parseLoose(balance, currency)
    val canContinue = when (step) {
        1 -> name.isNotBlank() && parsedBalance != null
        else -> true
    }

    fun next() {
        if (step < STEPS - 1) {
            step++
            // Payment prompts and budget alerts are notifications; Android 13 and later ask for this.
            if (step == STEPS - 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!finishing) {
            finishing = true
            val bosnian = Locale.getDefault().language in setOf("bs", "hr", "sr")
            vm.run { completeOnboarding(currency, name.trim(), type, parsedBalance ?: 0L, bosnian) }
        }
    }

    BackHandler(enabled = step > 0) { step-- }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
            AnimatedContent(
                step, modifier = Modifier.weight(1f),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { direction * it / 8 } + fadeIn(tween(Motion.SHORT))) togetherWith
                        fadeOut(tween(Motion.QUICK))
                },
                label = "onboarding",
            ) { shown ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 40.dp, bottom = 16.dp)) {
                    when (shown) {
                        0 -> {
                            StepHeader(Icons.Rounded.Savings, t(R.string.welcome_title), t(R.string.welcome_text))
                            Card(
                                onClick = { pickCurrency = true }, shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            ) {
                                ListItem(
                                    overlineContent = { Text(t(R.string.main_currency)) },
                                    headlineContent = { Text("$currency · ${Currency.getInstance(currency).displayName}") },
                                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                            Text(
                                t(R.string.main_currency_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                            )
                        }
                        1 -> {
                            StepHeader(Icons.Rounded.AccountBalanceWallet, t(R.string.first_account_title), t(R.string.first_account_text))
                            AccountTypeChips(type) { type = it }
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(name, { name = it }, label = { Text(t(R.string.account_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(16.dp))
                            AmountField(balance, { balance = it }, t(R.string.opening_balance), currency)
                        }
                        else -> {
                            StepHeader(Icons.Rounded.NotificationsActive, t(R.string.capture_title), t(R.string.capture_disclosure))
                            if (listenerOn) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(t(R.string.capture_enabled), style = MaterialTheme.typography.bodyLarge)
                                }
                            } else {
                                FilledTonalButton(onClick = { context.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                                    Text(t(R.string.capture_allow))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = { step-- }) { Text(t(R.string.back)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::next, enabled = canContinue && !finishing) {
                    Text(t(if (step == STEPS - 1) R.string.finish else R.string.next))
                }
            }
        }
    }

    if (pickCurrency) CurrencyPickerDialog(onPick = { currency = it }, onDismiss = { pickCurrency = false })
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, text: String) {
    IconBadge(icon, 64.dp, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(32.dp))
}
