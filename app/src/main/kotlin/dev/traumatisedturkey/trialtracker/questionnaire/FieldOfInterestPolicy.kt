package dev.traumatisedturkey.trialtracker.questionnaire

/**
 * Currently "of interest" is a hardcoded exclusion (everything except Comments).
 * A future, enhanced serviceRequest could declare this explicitly per-field (e.g. an "optional": true flag).
 */
fun interface FieldOfInterestPolicy {
    fun fieldsOfInterest(questionnaire: Questionnaire): List<FieldDef>
}

object DefaultFieldOfInterestPolicy : FieldOfInterestPolicy {
    // Default policy: every field is of interest except the one literally named "Comments".
    private const val EXCLUDED_FIELD_ID = "Comments"

    override fun fieldsOfInterest(questionnaire: Questionnaire): List<FieldDef> = questionnaire.itemGroup().filter { it.linkId != EXCLUDED_FIELD_ID }
}
