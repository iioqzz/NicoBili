package com.example.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DanmakuComment
import com.example.data.model.DanmakuFilterConfig
import com.example.data.model.DanmakuPosition
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DanmakuOverlay(
    comments: List<DanmakuComment>,
    currentPositionMs: Long,
    filterConfig: DanmakuFilterConfig,
    modifier: Modifier = Modifier
) {
    if (!filterConfig.enabled || comments.isEmpty()) return

    val scrollDurationMs = (7500f / filterConfig.speedMultiplier.coerceIn(0.5f, 2.0f)).toLong()
    val fixedDurationMs = 3500L

    val activeComments = remember(comments, currentPositionMs / 60L, filterConfig) {
        comments.filter { comment ->
            if (!filterConfig.matches(comment)) return@filter false
            val elapsed = currentPositionMs - comment.vposMs
            when (comment.position) {
                DanmakuPosition.SCROLL -> elapsed in 0..scrollDurationMs
                DanmakuPosition.TOP, DanmakuPosition.BOTTOM -> elapsed in 0..fixedDurationMs
            }
        }.take(45)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(filterConfig.opacity.coerceIn(0.15f, 1.0f))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val laneHeightPx = (24f * filterConfig.fontSizeScale).coerceAtLeast(18f) * 2.2f
        val maxLanes = ((heightPx * 0.78f) / laneHeightPx).toInt().coerceIn(3, 12)

        activeComments.forEach { comment ->
            val elapsed = (currentPositionMs - comment.vposMs).coerceAtLeast(0L)
            val laneIndex = abs(comment.id.hashCode()) % maxLanes
            val textSizeSp = (15f * comment.size.scale * filterConfig.fontSizeScale).sp
            val textStyle = TextStyle(
                color = Color(comment.colorHex),
                fontSize = textSizeSp,
                fontWeight = FontWeight.Bold,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    offset = Offset(1.5f, 1.5f),
                    blurRadius = 3f
                )
            )

            when (comment.position) {
                DanmakuPosition.SCROLL -> {
                    val progress = elapsed.toFloat() / scrollDurationMs.toFloat()
                    val approxTextWidth = comment.body.length * textSizeSp.value * 2.2f
                    val x = widthPx - progress * (widthPx + approxTextWidth)
                    val y = (laneIndex * laneHeightPx + 12f).roundToInt()
                    Text(
                        text = comment.body,
                        style = textStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.offset { IntOffset(x.roundToInt(), y) }
                    )
                }
                DanmakuPosition.TOP -> {
                    val topOffset = ((laneIndex % 4) * laneHeightPx + 12f).roundToInt()
                    Text(
                        text = comment.body,
                        style = textStyle,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, topOffset) }
                    )
                }
                DanmakuPosition.BOTTOM -> {
                    val bottomOffset = -((laneIndex % 3) * laneHeightPx + 36f).roundToInt()
                    Text(
                        text = comment.body,
                        style = textStyle,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset { IntOffset(0, bottomOffset) }
                    )
                }
            }
        }
    }
}
