package dev.traumatisedturkey.trialtracker.trialsetup

import dev.traumatisedturkey.trialtracker.data.TrialDao
import dev.traumatisedturkey.trialtracker.data.TrialRecord
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaireJson
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTrialDao(initial: TrialRecord? = null) : TrialDao {
    var record: TrialRecord? = initial
        private set
    val upserts = mutableListOf<TrialRecord>()

    override suspend fun upsert(trial: TrialRecord) {
        upserts += trial
        record = trial
    }

    override suspend fun getActiveTrial(id: String): TrialRecord? = record
}

private val validServiceRequestJson =
    """
        {
            "resourceType": "ServiceRequest",
            "code": { "text": "foo" },
            "identifier": [ { "system": "sys1", "value": "sr1" } ],
            "requester": { "display": "Hospital" },
            "occurrenceTiming": { "repeat": { "period": 1, "periodUnit": "d" } }
        }
    """.trimIndent()

// Same shape as exampleQuestionnaireJson but with every field except 'notes' removed - a
// superset-violation (missing fields) against the already-imported exampleQuestionnaire.
private val incompatibleQuestionnaireJson =
    """
        {
          "resourceType": "Questionnaire",
          "title": "Test Trial",
          "version": "1.2.3",
          "id": "id001",
          "url": "https://foo.com/bar",
          "status": "active",
          "item": [
            { "linkId": "LogEntry", "text": "Log entry", "type": "group", "repeats": true,
              "item": [
                { "linkId": "notes", "type": "text" }
              ]
            }
          ]
        }
    """.trimIndent()

