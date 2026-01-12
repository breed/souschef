package com.homeofcode.souschef

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homeofcode.souschef.com.homeofcode.souschef.model.BakeModel
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import org.jetbrains.compose.resources.painterResource

@Composable
fun RecipeCard(
    bake: BakeModel,
    modifier: Modifier = Modifier,
    onEndRecipe: (() -> Unit)? = null
) {
    var alarmEnabled by remember { bake.alarmEnabled }
    val recipeStartTime by remember { bake.startTime }
    var alarmTriggered by remember { bake.alarmTriggered }
    val currentBakeStep by remember { bake.currentStep }
    val now = now()
    val pastDue = bake.pastDue(now)

    val nextAlarm =
        // we always want to evaluate this, so use the raw value
        if (bake.alarmEnabled.value) {
            val nextAlarm = bake.calculateNextAlarm(now)
            nextAlarm
        } else {
            null
        }

    var showFinishConfirmation by remember { mutableStateOf(false) }

    // Finish baking confirmation dialog
    if (showFinishConfirmation && onEndRecipe != null) {
        AlertDialog(
            onDismissRequest = { showFinishConfirmation = false },
            title = { Text("Finish Baking") },
            text = { Text("Are you sure you want to finish baking ${bake.title}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishConfirmation = false
                        onEndRecipe()
                    }
                ) {
                    Text("Finish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    MaterialTheme {
        Box(modifier = modifier) {
            val imagePainter = painterResource(bake.image)
            // Image(imagePainter, contentDescription = bake.imageDescription, modifier = modifier)
            val translucentBg = Modifier.background(Color(0xB0FFFFFF))
            var screenWidth by remember { mutableIntStateOf(0) }
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned { x -> screenWidth = x.size.width },
                horizontalAlignment = Alignment.Start
            ) {
                // TODO: the .8f is such an ugly hack. i need to figure out how to get the width of the text
                Text(
                    text = bake.title,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h3,
                    modifier = translucentBg.fillMaxWidth()
                )
                val recipeStartTimeNow = recipeStartTime
                if (recipeStartTimeNow == null) {
                    Button(
                        onClick = {
                            val onClickNow = now()
                            bake.start(onClickNow)
                            if (bake.alarmEnabled.value) setAlarm(bake.calculateNextAlarm(onClickNow))
                        }, modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start")
                    }
                } else {
                    Column {
                        Text(
                            text = "Started at ${
                                formatInstant(recipeStartTimeNow)
                            }", textAlign = TextAlign.Right
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Timer Alarm")
                            Switch(checked = alarmEnabled, onCheckedChange = {
                                alarmEnabled = it
                            })
                        }
                        Text(
                            text = "Next Alarm: ${
                                formatInstant(nextAlarm)
                            }", textAlign = TextAlign.Right
                        )
                        if (onEndRecipe != null) {
                            Button(
                                onClick = { showFinishConfirmation = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Finish Baking")
                            }
                        }
                    }

                }
                //Spacer(modifier = Modifier.fillMaxHeight(.2f).fillMaxWidth())
                Column(
                    modifier = translucentBg.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Ingredients",
                        style = MaterialTheme.typography.h4,
                    )
                    // someday we might want multicolumn ingredients
                    var minDp = 0.dp
                    bake.recipe.ingredients.values.forEach { ingredient ->
                        val text = "${ingredient.quantity} ${ingredient.unit}   ${ingredient.name}"
                        val width = textWidth(text)
                        if (width > minDp) {
                            minDp = width
                        }
                    }

                    bake.recipe.ingredients.values.sortedBy { it.name }.forEach {
                        with(it) {
                            IngredientItem(
                                amount = quantity.toString(),
                                unit = unit,
                                ingredient = name,
                            )
                        }
                    }

                    Text(text = "Steps", style = MaterialTheme.typography.h4)
                    bake.recipeSteps.forEachIndexed { index, step ->
                        RecipeStepItem(
                            step = step,
                            isCurrentStep = {
                                currentBakeStep == index
                            },
                            onClick = { bake.moveToNextStep() },
                            bakeStartTime = recipeStartTime,
                            modifier = if (step in pastDue) Modifier.background(Color.Red) else Modifier
                        )
                    }
                }
            }
        }
    }
}