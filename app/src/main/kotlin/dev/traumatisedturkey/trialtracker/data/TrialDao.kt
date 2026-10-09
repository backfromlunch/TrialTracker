package dev.traumatisedturkey.trialtracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TrialDao {
    // 'upsert' allows for 'replace (existing)' and 'insert (new)'.
    @Upsert
    suspend fun upsert(trial: TrialRecord)

    // There's currently only ever 0 or 1 rows (see TrialRecord.ACTIVE_ID).
    @Query("SELECT * FROM trials WHERE id = :id LIMIT 1")
    suspend fun getActiveTrial(id: String = TrialRecord.ACTIVE_ID): TrialRecord?
}
