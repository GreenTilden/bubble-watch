package com.darney.bubblewatch

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.random.Random

// Soft, muted palette — not overstimulating
private val BUBBLE_COLORS = listOf(
    Color(0xFF6FA8DC), // soft blue
    Color(0xFF93C47D), // sage green
    Color(0xFFF6B26B), // warm peach
    Color(0xFFC9A0DC), // lavender
    Color(0xFF76D7C4), // mint
    Color(0xFFF7C6C7), // soft pink
)

private const val MAX_BUBBLES = 6
private const val BUBBLE_LIFETIME_MS = 2500L
private const val TAP_COOLDOWN_MS = 200L

data class Bubble(
    val id: Int,
    val x: Float,
    val y: Float,
    val color: Color,
    val createdAt: Long,
    val maxRadius: Float,
)

/**
 * Bubble animation. Two behaviors:
 *  - toddlerLock = true (default): consumes ALL pointer events so Wear OS dismiss
 *    gestures can't exit — the original toddler-distraction mode.
 *  - toddlerLock = false: used as the co-pilot idle/ambient screen — taps still pop
 *    bubbles, a long-press calls [onExit], and swipe-to-dismiss is left to the nav host.
 */
@Composable
fun BubbleScreen(
    modifier: Modifier = Modifier,
    toddlerLock: Boolean = true,
    onExit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    var bubbles by remember { mutableStateOf(listOf<Bubble>()) }
    var nextId by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val infiniteTransition = rememberInfiniteTransition(label = "frame")
    val frameTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tick"
    )

    val bgColor by animateColorAsState(
        targetValue = if (bubbles.isEmpty()) Color(0xFF1A1A2E) else Color(0xFF16213E),
        animationSpec = tween(800),
        label = "bg"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            val now = System.currentTimeMillis()
            bubbles = bubbles.filter { now - it.createdAt < BUBBLE_LIFETIME_MS }
        }
    }

    fun spawnBubble(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < TAP_COOLDOWN_MS) return
        lastTapTime = now

        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, 60))
        } catch (_: Exception) {
            // Some devices/permissions may reject haptics — never let it crash the animation.
        }

        val color = BUBBLE_COLORS[Random.nextInt(BUBBLE_COLORS.size)]
        val maxRadius = Random.nextFloat() * 20f + 25f
        val newBubble = Bubble(
            id = nextId++,
            x = x,
            y = y,
            color = color,
            createdAt = now,
            maxRadius = maxRadius,
        )
        bubbles = (bubbles + newBubble).takeLast(MAX_BUBBLES)
    }

    val gestureModifier = if (toddlerLock) {
        Modifier.pointerInput(Unit) {
            // Consume ALL pointer events to prevent Wear OS dismiss gestures.
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val firstDown = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                    if (firstDown != null) {
                        spawnBubble(firstDown.position.x, firstDown.position.y)
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    } else {
        // Idle mode: pop on tap, exit on long-press; leave swipe for the nav host.
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { pos -> spawnBubble(pos.x, pos.y) },
                onLongPress = { onExit?.invoke() },
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .onSizeChanged { canvasSize = it }
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frameTick // read to ensure recomposition

            val now = System.currentTimeMillis()

            for (bubble in bubbles) {
                val age = (now - bubble.createdAt).toFloat()
                val progress = (age / BUBBLE_LIFETIME_MS).coerceIn(0f, 1f)

                val scale = if (progress < 0.3f) {
                    progress / 0.3f
                } else {
                    1f - ((progress - 0.3f) / 0.7f) * 0.4f
                }
                val alpha = if (progress < 0.2f) {
                    progress / 0.2f
                } else {
                    1f - ((progress - 0.2f) / 0.8f)
                }.coerceIn(0f, 0.7f)

                val radius = bubble.maxRadius * scale * this.density

                drawCircle(
                    color = bubble.color.copy(alpha = alpha * 0.3f),
                    radius = radius * 1.5f,
                    center = Offset(bubble.x, bubble.y)
                )
                drawCircle(
                    color = bubble.color.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(bubble.x, bubble.y)
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.4f),
                    radius = radius * 0.35f,
                    center = Offset(
                        bubble.x - radius * 0.25f,
                        bubble.y - radius * 0.25f
                    )
                )
            }
        }
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize != IntSize.Zero && bubbles.isEmpty()) {
            spawnBubble(canvasSize.width / 2f, canvasSize.height / 2f)
        }
    }
}
