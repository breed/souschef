package com.homeofcode.souschef

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.homeofcode.souschef.com.homeofcode.souschef.model.BakeModel

@OptIn(ExperimentalFoundationApi::class)
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

    // Auto-start the recipe when opened
    LaunchedEffect(Unit) {
        if (bake.startTime.value == null) {
            val startNow = now()
            bake.start(startNow)
            if (bake.alarmEnabled.value) {
                setAlarm(bake.calculateNextAlarm(startNow))
            }
        }
    }

    // Load images from platform storage
    val imageBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(bake.imageNames) {
        if (bake.imageNames.isNotEmpty()) {
            bake.imageNames.forEach { imageName ->
                val bytes = getPlatform().loadRecipeImage(bake.id, imageName)
                if (bytes != null) {
                    decodeImageBytes(bytes)?.let { bitmap ->
                        imageBitmaps[imageName] = bitmap
                    }
                }
            }
        }
    }

    val nextAlarm =
        // we always want to evaluate this, so use the raw value
        if (bake.alarmEnabled.value) {
            val nextAlarm = bake.calculateNextAlarm(now)
            nextAlarm
        } else {
            null
        }

    var showFinishConfirmation by remember { mutableStateOf(false) }
    var showPhotoGallery by remember { mutableStateOf(false) }

    // Full-screen photo gallery overlay
    if (showPhotoGallery && bake.imageNames.isNotEmpty()) {
        BackHandler { showPhotoGallery = false }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { showPhotoGallery = false }
        ) {
            val galleryPagerState = rememberPagerState(pageCount = { bake.imageNames.size })

            HorizontalPager(
                state = galleryPagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val imageName = bake.imageNames[page]
                val bitmap = imageBitmaps[imageName]
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Recipe photo ${page + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Page indicators at bottom
            if (bake.imageNames.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(bake.imageNames.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (galleryPagerState.currentPage == index) Color.White
                                    else Color.White.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }

            // Photo counter at top
            Text(
                text = "${galleryPagerState.currentPage + 1} / ${bake.imageNames.size}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }
        return
    }

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
        val translucentBg = Modifier.background(Color(0xB0FFFFFF))

        Column(modifier = modifier.fillMaxSize().padding(bottom = 0.dp)) {
            // Header area with photo background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(bottom = 0.dp)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = {
                            if (bake.imageNames.isNotEmpty()) {
                                showPhotoGallery = true
                            }
                        }
                    )
            ) {
                // Background image (use first image if available)
                if (bake.imageNames.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { bake.imageNames.size })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val imageName = bake.imageNames[page]
                        val bitmap = imageBitmaps[imageName]
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Recipe photo ${page + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.LightGray)
                            )
                        }
                    }

                    // Page indicators
                    if (bake.imageNames.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(bake.imageNames.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (pagerState.currentPage == index) Color.White
                                            else Color.White.copy(alpha = 0.5f)
                                        )
                                )
                            }
                        }
                    }
                }

                // Overlay content on top of image
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x60000000))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Title at top
                    Text(
                        text = bake.title,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.h3,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Controls at bottom
                    Column {
                        recipeStartTime?.let { startTime ->
                            Text(
                                text = "Started at ${formatInstant(startTime)}",
                                color = Color.White
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Timer Alarm", color = Color.White)
                            Switch(checked = alarmEnabled, onCheckedChange = {
                                alarmEnabled = it
                            })
                        }
                        Text(
                            text = "Next Alarm: ${formatInstant(nextAlarm)}",
                            color = Color.White
                        )
                    }
                }
            }

            if (onEndRecipe != null) {
                Button(
                    onClick = { showFinishConfirmation = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 0.dp)
                ) {
                    Text("Finish Baking")
                }
            }

            // Scrollable content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Markdown(
                    bake.recipe.meta["description"] as? String ?: "",
                )
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
