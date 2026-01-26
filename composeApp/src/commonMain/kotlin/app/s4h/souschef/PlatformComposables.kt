package app.s4h.souschef

import androidx.compose.runtime.Composable

// Platform-specific back handler
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

// Platform-specific time picker
@Composable
expect fun PlatformTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onTimePicked: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
)
