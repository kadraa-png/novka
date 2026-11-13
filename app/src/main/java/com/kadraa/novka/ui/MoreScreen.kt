package com.kadraa.novka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kadraa.novka.R

private data class MoreLink(val route: String, val label: Int, val icon: ImageVector)

private val moneyLinks = listOf(
    MoreLink("accounts", R.string.accounts, Icons.Rounded.AccountBalanceWallet),
    MoreLink("categories", R.string.categories, Icons.Rounded.Category),
    MoreLink("recurring", R.string.recurring, Icons.Rounded.Autorenew),
    MoreLink("goals", R.string.goals, Icons.Rounded.Flag),
    MoreLink("debts", R.string.debts, Icons.Rounded.Handshake),
)

private val settingsLinks = listOf(
    MoreLink("capture", R.string.capture, Icons.Rounded.NotificationsActive),
    MoreLink("currencies", R.string.currencies, Icons.Rounded.CurrencyExchange),
    MoreLink("security", R.string.security, Icons.Rounded.Lock),
    MoreLink("backup", R.string.backup, Icons.Rounded.Backup),
    MoreLink("privacy", R.string.privacy, Icons.Rounded.PrivacyTip),
)

@Composable
fun MoreScreen(data: AppData, vm: MainViewModel, nav: NavHostController) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(t(R.string.tab_more), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp))
            LinkGroup(moneyLinks, nav)
            LinkGroup(settingsLinks, nav, Modifier.padding(top = 16.dp))

            SectionTitle(t(R.string.theme), Modifier.padding(horizontal = 16.dp))
            val themes = listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
            Segmented(
                themes.map { (value, label) -> value to t(label) }, data.settings.theme,
                { value -> vm.run { settings.update { it.copy(theme = value) } } }, Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun LinkGroup(links: List<MoreLink>, nav: NavHostController, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Column {
            links.forEach { link ->
                ListItem(
                    headlineContent = { Text(t(link.label)) },
                    leadingContent = { Icon(link.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { nav.navigate(link.route) },
                )
            }
        }
    }
}
