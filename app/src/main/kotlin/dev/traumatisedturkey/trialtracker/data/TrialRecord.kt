package dev.traumatisedturkey.trialtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trials")
data class TrialRecord(
    // Fixed singleton value - there's only ever 0 or 1 rows - ACTIVE_ID.
    @PrimaryKey val id: String,

    // Both the below JSON fields:
    // - full raw configuration JSON, stored as-is.
    // - in principle should never be updated once set.
    //   + we have been allowing 'dev only' replacement.
    //   + if really needed, could be handled by creating a whole new row.
    //
    // Both are (at this level) independently nullable and only become non-null after an import.
    // NOTE: higher levels enforce a 'ServiceRequest before Questionnaire' ordering.
    val questionnaireJson: String? = null,
    val serviceRequestJson: String? = null,
    val importedAt: Long,
) {
    companion object {
        // The only id a TrialRecord row ever has.
        // importedAt (not id) could be used to distinguishe successive versions.
        const val ACTIVE_ID = "active"
    }
}
