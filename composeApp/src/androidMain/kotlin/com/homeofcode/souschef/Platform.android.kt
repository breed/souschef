package com.homeofcode.souschef

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.datetime.Instant

object AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activity = context as MainActivity
        activity.alarmTriggered()
    }
}

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override fun setAlarm(time: Instant?, action: () -> Unit): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val intent = Intent("com.homeofcode.souschef.ALARM")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (time == null) {
            alarmManager.cancel(pendingIntent)
            return true
        }
        toast("Alarm set for ${time.toEpochMilliseconds() - System.currentTimeMillis()}")
        val triggerAtMillis = time.toEpochMilliseconds()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        return true
    }

    override fun toast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
actual fun getPlatform(): Platform = AndroidPlatform(LocalContext.current)