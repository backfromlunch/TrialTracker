package dev.traumatisedturkey.trialtracker.export

import dev.traumatisedturkey.trialtracker.data.LiveTrial
import dev.traumatisedturkey.trialtracker.data.TrialDatabase
import java.time.OffsetDateTime

object TrialExportCoordinator {
    suspend fun buildCsvExport(db: TrialDatabase, trial: LiveTrial, authored: OffsetDateTime = OffsetDateTime.now()): String {
        val entries = db.entryDao().getAll(trial.id)
        return CsvExporter.buildCsv(trial.questionnaire, trial.serviceRequest, entries, authored)
    }

    suspend fun buildJsonExport(
        db: TrialDatabase,
        trial: LiveTrial,
        authored: OffsetDateTime = OffsetDateTime.now(),
    ): String {
        val entries = db.entryDao().getAll(trial.id)
        return JsonExporter.buildJson(
            questionnaire = trial.questionnaire,
            serviceRequest = trial.serviceRequest,
            entries = entries,
            authored = authored,
        )
    }
}
