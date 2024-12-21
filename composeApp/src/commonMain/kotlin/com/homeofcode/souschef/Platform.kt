package com.homeofcode.souschef

import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant

interface Platform {
    val name: String
    // if time is null, the alarm will be unregistered
    fun setAlarm(time: Instant?, action: () -> Unit): Boolean
    fun toast(s: String)
}

@Composable
expect fun getPlatform(): Platform