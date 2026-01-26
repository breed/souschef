package app.s4h.souschef

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun PlatformTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onTimePicked: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dialog = TimePickerDialog(
        context,
        { _, hour, minute -> onTimePicked(hour, minute) },
        initialHour,
        initialMinute,
        false
    )
    dialog.setOnDismissListener { onDismiss() }
    dialog.show()
}
