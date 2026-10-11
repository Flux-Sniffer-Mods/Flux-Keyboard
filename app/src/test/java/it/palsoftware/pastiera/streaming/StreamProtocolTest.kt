package it.palsoftware.pastiera.streaming

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamProtocolTest {
    private val key = ByteArray(16) { (it * 7 + 3).toByte() }

    /** What Moonlight's depacketizer does: payloads in order, the frame header skipped. */
    private fun reassemble(packets: List<ByteArray>, size: Int): ByteArray =
        packets.map { it.copyOfRange(32, it.size) }.reduce { a, b -> a + b }.copyOfRange(8, 8 + size)

    @Test
    fun aFrameIsCutIntoMoonlightsVideoPackets() {
        val frame = ByteArray(3000) { (it % 251).toByte() }
        val packets = StreamProtocol.VideoPacketizer(1392).packetize(frame, 7, keyframe = true, timestamp = 90_000)
        assertEquals(3, packets.size)
        packets.forEachIndexed { i, packet ->
            // Every packet full size: RTP with its extension, then packetSize
            assertEquals(1392 + 16, packet.size)
            assertEquals(0x90.toByte(), packet[0])
            val rtp = ByteBuffer.wrap(packet)
            assertEquals(i, rtp.getShort(2).toInt())
            assertEquals(90_000, rtp.getInt(4))
            val nv = ByteBuffer.wrap(packet, 16, 16).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(i shl 8, nv.getInt(16))
            assertEquals(7, nv.getInt(20))
            val flags = packet[24].toInt()
            assertEquals(i == 0, flags and 0x4 != 0)
            assertEquals(i == 2, flags and 0x2 != 0)
            assertEquals(0x1, flags and 0x1)
            val fec = nv.getInt(28)
            assertEquals(i, (fec ushr 12) and 0x3FF)
            assertEquals(3, fec ushr 22)
            assertEquals(0, (fec ushr 4) and 0xFF)
        }
        // Sunshine's frame header: short type, keyframe
        assertEquals(0x01.toByte(), packets[0][32])
        assertEquals(2.toByte(), packets[0][35])
        assertArrayEquals(frame, reassemble(packets, frame.size))
    }

    @Test
    fun aBigFrameIsSplitIntoBlocks() {
        val frame = ByteArray(400 * 1376) { it.toByte() }
        val packetizer = StreamProtocol.VideoPacketizer(1392)
        val packets = packetizer.packetize(frame, 1, keyframe = false, timestamp = 0)
        val blocks = packets.map { (it[27].toInt() ushr 4) and 0x3 }
        assertEquals(listOf(0, 1), blocks.distinct())
        assertTrue(packets.all { (it[27].toInt() ushr 6) and 0x3 == 1 })
        // Each block's first packet starts it, its last ends it; the sequence carries on
        val starts = packets.indices.filter { packets[it][24].toInt() and 0x4 != 0 }
        assertEquals(2, starts.size)
        assertArrayEquals(frame, reassemble(packets, frame.size))
        assertEquals(packets.size, ByteBuffer.wrap(packetizer.packetize(ByteArray(10), 2, false, 0)[0]).getShort(2).toInt())
    }

    @Test
    fun controlMessagesSealAndOpenAsMoonlightDoes() {
        val host = StreamProtocol.ControlCipher(key)
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val sealed = StreamProtocol.sealAsClient(key, 41, StreamProtocol.CONTROL_INPUT, payload)
        val (type, opened) = host.open(sealed)!!
        assertEquals(StreamProtocol.CONTROL_INPUT, type)
        assertArrayEquals(payload, opened)

        // The host's own: Moonlight opens it with the 'H' IV, the tag first
        val message = host.seal(StreamProtocol.CONTROL_TERMINATION, byteArrayOf(0x80.toByte(), 3, 0, 0x23))
        val header = ByteBuffer.wrap(message).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, header.short.toInt())
        assertEquals(message.size - 4, header.short.toInt())
        val seq = header.int
        val iv = ByteArray(12).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(seq); it[10] = 'H'.code.toByte(); it[11] = 'C'.code.toByte() }
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            doFinal(message.copyOfRange(24, message.size) + message.copyOfRange(8, 24))
        }
        val inner = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(StreamProtocol.CONTROL_TERMINATION, inner.short.toInt())
        assertEquals(4, inner.short.toInt())
    }

    @Test
    fun audioIsEncryptedWithTheKeyIdAndSequence() {
        val opus = ByteArray(40) { it.toByte() }
        val packet = StreamProtocol.audioPacket(opus, 5, 25, key, 1000)
        assertEquals(97.toByte(), packet[1])
        val iv = ByteArray(16).also { ByteBuffer.wrap(it).putInt(1005) }
        val plain = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(packet.copyOfRange(12, packet.size))
        }
        assertArrayEquals(opus, plain)
        assertArrayEquals(opus, StreamProtocol.audioPacket(opus, 5, 25, null, 0).copyOfRange(12, 52))
    }

    @Test
    fun enetConnectIsAnsweredAndDataAcknowledged() {
        val enet = StreamControl.Enet
        // Moonlight's connect: peer 0xFFF, with its sent time
        val connect = ByteBuffer.allocate(4 + 48).putShort((0xFFF or 0x8000).toShort()).putShort(1234)
            .put(0x82.toByte()).put(0xFF.toByte()).putShort(1)
            .putShort(3).put(0xFF.toByte()).put(0xFF.toByte()).putInt(1400).putInt(32768).putInt(0x20)
            .putInt(0).putInt(0).putInt(5000).putInt(2).putInt(2).putInt(0x11223344).putInt(0).array()
        val parsed = enet.parse(connect)!!
        assertEquals(1234, parsed.sentTime)
        val command = parsed.commands.single()
        assertEquals(StreamControl.Enet.CONNECT, command.type)
        val verify = ByteBuffer.wrap(enet.verifyConnect(command.connect!!, 1234))
        assertEquals(3 or 0x8000, verify.short.toInt() and 0xFFFF)
        verify.short
        assertEquals(0x83, verify.get().toInt() and 0xFF)
        assertEquals(0xFF, verify.get().toInt() and 0xFF)
        assertEquals(1, verify.short.toInt())
        assertEquals(0, verify.short.toInt())
        verify.get(); verify.get()
        assertEquals(1400, verify.int); assertEquals(32768, verify.int); assertEquals(0x20, verify.int)
        verify.int; verify.int
        assertEquals(5000, verify.int); assertEquals(2, verify.int); assertEquals(2, verify.int)
        assertEquals(0x11223344, verify.int)

        // A reliable message on channel 2, then a ping
        val data = byteArrayOf(9, 8, 7)
        val reliable = ByteBuffer.allocate(4 + 6 + 3 + 4).putShort(0x8000.toShort()).putShort(55)
            .put(0x86.toByte()).put(2).putShort(4).putShort(3).put(data)
            .put(0x85.toByte()).put(0xFF.toByte()).putShort(5).array()
        val got = enet.parse(reliable)!!
        assertEquals(2, got.commands.size)
        assertArrayEquals(data, got.commands[0].data)
        assertTrue(got.commands.all { it.acknowledge })
        val acks = ByteBuffer.wrap(enet.acknowledgements(3, got.commands, 55))
        assertEquals(3, acks.short.toInt())
        assertEquals(1, acks.get().toInt()); assertEquals(2, acks.get().toInt()); assertEquals(4, acks.short.toInt())
        assertEquals(4, acks.short.toInt()); assertEquals(55, acks.short.toInt())
    }

    @Test
    fun inputPacketsAreRead() {
        val key = ByteBuffer.allocate(14).putInt(10).order(ByteOrder.LITTLE_ENDIAN).putInt(0x03)
            .put(0).putShort((0x8000 or 0x41).toShort()).put(0).putShort(0).array()
        assertEquals(StreamProtocol.Input.Key(true, 0x41, 0), StreamProtocol.parseInput(key))
        val move = ByteBuffer.allocate(12).putInt(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0x07)
            .order(ByteOrder.BIG_ENDIAN).putShort(-5).putShort(12).array()
        assertEquals(StreamProtocol.Input.MouseMove(-5, 12), StreamProtocol.parseInput(move))
        val pad = ByteBuffer.allocate(34).putInt(30).order(ByteOrder.LITTLE_ENDIAN).putInt(0x0C)
            .putShort(0x1A).putShort(0).putShort(1).putShort(0x14).putShort(StreamProtocol.PAD_A.toShort())
            .put(255.toByte()).put(0).putShort(32767).putShort(-32768).putShort(0).putShort(100)
            .putShort(0x9C).putShort(0).putShort(0x55).array()
        val parsed = StreamProtocol.parseInput(pad) as StreamProtocol.Input.Pad
        assertEquals(StreamProtocol.PAD_A, parsed.buttons)
        assertEquals(255, parsed.leftTrigger)
        assertEquals(32767, parsed.lx); assertEquals(-32768, parsed.ly); assertEquals(100, parsed.ry)
    }

    @Test
    fun rtspAndSdpAreRead() {
        val request = StreamProtocol.parseRtspHead("ANNOUNCE streamid=control/13/0 RTSP/1.0\r\nCSeq: 6\r\nContent-length: 10\r\n\r\n", "a=x:1")!!
        assertEquals("ANNOUNCE", request.method)
        assertEquals(6, request.cseq)
        assertEquals("1392", StreamProtocol.sdp("v=0\r\na=x-nv-video[0].packetSize:1392\r\n", "x-nv-video[0].packetSize"))
        assertNotNull(StreamProtocol.sdp(StreamProtocol.describe(), "x-ss-general.encryptionSupported"))
        val response = String(StreamProtocol.rtspResponse(3, listOf("Transport" to "server_port=47998")))
        assertTrue(response.startsWith("RTSP/1.0 200 OK\r\nCSeq: 3\r\nTransport: server_port=47998\r\n\r\n"))
    }

    @Test
    fun windowsKeysBecomeAndroidKeys() {
        assertEquals(KeyEvent.KEYCODE_A, StreamInput.keyCode(0x41))
        assertEquals(KeyEvent.KEYCODE_9, StreamInput.keyCode(0x39))
        assertEquals(KeyEvent.KEYCODE_F12, StreamInput.keyCode(0x7B))
        assertEquals(KeyEvent.KEYCODE_ENTER, StreamInput.keyCode(0x0D))
        assertEquals(KeyEvent.KEYCODE_SHIFT_RIGHT, StreamInput.keyCode(0xA1))
    }
}
