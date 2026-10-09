package dev.traumatisedturkey.trialtracker.export

/**
 * The two exportable file formats and matching file extensions.
 */
enum class ExportFormat(val extension: String) {
    JSON("json"),
    CSV("csv"),
}

fun formatForFilename(name: String): ExportFormat? = ExportFormat.entries.firstOrNull { name.endsWith(".${it.extension}", ignoreCase = true) }
