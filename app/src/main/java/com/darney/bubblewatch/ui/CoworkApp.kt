package com.darney.bubblewatch.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
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

@Composable
fun CoworkApp() {
    MaterialTheme {
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
