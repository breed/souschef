package com.homeofcode.souschef

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.homeofcode.souschef.com.homeofcode.souschef.model.BakeModel

const val TIMER_ACTION_INTENT: String = "com.homeofcode.souschef.ACTION_TRIGGER_ALARM"

class MainActivity : ComponentActivity() {
    companion object {
        var platform: AndroidPlatform? = null
    }
    var bake: BakeModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platform = AndroidPlatform(this)
        setContent {
            if (bake == null) bake = BakeModel()
            if (intent.action.equals(TIMER_ACTION_INTENT)) {
                if (bake?.alarmEnabled?.value ?: false) {
                    val nextAlarm = bake?.calculateNextAlarm(now())
                    if (nextAlarm != null) {
                        setAlarm(nextAlarm)
                    }
                }
                println("got timer!")
            }
            App(bake!!)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val bake = BakeModel()
    App(bake)
}