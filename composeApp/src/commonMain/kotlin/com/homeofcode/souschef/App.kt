package com.homeofcode.souschef

import android.app.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import souschef.composeapp.generated.resources.Res
import souschef.composeapp.generated.resources.compose_multiplatform
import kotlin.time.Duration

data class Ingredient(val amount: Float, val unit: String, val name: String)
data class RecipeStep(
    val step: String,
    val duration: Duration? = null,
    var startTime: Instant? = null,
    var completeTime: Instant? = null,
)

fun now(): Instant {
    // this is kind of gross and stupid, but i want local time, and Instant is the
    // only class that works well with Duration and formatter, so i trick Instant
    // into thinking the current time is UTC
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        .toInstant(TimeZone.UTC)
}

fun timeToUTC(time: Instant): Instant {
    return time.toLocalDateTime(TimeZone.UTC).toInstant(TimeZone.currentSystemDefault())
}

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App(alarmTriggered: Instant?) {
    MaterialTheme {
        val image = painterResource(Res.drawable.compose_multiplatform)


        val recipeText = runBlocking {
            try {
                String(Res.readBytes("files/recipe.cooklang"))
            } catch (e: Exception) {
                e.printStackTrace()
                "nothing"
            }
        }

        CookLangExtractor(recipeText).let { extractor ->
            RecipeCard(
                title = extractor.meta["title"] as String,
                imagePainter = image,
                imageDescription = "A pancake",
                ingredients = extractor.ingredients.values.map {
                    Ingredient(it.quantity, it.unit, it.name)
                },
                steps = extractor.steps.map {
                    RecipeStep(it.text, it.duration)
                },
                alarmTriggered = alarmTriggered
            )
        }
    }
}

@Composable
fun IngredientItem(ingredient: Ingredient, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        Text(
            text = "${ingredient.amount} ${ingredient.unit}",
            textAlign = TextAlign.Right,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = ingredient.name, textAlign = TextAlign.Left, modifier = Modifier.weight(1f)
        )
    }
}

val formatter = DateTimeComponents.Format {
    amPmHour()
    char(':')
    minute()
    char(':')
    second()
}

@Composable
fun RecipeStepItem(
    number: Int,
    step: RecipeStep,
    startTime: Instant?,
    currentStep: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var completeState by remember { mutableStateOf(false) }
    var completeTime by remember { mutableStateOf<Instant?>(step.completeTime) }
    val numDp = textWidth(" \uD83D\uDC49 ")
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
        ) {
            val indicator = if (completeState) {
                "✔"
            } else if (startTime != null && currentStep != null) {
                "\uD83D\uDC49"
            } else {
                "* "
            }
            Text(
                text = "$indicator ",
                textAlign = TextAlign.Right,
                modifier = Modifier.width(width = numDp).align(Alignment.Top)
                    .clickable(enabled = currentStep != null && startTime != null, onClick = {
                        if (currentStep != null) {
                            completeState = true
                            completeTime = now()
                            step.completeTime = completeTime
                            currentStep()
                        }
                    })
            )
            Text(text = step.step, textAlign = TextAlign.Left, modifier = modifier)
            if (step.duration != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "(${step.duration})", textAlign = TextAlign.Right)
            }
        }
    }
    if (step.duration != null && startTime != null) {
        Row {
            Spacer(modifier = Modifier.width(numDp))
            val start = startTime.format(formatter)
            val end = startTime.plus(step.duration).format(formatter)
            val complete = step.completeTime?.format(formatter)
            val text = if (step.completeTime == null) {
                "$start to $end"
            } else {
                "$start to $end finished ${complete}"
            }
            Text(
                text = text
            )
        }
    }
}

@Composable
fun textWidth(String: String): Dp {
    val textMeasurer = rememberTextMeasurer()
    return with(LocalDensity.current) { textMeasurer.measure(String).size.width.toDp() }
}

