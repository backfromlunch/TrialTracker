package dev.traumatisedturkey.trialtracker.servicerequest

import dev.traumatisedturkey.trialtracker.data.TrialDateRange
import kotlinx.serialization.Serializable

@Serializable
data class ServiceRequest(
    val codeText: String,
    val period: Int,
    val periodUnit: String,
    val trialDateRange: TrialDateRange = TrialDateRange(),
    val serviceRequestSystem: String,
    val serviceRequestValue: String,

    val requesterDisplay: String,
)
