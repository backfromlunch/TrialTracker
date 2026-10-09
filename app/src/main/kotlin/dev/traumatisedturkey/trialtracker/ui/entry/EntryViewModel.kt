package dev.traumatisedturkey.trialtracker.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.data.EntryDao
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class EntryViewModel(
    private val dao: EntryDao,
    val questionnaire: Questionnaire,
    private val trialId: String,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    // Answers are stored as a JsonObject rather than a custom sealed AnswerValue type.

    private val _answers = MutableStateFlow<JsonObject>(JsonObject(emptyMap()))
    val answers: StateFlow<JsonObject> = _answers.asStateFlow()

    init {
        loadDate(_currentDate.value)
    }

    fun goToPreviousDay() {
        val newDate = _currentDate.value.minusDays(1)
        _currentDate.value = newDate
        loadDate(newDate)
    }

    fun goToNextDay() {
        val newDate = _currentDate.value.plusDays(1)
        if (newDate.isAfter(LocalDate.now())) return // do not allow future travel
        _currentDate.value = newDate
        loadDate(newDate)
    }

    fun goToDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        if (date == _currentDate.value) return
        _currentDate.value = date
        loadDate(date)
    }

    fun updateAnswer(
        fieldId: String,
        value: JsonElement,
    ) {
        // This is 'autosave'
        val updated = JsonObject(_answers.value.toMutableMap().apply { put(fieldId, value) })
        _answers.value = updated
        saveCurrentAnswers(updated)
    }

    private fun loadDate(date: LocalDate) {
        viewModelScope.launch {
            val key = date.format(dateFormatter)
            val entry = dao.getByDate(trialId, key)
            // Guard against out-of-order completion: if the user has navigated to a different
            // date while this query was in flight, this result is stale.
            if (date != _currentDate.value) return@launch
            _answers.value =
                if (entry != null) {
                    json.decodeFromString(JsonObject.serializer(), entry.answersJson)
                } else {
                    JsonObject(emptyMap())
                }
        }
    }

    private fun saveCurrentAnswers(answers: JsonObject) {
        viewModelScope.launch {
            val key = _currentDate.value.format(dateFormatter)
            val answersJson = json.encodeToString(JsonObject.serializer(), answers)
            dao.upsert(Entry(trialId = trialId, date = key, answersJson = answersJson))
        }
    }

    class Factory(
        private val dao: EntryDao,
        private val questionnaire: Questionnaire,
        private val trialId: String,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EntryViewModel(dao, questionnaire, trialId) as T
        }
    }
}
