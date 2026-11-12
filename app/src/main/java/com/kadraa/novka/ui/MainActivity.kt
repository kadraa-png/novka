@file:OptIn(ExperimentalMaterial3Api::class)

package com.kadraa.novka.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kadraa.novka.R
import com.kadraa.novka.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val data = rememberAppData(vm)
            val lockEnabled = data?.settings?.lockEnabled
            LaunchedEffect(lockEnabled) {
                if (lockEnabled == null) return@LaunchedEffect
                // Hides content in the recent-apps view and blocks screenshots while the lock is on.
                if (lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                if (!vm.lockChecked) {
                    vm.lockChecked = true
                    vm.locked = lockEnabled
                }
            }
            NovkaTheme(data?.settings?.theme ?: "system") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        data == null || (data.settings.lockEnabled && !vm.lockChecked) -> Unit
                        !data.settings.onboarded -> Onboarding(vm)
                        vm.locked -> LockScreen(vm, data.settings, this)
                        else -> AppScaffold(data, vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val reviewId = intent?.getLongExtra(EXTRA_REVIEW_TX, 0) ?: 0
        if (reviewId != 0L) vm.openTx(reviewId)
        if (intent?.getBooleanExtra(EXTRA_ADD, false) == true) vm.editing = Editing()
        intent?.removeExtra(EXTRA_REVIEW_TX)
        intent?.removeExtra(EXTRA_ADD)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val settings = vm.settings.value ?: return
        val away = SystemClock.elapsedRealtime() - vm.backgroundedAt
        if (settings.lockEnabled && vm.backgroundedAt != 0L && away >= settings.lockDelaySeconds * 1000L) vm.locked = true
        vm.backgroundedAt = 0
    }

    companion object {
        const val EXTRA_REVIEW_TX = "review_tx"
        const val EXTRA_ADD = "add"
    }
}

@Composable
fun LockScreen(vm: MainViewModel, settings: Settings, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val title = t(R.string.unlock_title)
    val usePin = t(R.string.use_pin)
    val canUseBiometrics = remember(settings.biometric) {
        settings.biometric && BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun biometric() {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.locked = false
                }
            },
        )
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(usePin).build())
    }

    fun submit() {
        if (busy || pin.isEmpty()) return
        busy = true
        scope.launch {
            // Each wrong attempt makes the next one wait longer.
            delay(minOf(attempts * 700L, 10_000L))
            if (vm.repo.settings.checkPin(pin)) {
                vm.locked = false
            } else {
                attempts++
                wrong = true
                pin = ""
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { if (canUseBiometrics) biometric() }

    Column(
        Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Novka", style = MaterialTheme.typography.headlineMedium)
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = pin, onValueChange = { new -> if (new.all(Char::isDigit) && new.length <= 12) { pin = new; wrong = false } },
            label = { Text(t(R.string.pin)) }, singleLine = true, isError = wrong,
            supportingText = { if (wrong) Text(t(R.string.wrong_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Button(onClick = ::submit, enabled = !busy && pin.isNotEmpty()) { Text(t(R.string.unlock)) }
        if (canUseBiometrics) TextButton(onClick = ::biometric) { Text(t(R.string.use_biometrics)) }
    }
}

private val insightsIcon: ImageVector = materialIcon("Insights") {
    materialPath {
        moveTo(4f, 20f); lineTo(4f, 11f); lineTo(8f, 11f); lineTo(8f, 20f); close()
        moveTo(10f, 20f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); close()
        moveTo(16f, 20f); lineTo(16f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); close()
    }
}

private val budgetsIcon: ImageVector = materialIcon("Budgets") {
    materialPath {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 8f); lineTo(4f, 8f); close()
        moveTo(4f, 10.5f); lineTo(15f, 10.5f); lineTo(15f, 13.5f); lineTo(4f, 13.5f); close()
        moveTo(4f, 16f); lineTo(10f, 16f); lineTo(10f, 19f); lineTo(4f, 19f); close()
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", R.string.tab_home, Icons.Filled.Home),
    Tab("transactions", R.string.tab_transactions, Icons.AutoMirrored.Filled.List),
    Tab("insights", R.string.tab_insights, insightsIcon),
    Tab("budgets", R.string.tab_budgets, budgetsIcon),
    Tab("more", R.string.tab_more, Icons.Filled.Menu),
)

fun NavHostController.goToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppScaffold(data: AppData, vm: MainViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.snacks.collect { snack ->
            launch {
                val result = snackbar.showSnackbar(snack.text, snack.action, duration = if (snack.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) snack.onAction()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route, onClick = { nav.goToTab(tab.route) },
                            icon = { Icon(tab.icon, null) }, label = { Text(t(tab.label), maxLines = 1) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (onTab && route != "more") {
                FloatingActionButton(onClick = { vm.editing = Editing() }) { Icon(Icons.Filled.Add, t(R.string.add_transaction)) }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(if (onTab) padding else androidx.compose.foundation.layout.PaddingValues())) {
            composable("home") { HomeScreen(data, vm, nav) }
            composable("transactions") { TransactionsScreen(data, vm) }
            composable("insights") { InsightsScreen(data, vm, nav) }
            composable("budgets") { BudgetsScreen(data, vm) }
            composable("more") { MoreScreen(data, vm, nav) }
            composable("accounts") { AccountsScreen(data, vm, nav::popBackStack) }
            composable("categories") { CategoriesScreen(data, vm, nav::popBackStack) }
            composable("recurring") { RecurringScreen(data, vm, nav::popBackStack) }
            composable("capture") { CaptureScreen(data, vm, nav::popBackStack) }
            composable("currencies") { CurrenciesScreen(data, vm, nav::popBackStack) }
            composable("security") { SecurityScreen(data, vm, nav::popBackStack) }
            composable("backup") { BackupScreen(data, vm, nav::popBackStack) }
            composable("goals") { GoalsScreen(data, vm, nav::popBackStack) }
            composable("debts") { DebtsScreen(data, vm, nav::popBackStack) }
            composable("privacy") { PrivacyScreen(nav::popBackStack) }
        }
    }

    vm.editing?.let { editing -> TxEditorSheet(editing, data, vm) }
}
