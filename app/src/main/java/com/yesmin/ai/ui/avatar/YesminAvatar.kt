package com.yesmin.ai.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.yesmin.ai.assistant.AvatarState
import com.yesmin.ai.ui.theme.YLavender
import com.yesmin.ai.ui.theme.YPink
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object AvatarPalette {
    val hair = listOf(
        "Rose Pink" to (Color(0xFFFF9CC2) to Color(0xFFD14D8B)),
        "Lavender" to (Color(0xFFC9B3FF) to Color(0xFF8A63D2)),
        "Midnight" to (Color(0xFF5A4A78) to Color(0xFF1E1633)),
        "Chocolate" to (Color(0xFF9A6B55) to Color(0xFF4B2E24))
    )
    val eyes = listOf(
        "Violet" to Color(0xFF9B5DE5), "Sky" to Color(0xFF4CC9F0),
        "Honey" to Color(0xFFC97B2B), "Mint" to Color(0xFF2EC4B6)
    )
}

/**
 * Procedurally drawn, shaded "2.5D" anime-style avatar (gradients give the 3D feel).
 * Animations: breathing, blinking, gaze drift, listening rings, thinking dots, talking mouth with
 * per-word lip-sync kicks (from TTS), happy eyes/blush/sparkles. A true 3D rigged model needs an
 * external asset - see README "Upgrade to a real 3D avatar".
 */
