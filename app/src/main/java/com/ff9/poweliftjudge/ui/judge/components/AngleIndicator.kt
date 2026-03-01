package com.ff9.poweliftjudge.ui.judge.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ff9.poweliftjudge.ui.theme.AccentRed
import com.ff9.poweliftjudge.ui.theme.PrimaryRed
import com.ff9.poweliftjudge.ui.theme.SuccessGreen
import kotlin.math.roundToInt

@Composable
fun AngleIndicator(
    angleDelta: Float,
    progress: Int,
    isGoodLift: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat(),
        animationSpec = tween(100),
        label = "progress"
    )

    val arcColor by animateColorAsState(
        targetValue = when {
            isGoodLift -> SuccessGreen
            progress > 70 -> AccentRed
            progress > 40 -> PrimaryRed
            else -> Color.Gray
        },
        animationSpec = tween(300),
        label = "arcColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isGoodLift) SuccessGreen else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(300),
        label = "textColor"
    )

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 16.dp.toPx()
            val padding = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(padding, padding)

            // Background arc
            drawArc(
                color = Color.Gray.copy(alpha = 0.2f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                color = arcColor,
                startAngle = 135f,
                sweepAngle = 270f * (animatedProgress / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Text(
            text = "${angleDelta.roundToInt()}°",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 56.sp,
                fontWeight = FontWeight.Black
            ),
            color = textColor
        )
    }
}
