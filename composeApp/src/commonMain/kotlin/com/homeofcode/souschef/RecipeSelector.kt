package com.homeofcode.souschef

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Simple copy icon using path data
@Composable
private fun CopyIcon(tint: Color) {
    Icon(
        Icons.Default.Add, // Placeholder, we'll use text instead
        contentDescription = "Duplicate",
        tint = tint,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
fun RecipeSelector(
    onRecipeSelected: (RecipeInfo) -> Unit,
    onAddRecipe: () -> Unit = {},
    onEditRecipe: (RecipeInfo) -> Unit = {},
    onDeleteRecipe: (RecipeInfo) -> Unit = {},
    onDuplicateRecipe: (RecipeInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var recipeToDelete by remember { mutableStateOf<RecipeInfo?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recipes",
                style = MaterialTheme.typography.h4,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onAddRecipe) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(4.dp))
                Text("New")
            }
        }

        // User recipes section
        val userRecipes = RecipeRegistry.recipes.filter { !it.isBuiltIn }
        if (userRecipes.isNotEmpty()) {
            Text(
                text = "My Recipes",
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            userRecipes.forEach { recipe ->
                RecipeListItem(
                    recipe = recipe,
                    onClick = { onRecipeSelected(recipe) },
                    onEdit = { onEditRecipe(recipe) },
                    onDelete = { recipeToDelete = recipe },
                    onDuplicate = { onDuplicateRecipe(recipe) },
                    isUserRecipe = true
                )
            }
        }

        // Built-in recipes section
        val builtInRecipes = RecipeRegistry.recipes.filter { it.isBuiltIn }
        if (builtInRecipes.isNotEmpty()) {
            Text(
                text = if (userRecipes.isNotEmpty()) "Built-in Recipes" else "Recipes",
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            builtInRecipes.forEach { recipe ->
                RecipeListItem(
                    recipe = recipe,
                    onClick = { onRecipeSelected(recipe) },
                    onDuplicate = { onDuplicateRecipe(recipe) },
                    isUserRecipe = false
                )
            }
        }
    }

    // Delete confirmation dialog
    recipeToDelete?.let { recipe ->
        AlertDialog(
            onDismissRequest = { recipeToDelete = null },
            title = { Text("Delete Recipe") },
            text = { Text("Are you sure you want to delete \"${recipe.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecipe(recipe)
                        recipeToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { recipeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecipeListItem(
    recipe: RecipeInfo,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    isUserRecipe: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.h6
                )
                if (recipe.description.isNotBlank()) {
                    Text(
                        text = recipe.description,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2
                    )
                }
            }

            // Duplicate button (shown for all recipes)
            IconButton(onClick = onDuplicate) {
                Text(
                    text = "copy",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
            }

            if (isUserRecipe) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colors.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colors.error
                    )
                }
            }
        }
    }
}
