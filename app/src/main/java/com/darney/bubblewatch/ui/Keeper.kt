package com.darney.bubblewatch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlin.math.PI
import kotlin.math.sin

// The Keeper — Bubbles' mascot IS the DArnTech logomark: the blue-and-yellow
// ball-and-stick mark, rendered as a chunky 32×32 (16/32-bit style) pixel sprite.
// No body, no face — the mark is the whole character. The grid below is rasterized
// 1:1 from daliquot/scripts/logomark.gd (blue vertical spine + upper-left arm,
// yellow horizontal bar + lower-right arm, round node beads, up-left gloss), the
// same mark the game strikes in 3D. He's the app's single "something's happening"
// indicator: 💡 Haiku thinking, 🛁 the context-bath (an actual Tamagotchi soak),
// and the post-send confirm.

private val MARK_B = Color(0xFF5B86C8)   // BRAND_BLUE base
private val MARK_BHI = Color(0xFF8FB4E6) // blue gloss
private val MARK_BSH = Color(0xFF3B5C92) // blue rim/shade
private val MARK_Y = Color(0xFFF5DC28)   // BRAND_YELLOW base (also the spark)
private val MARK_YHI = Color(0xFFFFF09A) // yellow gloss
private val MARK_YSH = Color(0xFFBFA818) // yellow rim/shade
private val TUB = Color(0xFFE3EBF2)
private val TUB_SH = Color(0xFFB6C6D4)
private val WATER = Color(0x9970C8E6)     // translucent cyan — the mark shows through
private val FOAM = Color(0xFFFFFFFF)
// Internal, not private: the thread detail screen paints Claude's closing message
// in it, and one brand green declared twice is how two greens end up on one screen.
internal val BUBBLE_GREEN = Color(0xFF3DDC84) // Android-green soap bubbles (the green-bubbles flex)
private val LABEL = Color(0xFFFFB300)

enum class KeeperMode { THINKING, BATH }

private val SENT_GREEN = Color(0xFF4ADE80)

/** Overshoot ease so the check "pops" in past 1.0 and settles — the satisfying bit. */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/**
 * Self-animating green success checkmark — the "sent ✓" confirmation. Draws its
 * stroke on (left-down then up-right) with a pop, then emits one expanding ring
 * ping. Runs once on appearance (the confirm overlay mounts it fresh each send).
 */
@Composable
fun SentCheck(modifier: Modifier = Modifier, sizeDp: Dp = 76.dp) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(560)) }
    Canvas(modifier = modifier.size(sizeDp)) {
        val s = size.minDimension
        val t = p.value.coerceIn(0f, 1f)
        val reveal = FastOutSlowInEasing.transform(t)
        scale(easeOutBack(t), pivot = center) {
            val path = Path().apply {
                moveTo(0.22f * s, 0.53f * s)
                lineTo(0.42f * s, 0.72f * s)
                lineTo(0.78f * s, 0.30f * s)
            }
            val pm = PathMeasure().apply { setPath(path, false) }
            val seg = Path()
            pm.getSegment(0f, pm.length * reveal, seg, true)
            drawPath(
                seg, SENT_GREEN,
                style = Stroke(width = 0.13f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            if (t > 0.5f) {
                val rp = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)
                drawCircle(
                    SENT_GREEN.copy(alpha = 0.5f * (1f - rp)),
                    radius = (0.36f + 0.12f * rp) * s,
                    center = center,
                    style = Stroke(width = 0.03f * s),
                )
            }
        }
    }
}

// 32×32 sprite. '.'=empty, blue: b/B/d = base/gloss/shade, yellow: y/Y/g.
private val MARK = listOf(
    "................................",
    "................................",
    "................................",
    "................................",
    "............ddbdd...............",
    "............dBBBd...............",
    "............dBBBb...............",
    "...........dbBBBd...............",
    "...........dBBBb................",
    "..........dBBbBb................",
    ".........dbBbbBb........gg......",
    "........dbBBdbBb.......gyyyg....",
    "........dBBb.bBb......gyyyyg....",
    "......gyBBbd.bBbd....dbyyyyg....",
    ".....gyYBByyyBBByyyyybbyyyg.....",
    ".....gYYYYYYYYByyyyyyybbbg......",
    "....dbBYYyyyyBbbyyyyyybbb.......",
    "...dBBBbgg...bbbd.gyyyydd.......",
    "...dBBBBd....bbb..gyyyg.........",
    "...dbBBb.....bbb.gyyyg..........",
    "....dddd.....bbbgyyyg...........",
    ".............bbbyyyg............",
    ".............bbyyyg.............",
    "............dbbyyg..............",
    "............dbbbb...............",
    "............dbbbd...............",
    "............ddbdd...............",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
)

