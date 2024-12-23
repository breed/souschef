package com.homeofcode.souschef

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.homeofcode.souschef.com.homeofcode.souschef.model.BakeModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


fun now(): Instant {
    // this is kind of gross and stupid, but i want local time, and Instant is the
    // only class that works well with Duration and formatter, so i trick Instant
    // into thinking the current time is UTC
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        .toInstant(TimeZone.UTC)
}

fun timeToUTC(time: Instant): Instant {
    return time.toLocalDateTime(TimeZone.UTC).toInstant(TimeZone.currentSystemDefault())
}

@Composable
@Preview
fun App(bake: BakeModel) {
    MaterialTheme {
        RecipeCard(
            bake
        )
    }
}


fun formatInstant(instant: Instant?): String =
    if (instant == null) "" else instant.toLocalDateTime(TimeZone.currentSystemDefault())
        .toJavaLocalDateTime().format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    )

@Composable
fun textWidth(text: String): Dp {
    val textMeasurer = rememberTextMeasurer()
    return with(LocalDensity.current) { textMeasurer.measure(text).size.width.toDp() }
}

var lastAlarmSet: Instant? = Instant.DISTANT_PAST

fun setAlarm(localAlarmTime: Instant?): Boolean {
    val alarmTime = if (localAlarmTime != null) timeToUTC(localAlarmTime) else null
    if (lastAlarmSet != alarmTime) {
        lastAlarmSet = alarmTime
        getPlatform().setAlarm(alarmTime)
    }
    return true
}
