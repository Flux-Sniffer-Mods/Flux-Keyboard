package it.palsoftware.pastiera.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** What's new stays readable: valid JSON, every entry with text, times (when written) well formed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhatsNewFileTest {
    private val file = listOf("src/main/assets/fork/whats_new.json", "app/src/main/assets/fork/whats_new.json")
        .map(::File).first { it.exists() }

    @Test
    fun everyEntryHasTextAndAWellFormedTime() {
        val json = JSONObject(file.readText())
        for (key in listOf("highlights", "improvements", "bugFixes", "upstream")) {
            val entries = json.optJSONArray(key) ?: JSONArray()
            for (i in 0 until entries.length()) {
                val entry = entries.get(i)
                val text = (entry as? JSONObject)?.optString("text") ?: entry as? String
                assertTrue("$key[$i] has no text", !text.isNullOrBlank())
                val after = (entry as? JSONObject)?.optString("after").orEmpty()
                assertTrue("$key[$i] has a broken time: $after", after.isEmpty() || Regex("\\d{12}").matches(after))
            }
        }
    }
}
