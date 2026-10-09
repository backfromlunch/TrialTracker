package dev.traumatisedturkey.trialtracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

// Help content lives in the 'assets' directory
private const val HELP_ASSET_FILE_NAME = "help.md"

@Composable
fun HelpScreen() {
    val context = LocalContext.current

    // The asset's contents are static - so read once not on every recomposition.
    val helpMarkdown = remember { loadMarkdownAsset(context, HELP_ASSET_FILE_NAME) }

    // Scale the markdown
    fun TextStyle.s() = copy(fontSize = fontSize * 0.85f, lineHeight = lineHeight * 0.85f)
    val compactTypography = MaterialTheme.typography.let { t ->
        markdownTypography(
            h1 = t.headlineLarge.s(),
            h2 = t.headlineMedium.s(),
            h3 = t.headlineSmall.s(),
            h4 = t.titleLarge.s(),
            h5 = t.titleMedium.s(),
            h6 = t.titleSmall.s(),
            text = t.bodyLarge.s(),
            paragraph = t.bodyMedium.s(),
            code = t.bodySmall.s(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Markdown(helpMarkdown, typography = compactTypography)
    }
}
