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
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.datetime.Instant
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
}

actual fun getPlatform(): Platform = MainActivity.platform!!