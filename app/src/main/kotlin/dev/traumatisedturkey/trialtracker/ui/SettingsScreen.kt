package dev.traumatisedturkey.trialtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.traumatisedturkey.trialtracker.data.ThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // One row per option, each in its own Card.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BooleanSettingRow(
                label = "Lock history",
                description = "Prevent editing of past days' entries.",
                checked = settings.lockHistory,
                onCheckedChange = viewModel::setLockHistory,
            )
            ThemeSettingRow(
                selected = ThemeMode.fromStored(settings.themeMode),
                onSelect = viewModel::setThemeMode,
            )
        }
    }
}

@Composable
private fun BooleanSettingRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

// Display *label* lives here (ui layer), not on ThemeMode itself (data layer).
private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.COLOR_BLIND -> "Colour-blind friendly"
    }

@Composable
private fun ThemeSettingRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(mode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                    Text(text = mode.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
