package app.s4h.souschef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import app.s4h.souschef.model.BakeModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview


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

sealed class EditorScreen {
    data object None : EditorScreen()
    data object AddRecipe : EditorScreen()
    data class EditRecipe(val recipe: RecipeInfo) : EditorScreen()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun App(onBakeCreated: ((BakeModel) -> Unit)? = null) {
    var editorScreen by remember { mutableStateOf<EditorScreen>(EditorScreen.None) }

    // Load any persisted active bakes and user recipes on first composition
    LaunchedEffect(Unit) {
        RecipeRegistry.loadUserRecipes()
        ActiveBakesManager.loadPersistedActiveBakes()
    }

    MaterialTheme {
        when (val editor = editorScreen) {
            is EditorScreen.None -> {
                MainPager(
                    onBakeCreated = onBakeCreated,
                    onAddRecipe = { editorScreen = EditorScreen.AddRecipe },
                    onEditRecipe = { recipe -> editorScreen = EditorScreen.EditRecipe(recipe) }
                )
            }
            is EditorScreen.AddRecipe -> {
                RecipeEditor(
                    onSave = { savedRecipe ->
                        editorScreen = EditorScreen.None
                    },
                    onCancel = {
                        editorScreen = EditorScreen.None
                    }
                )
            }
            is EditorScreen.EditRecipe -> {
                RecipeEditor(
                    existingRecipe = editor.recipe,
                    onSave = { savedRecipe ->
                        // Remove old bake if it exists (recipe content may have changed)
                        ActiveBakesManager.removeBake(editor.recipe.id)
                        editorScreen = EditorScreen.None
                    },
                    onCancel = {
                        editorScreen = EditorScreen.None
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPager(
    onBakeCreated: ((BakeModel) -> Unit)? = null,
    onAddRecipe: () -> Unit,
    onEditRecipe: (RecipeInfo) -> Unit
) {
    val activeBakes = ActiveBakesManager.activeBakes
    val coroutineScope = rememberCoroutineScope()

    // Page 0 = Recipe list, Pages 1+ = Active recipes
    // Use derivedStateOf to ensure pageCount updates reactively
    val pageCount by remember { derivedStateOf { 1 + activeBakes.size } }

    // Start on the active bake page if one exists, otherwise start on recipe list
    val initialPage = remember {
        if (ActiveBakesManager.activeBakes.isNotEmpty()) 1 else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 1 + ActiveBakesManager.activeBakes.size }
    )

    // Navigate to active bake on initial load if one exists
    LaunchedEffect(Unit) {
        if (ActiveBakesManager.activeBakes.isNotEmpty() && pagerState.currentPage == 0) {
            pagerState.scrollToPage(1)
        }
    }

    // Track page changes to update current bake for alarms
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val currentActiveBakes = ActiveBakesManager.activeBakes
            if (page > 0 && page - 1 in currentActiveBakes.indices) {
                onBakeCreated?.invoke(currentActiveBakes[page - 1].bakeModel)
            }
        }
    }

    // Handle back button to go to recipe list when viewing a recipe
    PlatformBackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val currentActiveBakes = ActiveBakesManager.activeBakes
            if (page == 0) {
                // Recipe list page
                RecipeSelector(
                    onRecipeSelected = { recipe ->
                        val bake = ActiveBakesManager.getOrCreateBake(recipe)
                        onBakeCreated?.invoke(bake)
                        // Find the page index for this recipe and navigate to it
                        val recipeIndex = ActiveBakesManager.activeBakes.indexOfFirst {
                            it.recipeInfo.id == recipe.id
                        }
                        if (recipeIndex >= 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(recipeIndex + 1)
                            }
                        }
                    },
                    onAddRecipe = onAddRecipe,
                    onEditRecipe = onEditRecipe,
                    onDeleteRecipe = { recipe ->
                        RecipeRegistry.deleteUserRecipe(recipe.id)
                        ActiveBakesManager.removeBake(recipe.id)
                    },
                    onDuplicateRecipe = { recipe ->
                        val duplicated = RecipeRegistry.duplicateRecipe(recipe)
                        if (duplicated != null) {
                            onEditRecipe(duplicated)
                        }
                    }
                )
            } else {
                // Active recipe pages
                val recipeIndex = page - 1
                if (recipeIndex in currentActiveBakes.indices) {
                    val activeBake = currentActiveBakes[recipeIndex]
                    RecipeCard(
                        bake = activeBake.bakeModel,
                        onEndRecipe = {
                            ActiveBakesManager.removeBake(activeBake.recipeInfo.id)
                            // Navigate to recipe list after closing
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        }
                    )
                }
            }
        }

        // Page indicator dots at the bottom
        if (pageCount > 1) {
            PageIndicator(
                pagerState = pagerState,
                pageCount = pageCount,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colors.primary
                        else Color.LightGray
                    )
            )
        }
    }
}


fun formatInstant(instant: Instant?): String {
    if (instant == null) return ""
    val localTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = localTime.hour
    val minute = localTime.minute
    val amPm = if (hour < 12) "AM" else "PM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$hour12:${minute.toString().padStart(2, '0')} $amPm"
}

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
