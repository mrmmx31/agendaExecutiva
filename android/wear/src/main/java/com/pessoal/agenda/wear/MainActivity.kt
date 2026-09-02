package com.pessoal.agenda.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.data.WearFeedback
import com.pessoal.agenda.wear.data.WearVisibleAlert
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgendaWearRoute() }
    }
}

@Composable
private fun AgendaWearRoute(viewModel: WearAgendaViewModel = viewModel()) {
    val alert by viewModel.alert.collectAsStateWithLifecycle()
    AgendaWearApp(
        alert = alert,
        onComplete = viewModel::complete,
        onSnooze = viewModel::snooze,
        onFeedbackShown = viewModel::dismissFeedback,
    )
}

@Composable
fun AgendaWearApp(
    alert: WearVisibleAlert?,
    onComplete: (String) -> Unit,
    onSnooze: (String, Int) -> Unit,
    onFeedbackShown: (String) -> Unit,
) {
    MaterialTheme {
        var choosingSnooze by remember(alert?.alertId) { mutableStateOf(false) }
        val feedback = alert?.feedback
        LaunchedEffect(alert?.alertId, feedback) {
            if (alert != null && feedback != null) {
                delay(FEEDBACK_MILLIS)
                onFeedbackShown(alert.alertId)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = Color(0xFF74D6B5),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            when {
                alert == null -> EmptyState()
                feedback != null -> FeedbackState(feedback)
                choosingSnooze -> SnoozeOptions(
                    alert = alert,
                    onSnooze = { minutes -> onSnooze(alert.alertId, minutes) },
                )
                else -> AlertActions(
                    alert = alert,
                    onComplete = { onComplete(alert.alertId) },
                    onChooseSnooze = { choosingSnooze = true },
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = stringResource(R.string.no_active_alert),
        modifier = Modifier.padding(top = 8.dp),
        color = Color(0xFFE3E3DF),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AlertActions(
    alert: WearVisibleAlert,
    onComplete: () -> Unit,
    onChooseSnooze: () -> Unit,
) {
    Text(
        text = alert.text,
        modifier = Modifier.padding(top = 8.dp),
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
    )
    Text(
        text = alert.reason,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        color = Color(0xFFC8CAC6),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.complete)) },
    )
    Button(
        onClick = onChooseSnooze,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        label = { Text(stringResource(R.string.snooze)) },
    )
}

@Composable
private fun SnoozeOptions(alert: WearVisibleAlert, onSnooze: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.snooze_for),
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
    )
    alert.snoozeOptionsMinutes.forEach { minutes ->
        Button(
            onClick = { onSnooze(minutes) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            label = { Text(stringResource(R.string.minutes_format, minutes)) },
        )
    }
}

@Composable
private fun FeedbackState(feedback: WearFeedback) {
    Text(
        text = when (feedback.action) {
            WearActionType.COMPLETE -> stringResource(R.string.completed)
            WearActionType.SNOOZE -> stringResource(
                R.string.snoozed_for_format,
                requireNotNull(feedback.snoozeMinutes),
            )
        },
        modifier = Modifier.padding(top = 10.dp),
        color = Color(0xFF74D6B5),
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
    )
}

private const val FEEDBACK_MILLIS = 2_000L
