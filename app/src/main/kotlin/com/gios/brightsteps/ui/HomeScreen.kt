package com.gios.brightsteps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.brightsteps.ui.theme.Dim
import com.gios.brightsteps.ui.theme.Faint
import com.gios.brightsteps.ui.theme.RuleGrey
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun HomeScreen(
    state: StepsUiState,
    granted: Boolean,
    onGrant: () -> Unit,
    onOpenGoal: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !granted -> PermissionPrompt(onGrant)
                !state.sensorAvailable -> EmptyState("This phone has no step sensor.")
                else -> Content(state)
            }
        }
        ActionBar(listOf(BarAction("GOAL", onOpenGoal)))
    }
}

@Composable
private fun Content(state: StepsUiState) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GoalRing(today = state.today, goal = state.goal)
        Spacer(Modifier.height(10.dp))
        Text(
            "OF ${"%,d".format(state.goal)}".uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Dim,
        )
        Spacer(Modifier.height(44.dp))
        WeekStrip(state.week, state.goal)
    }
}

/** A white arc over a faint track, with the day's count set in the middle. */
@Composable
private fun GoalRing(today: Long, goal: Int) {
    val fraction = if (goal > 0) min(1f, today.toFloat() / goal) else 0f
    Box(Modifier.size(216.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Faint,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = Color.White,
                startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "%,d".format(today),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Seven days, oldest to newest. Bar heights are square-rooted so a single big walk does not
 * flatten every other day to nothing; the scale reaches to whichever is larger, the week's best
 * day or the goal, so a bar meeting the top means the goal was met.
 */
@Composable
private fun WeekStrip(week: List<DayBar>, goal: Int) {
    if (week.isEmpty()) return
    val scaleMax = maxOf(goal.toLong(), week.maxOf { it.steps }, 1L)
    val rootMax = sqrt(scaleMax.toFloat())
    val today = week.last().date

    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { day ->
            val isToday = day.date == today
            val frac = if (day.steps > 0) sqrt(day.steps.toFloat()) / rootMax else 0f
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 5.dp).weight(1f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((96f * frac).dp.coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isToday) Color.White else RuleGrey),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) Color.White else Dim,
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Steps needs permission to read the phone's step counter.",
            style = MaterialTheme.typography.bodyLarge,
            color = Dim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A1A))
                .width(200.dp)
                .height(52.dp)
                .clickable { onGrant() },
            contentAlignment = Alignment.Center,
        ) {
            Text("ALLOW", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}
