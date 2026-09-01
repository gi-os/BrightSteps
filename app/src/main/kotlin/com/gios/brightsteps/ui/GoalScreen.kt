package com.gios.brightsteps.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.brightsteps.data.Prefs
import com.gios.brightsteps.ui.theme.Dim

/** Set the daily goal: tap − / + to step by 500, DONE to go back. */
@Composable
fun GoalScreen(goal: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "DAILY GOAL",
            style = MaterialTheme.typography.labelSmall,
            color = Dim,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stepper("−") { onChange((goal - Prefs.GOAL_STEP).coerceAtLeast(Prefs.MIN_GOAL)) }
            Text(
                "%,d".format(goal),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Stepper("+") { onChange((goal + Prefs.GOAL_STEP).coerceAtMost(Prefs.MAX_GOAL)) }
        }
        ActionBar(listOf(BarAction("DONE", onDone)))
    }
}

@Composable
private fun Stepper(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}
