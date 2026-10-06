package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.device.T2eCornerCalibration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Titan2EliteContourLedsTest {
    @Test
    fun ledsSitOnTheDisplayEdgeUnlessCalibratedInward() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(0f, T2eCornerCalibration.readSaved(context).ledOffsetPx)
        T2eCornerCalibration(ledOffsetPx = 10f).save(context)
        assertEquals(10f, T2eCornerCalibration.readSaved(context).ledOffsetPx)
    }
}
