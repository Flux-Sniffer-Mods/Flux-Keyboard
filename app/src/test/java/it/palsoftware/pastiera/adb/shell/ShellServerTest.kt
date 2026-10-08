package it.palsoftware.pastiera.adb.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.TimeUnit

class ShellServerTest {
    companion object {
        private const val UID = 13_579
        private const val TOKEN = "0123456789abcdef0123456789abcdef"

        @BeforeClass @JvmStatic
        fun startHelper() {
            Thread { ShellServer.serve(UID, TOKEN) }.apply { isDaemon = true; start() }
            repeat(50) {
                if (ShellProtocol.handshake(ShellProtocol.ports(UID)[0], TOKEN)?.also { it.close() } != null) return
                Thread.sleep(100)
            }
        }

        private fun run(vararg argv: String) =
            ShellProcess(ShellProtocol.handshake(ShellProtocol.ports(UID)[0], TOKEN)!!, argv.toList())
    }

    @Test
    fun runsACommand() {
        val process = run("sh", "-c", "echo hello; echo oops >&2; exit 3")
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        assertEquals(3, process.waitFor())
        assertEquals("hello\n", out)
        assertEquals("oops\n", err)
    }

    @Test
    fun passesInput() {
        val process = run("cat")
        process.outputStream.apply { write("typed".toByteArray()); flush(); close() }
        assertEquals("typed", process.inputStream.bufferedReader().readText())
        assertEquals(0, process.waitFor())
    }

    @Test
    fun destroyStopsTheCommand() {
        val process = run("sleep", "30")
        assertEquals(false, process.waitFor(200, TimeUnit.MILLISECONDS))
        process.destroy()
        assertEquals(true, process.waitFor(2, TimeUnit.SECONDS))
    }

    @Test
    fun refusesAWrongToken() {
        assertNull(ShellProtocol.handshake(ShellProtocol.ports(UID)[0], "wrong"))
    }
}
