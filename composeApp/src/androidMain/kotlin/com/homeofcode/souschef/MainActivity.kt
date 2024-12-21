package com.homeofcode.souschef

import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.Instant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var alarmTriggered: Instant? by mutableStateOf(Instant.DISTANT_PAST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentFilter = IntentFilter("com.homeofcode.souschef.ALARM")
        registerReceiver(AlarmReceiver, intentFilter);
        setContent {
            App(alarmTriggered)
        }

    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(AlarmReceiver)
        super.onDestroy()
    }

    fun alarmTriggered() {
        alarmTriggered = now()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    var alarmTriggered: Instant? by mutableStateOf(Instant.DISTANT_PAST)

    App(alarmTriggered)
}