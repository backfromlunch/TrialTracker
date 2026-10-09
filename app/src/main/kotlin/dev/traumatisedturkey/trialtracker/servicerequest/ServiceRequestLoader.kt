package dev.traumatisedturkey.trialtracker.servicerequest

import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * Parses a ServiceRequest from a raw FHIR ServiceRequest JSON string, whatever its origin:
 * an imported file, or a Trial row's stored serviceRequest Json column.
 *
 * Decodes into FhirServiceRequestDto first, then flattens via toServiceRequest().
 */
fun parseServiceRequest(serviceRequestJson: String): ServiceRequest = json.decodeFromString<FhirServiceRequestDto>(serviceRequestJson).toServiceRequest()