@Composable
fun RecipeCard(
    title: String,
    imagePainter: Painter,
    imageDescription: String,
    ingredients: List<Ingredient>,
    steps: List<RecipeStep>,
    modifier: Modifier = Modifier,
    alarmTriggered: Instant?
) {
    var lastAlarmTriggered by remember { mutableStateOf(Instant.DISTANT_PAST) }
    var recipeStartTime by remember { mutableStateOf<Instant?>(null) }
    var alarmEnable by remember { mutableStateOf(true) }
    var currentStep by remember { mutableStateOf(0) }
    val startTimes = mutableListOf<Instant?>()
    var nextAlarm: Instant? = null
    var pastDue by remember { mutableStateOf(setOf<RecipeStep>()) }
    val now = now()
    with(startTimes) {
        var prevTime = recipeStartTime
        steps.forEach { step ->
            if (step.duration != null) {
                val stepStartTime = prevTime?.plus(step.duration)
                if (stepStartTime != null) {
                    if ((nextAlarm == null) && (stepStartTime > now)) {
                        nextAlarm = stepStartTime
                    }
                    if (stepStartTime < now) {
                        pastDue += step
                    }
                }
                prevTime = stepStartTime
            }
            add(prevTime)
        }
    }

    val doAlarm = if (alarmTriggered != null && alarmTriggered != lastAlarmTriggered) {
        lastAlarmTriggered = alarmTriggered
        true
    } else {
        false
    }
    if (doAlarm && alarmEnable && pastDue.isNotEmpty()) {
        val ringtone = android.media.RingtoneManager.getRingtone(
            LocalContext.current,
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        )
        ringtone?.play()
        val text: StringBuilder = StringBuilder()
        text.append("Finish:\n")
        pastDue.sortedBy { it.startTime }.forEach {
            text.append("* ${it.step}\n")
        }
        AlertDialog.Builder(LocalContext.current).setTitle("Recipe Timer").setMessage(text)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                ringtone?.stop()
            }.show()
    }

    if (alarmEnable && nextAlarm != null) {
        setAlarm(nextAlarm!!)
    } else {
        cancelAlarm()
    }


    MaterialTheme {
        Box(modifier = modifier) {
            Image(imagePainter, contentDescription = imageDescription, modifier = modifier)
            val translucentBg = Modifier.background(Color(0xB0FFFFFF))
            var screenWidth by remember { mutableStateOf(0) }
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned { x -> screenWidth = x.size.width },
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title, style = MaterialTheme.typography.h3, modifier = translucentBg
                    )
                    if (recipeStartTime == null) {
                        Button(onClick = { recipeStartTime = now() }) {
                            Text("Start")
                        }
                    } else {
                        Column {
                            Text(
                                text = "Started at ${recipeStartTime?.format(formatter)}",
                                textAlign = TextAlign.Right
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Timer Alarm")
                                Switch(checked = alarmEnable, onCheckedChange = {
                                    alarmEnable = it
                                })
                            }
                            Text(text = "Next Alarm: ${nextAlarm?.format(formatter) ?: "None"}")
                        }
                    }
                }
                Spacer(modifier = Modifier.fillMaxHeight(.2f).fillMaxWidth())
                Column(
                    modifier = translucentBg.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Ingredients",
                        style = MaterialTheme.typography.h4,
                    )
                    // someday we might want multicolumn ingredients
                    var minDp = 0.dp
                    ingredients.forEach { ingredient ->
                        val text = "${ingredient.amount} ${ingredient.unit}   ${ingredient.name}"
                        val width = textWidth(text)
                        if (width.compareTo(minDp) > 0) {
                            minDp = width
                        }
                    }

                    for (i in 0..(ingredients.size - 1)) {
                        IngredientItem(ingredients[i])
                    }

                    Text(text = "Steps", style = MaterialTheme.typography.h4)
                    var totalDuration = Duration.ZERO
                    steps.forEachIndexed { index, step ->
                        if (step.duration != null) {
                            step.startTime = recipeStartTime?.plus(totalDuration)
                            totalDuration = totalDuration.plus(step.duration)
                        }
                        RecipeStepItem(index + 1,
                            step = step,
                            startTime = startTimes[index],
                            if (currentStep == index) {
                                { currentStep += 1 }
                            } else null,
                            modifier = if (step in pastDue) Modifier.background(Color.Red) else Modifier)
                    }
                }
            }
        }
    }
}

@Composable
fun cancelAlarm(): Boolean {
    return setAlarm(null)
}

@Composable
fun setAlarm(localAlarmTime: Instant?): Boolean {
    val alarmTime = if (localAlarmTime != null) timeToUTC(localAlarmTime) else null
    var lastSetAlarm: Instant? by remember { mutableStateOf(null) }
    if (lastSetAlarm != alarmTime) {
        lastSetAlarm = alarmTime
        if (lastSetAlarm != null) {
            return getPlatform().setAlarm(lastSetAlarm, {
                // this is a hack to force recomposition
                lastSetAlarm = null
            })
        } else {
            return getPlatform().setAlarm(null, {})
        }
    }
    return true
}
