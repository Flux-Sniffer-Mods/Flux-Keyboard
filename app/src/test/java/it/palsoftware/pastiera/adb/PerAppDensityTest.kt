package it.palsoftware.pastiera.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PerAppDensityTest {
    @Test
    fun readsTheResumedAppFromTheActivityDump() {
        val dump = """
              topResumedActivity=ActivityRecord{1a2b3c u0 bitpit.launcher/.ui.HomeActivity t5}
                mResumedActivity: ActivityRecord{1a2b3c u0 bitpit.launcher/.ui.HomeActivity t5}
        """.trimIndent()
        assertEquals("bitpit.launcher", PerAppDensity.parseResumed(dump))
        assertEquals(
            "com.google.android.calendar",
            PerAppDensity.parseResumed("  mResumedActivity: ActivityRecord{9f u0 com.google.android.calendar/com.android.calendar.AllInOneActivity t31}")
        )
        assertNull(PerAppDensity.parseResumed("nothing resumed"))
    }
}
