package dev.traumatisedturkey.trialtracker.questionnaire

import kotlinx.serialization.Serializable

@Serializable
data class SingleItem(
    val linkId: String,
    val text: String,
    val type: String,
    val repeats: Boolean,
    val item: List<FieldDef>,
)

@Serializable
data class Questionnaire(
    val id: String,
    val publisher: String? = null, // This may not be fully compliant FHIR
    val url: String,
    val version: String,
    val status: String,
    val title: String,
    val item: List<SingleItem>,
) {
    // Questionnaire.version is non-nullable so this only guards against an empty string.
    fun questionnaireCanonical(): String = if (version.isBlank()) url else "$url|$version"

    fun itemGroup(): List<FieldDef> = item[0].item
}
