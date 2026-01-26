@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.s4h.souschef

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.*
import kotlinx.datetime.Instant
import org.jetbrains.skia.Image
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.posix.memcpy

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    private val fileManager = NSFileManager.defaultManager

    private val documentsDirectory: String
        get() {
            val paths = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )
            return paths.first() as String
        }

    private val recipesDirectory: String
        get() {
            val dir = "$documentsDirectory/recipes"
            if (!fileManager.fileExistsAtPath(dir)) {
                fileManager.createDirectoryAtPath(dir, true, null, null)
            }
            return dir
        }

    private val stateFilePath: String
        get() = "$documentsDirectory/state.txt"

    override fun setAlarm(time: Instant?): Boolean {
        // TODO: Implement iOS alarm with UNUserNotificationCenter
        return false
    }

    override fun readState(): ByteArray? {
        val data = NSData.dataWithContentsOfFile(stateFilePath) ?: return null
        return data.toByteArray()
    }

    override fun writeState(data: ByteArray) {
        val nsData = data.toNSData()
        nsData.writeToFile(stateFilePath, true)
    }

    override fun toast(message: String) {
        // TODO: Implement iOS toast/alert
        println("Toast: $message")
    }

    override fun listUserRecipes(): List<String> {
        val contents = fileManager.contentsOfDirectoryAtPath(recipesDirectory, null) ?: return emptyList()
        return contents.mapNotNull { it as? String }
            .filter { it.endsWith(".cooklang") }
    }

    override fun readUserRecipe(filename: String): String? {
        val path = "$recipesDirectory/$filename"
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    }

    override fun writeUserRecipe(filename: String, content: String) {
        val path = "$recipesDirectory/$filename"
        (content as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
    }

    override fun deleteUserRecipe(filename: String): Boolean {
        val path = "$recipesDirectory/$filename"
        return fileManager.removeItemAtPath(path, null)
    }

    override fun shareRecipe(title: String, content: String) {
        // TODO: Implement iOS sharing with UIActivityViewController
    }

    override fun pickImage(onImagePicked: (ByteArray?) -> Unit) {
        // TODO: Implement iOS image picker with PHPickerViewController
        onImagePicked(null)
    }

    private fun getRecipeImagesDir(recipeId: String): String {
        val sanitizedId = recipeId.replace(Regex("[^a-zA-Z0-9-]"), "_")
        val dir = "$recipesDirectory/$sanitizedId.images"
        if (!fileManager.fileExistsAtPath(dir)) {
            fileManager.createDirectoryAtPath(dir, true, null, null)
        }
        return dir
    }

    override fun saveRecipeImage(recipeId: String, imageData: ByteArray): String? {
        return try {
            val imagesDir = getRecipeImagesDir(recipeId)
            val filename = "image-${NSDate().timeIntervalSince1970.toLong()}.jpg"
            val path = "$imagesDir/$filename"
            val nsData = imageData.toNSData()
            nsData.writeToFile(path, true)
            filename
        } catch (e: Exception) {
            null
        }
    }

    override fun getRecipeImages(recipeId: String): List<String> {
        val imagesDir = getRecipeImagesDir(recipeId)
        val contents = fileManager.contentsOfDirectoryAtPath(imagesDir, null) ?: return emptyList()
        return contents.mapNotNull { it as? String }
            .filter { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }
            .sorted()
    }

    override fun loadRecipeImage(recipeId: String, imageName: String): ByteArray? {
        val imagesDir = getRecipeImagesDir(recipeId)
        val path = "$imagesDir/$imageName"
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return data.toByteArray()
    }

    override fun deleteRecipeImage(recipeId: String, imageName: String): Boolean {
        val imagesDir = getRecipeImagesDir(recipeId)
        val path = "$imagesDir/$imageName"
        return fileManager.removeItemAtPath(path, null)
    }

    override fun deleteAllRecipeImages(recipeId: String): Boolean {
        val imagesDir = getRecipeImagesDir(recipeId)
        return fileManager.removeItemAtPath(imagesDir, null)
    }

    override fun moveRecipeImages(fromRecipeId: String, toRecipeId: String): Boolean {
        return try {
            val fromDir = getRecipeImagesDir(fromRecipeId)
            val toDir = getRecipeImagesDir(toRecipeId)
            val contents = fileManager.contentsOfDirectoryAtPath(fromDir, null) ?: return true
            contents.mapNotNull { it as? String }.forEach { filename ->
                val fromPath = "$fromDir/$filename"
                val toPath = "$toDir/$filename"
                fileManager.copyItemAtPath(fromPath, toPath, null)
            }
            fileManager.removeItemAtPath(fromDir, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}

// Extension functions for ByteArray <-> NSData conversion
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? {
    return try {
        val image = Image.makeFromEncoded(bytes)
        image.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}
