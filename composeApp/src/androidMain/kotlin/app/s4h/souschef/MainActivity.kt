package app.s4h.souschef

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.s4h.souschef.model.BakeModel

const val TIMER_ACTION_INTENT: String = "app.s4h.souschef.ACTION_TRIGGER_ALARM"

class MainActivity : ComponentActivity() {
    companion object {
        var platform: AndroidPlatform? = null
        var currentBake: BakeModel? = null
    }

    // Image picker launcher - must be registered before onCreate
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        platform?.handlePickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platform = AndroidPlatform(this).also {
            it.imagePickerLauncher = imagePickerLauncher
        }

        // Handle incoming file intents on cold start
        handleIncomingIntent(intent)

        setContent {
            if (intent.action.equals(TIMER_ACTION_INTENT)) {
                currentBake?.let { bake ->
                    if (bake.alarmEnabled.value) {
                        val nextAlarm = bake.calculateNextAlarm(now())
                        if (nextAlarm != null) {
                            setAlarm(nextAlarm)
                        }
                    }
                }
                println("got timer!")
            }
            App(
                onBakeCreated = { bake ->
                    currentBake = bake
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                // Handle opening a .cooklang file directly
                intent.data?.let { uri ->
                    platform?.importRecipeFromUri(uri)
                }
            }
            Intent.ACTION_SEND -> {
                // Handle receiving a shared file
                if (intent.type == "text/plain" || intent.type == "application/octet-stream") {
                    val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    uri?.let {
                        platform?.importRecipeFromUri(it)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}