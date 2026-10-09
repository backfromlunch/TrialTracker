package dev.traumatisedturkey.trialtracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "entries",
    primaryKeys = ["trialId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = TrialRecord::class,
            parentColumns = ["id"],
            childColumns = ["trialId"],
        ),
    ],
    indices = [Index("trialId")],
)
data class Entry(
    val trialId: String, // FK -> TrialRecord.id (currently always TrialRecord.ACTIVE_ID)
    val date: String, // ISO-8601 "yyyy-MM-dd", sortable as text
    val answersJson: String,
)
