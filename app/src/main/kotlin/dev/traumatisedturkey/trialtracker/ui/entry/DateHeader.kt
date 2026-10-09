package dev.traumatisedturkey.trialtracker.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val dowFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE")

@Composable
internal fun DateHeader(
    date: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }

        Text(
            text = if (isToday) "Today" else date.format(dowFormatter),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        IconButton(
            onClick = onNext,
            enabled = !isToday,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
}
