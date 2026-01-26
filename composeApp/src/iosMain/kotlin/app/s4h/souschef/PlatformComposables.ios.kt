package app.s4h.souschef

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back navigation differently (swipe gestures handled by system)
    // No-op for now
}

@Composable
actual fun PlatformTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onTimePicked: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // TODO: Implement iOS time picker using UIDatePicker
    // For now, just dismiss
    onDismiss()
}
