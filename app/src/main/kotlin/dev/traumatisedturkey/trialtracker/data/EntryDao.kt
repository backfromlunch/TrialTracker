package dev.traumatisedturkey.trialtracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Upsert
    suspend fun upsert(entry: Entry)

    @Query("SELECT * FROM entries WHERE trialId = :trialId AND date = :date")
    suspend fun getByDate(
        trialId: String,
        date: String,
    ): Entry?

    @Query("SELECT * FROM entries WHERE trialId = :trialId AND date = :date")
    fun observeByDate(
        trialId: String,
        date: String,
    ): Flow<Entry?>

    @Query("SELECT * FROM entries WHERE trialId = :trialId ORDER BY date ASC")
    suspend fun getAll(trialId: String): List<Entry>

    // Get min/max dates
    @Query("SELECT MIN(date) as minDate, MAX(date) as maxDate FROM entries WHERE trialId = :trialId")
    suspend fun getDateBounds(trialId: String): DateBounds
}

// Both fields are nullable because MIN/MAX over zero rows returns NULL, not an exception
data class DateBounds(
    val minDate: String?,
    val maxDate: String?,
)
