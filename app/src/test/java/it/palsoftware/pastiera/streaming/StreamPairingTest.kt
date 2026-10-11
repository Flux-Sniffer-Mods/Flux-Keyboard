package it.palsoftware.pastiera.streaming

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** The host's side of pairing, against a client doing what Moonlight's pairing does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamPairingTest {
    private val host = StreamCert.make("Flux Keyboard")
    private val client = StreamCert.make("NVIDIA GameStream Client")
    private val random = SecureRandom()

    private fun aes(key: ByteArray, mode: Int, data: ByteArray) =
        Cipher.getInstance("AES/ECB/NoPadding").apply { init(mode, SecretKeySpec(key, "AES")) }.doFinal(data)

    private fun pairWith(hostPin: String, clientPin: String): Boolean {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val key = StreamPairing.sha256(salt + clientPin.toByteArray()).copyOf(16)
        val session = StreamPairing(host, client.cert, salt)
        session.pin(hostPin)

        // The client's challenge, and the host's answer
        val challenge = ByteArray(16).also { random.nextBytes(it) }
        val answer = aes(key, Cipher.DECRYPT_MODE, session.clientChallenge(aes(key, Cipher.ENCRYPT_MODE, challenge)))
        val serverResponse = answer.copyOfRange(0, 32)
        val serverChallenge = answer.copyOfRange(32, 48)

        // The client answers the host's challenge; the host hands over its secret, signed
        val clientSecret = ByteArray(16).also { random.nextBytes(it) }
        val hash = StreamPairing.sha256(serverChallenge + client.cert.signature + clientSecret)
        val pairingSecret = session.serverChallengeResponse(aes(key, Cipher.ENCRYPT_MODE, hash))
        val serverSecret = pairingSecret.copyOfRange(0, 16)
        val signed = Signature.getInstance("SHA256withRSA").apply { initVerify(host.cert.publicKey); update(serverSecret) }
            .verify(pairingSecret.copyOfRange(16, pairingSecret.size))
        assertTrue("the host's secret is signed by its certificate", signed)
        // The client checks the host knew the PIN
        val hostKnewPin = serverResponse.contentEquals(StreamPairing.sha256(challenge + host.cert.signature + serverSecret))

        // The client's secret, signed: the host checks it
        val clientSignature = Signature.getInstance("SHA256withRSA").apply { initSign(client.key); update(clientSecret) }.sign()
        return session.clientPairingSecret(clientSecret + clientSignature) && hostKnewPin
    }

    @Test
    fun aClientWithThePinPairs() = assertTrue(pairWith("1234", "1234"))

    @Test
    fun aWrongPinDoesNot() = assertFalse(pairWith("1234", "4321"))

    @Test
    fun theHostsCertificateReadsBackFromItsPem() {
        assertArrayEquals(host.cert.encoded, StreamCert.fromPem(host.pem).encoded)
        assertEquals("CN=Flux Keyboard", host.cert.subjectX500Principal.name)
    }

    @Test
    fun hexRoundTrips() {
        val bytes = byteArrayOf(0, 15, -1, 127)
        assertEquals("000fff7f", StreamPairing.hex(bytes))
        assertArrayEquals(bytes, StreamPairing.unhex("000FFF7F"))
    }
}
