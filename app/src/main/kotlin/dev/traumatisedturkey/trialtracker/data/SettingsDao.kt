package dev.traumatisedturkey.trialtracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Upsert
    suspend fun upsert(settings: Settings)

    // One-shot read.
    @Query("SELECT * FROM settings WHERE id = ${Settings.SINGLETON_ID}")
    suspend fun get(): Settings?

    // Preferred entry point for consumers.
    // Seeds the row with defaults on first call i.e. always returns a non-null Settings.
    suspend fun getOrCreateDefault(): Settings = get() ?: Settings().also { upsert(it) }

    // Live read.
    @Query("SELECT * FROM settings WHERE id = ${Settings.SINGLETON_ID}")
    fun observe(): Flow<Settings?>
}
