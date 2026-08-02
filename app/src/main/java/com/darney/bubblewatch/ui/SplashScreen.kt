package com.darney.bubblewatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The wordmark colors are the founder brand pair: olive + warm copper (the
// darn-tech.com action token that replaced IBM carbon blue). The ball-and-stick
// logomark keeps its own blue/yellow; only the WORDMARK is olive+copper. Drawn as a
// 5x7 pixel font so it reads as one family with the chunky Keeper sprite.
private val OLIVE = Color(0xFF747438)
private val COPPER = Color(0xFFC2703F)

// 5-wide x 7-tall glyphs for exactly the letters in BUBBLES.
private val FONT: Map<Char, List<String>> = mapOf(
    'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
)
private const val WORD = "BUBBLES"
private const val GW = 5   // glyph width in cells
private const val GH = 7   // glyph height in cells
private const val GAP = 1  // blank columns between glyphs
private val TOTAL_COLS = WORD.length * GW + (WORD.length - 1) * GAP  // 41

/**
 * The pixel "Bubbles" wordmark: olive letters with a warm-copper drop-shadow, matching
 * the 16/32-bit look of the logomark sprite. Sizes itself to [widthDp].
 */
@Composable
fun BubblesWordmark(modifier: Modifier = Modifier, widthDp: Dp = 128.dp) {
    val cell = widthDp / (TOTAL_COLS + 1)   // +1 leaves room for the shadow offset
    val heightDp = cell * (GH + 1)
    Canvas(modifier = modifier.size(widthDp, heightDp)) {
        val s = size.width / (TOTAL_COLS + 1)
        val sh = s * 0.32f                    // copper drop-shadow offset
        var colBase = 0
        for (ch in WORD) {
            val glyph = FONT[ch] ?: continue
            for (r in 0 until GH) {
                val row = glyph[r]
                for (c in 0 until GW) {
                    if (row[c] != '1') continue
                    val x = (colBase + c) * s
                    val y = r * s
                    drawRect(COPPER, Offset(x + sh, y + sh), Size(s, s))
                    drawRect(OLIVE, Offset(x, y), Size(s, s))
                }
            }
            colBase += GW + GAP
        }
    }
}

/**
 * In-app title card shown over the loading thread list to mask cold-start. Reuses the
 * Keeper green soap-bubble bath (KeeperMode.BATH) with the olive/copper pixel wordmark,
 * on true black (OLED). CoworkApp crossfades it away once the list first poll lands.
 */
@Composable
fun SplashCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KeeperPixel(heightDp = 84.dp, mode = KeeperMode.BATH)
            Spacer(Modifier.height(10.dp))
            BubblesWordmark(widthDp = 128.dp)
        }
    }
}
