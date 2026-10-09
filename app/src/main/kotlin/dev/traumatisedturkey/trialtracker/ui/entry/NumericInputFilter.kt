package dev.traumatisedturkey.trialtracker.ui.entry

import kotlin.text.iterator

/**
 * Numeric text-field input
 * character filtering, inclusive-range validation, and canonical-form normalization.
 */
object NumericInputFilter {

    fun filter(raw: String, allowDecimal: Boolean): String {
        val cleaned = raw.replace("\n", "")
        if (!allowDecimal) return cleaned.filter { it.isDigit() }

        val sb = StringBuilder()
        var seenDot = false
        for (c in cleaned) {
            when {
                c.isDigit() -> sb.append(c)

                c == '.' && !seenDot -> {
                    sb.append(c)
                    seenDot = true
                }
            }
        }
        return sb.toString()
    }

    /** Determine if the string can represent a number in the given range. */
    fun isValid(text: String, min: Double, max: Double): Boolean {
        val parsed = text.toDoubleOrNull() ?: return false
        return parsed in min..max
    }

    /** Canonical committed representation, e.g. "75.00" -> "75.0" for a float (Decimal) field. */
    fun normalize(text: String, allowDecimal: Boolean): String? {
        val parsed = text.toDoubleOrNull() ?: return null
        return if (allowDecimal) parsed.toFloat().toString() else parsed.toInt().toString()
    }
}
