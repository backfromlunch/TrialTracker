package dev.traumatisedturkey.trialtracker.questionnaire

// Result of comparing two questionnaires for "safe to replace" purposes.
sealed class QuestionnaireCompatibility {
    object Compatible : QuestionnaireCompatibility()

    data class Incompatible(val reasons: List<String>) : QuestionnaireCompatibility()
}

/**
 * Checks whether `candidate` is a strict superset of `existing`, i.e. safe to replace `existing`
 * without invalidating any Entry rows recorded against `existing`.
 *
 * Top-level checks:
 *   - id: if `existing.id` is present, `candidate.id` must match it exactly.
 *   - version: unconstrained.
 *   - title: must be non-blank. This is a sanity check only, not a "did it change" check.
 *
 * Field-level, "strict superset" means:
 *   - every field in `existing` must appear in `candidate`, completely unchanged
 *   - `candidate` may additionally contain brand-new fields
 *
 * Deliberately conservative: no narrowing OR widening of an existing field is
 * allowed, only leaving it alone or adding new ones. Existing-field order
 * doesn't matter, BUT a field's own internal ordering
 * (e.g. ChoiceField.choices) does, since it's part of that field's equality.
 */
fun checkSuperset(
    existing: Questionnaire,
    candidate: Questionnaire,
): QuestionnaireCompatibility {
    val reasons = mutableListOf<String>()

    val existingId = existing.id
    if (candidate.id != existingId) {
        reasons += "Questionnaire id changed from '$existingId' to '${candidate.id}'."
    }

    if (candidate.title.isBlank()) {
        reasons += "New questionnaire's title is blank."
    }

    if (candidate.version.isBlank()) {
        reasons += "New questionnaire's version is blank."
    }

    val candidateFieldsById: Map<String, FieldDef> = candidate.itemGroup().associateBy { it.linkId }
    for (existingField in existing.itemGroup()) {
        val candidateField = candidateFieldsById[existingField.linkId]
        when {
            candidateField == null ->
                reasons += "Field '${existingField.linkId}' is missing from the new questionnaire."

            candidateField != existingField ->
                reasons += "Field '${existingField.linkId}' changed (was $existingField, now $candidateField)."
        }
    }

    return if (reasons.isEmpty()) QuestionnaireCompatibility.Compatible else QuestionnaireCompatibility.Incompatible(reasons)
}