private fun colorFor(ch: Char): Color? = when (ch) {
    'b' -> MARK_B; 'B' -> MARK_BHI; 'd' -> MARK_BSH
    'y' -> MARK_Y; 'Y' -> MARK_YHI; 'g' -> MARK_YSH
    else -> null
}

private fun DrawScope.cell(x: Int, y: Int, c: Color, s: Float, oy: Float = 0f) {
    drawRect(c, Offset(x * s, y * s + oy), Size(s, s))
}

/** Draw the logomark with vertical bob [oy]; rows past [submergeAt] are skipped so
 *  the mark can sit down in the tub. */
private fun DrawScope.drawMark(s: Float, oy: Float, submergeAt: Int = 99) {
    for (y in MARK.indices) {
        if (y > submergeAt) break
        val row = MARK[y]
        for (x in row.indices) {
            val c = colorFor(row[x]) ?: continue
            cell(x, y, c, s, oy)
        }
    }
}

/**
 * The animated Keeper (the logomark).
 *
 * @param mode THINKING = mark + bouncing yellow sparkline (generating); CALM = a
 *   quiet bob (post-send confirm); BATH = the mark settled in a porcelain tub with
 *   rising soap bubbles — the Tamagotchi context-bath.
 */
@Composable
fun KeeperPixel(
    modifier: Modifier = Modifier,
    heightDp: Dp = 48.dp,
    mode: KeeperMode = KeeperMode.THINKING,
) {
    val rows = if (mode == KeeperMode.BATH) 40 else 32
    val cols = if (mode == KeeperMode.THINKING) 47 else 32
    val width = heightDp * cols / rows

    val t = rememberInfiniteTransition(label = "keeper")
    val bob by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bob",
    )
    val wave by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "wave",
    )
    val rise by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "rise",
    )

    Canvas(modifier = modifier.size(width, heightDp)) {
        val s = size.height / rows
        val oy = if (bob > 0.5f) -s else 0f

        when (mode) {
            KeeperMode.BATH -> {
                // Mark settled into the basin; porcelain tub + translucent water over
                // its lower half, soap bubbles rising past it.
                drawMark(s, oy, submergeAt = 27)
                for (x in 1..30) { cell(x, 24, TUB, s); cell(x, 25, TUB, s) }        // lip
                for (y in 26..36) {
                    cell(1, y, TUB_SH, s); cell(2, y, TUB_SH, s)
                    cell(29, y, TUB_SH, s); cell(30, y, TUB_SH, s)
                    for (x in 3..28) cell(x, y, WATER, s)
                }
                for (x in 2..29) cell(x, 37, TUB, s)
                for (x in 4..27) cell(x, 38, TUB_SH, s)
                // foam bobbing on the surface
                cell(4, 24, FOAM, s, oy); cell(9, 23, FOAM, s, -oy)
                cell(24, 24, FOAM, s, -oy); cell(27, 23, FOAM, s, oy)
                // rising soap bubbles
                val cx = intArrayOf(5, 10, 15, 20, 25, 8)
                for (i in 0 until 6) {
                    val prog = (rise + i * 0.16f) % 1f
                    val by = 36f - prog * 34f
                    val sway = sin((wave + i).toDouble()).toFloat() * if (i % 2 == 0) 1.2f else 0.5f
                    val bs = if (i % 3 == 0) 3f else 2f
                    drawRect(
                        BUBBLE_GREEN.copy(alpha = 0.9f * (1f - prog)),
                        Offset((cx[i] + sway) * s, by * s), Size(s * bs, s * bs),
                    )
                }
            }
            else -> {
                drawMark(s, oy)
                if (mode == KeeperMode.THINKING) {
                    for (i in 0 until 4) {
                        val v = sin((wave + i * 0.9f).toDouble()).toFloat()
                        val h = (2f + 16f * (0.5f + 0.5f * v)) * s
                        drawRect(MARK_Y, Offset((34 + i * 3) * s, size.height - h), Size(2 * s, h))
                    }
                }
            }
        }
    }
}

/**
 * Keeper + caption, stacked and centered — the shared inline indicator used wherever
 * the watch is mid-operation. Full-width and stacked (never a side column), per the
 * "little notes render below" UI rule.
 */
@Composable
fun KeeperIndicator(
    label: String,
    modifier: Modifier = Modifier,
    mode: KeeperMode = KeeperMode.THINKING,
    scale: Float = 1f,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val baseH = if (mode == KeeperMode.BATH) 60.dp else 48.dp
        KeeperPixel(heightDp = baseH * scale, mode = mode)
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = LABEL,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}
