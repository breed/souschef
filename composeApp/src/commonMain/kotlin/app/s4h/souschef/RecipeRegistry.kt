package app.s4h.souschef

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import souschef.composeapp.generated.resources.Res

data class RecipeInfo(
    val id: String,
    val name: String,
    val description: String,
    val filePath: String
)

@OptIn(ExperimentalResourceApi::class)
object RecipeRegistry {
    private val _userRecipes = mutableStateListOf<RecipeInfo>()

    val recipes: List<RecipeInfo>
        get() = _userRecipes.toList()

    fun getRecipeById(id: String): RecipeInfo? = recipes.find { it.id == id }

    fun loadUserRecipes() {
        _userRecipes.clear()
        val filenames = getPlatform().listUserRecipes()

        // First launch: copy default sourdough recipe if no recipes exist
        if (filenames.isEmpty()) {
            copyDefaultRecipe()
        }

        // Load all user recipes
        getPlatform().listUserRecipes().forEach { filename ->
            val content = getPlatform().readUserRecipe(filename)
            if (content != null) {
                val extractor = CookLangExtractor(content)
                val title = extractor.meta["title"] as? String ?: filename.removeSuffix(".cooklang")
                val description = extractor.meta["description"] as? String ?: ""
                val id = "user-${filename.removeSuffix(".cooklang")}"
                _userRecipes.add(
                    RecipeInfo(
                        id = id,
                        name = title,
                        description = description,
                        filePath = "user:$filename"
                    )
                )
            }
        }
    }

    private fun copyDefaultRecipe() {
        runBlocking {
            try {
                // Copy sourdough recipe content
                val recipeContent = String(Res.readBytes("files/sourdough.cooklang"))
                val filename = "sourdough.cooklang"
                getPlatform().writeUserRecipe(filename, recipeContent)

                // Copy sourdough image
                val imageBytes = Res.readBytes("files/sourdough.png")
                val recipeId = "user-sourdough"
                getPlatform().saveRecipeImage(recipeId, imageBytes)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addUserRecipe(filename: String, content: String, tempImageId: String? = null): RecipeInfo {
        getPlatform().writeUserRecipe(filename, content)
        val extractor = CookLangExtractor(content)
        val title = extractor.meta["title"] as? String ?: filename.removeSuffix(".cooklang")
        val description = extractor.meta["description"] as? String ?: ""
        val id = "user-${filename.removeSuffix(".cooklang")}"

        // Move images from temp ID to actual recipe ID if provided
        if (tempImageId != null && tempImageId != id) {
            getPlatform().moveRecipeImages(tempImageId, id)
        }

        val recipe = RecipeInfo(
            id = id,
            name = title,
            description = description,
            filePath = "user:$filename"
        )
        // Remove existing if updating
        _userRecipes.removeAll { it.id == id }
        _userRecipes.add(recipe)
        return recipe
    }

    fun updateUserRecipe(recipeId: String, content: String): RecipeInfo? {
        val existing = _userRecipes.find { it.id == recipeId } ?: return null
        val filename = existing.filePath.removePrefix("user:")
        return addUserRecipe(filename, content)
    }

    fun deleteUserRecipe(recipeId: String): Boolean {
        val recipe = _userRecipes.find { it.id == recipeId } ?: return false
        val filename = recipe.filePath.removePrefix("user:")
        val deleted = getPlatform().deleteUserRecipe(filename)
        if (deleted) {
            // Also delete associated images
            getPlatform().deleteAllRecipeImages(recipeId)
            _userRecipes.removeAll { it.id == recipeId }
        }
        return deleted
    }

    fun getRecipeContent(recipe: RecipeInfo): String? {
        val filename = recipe.filePath.removePrefix("user:")
        return getPlatform().readUserRecipe(filename)
    }

    fun duplicateRecipe(recipe: RecipeInfo): RecipeInfo? {
        val content = getRecipeContent(recipe) ?: return null

        // Parse the content to modify the title
        val extractor = CookLangExtractor(content)
        val originalTitle = extractor.meta["title"] as? String ?: recipe.name
        val newTitle = "$originalTitle (Copy)"

        // Replace the title in the content
        val newContent = if (content.contains("title:")) {
            content.replace(
                Regex("title:\\s*.*"),
                "title: $newTitle"
            )
        } else {
            // Add title if it doesn't exist
            "---\ntitle: $newTitle\n---\n\n$content"
        }

        val filename = generateUniqueFilename(newTitle)
        return addUserRecipe(filename, newContent)
    }

    fun generateUniqueFilename(baseName: String): String {
        val sanitized = baseName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        var filename = "$sanitized.cooklang"
        var counter = 1
        val existingFiles = getPlatform().listUserRecipes()
        while (existingFiles.contains(filename)) {
            filename = "$sanitized-$counter.cooklang"
            counter++
        }
        return filename
    }
}
