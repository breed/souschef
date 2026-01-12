package com.homeofcode.souschef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.homeofcode.souschef.com.homeofcode.souschef.model.BakeModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


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

sealed class Screen {
    data object RecipeList : Screen()
    data class RecipeDetail(val recipeId: String) : Screen()
    data object AddRecipe : Screen()
    data class EditRecipe(val recipe: RecipeInfo) : Screen()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun App(onBakeCreated: ((BakeModel) -> Unit)? = null) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.RecipeList) }

    // Load any persisted active bakes and user recipes on first composition
    LaunchedEffect(Unit) {
        RecipeRegistry.loadUserRecipes()
        ActiveBakesManager.loadPersistedActiveBakes()
    }

    MaterialTheme {
        when (val screen = currentScreen) {
            is Screen.RecipeList -> {
                RecipeSelector(
                    onRecipeSelected = { recipe ->
                        val bake = ActiveBakesManager.getOrCreateBake(recipe)
                        onBakeCreated?.invoke(bake)
                        currentScreen = Screen.RecipeDetail(recipe.id)
                    },
                    onAddRecipe = {
                        currentScreen = Screen.AddRecipe
                    },
                    onEditRecipe = { recipe ->
                        currentScreen = Screen.EditRecipe(recipe)
                    },
                    onDeleteRecipe = { recipe ->
                        RecipeRegistry.deleteUserRecipe(recipe.id)
                        ActiveBakesManager.removeBake(recipe.id)
                    }
                )
            }
            is Screen.RecipeDetail -> {
                ActiveRecipePager(
                    initialRecipeId = screen.recipeId,
                    onBackToList = { currentScreen = Screen.RecipeList },
                    onBakeCreated = onBakeCreated
                )
            }
            is Screen.AddRecipe -> {
                RecipeEditor(
                    onSave = { savedRecipe ->
                        currentScreen = Screen.RecipeList
                    },
                    onCancel = {
                        currentScreen = Screen.RecipeList
                    }
                )
            }
            is Screen.EditRecipe -> {
                RecipeEditor(
                    existingRecipe = screen.recipe,
                    onSave = { savedRecipe ->
                        // Remove old bake if it exists (recipe content may have changed)
                        ActiveBakesManager.removeBake(screen.recipe.id)
                        currentScreen = Screen.RecipeList
                    },
                    onCancel = {
                        currentScreen = Screen.RecipeList
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveRecipePager(
    initialRecipeId: String,
    onBackToList: () -> Unit,
    onBakeCreated: ((BakeModel) -> Unit)? = null
) {
    val activeBakes = ActiveBakesManager.activeBakes
    var currentRecipeId by remember { mutableStateOf(initialRecipeId) }

    // Find the initial page index
    val initialPage = remember(initialRecipeId) {
        activeBakes.indexOfFirst { it.recipeInfo.id == initialRecipeId }.coerceAtLeast(0)
    }

    if (activeBakes.isEmpty()) {
        onBackToList()
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { activeBakes.size }
    )

    // Track page changes to update current recipe
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page in activeBakes.indices) {
                currentRecipeId = activeBakes[page].recipeInfo.id
                onBakeCreated?.invoke(activeBakes[page].bakeModel)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page in activeBakes.indices) {
                val activeBake = activeBakes[page]
                RecipeCard(
                    bake = activeBake.bakeModel,
                    onBackToList = onBackToList,
                    showPageIndicator = activeBakes.size > 1,
                    currentPage = pagerState.currentPage,
                    totalPages = activeBakes.size
                )
            }
        }
    }
}


fun formatInstant(instant: Instant?): String =
    if (instant == null) "" else instant.toLocalDateTime(TimeZone.currentSystemDefault())
        .toJavaLocalDateTime().format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    )

@Composable
fun textWidth(text: String): Dp {
    val textMeasurer = rememberTextMeasurer()
    return with(LocalDensity.current) { textMeasurer.measure(text).size.width.toDp() }
}

var lastAlarmSet: Instant? = Instant.DISTANT_PAST

fun setAlarm(localAlarmTime: Instant?): Boolean {
    val alarmTime = if (localAlarmTime != null) timeToUTC(localAlarmTime) else null
    if (lastAlarmSet != alarmTime) {
        lastAlarmSet = alarmTime
        getPlatform().setAlarm(alarmTime)
    }
    return true
}
