package dev.traumatisedturkey.trialtracker.data

// Stored as plain String (== name), not via @TypeConverter.
// LIGHT is Settings' default.
enum class ThemeMode {
    LIGHT,
    DARK,
    COLOR_BLIND,
    ;

    companion object {
        // Falls back to LIGHT for unrecognized/corrupt stored values instead of crashing.
        fun fromStored(value: String): ThemeMode = entries.find { it.name == value } ?: LIGHT
    }
}
