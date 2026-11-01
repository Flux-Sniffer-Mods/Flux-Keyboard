package it.palsoftware.pastiera.airplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

class AirPlayTest {
    // Made with srptools (as pyatv pairs): client private a5…, salt with a leading zero byte, PIN 3939
    private val srpB = (
            "0a96f0d87b4bc5165f18861374caa34b37dfa521d271ddbde3e32ef3d605bab2f72a01c9e3005b02aeeebf28261a7562" +
            "8bcf67deeaf2ff79e31d5a18f23d4bbecc210496c27f63302dca6c99742ed00a759f1b217953d1ec61538b8886cd28b6" +
            "09a22d740d7ebf2254edd7797ef7ce39280d7180873db30f91b3f522556d46259d595b91b69fb1838fdf181cb4d36e34" +
            "2505a484985e4173f1895f1b155db235a89e6b11e77ca34c678594c5ebb377ed29730599393cd9b70b9c523f7bed99e2" +
            "430b6edc85466fb9d508556e44072e34fb462f249d9c5f85f795d464f55ab47e76318f7571d1e44689e87047233a0088" +
            "a1ffc59985e541392cc288dc8b7b719ab3bf95101e9639bdb2f3c703be3e730d4e3bd456fbf57380801e4e4bb1b55532" +
            "790aca88a0d2fb253e34c71b05bc00936b71f328b841a4f49b3163e0b5576b9ab851e12402157827bc6f5b32207bf6fe" +
            "a4dbc2dfeeb6e78a8077157d35711d2d6118d858905e4cc4a842f7fa32a13ce339a3336fb1014aa4211e57bead1a0245"
        )
    private val srpA = (
            "7adff9a1a617076c8942f4e034880f9ed18d5befd9f9723395a4181299d54f987cfc9e77ade619d948e26916e51a8f40" +
            "dde8a4440d7a0c4fc2170c16a3e8368619b61d740fa11c47d78cda8700331363c35efc701cd3c2be22e3459fa894b01f" +
            "52d56a0ee0a24f7e5bfd333fdfd602e8358620927da1578d2c8926a7e8472149e19fd393840b09acbf50489f95d4d169" +
            "a267c096d023abc6c1f8c9a74f5a93b1ad0c4b97e8fc5316ab11487a0b78546ee14a564af9ea3a5bce1fe93e6d1da5b7" +
            "72db6c4e6457243fd13d5911f7935ecbeb1310499ece9b6122d326c7007a33608cd66f6289371209e6da5c7948f8eddd" +
            "650064f9fb3510fae996a98ec2312ab1f061ce8d04e49c7c2df6826615254a585acd3f3238f6435cfc5c51c5ba4799a2" +
            "2ae633c8af4def8939f17d04d9bced5eb8bca24ba6729d2fe89d8df4e2ea8ce6763b57d6f0221758468957edbfb94223" +
            "c0c2e33cfa2d9c8954343804205bcf92b5e1f5d41eb4ea9fdd3155c6ab61cf4c3274d6d73505408f71780a8ed858e3a1"
        )
    private val srpM = (
            "20d92f04f5dd489a23f02f1f798c542871e1c56cf9b06c64d74d6f30722471bd09f5aadcc4fc61acf8141816ae6b9772" +
            "41f7e7cc7043e8ad47c54784c57cf592"
        )
    private val srpK = (
            "0eda518ac5d52dcd7f378122463f4e6469d681257c8572c666723cb4dc795e82999f2b57ec765a6f6bea233dc082a655" +
            "8462c8879e049c7113df941c74295028"
        )

    @Test
    fun srpMatchesWhatReceiversCheck() {
        val client = SrpClient("3939", BigInteger("a5".repeat(32), 16))
        client.process(("00" + "1f".repeat(15)).unhex(), srpB.unhex())
        assertEquals(srpA, client.publicKey.hex())
        assertEquals(srpM, client.proof.hex())
        assertEquals(srpK, client.sessionKey.hex())
    }

