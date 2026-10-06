package it.palsoftware.pastiera.core.suggestions

import java.util.Locale

/**
 * Gives suggestions the right capitalisation
 * from the pattern of the word being typed.
 */
object CasingHelper {

    private fun capitalizeFirstLetter(candidate: String): String {
        val idx = candidate.indexOfFirst { it.isLetter() }
        if (idx < 0) return candidate
        val first = candidate[idx]
        val cap = if (first.isLowerCase()) first.titlecase(Locale.getDefault()) else first.toString()
        return candidate.substring(0, idx) + cap + candidate.substring(idx + 1)
    }

    /**
     * Capitalises a suggestion to match the typed word's pattern.
     * 
     * @param candidate The suggested word (e.g. "Parenzo")
     * @param original The typed word (e.g. "parenz", "Parenz", "PARENZ")
     * @param forceLeadingCapital Forces a capital first letter (for auto-capitalisation)
     * @return The word, capitalised
     */
    fun applyCasing(
        candidate: String,
        original: String,
        forceLeadingCapital: Boolean = false
    ): String {
        if (candidate.isEmpty()) return candidate
        
        // The field forces capitalisation: title case
        if (forceLeadingCapital) {
            return capitalizeFirstLetter(candidate)
        }
        
        if (original.isEmpty()) return candidate
        
        // The capitalisation pattern, from letters only (apostrophes and punctuation ignored)
        val letters = original.filter { it.isLetter() }
        if (letters.isEmpty()) return candidate

        val allUpper = letters.length > 1 && letters.all { it.isUpperCase() }
        val allLower = letters.all { it.isLowerCase() }
        val firstLetter = letters.first()
        val restLetters = letters.drop(1)
        val firstUpper = firstLetter.isUpperCase()
        val restLower = restLetters.all { it.isLowerCase() }

        // A candidate with capitals, when the typed word isn't all caps (2+ capitals),
        // keeps the dictionary's casing.
        val candidateHasUpper = candidate.any { it.isUpperCase() }
        val candidateLettersUpperCount = candidate.count { it.isUpperCase() }
        if (!forceLeadingCapital && candidateHasUpper && candidateLettersUpperCount < 2) {
            return candidate
        }
        // All lowercase typed but the candidate has capitals (e.g. "mccartney" -> "McCartney"):
        // keep the candidate's casing.
        if (allLower && candidateHasUpper) {
            return candidate
        }
        
        return when {
            // Caso: PARENZ -> PARENZO (tutto maiuscolo)
            allUpper -> candidate.uppercase(Locale.getDefault())
            // Parenz -> Parenzo (capital first, the rest lowercase)
            firstUpper && restLower -> capitalizeFirstLetter(candidate)
            // Caso: parenz -> parenzo (tutto minuscolo)
            allLower -> candidate.lowercase(Locale.getDefault())
            // Otherwise use the suggestion as it is
            else -> candidate
        }
    }
}

