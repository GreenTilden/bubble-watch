package com.darney.bubblewatch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.delay

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

// The in-app title card stays up at least this long (so the bath animation reads) and
// never longer than the cap, even if the first poll hangs (offline / not configured).
private const val SPLASH_MIN_MS = 1300L
private const val SPLASH_MAX_MS = 4000L

@Composable
fun CoworkApp() {
    MaterialTheme(colors = GreenBubblesColors) {
        // The thread list mounts underneath immediately and loads while the splash is
        // up; we crossfade the splash away once its first poll returns (or a cap hits).
        var listLoaded by remember { mutableStateOf(false) }
        var minTimePassed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(SPLASH_MIN_MS); minTimePassed = true }
        LaunchedEffect(Unit) { delay(SPLASH_MAX_MS); listLoaded = true } // hard fallback
        val showSplash = !(listLoaded && minTimePassed)
        val splashAlpha by animateFloatAsState(
            targetValue = if (showSplash) 1f else 0f,
            animationSpec = tween(500),
            label = "splashFade",
        )

        Box(Modifier.fillMaxSize()) {
            val nav = rememberSwipeDismissableNavController()
            SwipeDismissableNavHost(navController = nav, startDestination = Routes.THREADS) {
                composable(Routes.THREADS) {
                    ThreadListScreen(
                        onOpenThread = { index -> nav.navigate(Routes.detail(index)) },
                        onOpenIdle = { nav.navigate(Routes.IDLE) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onFirstLoad = { listLoaded = true },
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

            // Splash overlay on top of the list; removed from composition once faded out
            // (stops the bath animation). Enters instantly (seamless from the system
            // splash), fades out over 500ms to reveal the loaded list.
            if (splashAlpha > 0.01f) {
                SplashCard(Modifier.fillMaxSize().alpha(splashAlpha))
            }
        }
    }
}