@Composable
fun YesminAvatar(
    state: AvatarState, happy: Boolean, mouthKick: Int, hairIndex: Int, eyeIndex: Int,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "avatar")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val talk by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse), label = "talk")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val gaze by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gaze")
    val blink = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 5200))
            blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(120))
        }
    }
    val kick = remember { Animatable(0f) }
    LaunchedEffect(mouthKick) { if (mouthKick > 0) { kick.snapTo(1f); kick.animateTo(0.2f, tween(150)) } }
    val happyAmt by animateFloatAsState(if (happy) 1f else 0f, tween(350), label = "happy")

    val (hairLight, hairDark) = AvatarPalette.hair[hairIndex.coerceIn(0, AvatarPalette.hair.lastIndex)].second
    val eyeColor = AvatarPalette.eyes[eyeIndex.coerceIn(0, AvatarPalette.eyes.lastIndex)].second
    val skin = Color(0xFFFFE3D6)
    val skinShade = Color(0xFFF2B9A8)
    val ink = Color(0xFF3A2748)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val u = min(w, h * 0.95f)
        val cx = w / 2f
        val bob = (breathe - 0.5f) * u * 0.014f
        val hc = Offset(cx, h * 0.42f + bob)
        val fw = u * 0.255f
        val fh = u * 0.30f
        val blinkV = blink.value
        val mouthOpen = if (state == AvatarState.Talking) max(kick.value * 0.9f, 0.12f + 0.45f * talk) else 0f
        val eyesHappy = happyAmt > 0.5f && state != AvatarState.Talking
        val tilt = when (state) { AvatarState.Thinking -> -4f; AvatarState.Listening -> 3f; else -> 0f } + gaze * 1.2f
        val gazeX = gaze * (if (state == AvatarState.Thinking) 1.6f else 1f)
        val hairBrush = Brush.verticalGradient(listOf(hairLight, hairDark), startY = hc.y - fh * 1.3f, endY = hc.y + fh * 1.6f)

        // soft glow
        drawCircle(
            Brush.radialGradient(listOf(YPink.copy(alpha = 0.40f), YLavender.copy(alpha = 0.18f), Color.Transparent), center = hc, radius = u * 0.62f),
            radius = u * 0.62f, center = hc
        )
        if (state == AvatarState.Listening) for (k in 0..1) {
            val p = (pulse + k * 0.5f) % 1f
            drawCircle(YPink.copy(alpha = (1 - p) * 0.55f), radius = u * (0.34f + 0.22f * p), center = hc, style = Stroke(u * 0.008f))
        }

        rotate(tilt, Offset(cx, h * 0.85f)) {
            // back hair
            val back = Path().apply {
                moveTo(hc.x, hc.y - fh * 1.22f)
                cubicTo(hc.x + fw * 1.6f, hc.y - fh * 1.2f, hc.x + fw * 1.75f, hc.y + fh * 0.4f, hc.x + fw * 1.55f, hc.y + fh * 1.55f)
                lineTo(hc.x - fw * 1.55f, hc.y + fh * 1.55f)
                cubicTo(hc.x - fw * 1.75f, hc.y + fh * 0.4f, hc.x - fw * 1.6f, hc.y - fh * 1.2f, hc.x, hc.y - fh * 1.22f)
                close()
            }
            drawPath(back, hairBrush)

            // outfit
            val body = Path().apply {
                moveTo(cx - u * 0.42f, h)
                cubicTo(cx - u * 0.42f, hc.y + fh * 1.35f, cx - u * 0.2f, hc.y + fh * 1.2f, cx - fw * 0.3f, hc.y + fh * 1.12f)
                lineTo(cx + fw * 0.3f, hc.y + fh * 1.12f)
                cubicTo(cx + u * 0.2f, hc.y + fh * 1.2f, cx + u * 0.42f, hc.y + fh * 1.35f, cx + u * 0.42f, h)
                close()
            }
            drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFFB3D3), Color(0xFFB79CFF)), startY = hc.y + fh, endY = h))
            // neck
            drawRoundRect(skinShade, Offset(cx - fw * 0.28f, hc.y + fh * 0.85f), Size(fw * 0.56f, fh * 0.45f), CornerRadius(fw * 0.2f))
            // collar heart
            heart(Offset(cx, hc.y + fh * 1.36f), u * 0.03f, Color(0xFFFF4D8D))

            // face
            val face = Path().apply {
                moveTo(hc.x, hc.y - fh)
                cubicTo(hc.x + fw * 1.05f, hc.y - fh, hc.x + fw * 1.08f, hc.y + fh * 0.25f, hc.x + fw * 0.55f, hc.y + fh * 0.78f)
                cubicTo(hc.x + fw * 0.30f, hc.y + fh * 1.02f, hc.x + fw * 0.12f, hc.y + fh * 1.06f, hc.x, hc.y + fh * 1.06f)
                cubicTo(hc.x - fw * 0.12f, hc.y + fh * 1.06f, hc.x - fw * 0.30f, hc.y + fh * 1.02f, hc.x - fw * 0.55f, hc.y + fh * 0.78f)
                cubicTo(hc.x - fw * 1.08f, hc.y + fh * 0.25f, hc.x - fw * 1.05f, hc.y - fh, hc.x, hc.y - fh)
                close()
            }
            drawPath(face, Brush.radialGradient(listOf(Color(0xFFFFF4EE), skin, skinShade),
                center = Offset(hc.x - fw * 0.3f, hc.y - fh * 0.3f), radius = fw * 2.1f))

            // blush
            for (sx in listOf(-1f, 1f)) {
                val c = Offset(cx + sx * fw * 0.62f, hc.y + fh * 0.42f)
                drawOval(
                    Brush.radialGradient(listOf(Color(0xFFFF7FA8).copy(alpha = 0.45f + 0.3f * happyAmt), Color.Transparent), center = c, radius = fw * 0.3f),
                    Offset(c.x - fw * 0.3f, c.y - fw * 0.2f), Size(fw * 0.6f, fw * 0.4f)
                )
            }

            // eyes
            val eyeY = hc.y + fh * 0.08f
            val ew = fw * 0.36f
            val eh = fh * 0.30f
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * fw * 0.5f, eyeY)
                if (eyesHappy) {
                    drawArc(ink, 200f, 140f, false, Offset(c.x - ew, c.y - eh * 0.45f), Size(ew * 2, eh * 1.1f),
                        style = Stroke(u * 0.014f, cap = StrokeCap.Round))
                } else {
                    val openAmt = (1f - blinkV).coerceIn(0.06f, 1f)
                    scale(1f, openAmt, Offset(c.x, c.y - eh * 0.9f)) {
                        val oval = Path().apply { addOval(Rect(c.x - ew, c.y - eh, c.x + ew, c.y + eh)) }
                        drawPath(oval, Color.White)
                        clipPath(oval) {
                            val ir = ew * 0.82f
                            val ic = Offset(c.x + gazeX * ew * 0.12f, c.y + eh * 0.06f - (if (state == AvatarState.Thinking) eh * 0.2f else 0f))
                            drawCircle(Brush.radialGradient(listOf(lerp(eyeColor, Color.White, 0.45f), eyeColor, lerp(eyeColor, Color.Black, 0.45f)), center = ic, radius = ir), ir, ic)
                            drawCircle(Color(0xFF241533), ir * 0.46f, ic)
                            drawCircle(Color.White, ir * 0.30f, Offset(ic.x - ir * 0.32f, ic.y - ir * 0.36f))
                            drawCircle(Color.White.copy(alpha = 0.85f), ir * 0.13f, Offset(ic.x + ir * 0.34f, ic.y + ir * 0.34f))
                            drawRect(Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent), startY = c.y - eh, endY = c.y - eh * 0.3f),
                                Offset(c.x - ew, c.y - eh), Size(ew * 2, eh * 0.7f))
                        }
                        drawArc(ink, 195f, 150f, false, Offset(c.x - ew, c.y - eh), Size(ew * 2, eh * 2),
                            style = Stroke(u * 0.017f, cap = StrokeCap.Round))
                        drawLine(ink, Offset(c.x + side * ew * 0.95f, c.y - eh * 0.25f), Offset(c.x + side * ew * 1.2f, c.y - eh * 0.6f),
                            strokeWidth = u * 0.012f, cap = StrokeCap.Round)
                    }
                }
            }

            // nose
            drawArc(skinShade, 20f, 140f, false, Offset(cx - fw * 0.05f, hc.y + fh * 0.36f), Size(fw * 0.1f, fw * 0.07f),
                style = Stroke(u * 0.005f, cap = StrokeCap.Round))

            // mouth
            val my = hc.y + fh * 0.70f
            val mw = fw * 0.30f
            if (mouthOpen < 0.07f) {
                val smile = 0.45f + 0.55f * happyAmt
                val p = Path().apply {
                    moveTo(cx - mw, my)
                    cubicTo(cx - mw * 0.45f, my + mw * (0.35f + 0.6f * smile), cx + mw * 0.45f, my + mw * (0.35f + 0.6f * smile), cx + mw, my)
                }
                drawPath(p, Color(0xFFD9577F), style = Stroke(u * 0.011f, cap = StrokeCap.Round))
            } else {
                val mh = mw * (0.25f + 0.95f * mouthOpen)
                val mwo = mw * (0.75f + 0.35f * mouthOpen)
                drawOval(Color(0xFF8E2C4B), Offset(cx - mwo, my - mh * 0.2f), Size(mwo * 2, mh * 1.4f))
                drawOval(Color(0xFFFF8FA8), Offset(cx - mwo * 0.6f, my + mh * 0.45f), Size(mwo * 1.2f, mh * 0.65f))
                if (mouthOpen > 0.3f) drawRoundRect(Color.White, Offset(cx - mwo * 0.6f, my - mh * 0.18f), Size(mwo * 1.2f, mh * 0.28f), CornerRadius(u * 0.006f))
            }

            // side locks + bangs (front hair)
            for (sx in listOf(-1f, 1f)) {
                val lock = Path().apply {
                    moveTo(hc.x + sx * fw * 1.1f, hc.y + fh * 0.1f)
                    cubicTo(hc.x + sx * fw * 1.25f, hc.y + fh * 0.7f, hc.x + sx * fw * 1.2f, hc.y + fh * 1.2f, hc.x + sx * fw * 0.95f, hc.y + fh * 1.45f)
                    cubicTo(hc.x + sx * fw * 0.85f, hc.y + fh * 1.0f, hc.x + sx * fw * 0.9f, hc.y + fh * 0.5f, hc.x + sx * fw * 0.98f, hc.y + fh * 0.1f)
                    close()
                }
                drawPath(lock, hairBrush)
            }
            val bangs = Path().apply {
                moveTo(hc.x - fw * 1.1f, hc.y + fh * 0.1f)
                cubicTo(hc.x - fw * 1.3f, hc.y - fh * 1.4f, hc.x + fw * 1.3f, hc.y - fh * 1.4f, hc.x + fw * 1.1f, hc.y + fh * 0.1f)
                cubicTo(hc.x + fw * 0.95f, hc.y - fh * 0.25f, hc.x + fw * 0.5f, hc.y - fh * 0.6f, hc.x + fw * 0.1f, hc.y - fh * 0.8f)
                cubicTo(hc.x - fw * 0.3f, hc.y - fh * 0.55f, hc.x - fw * 0.85f, hc.y - fh * 0.3f, hc.x - fw * 1.1f, hc.y + fh * 0.1f)
                close()
            }
            drawPath(bangs, hairBrush)
            drawArc(Color.White.copy(alpha = 0.35f), 205f, 55f, false, Offset(hc.x - fw * 1.0f, hc.y - fh * 1.15f), Size(fw * 2.0f, fh * 1.4f),
                style = Stroke(u * 0.018f, cap = StrokeCap.Round))

            // brows (drawn over the fringe, anime style)
            val lift = when (state) { AvatarState.Listening -> u * 0.014f; AvatarState.Thinking -> -u * 0.004f; else -> 0f }
            for (sx in listOf(-1f, 1f)) {
                val bx = cx + sx * fw * 0.5f
                val by = hc.y - fh * 0.36f - lift
                val bp = Path().apply {
                    moveTo(bx - fw * 0.22f, by + u * 0.006f)
                    cubicTo(bx - fw * 0.1f, by - u * 0.008f, bx + fw * 0.1f, by - u * 0.008f, bx + fw * 0.22f, by + u * 0.004f)
                }
                drawPath(bp, hairDark.copy(alpha = 0.8f), style = Stroke(u * 0.007f, cap = StrokeCap.Round))
            }

            // heart hair clip
            heart(Offset(hc.x - fw * 0.85f, hc.y - fh * 0.62f), u * 0.04f, Color(0xFFFF4D8D))
        }

        if (state == AvatarState.Thinking) for (i in 0..2) {
            val a = 0.3f + 0.7f * abs(sin(pulse * 2f * PI.toFloat() - i * 0.9f))
            drawCircle(YLavender.copy(alpha = a), u * (0.012f + 0.004f * i), Offset(hc.x + fw * 1.15f + i * u * 0.04f, hc.y - fh * 1.15f - i * u * 0.03f))
        }
        if (happyAmt > 0.1f) {
            val a = happyAmt * (0.5f + 0.5f * sin(pulse * 2f * PI.toFloat()))
            for ((dx, dy, r) in listOf(Triple(-1.5f, -1.0f, 0.02f), Triple(1.6f, -0.6f, 0.026f), Triple(1.3f, 0.5f, 0.016f))) {
                val c = Offset(hc.x + fw * dx, hc.y + fh * dy)
                val rr = u * r
                drawLine(Color.White.copy(alpha = a), Offset(c.x - rr, c.y), Offset(c.x + rr, c.y), strokeWidth = u * 0.006f, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = a), Offset(c.x, c.y - rr), Offset(c.x, c.y + rr), strokeWidth = u * 0.006f, cap = StrokeCap.Round)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.heart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s * 0.9f)
        cubicTo(c.x - s * 1.6f, c.y - s * 0.2f, c.x - s * 0.6f, c.y - s * 1.2f, c.x, c.y - s * 0.4f)
        cubicTo(c.x + s * 0.6f, c.y - s * 1.2f, c.x + s * 1.6f, c.y - s * 0.2f, c.x, c.y + s * 0.9f)
        close()
    }
    drawPath(p, color)
    drawCircle(Color.White.copy(alpha = 0.7f), s * 0.18f, Offset(c.x - s * 0.5f, c.y - s * 0.35f))
}
