package dev.traumatisedturkey.trialtracker.fixtures

import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest

val exampleServiceRequest = ServiceRequest(
    codeText = "Your Trial",
    period = 1,
    periodUnit = "d",
    serviceRequestSystem = "ssr1",
    serviceRequestValue = "srv1",
    requesterDisplay = "rd1",
)
