package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** No automatic Shift in scripts without capitals (palsoftware/pastiera#302). */
class CaselessAutoCapTest {
    @Test
    fun caselessScriptsNeverAutoShift() {
        listOf("th", "th_TH", "zh-CN", "ja", "ar", "he", "hi", "ka").forEach {
            assertTrue(it, AutoCapitalizeHelper.isCaselessLanguage(it))
        }
    }

    @Test
    fun casedScriptsKeepAutoCapitals() {
        listOf("en", "en_GB", "it", "de", "ru", "el", "hy", "vi", null).forEach {
            assertFalse("$it", AutoCapitalizeHelper.isCaselessLanguage(it))
        }
    }
}
