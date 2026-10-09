package it.palsoftware.pastiera.gaming

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfilesTest {
    private val profile = GameProfiles.newProfile("Wind Waker", GameStyle.GAMEPAD, setOf("org.dolphinemu.dolphinemu"))

    @Test
    fun dolphinProfileBindsTheKeyboardsKeysByName() {
        val ini = NativeProfiles.dolphinProfile(profile, "Titan Keyboard")
        assertTrue(ini.startsWith("[Profile]\nDevice = Android/0/Titan Keyboard\n"))
        // L is the bottom face button (Xbox A): GameCube A
        assertTrue(ini, ini.contains("Buttons/A = `L`"))
        assertTrue(ini, ini.contains("D-Pad/Up = `W`"))
    }

    @Test
    fun ppssppMappingReplacesTheGamesOwn() {
        val mapping = NativeProfiles.pspMapping(profile)
        assertTrue(mapping, mapping.contains("Cross = 1-${KeyEvent.KEYCODE_L}"))
        val ini = "[General]\nA = 1\n[ControlMapping]\nCross = 10-96\n[Graphics]\nB = 2\n"
        val out = NativeProfiles.withMapping(ini, mapping)
        assertTrue(out, out.contains("[General]") && out.contains("[Graphics]") && !out.contains("10-96"))
        assertTrue(out.endsWith(mapping))
    }

    @Test
    fun runningGamesAreToldByTheirFile() {
        val dump = """
            topResumedActivity=ActivityRecord{abc123 u0 org.ppsspp.ppsspp/.PpssppActivity t12}
            * Hist  #0: ActivityRecord{abc123 u0 org.ppsspp.ppsspp/.PpssppActivity t12}
                intent={act=android.intent.action.VIEW dat=content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fpsp/document/primary%3AROMs%2Fpsp%2FPatapon%202.cso flg=0x10000000 cmp=org.ppsspp.ppsspp/.PpssppActivity}
            * Hist  #1: ActivityRecord{def456 u0 other/.Main t3}
        """.trimIndent()
        val data = GameDetect.resumedData(dump)!!
        assertEquals("patapon2", GameDetect.key(data))
        val patapon = GameProfiles.newProfile("Patapon 2", GameStyle.GAMEPAD, setOf("org.ppsspp.ppsspp"))
        assertTrue(GameDetect.matches(patapon, "patapon2"))
    }
}
