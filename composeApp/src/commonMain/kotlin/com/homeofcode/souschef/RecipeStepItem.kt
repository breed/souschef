package com.homeofcode.souschef

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.homeofcode.souschef.com.homeofcode.souschef.model.BakeModel
import kotlinx.datetime.Instant

@Composable
fun RecipeStepItem(
    step: BakeModel.RecipeStep,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bakeStartTime: Instant?,
    isCurrentStep: () -> Boolean
) {
    val completeTime by remember { step.completeTime }
    val stepStartTime = bakeStartTime?.plus(step.startDelay)
    val numDp = textWidth(" \uD83D\uDC49 ")
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
                .clickable(enabled = isCurrentStep(), onClick = {
                    step.completeTime.value = now()
                    onClick()
                })
        )
        Text(text = "${step.instruction}", textAlign = TextAlign.Left, modifier = modifier)
    }

    if (step.duration != null && stepStartTime != null) {
        Row {
            Spacer(modifier = Modifier.width(numDp))
            val start = formatInstant(stepStartTime)
            val end = formatInstant(stepStartTime.plus(step.duration))
            val complete = formatInstant(completeTime)
            val text = if (completeTime == null) {
                "$start to $end"
            } else {
                "$start to $end finished $complete"
            }
            Text(
                text = text
            )
        }
    }
}