package it.palsoftware.pastiera.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtraScreenTest {
    private val list = """
        RootTask id=1 bounds=[0,0][1080,1240] displayId=0 userId=0
         configuration={1.0 ?mcc0mnc}
          taskId=1: com.android.launcher3/.uioverrides.QuickstepLauncher bounds=[0,0][1080,1240] userId=0 visible=true
        RootTask id=42 bounds=[0,0][1080,1240] displayId=0 userId=0
          taskId=42: com.infoldgames.infinitynikkien/com.unity3d.player.UnityPlayerActivity bounds=[0,0][1080,1240] userId=0 visible=false
        RootTask id=57 bounds=[0,0][1080,1240] displayId=0 userId=0
          taskId=57: it.palsoftware.pastiera/.gaming.XboxPlayActivity bounds=[0,0][1080,1240] userId=0 visible=false
        RootTask id=58 bounds=[0,0][1080,1240] displayId=0 userId=0
          taskId=58: it.palsoftware.pastiera/.gaming.ExtraScreenActivity bounds=[0,0][1080,1240] userId=0 visible=true
    """.trimIndent()

    @Test
    fun findsTheAppsRootTask() {
        assertEquals(42, ExtraScreen.rootTaskIn(list, "com.infoldgames.infinitynikkien", null))
    }

    @Test
    fun findsOwnActivityByName() {
        assertEquals(57, ExtraScreen.rootTaskIn(list, "it.palsoftware.pastiera", "XboxPlayActivity"))
    }

    @Test
    fun noTaskWhenNotOpen() {
        assertNull(ExtraScreen.rootTaskIn(list, "org.dolphinemu.dolphinemu", null))
    }

    @Test
    fun tvDensity() {
        assertEquals(319, ExtraScreen.dpiFor(1080))
        assertEquals(213, ExtraScreen.dpiFor(720))
    }
}
