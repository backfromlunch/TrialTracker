package dev.traumatisedturkey.trialtracker.servicerequest

import dev.traumatisedturkey.trialtracker.data.TrialDateRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.time.OffsetDateTime

/**
 * DTOs mirroring the shape of a FHIR ServiceRequest resource.
 *
 * Only the fields we actually consume are modelled; everything else in a real FHIR payload
 * (extension, focus, status, intent, authoredOn, etc.) is ignored via Json { ignoreUnknownKeys = true }.
 */
@Serializable
data class FhirServiceRequestDto(
    val resourceType: String? = null,
    val code: Code,
    val identifier: List<Identifier>,
    val requester: Requester,
    val occurrenceTiming: OccurrenceTiming,
) {
    @Serializable
    data class Code(
        val text: String,
    )

    @Serializable
    data class Identifier(
        val system: String,
        val value: String,
    )

    @Serializable
    data class Requester(
        val display: String,
    )

    @Serializable
    data class OccurrenceTiming(
        val repeat: Repeat,
    )

    @Serializable
    data class Repeat(
        val period: Int,
        val periodUnit: String,
        val boundsPeriod: BoundsPeriod = BoundsPeriod(),
    )

    @Serializable
    data class BoundsPeriod(
        // Raw ISO-8601 offset date-times (e.g. "2026-08-01T00:00:00+01:00"), as FHIR represents
        // Period.start/end.
        // Kept as String here and narrowed to LocalDate later.
        val start: String? = null,
        val end: String? = null,
    )
}

/**
 * Flattens the nested FHIR DTOinto ServiceRequest fields.
 * All the FHIR-specific knowledge (resourceType validation, identifier[0] selection,
 * OffsetDateTime -> LocalDate narrowing) lives here.
 */
fun FhirServiceRequestDto.toServiceRequest(): ServiceRequest {
    if (resourceType != "ServiceRequest") {
        throw SerializationException("Expected a top-level element named 'resourceType' to have value 'ServiceRequest'")
    }
    val firstIdentifier = identifier.first()
    val bounds = occurrenceTiming.repeat.boundsPeriod
    return ServiceRequest(
        codeText = code.text,
        period = occurrenceTiming.repeat.period,
        periodUnit = occurrenceTiming.repeat.periodUnit,
        trialDateRange = TrialDateRange(
            start = bounds.start?.let { OffsetDateTime.parse(it).toLocalDate() },
            end = bounds.end?.let { OffsetDateTime.parse(it).toLocalDate() },
        ),
        serviceRequestSystem = firstIdentifier.system,
        serviceRequestValue = firstIdentifier.value,
        requesterDisplay = requester.display,
    )
}
