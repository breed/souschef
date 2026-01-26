package app.s4h.souschef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.s4h.souschef.model.BakeModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class TimeEditMode { START, END }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipeStepItem(
    step: BakeModel.RecipeStep,
    stepIndex: Int,
    onClick: () -> Unit,
    onUncomplete: () -> Unit,
    onStartTimeAdjusted: (Int, Duration) -> Unit,
    onStartTimeReset: (Int) -> Unit,
    onEndTimeAdjusted: (Int, Duration) -> Unit,
    onEndTimeReset: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bakeStartTime: Instant?,
    isCurrentStep: () -> Boolean,
    isPreviousStep: () -> Boolean
) {
    // Read state values directly to ensure recomposition on changes
    val completeTime = step.completeTime.value
    val startTimeOffset = step.startTimeOffset.value
    val durationOffset = step.durationOffset.value

    val stepStartTime = bakeStartTime?.plus(step.getAdjustedStartDelay())
    val stepEndTime = stepStartTime?.plus(step.getAdjustedDuration() ?: Duration.ZERO)
    val numDp = textWidth(" \uD83D\uDC49 ")

    var showTimePickerMode by remember { mutableStateOf<TimeEditMode?>(null) }

    // Show time picker when requested
    showTimePickerMode?.let { mode ->
        val editingTime = if (mode == TimeEditMode.START) stepStartTime else stepEndTime
        if (editingTime != null && bakeStartTime != null) {
            val localTime = editingTime.toLocalDateTime(TimeZone.currentSystemDefault())
            PlatformTimePicker(
                initialHour = localTime.hour,
                initialMinute = localTime.minute,
                onTimePicked = { hourOfDay, minute ->
                    if (mode == TimeEditMode.START) {
                        val originalStartTime = bakeStartTime.plus(step.startDelay)
                        val originalLocal = originalStartTime.toLocalDateTime(TimeZone.currentSystemDefault())
                        val originalMinutes = originalLocal.hour * 60 + originalLocal.minute
                        val newMinutes = hourOfDay * 60 + minute
                        val diffMinutes = newMinutes - originalMinutes
                        onStartTimeAdjusted(stepIndex, diffMinutes.minutes)
                    } else {
                        val originalEndTime = bakeStartTime.plus(step.startDelay).plus(step.duration ?: Duration.ZERO)
                        val originalLocal = originalEndTime.toLocalDateTime(TimeZone.currentSystemDefault())
                        val originalMinutes = originalLocal.hour * 60 + originalLocal.minute
                        val newMinutes = hourOfDay * 60 + minute
                        val diffMinutes = newMinutes - originalMinutes
                        onEndTimeAdjusted(stepIndex, diffMinutes.minutes)
                    }
                    showTimePickerMode = null
                },
                onDismiss = { showTimePickerMode = null }
            )
        }
    }

    Row(
        verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()
    ) {
        val indicator = if (completeTime != null) {
            "✔"
        } else if (isCurrentStep()) {
            "\uD83D\uDC49"
        } else {
            "* "
        }
        Text(
            text = "$indicator ",
            textAlign = TextAlign.Right,
            modifier = Modifier.width(width = numDp).align(Alignment.Top)
                .combinedClickable(
                    enabled = isCurrentStep() || isPreviousStep(),
                    onClick = {
                        if (isCurrentStep()) {
                            step.completeTime.value = now()
                            onClick()
                        }
                    },
                    onLongClick = {
                        if (isPreviousStep()) {
                            onUncomplete()
                        }
                    }
                )
        )
        Text(text = "${step.instruction}", textAlign = TextAlign.Left, modifier = modifier)
    }

    if (step.duration != null && stepStartTime != null && stepEndTime != null) {
        Row {
            Spacer(modifier = Modifier.width(numDp))
            Column {
                Row {
                    // Start time - clickable
                    Text(
                        text = formatInstant(stepStartTime),
                        modifier = Modifier.combinedClickable(
                            onClick = { showTimePickerMode = TimeEditMode.START }
                        )
                    )
                    Text(text = " to ")
                    // End time - clickable
                    Text(
                        text = formatInstant(stepEndTime),
                        modifier = Modifier.combinedClickable(
                            onClick = { showTimePickerMode = TimeEditMode.END }
                        )
                    )
                }
                if (completeTime != null) {
                    Text(text = "finished ${formatInstant(completeTime)}")
                }
            }
        }
    }
}
