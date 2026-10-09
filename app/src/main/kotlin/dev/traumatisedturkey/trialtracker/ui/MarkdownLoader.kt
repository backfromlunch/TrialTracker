package dev.traumatisedturkey.trialtracker.ui

import android.content.Context
import java.io.IOException

fun loadMarkdownAsset(
    context: Context,
    fileName: String,
): String = try {
    context.assets.open(fileName).bufferedReader().use { it.readText() }
} catch (e: IOException) {
    // Don't be silent about errors
    error("Could not read markdown asset '$fileName': ${e.message}")
}
