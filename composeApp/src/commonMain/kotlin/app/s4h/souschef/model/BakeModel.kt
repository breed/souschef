package app.s4h.souschef.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.s4h.souschef.CookLangExtractor
import app.s4h.souschef.CookLangStep
import app.s4h.souschef.getPlatform
import app.s4h.souschef.parseProperties
import app.s4h.souschef.writeProperties
import kotlinx.datetime.Instant
import kotlin.time.Duration

class BakeModel(private val recipePath: String, private val recipeId: String) {
    inner class RecipeStep(
        private val cookLangeStep: CookLangStep,
        val startDelay: Duration,
        var completeTime: MutableState<Instant?>,
        var startTimeOffset: MutableState<Duration>,
        var durationOffset: MutableState<Duration>,
    ) {
        val duration = cookLangeStep.duration
        val instruction: String get() = cookLangeStep.text

        fun getAdjustedStartDelay(): Duration = startDelay + startTimeOffset.value
        fun getAdjustedDuration(): Duration? = duration?.plus(durationOffset.value)
    }

    fun moveToNextStep() {
        var stepIndex = currentStep.value
        if (stepIndex != null) {
            stepIndex += 1
            currentStep.value = stepIndex
        }
        save()
    }

    fun moveToPreviousStep() {
        var stepIndex = currentStep.value
        if (stepIndex != null && stepIndex > 0) {
            stepIndex -= 1
            recipeSteps[stepIndex].completeTime.value = null
            currentStep.value = stepIndex
        }
        save()
    }

    fun adjustStepTime(stepIndex: Int, newOffset: Duration) {
        val step = recipeSteps.getOrNull(stepIndex) ?: return
        val oldOffset = step.startTimeOffset.value
        val delta = newOffset - oldOffset

        // Adjust this step and all subsequent steps
        for (i in stepIndex until recipeSteps.size) {
            recipeSteps[i].startTimeOffset.value = recipeSteps[i].startTimeOffset.value + delta
        }
        save()
    }

    fun resetStepTime(stepIndex: Int) {
        val step = recipeSteps.getOrNull(stepIndex) ?: return
        val delta = -step.startTimeOffset.value

        // Reset this step and cascade to subsequent steps
        for (i in stepIndex until recipeSteps.size) {
            recipeSteps[i].startTimeOffset.value = recipeSteps[i].startTimeOffset.value + delta
        }
        save()
    }

    fun adjustStepEndTime(stepIndex: Int, newDurationOffset: Duration) {
        val step = recipeSteps.getOrNull(stepIndex) ?: return
        step.durationOffset.value = newDurationOffset
        save()
    }

    fun resetStepEndTime(stepIndex: Int) {
        val step = recipeSteps.getOrNull(stepIndex) ?: return
        step.durationOffset.value = Duration.ZERO
        save()
    }

    fun pastDue(now: Instant): List<RecipeStep> {
        if (startTime.value == null) return emptyList()
        val dueList = mutableListOf<RecipeStep>()
        recipeSteps.forEach {
            val baseTime = startTime.value
            val adjustedDuration = it.getAdjustedDuration()
            if (baseTime != null && adjustedDuration != null) {
                val due = baseTime + it.getAdjustedStartDelay() + adjustedDuration
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
                val adjustedDuration = step.getAdjustedDuration()
                if (adjustedDuration != null) {
                    val due = baseTime + step.getAdjustedStartDelay() + adjustedDuration
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
    val imageDescription: String = "Preview"
    val title: String
    var alarmEnabled: MutableState<Boolean>
    val recipe: CookLangExtractor
    val imageNames: List<String>  // List of image filenames
    val id: String = recipeId  // Expose the recipe ID
    var startTime: MutableState<Instant?>
    val recipeSteps: List<RecipeStep>
    var currentStep: MutableState<Int?>

    init {
        // Load image names from platform storage
        imageNames = getPlatform().getRecipeImages(recipeId)

        // Load recipe from user storage
        val filename = recipePath.removePrefix("user:")
        val recipeText = getPlatform().readUserRecipe(filename) ?: "nothing"

        recipe = CookLangExtractor(recipeText)
        title = if (recipe.meta.containsKey("title")) recipe.meta["title"] as String else "No Title"

        // Initialize from stored state
        val props: Map<String, String> = try {
            val data = getPlatform().readState()
            if (data != null) parseProperties(data.decodeToString()) else emptyMap()
        } catch (e: Exception) {
            print("Failed to load state: $e")
            emptyMap()
        }

        // Only restore state if it's for the same recipe
        val savedRecipeId = props["recipeId"]
        val isMatchingRecipe = savedRecipeId == recipeId

        alarmEnabled = mutableStateOf(props["alarmEnabled"] != "false")
        startTime = mutableStateOf(if (isMatchingRecipe) propToInstant(props["startTime"]) else null)
        currentStep = mutableStateOf(if (isMatchingRecipe) props["currentStep"]?.toIntOrNull() else null)

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
                    if (isMatchingRecipe) propToInstant(props["completeTime.${stepNumber}"]) else null
                ),
                startTimeOffset = mutableStateOf(
                    if (isMatchingRecipe) propToDuration(props["startTimeOffset.${stepNumber}"]) else Duration.ZERO
                ),
                durationOffset = mutableStateOf(
                    if (isMatchingRecipe) propToDuration(props["durationOffset.${stepNumber}"]) else Duration.ZERO
                ),
            )
        }
    }

    fun save() {
        val props = mutableMapOf<String, String>()
        props["recipeId"] = recipeId
        props["alarmEnabled"] = alarmEnabled.value.toString()
        props["startTime"] = startTime.value?.toEpochMilliseconds().toString()
        props["currentStep"] = currentStep.value?.toString() ?: "null"
        recipeSteps.forEachIndexed { index, step ->
            props["completeTime.${index + 1}"] = step.completeTime.value?.toEpochMilliseconds().toString()
            props["startTimeOffset.${index + 1}"] = step.startTimeOffset.value.toIsoString()
            props["durationOffset.${index + 1}"] = step.durationOffset.value.toIsoString()
        }
        getPlatform().writeState(writeProperties(props).encodeToByteArray())
    }

    private fun propToInstant(property: String?): Instant? {
        return property?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
    }

    private fun propToDuration(property: String?): Duration {
        return property?.let {
            try { Duration.parse(it) } catch (e: Exception) { Duration.ZERO }
        } ?: Duration.ZERO
    }

    fun restart() {
        startTime.value = null
        currentStep.value = null
        recipeSteps.forEach {
            it.completeTime.value = null
            it.startTimeOffset.value = Duration.ZERO
            it.durationOffset.value = Duration.ZERO
        }
        alarmTriggered.value = false
        save()
    }
}
