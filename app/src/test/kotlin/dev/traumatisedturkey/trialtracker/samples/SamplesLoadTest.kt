package dev.traumatisedturkey.trialtracker.samples

import dev.traumatisedturkey.trialtracker.questionnaire.parseQuestionnaire
import dev.traumatisedturkey.trialtracker.servicerequest.parseServiceRequest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Loads every *.json file in the top-level samples/ directory and asserts it parses without
 * throwing, via whichever loader its filename indicates. Routing is by filename.
 */
@RunWith(Parameterized::class)
class SamplesLoadTest(
    @Suppress("unused") private val label: String,
    private val file: File,
) {
    companion object {
        private val questionnaireNamePattern = Regex("(?i).*questionnaire.*\\.json")
        private val serviceRequestNamePattern = Regex("(?i).*servicerequest.*\\.json")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun samples(): List<Array<Any>> {
            val samplesDir = File(System.getProperty("samplesDir") ?: error("samplesDir system property not set"))
            assertTrue("samples directory not found: $samplesDir", samplesDir.isDirectory)

            val files = samplesDir.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }.orEmpty().toList()
            assertTrue("no *.json sample files found in $samplesDir", files.isNotEmpty())
            return files.map { arrayOf(it.name, it) }
        }
    }

    @Test
    fun `sample loads without throwing`() {
        val name = file.name
        val text = file.readText()
        when {
            questionnaireNamePattern.matches(name) -> parseQuestionnaire(text)

            serviceRequestNamePattern.matches(name) -> parseServiceRequest(text)

            else -> fail(
                "Sample file '$name' matches neither the questionnaire nor servicerequest naming " +
                    "pattern - rename it to include 'questionnaire' or 'servicerequest', or update " +
                    "SamplesLoadTest's patterns if a new sample kind is intentional.",
            )
        }
    }
}
