package dev.traumatisedturkey.trialtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * This table uses typed columns (contrast with Trial and Entry tables which use JSON blobs).
 *
 * We can grow this by adding a new typed column with a default value
 * Can use Room's @AutoMigration.
 *
 * Always exactly one row, with a fixed id of 1 - see SettingsDao/TrialDatabase seeding.
 */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lockHistory: Boolean = true,
    // @ColumnInfo(defaultValue=...) is required alongside the Kotlin default below: AutoMigration
    // reads column defaults from schema metadata, not Kotlin defaults - omitting this would make
    // AutoMigration silently skip the column on a future schema bump -> crash.
    @ColumnInfo(defaultValue = "LIGHT")
    val themeMode: String = ThemeMode.LIGHT.name,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