    @Test
    fun keysAndSealingMatchHomeKit() {
        val secret = ByteArray(64) { it.toByte() }
        val key = HapCrypto.hkdf("Control-Salt", "Control-Write-Encryption-Key", secret)
        assertEquals("5a6cb19bcbe7d4df2dd8279f39562f7fae2dbf73eb5a4f98849c245c82b2fe96", key.hex())
        assertEquals("fad9aa71ca1106ab7b6638c47a75af67756f2febb1",
            HapCrypto.seal(key, HapCrypto.nonce("PS-Msg05".toByteArray()), "hello".toByteArray()).hex())
        val frames = HapCipher(key, key).let { it.seal("abc".toByteArray()) + it.seal("de".toByteArray()) }
        assertEquals("0300" + "c268004f3c8bd2cd955e4974f1039f9aacedd9" + "0200" + "435c58c41724b8d73508af1c567df46b4346", frames.hex())
        val reader = HapCipher(key, key)
        val input = frames.inputStream()
        assertEquals("abc", String(reader.open(input)))
        assertEquals("de", String(reader.open(input)))
    }

    @Test
    fun longDataGoesInFramesOf1024() {
        val key = ByteArray(32) { 7 }
        val data = ByteArray(2500) { it.toByte() }
        val sealed = HapCipher(key, key).seal(data)
        assertEquals(2500 + 3 * 18, sealed.size)
        val reader = HapCipher(key, key)
        val input = sealed.inputStream()
        val out = ByteArrayOutputStream()
        repeat(3) { out.write(reader.open(input)) }
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun signaturesVerifyAndKeysAgree() {
        val seed = ByteArray(32) { (it * 3).toByte() }
        val message = "Pair-Verify".toByteArray()
        val signature = HapCrypto.edSign(seed, message)
        assertTrue(HapCrypto.edVerify(HapCrypto.edPublic(seed), message, signature))
        assertFalse(HapCrypto.edVerify(HapCrypto.edPublic(seed), "other".toByteArray(), signature))
        val a = HapCrypto.x25519()
        val b = HapCrypto.x25519()
        assertArrayEquals(a.agree(b.public), b.agree(a.public))
    }

    @Test
    fun tlvSplitsLongValuesAndJoinsThemBack() {
        val key = ByteArray(384) { it.toByte() }
        val encoded = Tlv8.encode(Tlv8.SEQ_NO to byteArrayOf(3), Tlv8.PUBLIC_KEY to key)
        assertEquals(3 + 2 + 255 + 2 + 129, encoded.size)
        val decoded = Tlv8.decode(encoded)
        assertArrayEquals(byteArrayOf(3), decoded[Tlv8.SEQ_NO])
        assertArrayEquals(key, decoded[Tlv8.PUBLIC_KEY])
    }

    @Test
    fun plistFromPythonReads() {
        val data = ("62706c6973743030d90102030405060708090a0b0c121314151617596576656e74506f72745a74696d696e67506f72745773747265616d73546e616d65526f6b5472617465546461746153626967536e656711cb2b1000a10dd20e0f101154747970655864617461506f7274106e111bbc6400430061006600e909233ff000000000000042010213000001000000000013fffffffffffffffb081b2530383d40454a4e525557595e636c6e717a7b8487900000000000000101000000000000001800000000000000000000000000000099").unhex()
        val map = Bplist.decode(data) as Map<*, *>
        assertEquals(52011L, map["eventPort"])
        assertEquals(0L, map["timingPort"])
        assertEquals("Café", map["name"])
        assertEquals(true, map["ok"])
        assertEquals(1.0, map["rate"])
        assertArrayEquals(byteArrayOf(1, 2), map["data"] as ByteArray)
        assertEquals(1L shl 40, map["big"])
        assertEquals(-5L, map["neg"])
        assertEquals(7100L, ((map["streams"] as List<*>)[0] as Map<*, *>)["dataPort"])
    }

    @Test
    fun plistRoundTrips() {
        val value = mapOf(
            "Content-Location" to "http://192.168.1.20:40000/flux.m3u8",
            "Start-Position-Seconds" to 0.0,
            "streamType" to 1,
            "volume" to 1.0,
            "nested" to mapOf("value" to true, "list" to listOf(1, "two", 3.5)),
            "long" to "x".repeat(300),
            "number" to 70000
        )
        val back = Bplist.decode(Bplist.encode(value)) as Map<*, *>
        assertEquals("http://192.168.1.20:40000/flux.m3u8", back["Content-Location"])
        assertEquals(1L, back["streamType"])
        assertEquals(70000L, back["number"])
        assertEquals("x".repeat(300), back["long"])
        assertEquals(listOf(1L, "two", 3.5), (back["nested"] as Map<*, *>)["list"])
    }

    @Test
    fun featuresAndFlagsRead() {
        assertEquals(0x1E5A7FFFF7L, AirPlayReceiver.features("0x5A7FFFF7,0x1E"))
        val tv = AirPlayReceiver.from("Living room", InetAddress.getLoopbackAddress(), 7000,
            mapOf("features" to "0x4A7FDFD5,0xBC157FDE", "flags" to "0x244", "deviceid" to "AA:BB"))
        assertTrue(tv.airPlay2)
        assertTrue(tv.pairs)
        assertTrue(tv.video)
        assertFalse(tv.wantsPin)
        assertEquals("AA:BB", tv.id)
        val old = AirPlayReceiver.from("Old", InetAddress.getLoopbackAddress(), 7000, mapOf("features" to "0x5A7FFFF7"))
        assertFalse(old.airPlay2)
        assertFalse(old.pairs)
        val pin = AirPlayReceiver.from("Pin", InetAddress.getLoopbackAddress(), 7000, mapOf("features" to "0x1,0x10000", "sf" to "0x8"))
        assertTrue(pin.wantsPin)
    }

    @Test
    fun tablesCarryTheRightChecks() {
        // ffmpeg's PAT for program 1 on PID 0x1000 ends 2A B1 04 B2
        val pat = byteArrayOf(0x00, 0xB0.toByte(), 0x0D, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00, 0x00, 0x01, 0xF0.toByte(), 0x00)
        assertEquals(0x2AB104B2, TsMuxer.crc32(pat))
        val out = ByteArrayOutputStream()
        TsMuxer(withAudio = true).tables(out)
        val bytes = out.toByteArray()
        assertEquals(2 * 188, bytes.size)
        assertEquals("2ab104b2", bytes.copyOfRange(17, 21).hex())
    }

    @Test
    fun picturesAndSoundPackInWholePackets() {
        val muxer = TsMuxer(withAudio = true)
        val out = ByteArrayOutputStream()
        for (size in listOf(1, 170, 171, 176, 177, 183, 184, 185, 1000, 40_000)) {
            out.reset()
            muxer.video(out, byteArrayOf(0, 0, 0, 1, 0x65) + ByteArray(size), 900_000, key = true)
            val bytes = out.toByteArray()
            assertEquals(0, bytes.size % 188)
            for (i in bytes.indices step 188) assertEquals(0x47.toByte(), bytes[i])
            // Payload intact: the PES's bytes, read back from the packets, end with the picture
            assertEquals(size + 5 + 6 + 14, payload(bytes).size)
        }
        out.reset()
        muxer.audio(out, TsMuxer.adts(300) + ByteArray(300), 900_000)
        assertEquals(0, out.size() % 188)
        assertEquals(300 + 7 + 14, payload(out.toByteArray()).size)
    }

    /** The bytes the packets carry, adaptation fields left out. */
    private fun payload(ts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (i in ts.indices step 188) {
            val control = (ts[i + 3].toInt() shr 4) and 3
            var at = i + 4
            if (control and 2 != 0) at += 1 + (ts[at].toInt() and 0xFF)
            if (control and 1 != 0) out.write(ts, at, i + 188 - at)
        }
        return out.toByteArray()
    }

    @Test
    fun adtsHeaderForStereo48k() {
        assertEquals("fff14c802b7ffc", TsMuxer.adts(340).hex())
    }

    @Test
    fun segmentsStartOnKeyframesAboutASecondApart() {
        val stream = HlsStream(withAudio = false)
        stream.video(byteArrayOf(0, 0, 0, 1, 0x67), 0, key = false, isConfig = true)
        // 30 fps from 1 s, a keyframe each second
        for (frame in 0 until 30 * 6) {
            val us = 1_000_000L + frame * 33_333L
            stream.video(byteArrayOf(0, 0, 0, 1, 0x41, 1), us, key = frame % 30 == 0, isConfig = false)
        }
        assertTrue(stream.ready())
        val playlist = stream.playlist()
        assertTrue(playlist.startsWith("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:1\n"))
        assertTrue(playlist.contains("#EXT-X-MEDIA-SEQUENCE:1\n"))
        assertTrue(playlist.contains("#EXTINF:1.000,\ns4.ts\n"))
        assertFalse(playlist.contains("ENDLIST"))
        val segment = stream.segment(4)
        assertNotNull(segment)
        assertEquals(0, segment!!.size % 188)
    }

    @Test
    fun encryptedRequestsGoBothWays() {
        val key1 = ByteArray(32) { 1 }
        val key2 = ByteArray(32) { 2 }
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val thread = Thread {
                server.accept().use { socket ->
                    val side = AirPlayConnection(socket)
                    side.encrypt(key2, key1)
                    val request = side.read()
                    assertEquals("SETUP rtsp://x/1 RTSP/1.0", request.first)
                    assertEquals("7", request.headers["cseq"])
                    val body = Bplist.encode(mapOf("eventPort" to 5000))
                    side.send(Message.format("RTSP/1.0 200 OK", listOf("CSeq" to 7, "Content-Type" to "application/x-apple-binary-plist"), body))
                }
            }.apply { start() }
            val client = AirPlayConnection(Socket(InetAddress.getLoopbackAddress(), server.localPort))
            client.encrypt(key1, key2)
            val answer = client.request("SETUP", "rtsp://x/1", listOf("CSeq" to 7), Bplist.encode(mapOf("timingPort" to 1)), "RTSP/1.0")
            assertEquals(200, answer.code)
            assertEquals(5000L, answer.plist?.get("eventPort"))
            thread.join()
            client.close()
        }
    }

