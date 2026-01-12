package com.homeofcode.souschef

import kotlinx.datetime.Instant
import platform.UIKit.UIDevice
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    override fun setAlarm(time: Instant?): Boolean {
        // TODO: Implement iOS alarm
        return false
    }

    override fun readState(): InputStream {
        // TODO: Implement iOS state persistence
        return ByteArrayInputStream(ByteArray(0))
    }

    override fun writeState(): OutputStream {
        // TODO: Implement iOS state persistence
        return ByteArrayOutputStream()
    }

    override fun toast(message: String) {
        // TODO: Implement iOS toast
    }

    override fun listUserRecipes(): List<String> {
        // TODO: Implement iOS recipe storage
        return emptyList()
    }

    override fun readUserRecipe(filename: String): String? {
        // TODO: Implement iOS recipe storage
        return null
    }

    override fun writeUserRecipe(filename: String, content: String) {
        // TODO: Implement iOS recipe storage
    }

    override fun deleteUserRecipe(filename: String): Boolean {
        // TODO: Implement iOS recipe storage
        return false
    }

    override fun shareRecipe(title: String, content: String) {
        // TODO: Implement iOS sharing with UIActivityViewController
    }

    override fun pickImage(onImagePicked: (ByteArray?) -> Unit) {
        // TODO: Implement iOS image picker
        onImagePicked(null)
    }

    override fun saveRecipeImage(recipeId: String, imageData: ByteArray): String? {
        // TODO: Implement iOS image storage
        return null
    }

    override fun getRecipeImages(recipeId: String): List<String> {
        // TODO: Implement iOS image storage
        return emptyList()
    }

    override fun loadRecipeImage(recipeId: String, imageName: String): ByteArray? {
        // TODO: Implement iOS image storage
        return null
    }

    override fun deleteRecipeImage(recipeId: String, imageName: String): Boolean {
        // TODO: Implement iOS image storage
        return false
    }

    override fun deleteAllRecipeImages(recipeId: String): Boolean {
        // TODO: Implement iOS image storage
        return false
    }

    override fun moveRecipeImages(fromRecipeId: String, toRecipeId: String): Boolean {
        // TODO: Implement iOS image storage
        return false
    }
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun decodeImageBytes(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? {
    // TODO: Implement iOS image decoding
    return null
}