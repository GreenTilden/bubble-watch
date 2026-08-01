package com.darney.bubblewatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.darney.bubblewatch.cowork.detail.ThreadDetailScreen
import com.darney.bubblewatch.cowork.idle.IdleScreen
import com.darney.bubblewatch.cowork.settings.SettingsScreen
import com.darney.bubblewatch.cowork.threads.ThreadListScreen

object Routes {
    const val THREADS = "threads"
    const val DETAIL = "detail/{index}"
    const val IDLE = "idle"
    const val SETTINGS = "settings"
    fun detail(index: Int) = "detail/$index"
}

// "Green Bubbles" — lean into the Android-green flex (Android brand green #3DDC84):
// accents, chips, and highlights go green. Background stays TRUE BLACK so the OLED
// pendant keeps sipping battery (pairs with the sleep/poll-gating work); surface gets
// only a faint green tint. Status amber + the blue/yellow Keeper mark are left as-is.
private val GreenBubblesColors = Colors(
    primary = Color(0xFF3DDC84),
    primaryVariant = Color(0xFF1F9E5A),
    secondary = Color(0xFF7BE0A3),
    secondaryVariant = Color(0xFF2EA866),
    background = Color(0xFF000000),
    surface = Color(0xFF0E1F14),
    error = Color(0xFFFFB300),
    onPrimary = Color(0xFF00210F),
    onSecondary = Color(0xFF00210F),
    onBackground = Color(0xFFE6F4EA),
    onSurface = Color(0xFFE6F4EA),
    onError = Color(0xFF000000),
)

@Composable
fun CoworkApp() {
    MaterialTheme(colors = GreenBubblesColors) {
        val nav = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(navController = nav, startDestination = Routes.THREADS) {
            composable(Routes.THREADS) {
                ThreadListScreen(
                    onOpenThread = { index -> nav.navigate(Routes.detail(index)) },
                    onOpenIdle = { nav.navigate(Routes.IDLE) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                val index = entry.arguments?.getInt("index") ?: return@composable
                ThreadDetailScreen(
                    index = index,
                    // After a reply/menu-answer lands, drop back to the thread list
                    // (which scrolls itself to the top on resume) for the next glance.
                    onDone = { nav.popBackStack(Routes.THREADS, inclusive = false) },
                )
            }
            composable(Routes.IDLE) {
                IdleScreen(
                    onExit = { nav.popBackStack() },
                    onNeedsAttention = {
                        // Return to the thread list when something needs input.
                        nav.popBackStack(Routes.THREADS, inclusive = false)
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
