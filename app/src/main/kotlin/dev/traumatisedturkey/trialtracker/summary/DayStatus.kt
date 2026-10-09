package dev.traumatisedturkey.trialtracker.summary

import dev.traumatisedturkey.trialtracker.questionnaire.DefaultFieldOfInterestPolicy
import dev.traumatisedturkey.trialtracker.questionnaire.FieldDef
import dev.traumatisedturkey.trialtracker.questionnaire.FieldOfInterestPolicy
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class DayStatus {
    EMPTY, // no fields of interest completed
    PARTIAL, // some, but not all, fields of interest completed
    COMPLETE, // all fields of interest completed
}

/**
 * A field counts as "completed" if it has an answer AND that answer isn't a blank string.
 */
private fun isCompleted(
    answers: JsonObject,
    field: FieldDef,
): Boolean {
    val value = answers[field.linkId] as? JsonPrimitive ?: return false
    // Deliberately type-agnostic: contentOrNull returns the raw string content for
    // numbers and choice values too (e.g. "3", "mild"), so the same blank-check
    // works uniformly for integer/decimal/choice/text/date/period without a per-type case.
    return !value.contentOrNull.isNullOrBlank()
}

fun computeDayStatus(
    questionnaire: Questionnaire,
    answers: JsonObject,
    policy: FieldOfInterestPolicy = DefaultFieldOfInterestPolicy,
): DayStatus {
    val fieldsOfInterest = policy.fieldsOfInterest(questionnaire)
    // Edge case: could occur if every field is excluded from "of interest".
    if (fieldsOfInterest.isEmpty()) return DayStatus.EMPTY

    val completedCount = fieldsOfInterest.count { isCompleted(answers, it) }
    return when (completedCount) {
        0 -> DayStatus.EMPTY
        fieldsOfInterest.size -> DayStatus.COMPLETE
        else -> DayStatus.PARTIAL
    }
}
