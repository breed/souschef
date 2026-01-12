package com.homeofcode.souschef

import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import java.io.InputStream
import java.io.OutputStream

interface Platform {
    val name: String
    // if time is null, the alarm will be unregistered
    fun setAlarm(time: Instant?): Boolean
    fun readState(): InputStream
    fun writeState(): OutputStream
    fun toast(message: String)

    // Recipe file operations
    fun listUserRecipes(): List<String>
    fun readUserRecipe(filename: String): String?
    fun writeUserRecipe(filename: String, content: String)
    fun deleteUserRecipe(filename: String): Boolean
}

expect fun getPlatform(): Platform