    /** A receiver's side of transient pairing, to pair with. */
    @Test
    fun transientPairingAgreesOnTheSecret() {
        val n = SrpClient.N
        val g = BigInteger.valueOf(5)
        fun sha(vararg parts: ByteArray) = MessageDigest.getInstance("SHA-512").run { parts.forEach { update(it) }; digest() }
        fun pad(v: BigInteger) = SrpClient.bytes(v).let { ByteArray(384 - it.size) + it }
        val salt = ByteArray(16) { 9 }
        val x = BigInteger(1, sha(salt, sha("Pair-Setup:3939".toByteArray())))
        val v = g.modPow(x, n)
        val k = BigInteger(1, sha(SrpClient.bytes(n), pad(g)))
        val b = BigInteger("1234567890abcdef".repeat(4), 16)
        val publicB = k.multiply(v).add(g.modPow(b, n)).mod(n)
        var serverKey: ByteArray? = null
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val thread = Thread {
                server.accept().use { socket ->
                    val side = AirPlayConnection(socket)
                    fun answer(body: ByteArray?) = side.send(Message.format("HTTP/1.1 200 OK", emptyList(), body))
                    assertTrue(side.read().first.startsWith("POST /pair-pin-start"))
                    answer(null)
                    val m1 = Tlv8.decode(side.read().body)
                    assertArrayEquals(byteArrayOf(0x10), m1[Tlv8.FLAGS])
                    answer(Tlv8.encode(Tlv8.SEQ_NO to byteArrayOf(2), Tlv8.SALT to salt, Tlv8.PUBLIC_KEY to SrpClient.bytes(publicB)))
                    val m3 = Tlv8.decode(side.read().body)
                    val publicA = BigInteger(1, m3[Tlv8.PUBLIC_KEY])
                    val u = BigInteger(1, sha(pad(publicA), pad(publicB)))
                    val secret = publicA.multiply(v.modPow(u, n)).modPow(b, n)
                    serverKey = sha(SrpClient.bytes(secret))
                    answer(Tlv8.encode(Tlv8.SEQ_NO to byteArrayOf(4), Tlv8.PROOF to ByteArray(64)))
                }
            }.apply { start() }
            val connection = AirPlayConnection(Socket(InetAddress.getLoopbackAddress(), server.localPort))
            val pairing = HapPairing(connection)
            pairing.transient()
            thread.join()
            assertArrayEquals(serverKey, pairing.secret)
            connection.close()
        }
    }

    @Test
    fun credentialsKeep() {
        val credentials = HapCredentials(ByteArray(32) { 1 }, "AA".toByteArray(), ByteArray(32) { 2 }, "ID".toByteArray())
        val back = HapCredentials.decode(credentials.encode())!!
        assertArrayEquals(credentials.receiverKey, back.receiverKey)
        assertArrayEquals(credentials.clientId, back.clientId)
    }
}
