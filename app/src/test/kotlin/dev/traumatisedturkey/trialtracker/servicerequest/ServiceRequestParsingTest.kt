package dev.traumatisedturkey.trialtracker.servicerequest

import dev.traumatisedturkey.trialtracker.data.TrialDateRange
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class ServiceRequestParsingTest {

    private val repeatBaseFields =
        """
            "period": 1,
            "periodUnit": "d"
        """.trimIndent()

    private val expectedBase =
        ServiceRequest(
            codeText = "foo",
            period = 1,
            periodUnit = "d",
            trialDateRange = TrialDateRange(),
            serviceRequestSystem = "https://provider.example/fhir/identifier/service-request",
            serviceRequestValue = "sr-7f3a9c1e",
            requesterDisplay = "St Swithin's Hospital",
        )

    private fun serviceRequestJson(boundsPeriodJson: String? = null): String {
        val repeatFields =
            listOfNotNull(
                repeatBaseFields,
                boundsPeriodJson?.let { "\"boundsPeriod\": $it" },
            )
        val repeatJson = "{\n" + repeatFields.joinToString(",\n") + "\n}"
        return """
            {
                "resourceType": "ServiceRequest",
                "code": { "text": "foo" },
                "identifier": [
                    { "system": "https://provider.example/fhir/identifier/service-request", "value": "sr-7f3a9c1e" }
                ],
                "requester": { "display": "St Swithin's Hospital" },
                "occurrenceTiming": { "repeat": $repeatJson }
            }
        """.trimIndent()
    }

    @Test
    fun `parses a fully-populated service request`() {
        val serviceRequest =
            parseServiceRequest(
                serviceRequestJson("""{"start": "2026-01-01T00:00:00Z", "end": "2026-06-30T00:00:00Z"}"""),
            )
        assertEquals(
            expectedBase.copy(trialDateRange = TrialDateRange(start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2026, 6, 30))),
            serviceRequest,
        )
    }

    @Test
    fun `throws if a required field is missing`() {
        val obj = Json.parseToJsonElement(serviceRequestJson()).jsonObject
        val missingSubject = Json.encodeToString(JsonObject.serializer(), JsonObject(obj - "requester"))
        assertThrows(SerializationException::class.java) {
            parseServiceRequest(missingSubject)
        }
    }

    // The next two tests exercise FhirServiceRequestDto.toServiceRequest()'s resourceType check.

    @Test
    fun `throws if 'resourceType' is not present`() {
        val obj = Json.parseToJsonElement(serviceRequestJson()).jsonObject
        val missingResourceType = Json.encodeToString(JsonObject.serializer(), JsonObject(obj - "resourceType"))
        assertThrows(SerializationException::class.java) {
            parseServiceRequest(missingResourceType)
        }
    }

    @Test
    fun `throws if 'resourceType' does not have value 'ServiceRequest'`() {
        val obj = Json.parseToJsonElement(serviceRequestJson()).jsonObject
        val wrongResourceType =
            Json.encodeToString(JsonObject.serializer(), JsonObject(obj + ("resourceType" to JsonPrimitive("WrongType"))))
        assertThrows(SerializationException::class.java) {
            parseServiceRequest(wrongResourceType)
        }
    }

    @Test
    fun `trialDateRange defaults to both bounds null when boundsPeriod is entirely absent`() {
        val serviceRequest = parseServiceRequest(serviceRequestJson(boundsPeriodJson = null))
        assertEquals(TrialDateRange(start = null, end = null), serviceRequest.trialDateRange)
    }

    @Test
    fun `trialDateRange defaults to both bounds null when boundsPeriod is present but empty`() {
        val serviceRequest = parseServiceRequest(serviceRequestJson("""{}"""))
        assertEquals(TrialDateRange(start = null, end = null), serviceRequest.trialDateRange)
    }

    @Test
    fun `trialDateRange defaults end to null when only start is present`() {
        val serviceRequest = parseServiceRequest(serviceRequestJson("""{"start": "2026-01-01T00:00:00Z"}"""))
        assertEquals(TrialDateRange(start = LocalDate.of(2026, 1, 1), end = null), serviceRequest.trialDateRange)
    }

    @Test
    fun `trialDateRange defaults start to null when only end is present`() {
        val serviceRequest = parseServiceRequest(serviceRequestJson("""{"end": "2026-06-30T00:00:00Z"}"""))
        assertEquals(TrialDateRange(start = null, end = LocalDate.of(2026, 6, 30)), serviceRequest.trialDateRange)
    }

    @Test
    fun `trialDateRange bounds are null when explicitly set to json null`() {
        val serviceRequest = parseServiceRequest(serviceRequestJson("""{"start": null, "end": null}"""))
        assertEquals(TrialDateRange(start = null, end = null), serviceRequest.trialDateRange)
    }
}
