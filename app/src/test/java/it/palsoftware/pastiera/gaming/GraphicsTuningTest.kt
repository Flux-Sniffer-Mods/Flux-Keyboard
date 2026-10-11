package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphicsTuningTest {
    private val mali = GraphicsTuning.Phone(GraphicsTuning.Gpu.MALI, vulkan = true)

    @Test
    fun theGpuIsKnownByItsDriversName() {
        assertEquals(GraphicsTuning.Gpu.MALI, GraphicsTuning.gpuOf("Mali-G615 MC2"))
        assertEquals(GraphicsTuning.Gpu.ADRENO, GraphicsTuning.gpuOf("Adreno (TM) 740"))
        assertEquals(GraphicsTuning.Gpu.POWERVR, GraphicsTuning.gpuOf("PowerVR Rogue GE8320"))
        assertEquals(false, GraphicsTuning.Phone(GraphicsTuning.Gpu.POWERVR, vulkan = true).useVulkan)
    }

    @Test
    fun dolphinGetsVulkanAndLightShadersOnMali() {
        val (core, gfx) = GraphicsTuning.dolphin("[Core]\nCPUCore = 4\n", "[Settings]\nInternalResolution = 3\n", mali)
        assertTrue("GFXBackend = Vulkan" in core)
        assertTrue("CPUCore = 4" in core)
        assertTrue("InternalResolution = 1" in gfx)
        assertTrue("ShaderCompilationMode = 3" in gfx)
        assertTrue("VSync = False" in gfx)
    }

    @Test
    fun ppssppGetsVulkanAndTwiceItsResolution() {
        val ini = GraphicsTuning.ppsspp("[Graphics]\nGraphicsBackend = 0 (OPENGL)\n", mali)
        assertTrue("GraphicsBackend = 3 (VULKAN)" in ini)
        assertTrue("InternalResolution = 2" in ini)
        assertTrue("FrameSkip = 0" in ini)
    }
}
