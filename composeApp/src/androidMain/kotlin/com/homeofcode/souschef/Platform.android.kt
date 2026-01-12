package com.homeofcode.souschef

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.datetime.Instant
import java.io.ByteArrayOutputStream
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "alarm_channel"
        val channelName = "Alarm Notifications"

        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = "Channel for alarm notifications"
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Create an intent to open MainActivity when the notification is clicked
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            setAction(TIMER_ACTION_INTENT)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.sous_icon)
            .setContentTitle("Sous Chef Step Finished")
            .setContentText("Ready for the next step!")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pendingIntent)
            .setAutoCancel(true).setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .build()

        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            notify(1, notification)
        }
    }
}

class AndroidPlatform(private val mainActivity: MainActivity) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    // Image picker callback
    var imagePickerCallback: ((ByteArray?) -> Unit)? = null

    // Image picker launcher - must be registered in MainActivity
    lateinit var imagePickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    override fun setAlarm(time: Instant?): Boolean {
        if (mainActivity.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            mainActivity.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 666)
        }

        val alarmManager = mainActivity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val intent = Intent(mainActivity, AlarmReceiver::class.java).apply {
            setAction(TIMER_ACTION_INTENT)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            mainActivity, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (time == null) {
            alarmManager.cancel(pendingIntent)
            return true
        }
        val localTime = time.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

        toast("Alarm set for $localTime")
        val triggerAtMillis = time.toEpochMilliseconds()
        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
        )
        return true
    }

    override fun readState(): InputStream {
        return Files.newInputStream(mainActivity.dataDir.toPath().resolve("state.txt"))
    }

    override fun writeState(): OutputStream {
        return Files.newOutputStream(
            mainActivity.dataDir.toPath().resolve("state.txt"),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    override fun toast(message: String) {
        android.widget.Toast.makeText(mainActivity, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private val recipesDir: java.io.File
        get() = java.io.File(mainActivity.dataDir, "recipes").also { it.mkdirs() }

    override fun listUserRecipes(): List<String> {
        return recipesDir.listFiles()
            ?.filter { it.extension == "cooklang" }
            ?.map { it.name }
            ?: emptyList()
    }

    override fun readUserRecipe(filename: String): String? {
        val file = java.io.File(recipesDir, filename)
        return if (file.exists()) file.readText() else null
    }

    override fun writeUserRecipe(filename: String, content: String) {
        val file = java.io.File(recipesDir, filename)
        file.writeText(content)
    }

    override fun deleteUserRecipe(filename: String): Boolean {
        val file = java.io.File(recipesDir, filename)
        return file.delete()
    }

    override fun shareRecipe(title: String, content: String) {
        // Write content to a temp file in cache directory
        val sharedDir = java.io.File(mainActivity.cacheDir, "shared_recipes").also { it.mkdirs() }
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9\\s-]"), "").replace(" ", "-")
        val tempFile = java.io.File(sharedDir, "$sanitizedTitle.cooklang")
        tempFile.writeText(content)

        // Get content URI via FileProvider
        val contentUri: Uri = FileProvider.getUriForFile(
            mainActivity,
            "${mainActivity.packageName}.fileprovider",
            tempFile
        )

        // Create share intent
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "$title.cooklang")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        mainActivity.startActivity(Intent.createChooser(shareIntent, "Share Recipe"))
    }

    fun importRecipeFromUri(uri: Uri): Boolean {
        return try {
            val inputStream = mainActivity.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val content = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                // Extract filename from URI or generate one
                val filename = uri.lastPathSegment?.let {
                    if (it.endsWith(".cooklang")) it else "$it.cooklang"
                } ?: "imported-${System.currentTimeMillis()}.cooklang"

                // Use RecipeRegistry to add the recipe
                RecipeRegistry.addUserRecipe(
                    RecipeRegistry.generateUniqueFilename(filename.removeSuffix(".cooklang")),
                    content
                )
                toast("Recipe imported successfully!")
                true
            } else {
                toast("Could not read file")
                false
            }
        } catch (e: Exception) {
            toast("Failed to import recipe: ${e.message}")
            false
        }
    }

    // Recipe images directory
    private fun getRecipeImagesDir(recipeId: String): java.io.File {
        val sanitizedId = recipeId.replace(Regex("[^a-zA-Z0-9-]"), "_")
        return java.io.File(recipesDir, "$sanitizedId.images").also { it.mkdirs() }
    }

    override fun pickImage(onImagePicked: (ByteArray?) -> Unit) {
        imagePickerCallback = onImagePicked
        imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun handlePickedImage(uri: Uri?) {
        val callback = imagePickerCallback
        imagePickerCallback = null

        if (uri == null) {
            callback?.invoke(null)
            return
        }

        try {
            val inputStream = mainActivity.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                // Decode and compress the image
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap != null) {
                    // Scale down if too large (max 1200px on longest side)
                    val maxSize = 1200
                    val scale = if (originalBitmap.width > originalBitmap.height) {
                        maxSize.toFloat() / originalBitmap.width
                    } else {
                        maxSize.toFloat() / originalBitmap.height
                    }

                    val scaledBitmap = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            originalBitmap,
                            (originalBitmap.width * scale).toInt(),
                            (originalBitmap.height * scale).toInt(),
                            true
                        )
                    } else {
                        originalBitmap
                    }

                    // Compress to JPEG
                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    callback?.invoke(outputStream.toByteArray())

                    if (scaledBitmap != originalBitmap) {
                        scaledBitmap.recycle()
                    }
                    originalBitmap.recycle()
                } else {
                    callback?.invoke(null)
                }
            } else {
                callback?.invoke(null)
            }
        } catch (e: Exception) {
            toast("Failed to load image: ${e.message}")
            callback?.invoke(null)
        }
    }

    override fun saveRecipeImage(recipeId: String, imageData: ByteArray): String? {
        return try {
            val imagesDir = getRecipeImagesDir(recipeId)
            val filename = "image-${System.currentTimeMillis()}.jpg"
            val file = java.io.File(imagesDir, filename)
            file.writeBytes(imageData)
            filename
        } catch (e: Exception) {
            toast("Failed to save image: ${e.message}")
            null
        }
    }

    override fun getRecipeImages(recipeId: String): List<String> {
        val imagesDir = getRecipeImagesDir(recipeId)
        return imagesDir.listFiles()
            ?.filter { it.extension == "jpg" || it.extension == "jpeg" || it.extension == "png" }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    override fun loadRecipeImage(recipeId: String, imageName: String): ByteArray? {
        return try {
            val imagesDir = getRecipeImagesDir(recipeId)
            val file = java.io.File(imagesDir, imageName)
            if (file.exists()) file.readBytes() else null
        } catch (e: Exception) {
            null
        }
    }

    override fun deleteRecipeImage(recipeId: String, imageName: String): Boolean {
        return try {
            val imagesDir = getRecipeImagesDir(recipeId)
            val file = java.io.File(imagesDir, imageName)
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    override fun deleteAllRecipeImages(recipeId: String): Boolean {
        return try {
            val imagesDir = getRecipeImagesDir(recipeId)
            imagesDir.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    override fun moveRecipeImages(fromRecipeId: String, toRecipeId: String): Boolean {
        return try {
            val fromDir = getRecipeImagesDir(fromRecipeId)
            val toDir = getRecipeImagesDir(toRecipeId)
            if (fromDir.exists() && fromDir.listFiles()?.isNotEmpty() == true) {
                fromDir.listFiles()?.forEach { file ->
                    file.copyTo(java.io.File(toDir, file.name), overwrite = true)
                }
                fromDir.deleteRecursively()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

actual fun getPlatform(): Platform = MainActivity.platform!!

actual fun decodeImageBytes(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}