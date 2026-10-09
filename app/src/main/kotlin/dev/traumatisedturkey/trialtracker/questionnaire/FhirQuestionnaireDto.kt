package dev.traumatisedturkey.trialtracker.questionnaire

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * DTO mirroring the shape of a FHIR Questionnaire resource's envelope.
 *
 * This isn't a fully-typed structure:
 * - the envelope fields are fixed, but
 * - 'item' is a list whose entries each have their own shape, chosen at runtime by a "type" discriminator.
 * So 'item' is kept as a simple JsonElement.
 */
@Serializable
data class FhirQuestionnaireDto(
    val resourceType: String? = null,
    val id: String,
    val publisher: String? = null,
    val url: String,
    val version: String,
    val status: String,
    val title: String,
    val item: JsonElement,
)

private val itemJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

/**
 * Flattens the FHIR DTO into the existing Questionnaire domain type.
 * All the FHIR-specific knowledge (resourceType validation, item's polymorphic decode) lives here.
 */
fun FhirQuestionnaireDto.toQuestionnaire(): Questionnaire {
    if (resourceType != "Questionnaire") {
        throw SerializationException("Expected a top-level element named 'resourceType' to have value 'Questionnaire'")
    }
    return Questionnaire(
        id = id,
        publisher = publisher,
        url = url,
        version = version,
        status = status,
        title = title,
        item = itemJson.decodeFromJsonElement(item),
    )
}
