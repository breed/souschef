package com.homeofcode.souschef

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
            // Extract just the steps part (after the YAML front matter)
            val stepsContent = existingContent
                .replace(Regex("^---[\\s\\S]*?---\\s*", RegexOption.MULTILINE), "")
                .trim()
            Triple(title, description, stepsContent)
        } else {
            Triple("", "", "")
        }
    }

    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var stepsContent by remember { mutableStateOf(initialSteps) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onCancel) {
                Text("<- Cancel")
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(
            text = if (existingRecipe != null) "Edit Recipe" else "New Recipe",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Recipe Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Recipe Steps (CookLang format)",
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = "Use @ingredient{amount%unit} for ingredients, ~{time%unit} for durations",
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = stepsContent,
            onValueChange = { stepsContent = it },
            label = { Text("Steps") },
            modifier = Modifier.fillMaxWidth().height(300.dp),
            minLines = 10
        )

        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (title.isBlank()) {
                    errorMessage = "Please enter a recipe title"
                    return@Button
                }
                if (stepsContent.isBlank()) {
                    errorMessage = "Please enter recipe steps"
                    return@Button
                }

                // Build the full cooklang content with YAML front matter
                val fullContent = buildString {
                    appendLine("---")
                    appendLine("title: $title")
                    if (description.isNotBlank()) {
                        appendLine("description: $description")
                    }
                    appendLine("---")
                    appendLine()
                    append(stepsContent.trim())
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
                        RecipeRegistry.addUserRecipe(filename, fullContent)
                    }

                    if (savedRecipe != null) {
                        onSave(savedRecipe)
                    } else {
                        errorMessage = "Failed to save recipe"
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing recipe: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (existingRecipe != null) "Save Changes" else "Create Recipe")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Help section
        Text(
            text = "CookLang Quick Reference",
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = """
                Ingredients: @flour{500%g}, @water{350%ml}
                Durations: ~{30%min}, ~{2%hours}

                Example step:
                mix @flour{500%g} with @water{350%ml} and let rest for ~{30%min}.
            """.trimIndent(),
            style = MaterialTheme.typography.body2
        )
    }
}
