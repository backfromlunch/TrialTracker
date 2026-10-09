package dev.traumatisedturkey.trialtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.traumatisedturkey.trialtracker.data.Settings
import dev.traumatisedturkey.trialtracker.data.SettingsDao
import dev.traumatisedturkey.trialtracker.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dao: SettingsDao,
) : ViewModel() {
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = dao.getOrCreateDefault()
        }
    }

    private fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        viewModelScope.launch { dao.upsert(updated) }
    }

    fun setLockHistory(value: Boolean) = update { it.copy(lockHistory = value) }

    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value.name) }

    class Factory(
        private val dao: SettingsDao,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(dao) as T
        }
    }
}
