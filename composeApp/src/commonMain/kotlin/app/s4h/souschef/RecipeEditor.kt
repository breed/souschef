package app.s4h.souschef

import kotlinx.datetime.Clock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun RecipeEditor(
    existingRecipe: RecipeInfo? = null,
    onSave: (RecipeInfo) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Load existing content if editing
    val existingContent = remember(existingRecipe) {
        existingRecipe?.let { RecipeRegistry.getRecipeContent(it) }
    }

    // Parse existing content to extract title, description, and steps
    val (initialTitle, initialDescription, initialSteps) = remember(existingContent) {
        if (existingContent != null) {
            val extractor = CookLangExtractor(existingContent)
            val title = extractor.meta["title"] as? String ?: ""
            val description = extractor.meta["description"] as? String ?: ""
            // Extract individual step lines from the content
            val stepsContent = existingContent
                .replace(Regex("^---[\\s\\S]*?---\\s*", RegexOption.MULTILINE), "")
                .trim()
            val steps = stepsContent.split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            Triple(title, description, steps)
        } else {
            Triple("", "", listOf(""))
        }
    }

    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    val steps = remember { mutableStateListOf(*initialSteps.toTypedArray()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Image state - list of image filenames
    val imageNames = remember { mutableStateListOf<String>() }
    // Cache of loaded image bitmaps
    val imageBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    // Stable temp ID for new recipes (so images are saved to same directory)
    val tempRecipeId = remember { "temp-${Clock.System.now().toEpochMilliseconds()}" }
    // Current recipe ID (existing or temp)
    val currentRecipeId = existingRecipe?.id ?: tempRecipeId

    // Load existing images when editing
    LaunchedEffect(existingRecipe) {
        if (existingRecipe != null) {
            val existingImages = getPlatform().getRecipeImages(existingRecipe.id)
            imageNames.clear()
            imageNames.addAll(existingImages)
            // Load thumbnails
            existingImages.forEach { imageName ->
                val bytes = getPlatform().loadRecipeImage(existingRecipe.id, imageName)
                if (bytes != null) {
                    decodeImageBytes(bytes)?.let { bitmap ->
                        imageBitmaps[imageName] = bitmap
                    }
                }
            }
        }
    }

    // Ensure at least one empty step exists
    if (steps.isEmpty()) {
        steps.add("")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("<- Cancel")
            }
            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Please enter a recipe title"
                        return@Button
                    }

                    val nonEmptySteps = steps.filter { it.isNotBlank() }
                    if (nonEmptySteps.isEmpty()) {
                        errorMessage = "Please add at least one step"
                        return@Button
                    }

                    // Build the full cooklang content
                    val fullContent = buildString {
                        appendLine("---")
                        appendLine("title: $title")
                        if (description.isNotBlank()) {
                            appendLine("description: $description")
                        }
                        if (imageNames.isNotEmpty()) {
                            appendLine("images: [${imageNames.joinToString(", ") { "\"$it\"" }}]")
                        }
                        appendLine("---")
                        appendLine()
                        nonEmptySteps.forEachIndexed { index, step ->
                            append(step.trim())
                            if (index < nonEmptySteps.size - 1) {
                                appendLine()
                                appendLine()
                            }
                        }
                    }

                    try {
                        // Validate by parsing
                        val extractor = CookLangExtractor(fullContent)
                        if (extractor.steps.isEmpty()) {
                            errorMessage = "No valid steps found. Check your CookLang syntax."
                            return@Button
                        }

                        val savedRecipe = if (existingRecipe != null) {
                            RecipeRegistry.updateUserRecipe(existingRecipe.id, fullContent)
                        } else {
                            val filename = RecipeRegistry.generateUniqueFilename(title)
                            // Pass temp image ID so images get moved to the actual recipe ID
                            RecipeRegistry.addUserRecipe(filename, fullContent, tempRecipeId)
                        }

                        if (savedRecipe != null) {
                            onSave(savedRecipe)
                        } else {
                            errorMessage = "Failed to save recipe"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error parsing recipe: ${e.message}"
                    }
                }
            ) {
                Text(if (existingRecipe != null) "Save" else "Create")
            }
        }

        Text(
            text = if (existingRecipe != null) "Edit Recipe" else "New Recipe",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Scrollable content
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title field
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMessage = null },
                    label = { Text("Recipe Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage?.contains("title") == true
                )
            }

            // Description field
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }

            // Images section
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photos",
                            style = MaterialTheme.typography.h6
                        )
                        OutlinedButton(
                            onClick = {
                                getPlatform().pickImage { imageBytes ->
                                    if (imageBytes != null) {
                                        val savedName = getPlatform().saveRecipeImage(currentRecipeId, imageBytes)
                                        if (savedName != null) {
                                            imageNames.add(savedName)
                                            decodeImageBytes(imageBytes)?.let { bitmap ->
                                                imageBitmaps[savedName] = bitmap
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Photo")
                        }
                    }

                    if (imageNames.isEmpty()) {
                        Text(
                            text = "No photos added yet",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(imageNames.toList()) { imageName ->
                                Box(
                                    modifier = Modifier.size(100.dp)
                                ) {
                                    imageBitmaps[imageName]?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = imageName,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } ?: Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.LightGray)
                                    )

                                    // Delete button
                                    IconButton(
                                        onClick = {
                                            getPlatform().deleteRecipeImage(currentRecipeId, imageName)
                                            imageNames.remove(imageName)
                                            imageBitmaps.remove(imageName)
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Steps header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Steps",
                        style = MaterialTheme.typography.h6
                    )
                    OutlinedButton(
                        onClick = { steps.add("") }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Step")
                    }
                }
            }

            // CookLang hint
            item {
                Text(
                    text = "Use @ingredient{amount%unit} for ingredients, ~{time%unit} for durations",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            // Steps list
            itemsIndexed(steps, key = { index, _ -> index }) { index, step ->
                StepEditorItem(
                    stepNumber = index + 1,
                    stepContent = step,
                    onContentChange = { newContent ->
                        steps[index] = newContent
                        errorMessage = null
                    },
                    onMoveUp = if (index > 0) {
                        {
                            val temp = steps[index]
                            steps[index] = steps[index - 1]
                            steps[index - 1] = temp
                        }
                    } else null,
                    onMoveDown = if (index < steps.size - 1) {
                        {
                            val temp = steps[index]
                            steps[index] = steps[index + 1]
                            steps[index + 1] = temp
                        }
                    } else null,
                    onDelete = if (steps.size > 1) {
                        { steps.removeAt(index) }
                    } else null
                )
            }

            // Add step button at the bottom
            item {
                OutlinedButton(
                    onClick = { steps.add("") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Another Step")
                }
            }

            // Help section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFF5F5F5),
                    elevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "CookLang Quick Reference",
                            style = MaterialTheme.typography.subtitle2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ingredients: @flour{500%g}, @water{350%ml}\n" +
                                    "Durations: ~{30%min}, ~{2%hours}\n\n" +
                                    "Example: mix @flour{500%g} with @water{350%ml} and let rest for ~{30%min}.",
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
        }

        // Error message
        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun StepEditorItem(
    stepNumber: Int,
    stepContent: String,
    onContentChange: (String) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Step header with controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step $stepNumber",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary
                )

                Row {
                    // Move up button
                    IconButton(
                        onClick = { onMoveUp?.invoke() },
                        enabled = onMoveUp != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = if (onMoveUp != null) MaterialTheme.colors.primary else Color.LightGray
                        )
                    }

                    // Move down button
                    IconButton(
                        onClick = { onMoveDown?.invoke() },
                        enabled = onMoveDown != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = if (onMoveDown != null) MaterialTheme.colors.primary else Color.LightGray
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = { onDelete?.invoke() },
                        enabled = onDelete != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = if (onDelete != null) MaterialTheme.colors.error else Color.LightGray
                        )
                    }
                }
            }

            // Step content text field
            OutlinedTextField(
                value = stepContent,
                onValueChange = onContentChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter step instructions...") },
                minLines = 2,
                maxLines = 4
            )
        }
    }
}
