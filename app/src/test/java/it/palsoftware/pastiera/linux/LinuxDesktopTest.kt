package it.palsoftware.pastiera.linux

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class LinuxDesktopTest {
    @Test
    fun vncLoginMatchesDes() {
        // Made with Python's DES: the password's bytes bit-reversed as the key, challenge 0..15
        assertEquals("c4d4c8ce321a50f105ba594249ad69bc", VncClient.response("flux1234", ByteArray(16) { it.toByte() }).hex())
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /** A VNC server's side of the talk, over pipes. */
    private class Server {
        val toClient = PipedOutputStream()
        val clientIn = PipedInputStream(toClient, 1 shl 20)
        val fromClient = PipedInputStream(1 shl 20)
        val clientOut = PipedOutputStream(fromClient)
        val out = DataOutputStream(toClient)
        val input = DataInputStream(fromClient)
    }

    @Test
    fun logsInAndDrawsRawAndCopiedRectangles() {
        val server = Server()
        val frames = mutableListOf<List<Int>>()
        val client = VncClient(server.clientIn, server.clientOut, onFrame = { x, y, w, h -> frames += listOf(x, y, w, h) })
        val challenge = ByteArray(16) { (it * 7).toByte() }
        val serverThread = Thread {
            server.out.write("RFB 003.008\n".toByteArray()); server.out.flush()
            assertEquals("RFB 003.008\n", String(ByteArray(12).also { server.input.readFully(it) }))
            server.out.writeByte(1); server.out.writeByte(VncClient.VNC_AUTH); server.out.flush()
            assertEquals(VncClient.VNC_AUTH, server.input.readUnsignedByte())
            server.out.write(challenge); server.out.flush()
            val answer = ByteArray(16).also { server.input.readFully(it) }
            server.out.writeInt(if (answer.contentEquals(VncClient.response("secret12", challenge))) 0 else 1); server.out.flush()
            assertEquals(1, server.input.readUnsignedByte())
            // ServerInit: 4 x 2, a pixel format, a name
            server.out.writeShort(4); server.out.writeShort(2); server.out.write(ByteArray(16))
            server.out.writeInt(5); server.out.write("flux1".toByteArray()); server.out.flush()
            // SetPixelFormat (20 bytes), SetEncodings (4 + 3 x 4), then the first request (10)
            server.input.readFully(ByteArray(20))
            server.input.readFully(ByteArray(4 + 12))
            server.input.readFully(ByteArray(10))
            // An update: a raw 2 x 1 at (0,0), then a copy of it to (2,1)
            server.out.writeByte(0); server.out.writeByte(0); server.out.writeShort(2)
            server.out.writeShort(0); server.out.writeShort(0); server.out.writeShort(2); server.out.writeShort(1)
            server.out.writeInt(VncClient.ENCODING_RAW)
            server.out.write(byteArrayOf(0x33, 0x22, 0x11, 0, 0x66, 0x55, 0x44, 0))
            server.out.writeShort(2); server.out.writeShort(1); server.out.writeShort(2); server.out.writeShort(1)
            server.out.writeInt(VncClient.ENCODING_COPY_RECT)
            server.out.writeShort(0); server.out.writeShort(0)
            server.out.flush()
            // The client's next request, then the end
            server.input.readFully(ByteArray(10))
            server.toClient.close()
        }.apply { start() }
        assertTrue(client.handshake("secret12"))
        assertEquals("flux1", client.name)
        assertEquals(4, client.width)
        runCatching { client.run() }
        serverThread.join(5_000)
        assertEquals(0xFF112233.toInt(), client.pixels[0])
        assertEquals(0xFF445566.toInt(), client.pixels[1])
        assertEquals(0xFF112233.toInt(), client.pixels[1 * 4 + 2])
        assertEquals(0xFF445566.toInt(), client.pixels[1 * 4 + 3])
        assertEquals(listOf(listOf(0, 0, 2, 1), listOf(2, 1, 2, 1)), frames)
    }

    @Test
    fun wrongPasswordIsRefused() {
        val server = Server()
        val client = VncClient(server.clientIn, server.clientOut)
        Thread {
            server.out.write("RFB 003.008\n".toByteArray()); server.out.flush()
            server.input.readFully(ByteArray(12))
            server.out.writeByte(1); server.out.writeByte(VncClient.VNC_AUTH); server.out.flush()
            server.input.readUnsignedByte()
            server.out.write(ByteArray(16)); server.out.flush()
            server.input.readFully(ByteArray(16))
            server.out.writeInt(1); server.out.flush()
        }.start()
        assertFalse(client.handshake("wrong"))
    }

    @Test
    fun pointerAndKeysGoOutAsRfb() {
        val server = Server()
        val client = VncClient(server.clientIn, server.clientOut)
        client.key(0xFF0D, true)
        assertArrayEquals(byteArrayOf(4, 1, 0, 0, 0, 0, 0xFF.toByte(), 0x0D), ByteArray(8).also { server.input.readFully(it) })
    }

    @Test
    fun keysymsForSpecialKeysAndCharacters() {
        assertEquals(0xFF0D, Keysyms.of(KeyEvent.KEYCODE_ENTER, '\n'.code))
        assertEquals(0xFF08, Keysyms.of(KeyEvent.KEYCODE_DEL, 0))
        assertEquals('a'.code, Keysyms.of(KeyEvent.KEYCODE_A, 'a'.code))
        assertEquals('é'.code, Keysyms.of('é'.code))
        assertEquals(0x10020AC, Keysyms.of('€'.code))
        assertEquals(0, Keysyms.of(KeyEvent.KEYCODE_UNKNOWN, 0))
        assertTrue(Keysyms.isModifier(Keysyms.of(KeyEvent.KEYCODE_SHIFT_LEFT, 0)))
    }

    @Test
    fun desktopResizesWhenTheServerSaysItCan() {
        val server = Server()
        val sizes = mutableListOf<Pair<Int, Int>>()
        val client = VncClient(server.clientIn, server.clientOut, onResize = { w, h -> sizes += w to h })
        val sent = ByteArray(24)
        val thread = Thread {
            server.out.write("RFB 003.008\n".toByteArray()); server.out.flush()
            server.input.readFully(ByteArray(12))
            server.out.writeByte(1); server.out.writeByte(VncClient.NO_AUTH); server.out.flush()
            server.input.readUnsignedByte()
            server.out.writeInt(0); server.out.flush()
            server.input.readUnsignedByte()
            server.out.writeShort(8); server.out.writeShort(4); server.out.write(ByteArray(16)); server.out.writeInt(0); server.out.flush()
            server.input.readFully(ByteArray(20 + 4 + 16 + 10))
            // ExtendedDesktopSize: one screen (id 7), now 6 x 2
            server.out.writeByte(0); server.out.writeByte(0); server.out.writeShort(1)
            server.out.writeShort(0); server.out.writeShort(0); server.out.writeShort(6); server.out.writeShort(2)
            server.out.writeInt(VncClient.ENCODING_EXTENDED_DESKTOP_SIZE)
            server.out.writeByte(1); server.out.write(ByteArray(3))
            server.out.writeInt(7); server.out.writeShort(0); server.out.writeShort(0); server.out.writeShort(6); server.out.writeShort(2); server.out.writeInt(0)
            server.out.flush()
            server.input.readFully(ByteArray(10))
            server.toClient.close()
            // What the client asks next, read here (a pipe's reader has to stay alive)
            server.input.readFully(sent)
        }
        thread.start()
        assertTrue(client.handshake(""))
        runCatching { client.run() }
        assertTrue(client.resizable)
        assertEquals(listOf(6 to 2), sizes)
        assertEquals(12, client.pixels.size)
        client.setSize(10, 6)
        thread.join(5_000)
        // SetDesktopSize: type 251, 10 x 6, one screen keeping the server's id
        assertArrayEquals(byteArrayOf(251.toByte(), 0, 0, 10, 0, 6, 1, 0, 0, 0, 0, 7, 0, 0, 0, 0, 0, 10, 0, 6, 0, 0, 0, 0), sent)
    }

    @Test
    fun programsReadFromDesktopFiles() {
        val listing = """
            @@app firefox-esr.desktop
            [Desktop Entry]
            Name=Firefox ESR
            Name[de]=Firefox ESR (de)
            Exec=/usr/lib/firefox-esr/firefox-esr %u
            Icon=firefox-esr
            Type=Application
            [Desktop Action new-window]
            Name=New Window
            Exec=firefox-esr --new-window

            @@app xfce4-terminal.desktop
            [Desktop Entry]
            Name=Xfce Terminal
            Exec=xfce4-terminal
            Icon=org.xfce.terminal
            Type=Application

            @@app hidden.desktop
            [Desktop Entry]
            Name=Hidden
            Exec=hidden
            NoDisplay=true
            Type=Application

            @@app gnome-only.desktop
            [Desktop Entry]
            Name=Gnome thing
            Exec=gnome-thing
            OnlyShowIn=GNOME;
            Type=Application

            @@app htop.desktop
            [Desktop Entry]
            Name=Htop
            Exec=htop
            Terminal=true
            Type=Application

            @@app xfce4-terminal.desktop
            [Desktop Entry]
            Name=My Terminal
            Exec=xfce4-terminal --maximize
            Type=Application
        """.trimIndent()
        val apps = LinuxApps.parse(listing, java.util.Locale.GERMANY)
        assertEquals(listOf("Firefox ESR (de)", "Htop", "My Terminal"), apps.map { it.name })
        assertEquals("/usr/lib/firefox-esr/firefox-esr", apps[0].exec)
        assertEquals("firefox-esr", apps[0].iconName)
        assertTrue(apps[1].terminal)
        // The user's own file of the same name wins
        assertEquals("xfce4-terminal --maximize", apps[2].exec)
    }

    @Test
    fun execFieldCodesAndIconNames() {
        assertEquals("gimp --new 100%", LinuxApps.cleanExec("gimp %F --new 100%%"))
        assertEquals("sh -c \"echo hi\"", LinuxApps.cleanExec("sh -c \"echo hi\" %U"))
        assertTrue(LinuxApps.safeIcon("org.xfce.terminal"))
        assertTrue(LinuxApps.safeIcon("/usr/share/pixmaps/htop.png"))
        assertFalse(LinuxApps.safeIcon("../../etc/passwd"))
        assertFalse(LinuxApps.safeIcon("a'b"))
        val icons = LinuxApps.parseIcons("@@icon one\n" + java.util.Base64.getMimeEncoder().encodeToString(ByteArray(40) { it.toByte() }) + "\n\n@@icon two\n\n")
        assertEquals(listOf("one"), icons.map { it.first })
        assertEquals(40, icons[0].second.size)
    }

    @Test
    fun newestUbuntuImageIsPicked() {
        val listing = "<a href=\"ubuntu-base-26.04-base-arm64.tar.gz\">x</a> ubuntu-base-26.04.10-base-arm64.tar.gz " +
            "ubuntu-base-26.04.1-base-arm64.tar.gz ubuntu-base-26.04.1-base-amd64.tar.gz ubuntu-base-24.04.9-base-arm64.tar.gz"
        assertEquals("ubuntu-base-26.04.10-base-arm64.tar.gz", LinuxDesktop.newestUbuntu(listing))
    }

    @Test
    fun linuxNotificationsReadFromTheQueue() {
        val notes = LinuxNotifications.parse("firefox-esr-3\tDownload done\tfile.zip\tFirefox\n\nbad line\nlinux-7\tUpdates\t\t\n")
        assertEquals(listOf("firefox-esr-3", "linux-7"), notes.map { it.tag })
        assertEquals("file.zip", notes[0].body)
        assertEquals("Firefox", notes[0].app)
    }

    @Test
    fun kdeProgramsListedForKde() {
        val listing = "@@app konsole.desktop\n[Desktop Entry]\nName=Konsole\nExec=konsole\nOnlyShowIn=KDE;\nType=Application\n"
        assertEquals(listOf("Konsole"), LinuxApps.parse(listing, java.util.Locale.UK, "KDE").map { it.name })
        assertTrue(LinuxApps.parse(listing, java.util.Locale.UK, "XFCE").isEmpty())
    }
}
