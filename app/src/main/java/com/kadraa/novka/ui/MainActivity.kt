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
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavBackStackEntry
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

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (canUseBiometrics) biometric() else focus.requestFocus() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(Icons.Rounded.Lock, 72.dp, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(24.dp))
        Text("Novka", style = MaterialTheme.typography.headlineMedium)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = pin, onValueChange = { new -> if (new.all(Char::isDigit) && new.length <= 12) { pin = new; wrong = false } },
                label = { Text(t(R.string.pin)) }, singleLine = true, isError = wrong,
                supportingText = { if (wrong) Text(t(R.string.wrong_pin)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Button(onClick = ::submit, enabled = !busy && pin.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(t(R.string.unlock)) }
            if (canUseBiometrics) {
                FilledTonalButton(onClick = ::biometric, modifier = Modifier.fillMaxWidth(), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Rounded.Fingerprint, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(t(R.string.use_biometrics))
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: Int, val selectedIcon: ImageVector, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", R.string.tab_home, Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("transactions", R.string.tab_transactions, Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab("insights", R.string.tab_insights, Icons.Rounded.Insights, Icons.Outlined.Insights),
    Tab("budgets", R.string.tab_budgets, Icons.Rounded.PieChart, Icons.Outlined.PieChart),
    Tab("more", R.string.tab_more, Icons.Rounded.Menu, Icons.Rounded.Menu),
)

private val tabRoutes = tabs.map { it.route }.toSet()

/** Moving between two tabs cross-fades; opening and closing a sub-screen slides a short way sideways. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSwitch() =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

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

    // Navigation bar on phones, navigation rail from 600dp of width.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val canAdd = onTab && route != "more"
    val addLabel = t(R.string.add_transaction)

    Row(Modifier.fillMaxSize()) {
        if (wide && onTab) {
            NavigationRail(
                header = {
                    if (canAdd) FloatingActionButton(onClick = { vm.editing = Editing() }) { Icon(Icons.Rounded.Add, addLabel) }
                },
            ) {
                tabs.forEach { tab ->
                    val selected = route == tab.route
                    NavigationRailItem(
                        selected = selected, onClick = { nav.goToTab(tab.route) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) }, label = { Text(t(tab.label), maxLines = 1) },
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide && onTab) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected, onClick = { nav.goToTab(tab.route) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) }, label = { Text(t(tab.label), maxLines = 1) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!wide && canAdd) {
                    ExtendedFloatingActionButton(onClick = { vm.editing = Editing() }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(addLabel) })
                }
            },
        ) { padding ->
            NavHost(
                nav, startDestination = "home",
                // Sub-screens bring their own scaffold; tabs sit inside this one.
                modifier = if (onTab) Modifier.padding(padding).consumeWindowInsets(padding) else Modifier,
                enterTransition = {
                    if (tabSwitch()) fadeIn(tween(Motion.SHORT))
                    else slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { it / 8 } + fadeIn(tween(Motion.SHORT))
                },
                exitTransition = { fadeOut(tween(Motion.QUICK)) },
                popEnterTransition = {
                    if (tabSwitch()) fadeIn(tween(Motion.SHORT))
                    else slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { -it / 8 } + fadeIn(tween(Motion.SHORT))
                },
                popExitTransition = {
                    if (tabSwitch()) fadeOut(tween(Motion.QUICK))
                    else slideOutHorizontally(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) { it / 8 } + fadeOut(tween(Motion.QUICK))
                },
            ) {
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
    }

    vm.editing?.let { editing -> TxEditorSheet(editing, data, vm) }
}
