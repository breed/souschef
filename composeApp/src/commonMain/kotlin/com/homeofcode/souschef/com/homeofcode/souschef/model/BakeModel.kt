package com.homeofcode.souschef.com.homeofcode.souschef.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.homeofcode.souschef.CookLangExtractor
import com.homeofcode.souschef.CookLangStep
import com.homeofcode.souschef.getPlatform
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import okio.ExperimentalFileSystem
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import souschef.composeapp.generated.resources.Res
import souschef.composeapp.generated.resources.compose_multiplatform
import java.util.Properties
import kotlin.time.Duration


@OptIn(ExperimentalResourceApi::class, ExperimentalFileSystem::class)
class BakeModel(private val recipePath: String, private val recipeId: String) {
    inner class RecipeStep(
        private val cookLangeStep: CookLangStep,
        val startDelay: Duration,
        var completeTime: MutableState<Instant?>,
    ) {
        val duration = cookLangeStep.duration
        val instruction: String get() = cookLangeStep.text
    }

    fun moveToNextStep() {
        var stepIndex = currentStep.value
        if (stepIndex != null) {
            stepIndex += 1
            currentStep.value = stepIndex
        }
        save()
    }

    fun pastDue(now: Instant): List<RecipeStep> {
        if (startTime.value == null) return emptyList()
        val dueList = mutableListOf<RecipeStep>()
        recipeSteps.forEach {
            val baseTime = startTime.value
            if (baseTime != null && it.duration != null) {
                val due =  baseTime + it.startDelay + it.duration
                if (due < now && it.completeTime.value == null) {
                    dueList.add(it)
                }
            }
        }
        return dueList
    }

    fun calculateNextAlarm(now: Instant): Instant? {
        val baseTime = startTime.value
        if (baseTime != null) {
            for (step in recipeSteps) {
                if (step.duration != null) {
                    val due = baseTime + step.startDelay + step.duration
                    if (due > now && step.completeTime.value == null) {
                        return due
                    }
                }
            }
        }
        return null
    }

    fun start(now: Instant) {
        currentStep.value = 0
        startTime.value = now
        save()
    }

    val alarmTriggered: MutableState<Boolean> = mutableStateOf(false)
    val imageDescription: String = "Preview" // TODO: fill in from cooklang zip
    val title: String
    var alarmEnabled: MutableState<Boolean>
    val recipe: CookLangExtractor
    val image: DrawableResource
    var startTime: MutableState<Instant?>
    val recipeSteps: List<RecipeStep>
    var currentStep: MutableState<Int?>

    init {
        val recipeText = if (recipePath.startsWith("user:")) {
            // Load user recipe from platform storage
            val filename = recipePath.removePrefix("user:")
            getPlatform().readUserRecipe(filename) ?: "nothing"
        } else {
            // Load built-in recipe from resources
            runBlocking {
                try {
                    String(Res.readBytes(recipePath))
                } catch (e: Exception) {
                    e.printStackTrace()
                    "nothing"
                }
            }
        }
        recipe = CookLangExtractor(recipeText)
        title = if (recipe.meta.containsKey("title")) recipe.meta["title"] as String else "No Title"
        image = Res.drawable.compose_multiplatform

        // Initialize from stored state
        val props = Properties()
        try {
            getPlatform().readState().use {
                props.load(it)
            }
        } catch (e: Exception) {
            print("Failed to load state: $e")
        }

        // Only restore state if it's for the same recipe
        val savedRecipeId = props.getProperty("recipeId")
        val isMatchingRecipe = savedRecipeId == recipeId

        alarmEnabled = mutableStateOf(props.getProperty("alarmEnabled") != "false")
        startTime = mutableStateOf(if (isMatchingRecipe) propToInstant(props.getProperty("startTime")) else null)
        currentStep = mutableStateOf(if (isMatchingRecipe) props.getProperty("currentStep")?.toIntOrNull() else null)

        var startDelay: Duration = Duration.ZERO
        var stepNumber = 0
        recipeSteps = recipe.steps.map {
            val stepStartDelay = startDelay
            startDelay = startDelay + (it.duration ?: Duration.ZERO)
            stepNumber += 1
            RecipeStep(
                it,
                startDelay = stepStartDelay,
                completeTime = mutableStateOf(
                    if (isMatchingRecipe) propToInstant(props.getProperty("completeTime.${stepNumber}")) else null
                ),
            )
        }
    }

    fun save() {
        val props = Properties()
        props.setProperty("recipeId", recipeId)
        props.setProperty("alarmEnabled", alarmEnabled.value.toString())
        props.setProperty("startTime", startTime.value?.toEpochMilliseconds().toString())
        props.setProperty("currentStep", currentStep.value?.toString() ?: "null")
        recipeSteps.forEachIndexed { index, step ->
            props.setProperty("completeTime.${index + 1}", step.completeTime.value?.toEpochMilliseconds().toString())
        }
        getPlatform().writeState().use {
            props.save(it, "Bake state")
        }
    }

    private fun propToInstant(property: String?): Instant? {
        return property?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
    }

    fun restart() {
        startTime.value = null
        currentStep.value = null
        recipeSteps.forEach { it.completeTime.value = null }
        alarmTriggered.value = false
        save()
    }
}