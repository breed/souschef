package app.s4h.souschef

import kotlinx.datetime.Instant

interface Platform {
    val name: String
    // if time is null, the alarm will be unregistered
    fun setAlarm(time: Instant?): Boolean
    fun readState(): ByteArray?
    fun writeState(data: ByteArray)
    fun toast(message: String)

    // Recipe file operations
    fun listUserRecipes(): List<String>
    fun readUserRecipe(filename: String): String?
    fun writeUserRecipe(filename: String, content: String)
    fun deleteUserRecipe(filename: String): Boolean

    // Sharing
    fun shareRecipe(title: String, content: String)

    // Recipe images
    fun pickImage(onImagePicked: (ByteArray?) -> Unit)
    fun saveRecipeImage(recipeId: String, imageData: ByteArray): String?
    fun getRecipeImages(recipeId: String): List<String>
    fun loadRecipeImage(recipeId: String, imageName: String): ByteArray?
    fun deleteRecipeImage(recipeId: String, imageName: String): Boolean
    fun deleteAllRecipeImages(recipeId: String): Boolean
    fun moveRecipeImages(fromRecipeId: String, toRecipeId: String): Boolean

}

expect fun getPlatform(): Platform

// Platform-specific image decoding
expect fun decodeImageBytes(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap?