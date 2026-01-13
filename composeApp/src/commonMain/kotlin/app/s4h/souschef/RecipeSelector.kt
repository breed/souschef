package app.s4h.souschef

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
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
    var expandedRecipeId by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Recipes",
                style = MaterialTheme.typography.h4,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // All recipes
            RecipeRegistry.recipes.forEach { recipe ->
                RecipeListItem(
                    recipe = recipe,
                    showActions = expandedRecipeId == recipe.id,
                    onClick = {
                        if (expandedRecipeId != null) {
                            // Dismiss actions if any are showing
                            expandedRecipeId = null
                        } else {
                            onRecipeSelected(recipe)
                        }
                    },
                    onLongClick = {
                        expandedRecipeId = if (expandedRecipeId == recipe.id) null else recipe.id
                    },
                    onEdit = {
                        expandedRecipeId = null
                        onEditRecipe(recipe)
                    },
                    onDelete = {
                        expandedRecipeId = null
                        recipeToDelete = recipe
                    },
                    onDuplicate = {
                        expandedRecipeId = null
                        onDuplicateRecipe(recipe)
                    },
                    onShare = {
                        expandedRecipeId = null
                        val content = RecipeRegistry.getRecipeContent(recipe)
                        if (content != null) {
                            getPlatform().shareRecipe(recipe.name, content)
                        }
                    }
                )
            }
        }

        // Floating Action Button in bottom right
        FloatingActionButton(
            onClick = onAddRecipe,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            shape = CircleShape,
            backgroundColor = MaterialTheme.colors.primary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Recipe",
                tint = MaterialTheme.colors.onPrimary
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeListItem(
    recipe: RecipeInfo,
    showActions: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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

            // Action buttons - only show on long press
            if (showActions) {
                // Share button
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colors.secondary
                    )
                }

                // Duplicate button
                IconButton(onClick = onDuplicate) {
                    Text(
                        text = "copy",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }

                // Edit button
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colors.primary
                    )
                }

                // Delete button
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
