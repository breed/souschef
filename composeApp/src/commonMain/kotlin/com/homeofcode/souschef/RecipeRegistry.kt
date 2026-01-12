package com.homeofcode.souschef

import androidx.compose.runtime.mutableStateListOf

data class RecipeInfo(
    val id: String,
    val name: String,
    val description: String,
    val filePath: String,
    val isBuiltIn: Boolean = true
)

object RecipeRegistry {
    private val builtInRecipes = listOf(
        RecipeInfo(
            id = "sourdough",
            name = "Sourdough Bread",
            description = "Classic sourdough bread with a crispy crust",
            filePath = "files/sourdough.cooklang",
            isBuiltIn = true
        ),
        RecipeInfo(
            id = "focaccia",
            name = "Focaccia",
            description = "Italian olive oil flatbread with herbs",
            filePath = "files/focaccia.cooklang",
            isBuiltIn = true
        ),
        RecipeInfo(
            id = "cinnamon-rolls",
            name = "Cinnamon Rolls",
            description = "Sweet rolls with cinnamon filling and cream cheese frosting",
            filePath = "files/cinnamon-rolls.cooklang",
            isBuiltIn = true
        ),
        RecipeInfo(
            id = "pizza-dough",
            name = "Pizza Dough",
            description = "Neapolitan-style pizza dough for crispy pizzas",
            filePath = "files/pizza-dough.cooklang",
            isBuiltIn = true
        ),
        RecipeInfo(
            id = "banana-bread",
            name = "Banana Bread",
            description = "Moist and delicious banana bread",
            filePath = "files/banana-bread.cooklang",
            isBuiltIn = true
        )
    )

    private val _userRecipes = mutableStateListOf<RecipeInfo>()

    val recipes: List<RecipeInfo>
        get() = builtInRecipes + _userRecipes

    fun getRecipeById(id: String): RecipeInfo? = recipes.find { it.id == id }

    fun loadUserRecipes() {
        _userRecipes.clear()
        val filenames = getPlatform().listUserRecipes()
        filenames.forEach { filename ->
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
                        filePath = "user:$filename",
                        isBuiltIn = false
                    )
                )
            }
        }
    }

    fun addUserRecipe(filename: String, content: String): RecipeInfo {
        getPlatform().writeUserRecipe(filename, content)
        val extractor = CookLangExtractor(content)
        val title = extractor.meta["title"] as? String ?: filename.removeSuffix(".cooklang")
        val description = extractor.meta["description"] as? String ?: ""
        val id = "user-${filename.removeSuffix(".cooklang")}"
        val recipe = RecipeInfo(
            id = id,
            name = title,
            description = description,
            filePath = "user:$filename",
            isBuiltIn = false
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
            _userRecipes.removeAll { it.id == recipeId }
        }
        return deleted
    }

    fun getRecipeContent(recipe: RecipeInfo): String? {
        return if (recipe.isBuiltIn) {
            null // Built-in recipes are loaded via Res.readBytes
        } else {
            val filename = recipe.filePath.removePrefix("user:")
            getPlatform().readUserRecipe(filename)
        }
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