@OptIn(ExperimentalCoroutinesApi::class)
class TrialSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(dao: TrialDao): TrialSetupViewModel = TrialSetupViewModel(dao).also {
        dispatcher.scheduler.advanceUntilIdle()
    }

    // Drives the same two-step (read -> confirm) sequence the UI does, and checks isReplacing
    // along the way so every call site's expectation is explicit.
    private fun importServiceRequest(viewModel: TrialSetupViewModel, json: String, expectReplace: Boolean) {
        viewModel.onServiceRequestTextRead(json)
        val pending = viewModel.serviceRequestImportController.flow as ImportFlow.PendingConfirm<ServiceRequest>
        assertEquals(expectReplace, pending.isReplacing)
        viewModel.confirmServiceRequestImport(pending)
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun importQuestionnaire(viewModel: TrialSetupViewModel, json: String, expectReplace: Boolean) {
        viewModel.onQuestionnaireTextRead(json)
        val pending = viewModel.questionnaireImportController.flow as ImportFlow.PendingConfirm<Questionnaire>
        assertEquals(expectReplace, pending.isReplacing)
        viewModel.confirmQuestionnaireImport(pending)
        dispatcher.scheduler.advanceUntilIdle()
    }

    // --- startup: does activeTrial correctly require BOTH artifacts? ---

    @Test
    fun `clean install has no imported artifacts and no active trial`() = runTest {
        val viewModel = viewModel(FakeTrialDao())
        assertTrue(viewModel.startupCheckComplete)
        assertNull(viewModel.importedQuestionnaire)
        assertNull(viewModel.importedServiceRequest)
        assertNull(viewModel.activeTrial)
    }

    @Test
    fun `existing row with both artifacts yields a non-null activeTrial on startup`() = runTest {
        val record = TrialRecord(
            id = TrialRecord.ACTIVE_ID,
            questionnaireJson = exampleQuestionnaireJson,
            serviceRequestJson = validServiceRequestJson,
            importedAt = 1L,
        )
        val viewModel = viewModel(FakeTrialDao(record))
        assertNotNull(viewModel.importedQuestionnaire)
        assertNotNull(viewModel.importedServiceRequest)
        assertNotNull(viewModel.activeTrial)
    }

    @Test
    fun `existing row with only a questionnaire has no active trial`() = runTest {
        val record = TrialRecord(
            id = TrialRecord.ACTIVE_ID,
            questionnaireJson = exampleQuestionnaireJson,
            serviceRequestJson = null,
            importedAt = 1L,
        )
        val viewModel = viewModel(FakeTrialDao(record))
        assertNotNull(viewModel.importedQuestionnaire)
        assertNull(viewModel.importedServiceRequest)
        assertNull(viewModel.activeTrial)
    }

    @Test
    fun `existing row with only a service request has no active trial`() = runTest {
        val record = TrialRecord(
            id = TrialRecord.ACTIVE_ID,
            questionnaireJson = null,
            serviceRequestJson = validServiceRequestJson,
            importedAt = 1L,
        )
        val viewModel = viewModel(FakeTrialDao(record))
        assertNull(viewModel.importedQuestionnaire)
        assertNotNull(viewModel.importedServiceRequest)
        assertNull(viewModel.activeTrial)
    }

    // --- ordering guard ---

    @Test
    fun `questionnaire import is rejected before any service request is loaded`() = runTest {
        val viewModel = viewModel(FakeTrialDao())
        viewModel.onQuestionnaireTextRead(exampleQuestionnaireJson)
        assertTrue(viewModel.questionnaireImportController.flow is ImportFlow.Failed)
    }

    // --- service request import ---

    @Test
    fun `invalid service request json shows a failure instead of throwing`() = runTest {
        val viewModel = viewModel(FakeTrialDao())
        viewModel.onServiceRequestTextRead("not valid json")
        assertTrue(viewModel.serviceRequestImportController.flow is ImportFlow.Failed)
    }

    @Test
    fun `confirming the first service request creates a record with no questionnaire yet, and no active trial`() = runTest {
        val dao = FakeTrialDao()
        val viewModel = viewModel(dao)

        importServiceRequest(viewModel, validServiceRequestJson, expectReplace = false)

        assertEquals(1, dao.upserts.size)
        assertNull(dao.upserts.single().questionnaireJson)
        assertNotNull(viewModel.importedServiceRequest)
        assertNull(viewModel.activeTrial)
        assertFalse(viewModel.showConfigCompleteDialog)
    }

    // --- questionnaire import, given a service request already exists ---

    @Test
    fun `first questionnaire import after a service request completes the trial and fires the dialog once`() = runTest {
        val dao = FakeTrialDao()
        val viewModel = viewModel(dao)
        importServiceRequest(viewModel, validServiceRequestJson, expectReplace = false)

        importQuestionnaire(viewModel, exampleQuestionnaireJson, expectReplace = false)

        assertNotNull(viewModel.activeTrial)
        assertTrue(viewModel.showConfigCompleteDialog)
        // The saved record carries the service request over unchanged, alongside the new questionnaire.
        assertEquals(validServiceRequestJson, dao.record?.serviceRequestJson)
    }

    @Test
    fun `dismissConfigCompleteDialog clears the flag`() = runTest {
        val viewModel = viewModel(FakeTrialDao())
        importServiceRequest(viewModel, validServiceRequestJson, expectReplace = false)
        importQuestionnaire(viewModel, exampleQuestionnaireJson, expectReplace = false)
        assertTrue(viewModel.showConfigCompleteDialog)

        viewModel.dismissConfigCompleteDialog()

        assertFalse(viewModel.showConfigCompleteDialog)
    }

    @Test
    fun `replacing with an identical questionnaire is compatible and flagged as a replace`() = runTest {
        val viewModel = viewModel(FakeTrialDao())
        importServiceRequest(viewModel, validServiceRequestJson, expectReplace = false)
        importQuestionnaire(viewModel, exampleQuestionnaireJson, expectReplace = false)

        viewModel.onQuestionnaireTextRead(exampleQuestionnaireJson)

        val flow = viewModel.questionnaireImportController.flow
        assertTrue(flow is ImportFlow.PendingConfirm<*>)
        assertTrue((flow as ImportFlow.PendingConfirm<*>).isReplacing)
    }

    @Test
    fun `replacing with a questionnaire missing an existing field is rejected before confirmation`() = runTest {
        val dao = FakeTrialDao()
        val viewModel = viewModel(dao)
        importServiceRequest(viewModel, validServiceRequestJson, expectReplace = false)
        importQuestionnaire(viewModel, exampleQuestionnaireJson, expectReplace = false)

        viewModel.onQuestionnaireTextRead(incompatibleQuestionnaireJson)

        val flow = viewModel.questionnaireImportController.flow
        assertTrue(flow is ImportFlow.Failed)
        assertTrue((flow as ImportFlow.Failed).message.contains("sleep"))
        // Rejected before persistence - the original questionnaire is untouched.
        assertEquals(exampleQuestionnaireJson, dao.record?.questionnaireJson)
    }

    @Test
    fun `invalid questionnaire json shows a failure instead of throwing`() = runTest {
        val viewModel = viewModel(FakeTrialDao())
        importServiceRequest(viewModel, validServiceRequestJson, expectReplace = false)

        viewModel.onQuestionnaireTextRead("not valid json")

        assertTrue(viewModel.questionnaireImportController.flow is ImportFlow.Failed)
    }

    @Test
    fun `replacing the service request after setup is complete does not refire the completion dialog`() = runTest {
        val dao = FakeTrialDao()
        val viewModel = viewModel(dao)
        importServiceRequest(viewModel, validServiceRequestJson, expectReplace = false)
        importQuestionnaire(viewModel, exampleQuestionnaireJson, expectReplace = false)
        viewModel.dismissConfigCompleteDialog()

        importServiceRequest(viewModel, validServiceRequestJson, expectReplace = true)

        assertNotNull(viewModel.activeTrial)
        assertFalse(viewModel.showConfigCompleteDialog)
        // The questionnaire carried over unchanged.
        assertEquals(exampleQuestionnaireJson, dao.record?.questionnaireJson)
    }
}
