package app.s4h.souschef

import androidx.compose.runtime.mutableStateListOf
import app.s4h.souschef.model.BakeModel

data class ActiveBake(
    val recipeInfo: RecipeInfo,
    val bakeModel: BakeModel
)

// Simple multiplatform key-value properties parser/writer
fun parseProperties(text: String): Map<String, String> {
    return text.lines()
        .filter { it.contains('=') && !it.startsWith('#') }
        .associate {
            val idx = it.indexOf('=')
            it.substring(0, idx) to it.substring(idx + 1)
        }
}

fun writeProperties(props: Map<String, String>): String {
    return props.entries.joinToString("\n") { "${it.key}=${it.value}" }
}

object ActiveBakesManager {
    private val _activeBakes = mutableStateListOf<ActiveBake>()
    val activeBakes: List<ActiveBake> get() = _activeBakes

    fun getOrCreateBake(recipeInfo: RecipeInfo): BakeModel {
        val existing = _activeBakes.find { it.recipeInfo.id == recipeInfo.id }
        if (existing != null) {
            return existing.bakeModel
        }

        val bake = BakeModel(recipeInfo.filePath, recipeInfo.id)
        _activeBakes.add(ActiveBake(recipeInfo, bake))
        return bake
    }

    fun getBakeByRecipeId(recipeId: String): ActiveBake? {
        return _activeBakes.find { it.recipeInfo.id == recipeId }
    }

    fun getStartedBakes(): List<ActiveBake> {
        return _activeBakes.filter { it.bakeModel.startTime.value != null }
    }

    fun indexOf(recipeId: String): Int {
        return _activeBakes.indexOfFirst { it.recipeInfo.id == recipeId }
    }

    fun loadPersistedActiveBakes() {
        // Check which recipes have persisted state and load them
        try {
            val data = getPlatform().readState()
            if (data != null) {
                val props = parseProperties(data.decodeToString())
                val savedRecipeId = props["recipeId"]
                if (savedRecipeId != null) {
                    val recipeInfo = RecipeRegistry.getRecipeById(savedRecipeId)
                    if (recipeInfo != null) {
                        getOrCreateBake(recipeInfo)
                    }
                }
            }
        } catch (e: Exception) {
            // No saved state, that's fine
        }
    }

    fun removeBake(recipeId: String) {
        val index = _activeBakes.indexOfFirst { it.recipeInfo.id == recipeId }
        if (index >= 0) {
            _activeBakes[index].bakeModel.restart()
            _activeBakes.removeAt(index)
        }
    }
}
