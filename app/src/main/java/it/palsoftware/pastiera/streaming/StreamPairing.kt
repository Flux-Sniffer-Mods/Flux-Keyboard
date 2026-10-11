package it.palsoftware.pastiera.streaming

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * One Moonlight client pairing, the host's side of GameStream's PIN pairing (as Sunshine does
 * it): the client sends its certificate and a salt, the PIN shown on the client and typed on the
 * phone makes an AES key, and each side proves it knows the PIN by hashing a challenge with its
 * certificate's signature and a secret it then signs. Each step answers the client's request
 * with what its next one needs; the client is trusted once the last step checks out.
 */
class StreamPairing(private val host: StreamCert, val client: X509Certificate, private val salt: ByteArray) {
    private val random = SecureRandom()
    private var key: SecretKeySpec? = null
    private val serverSecret = ByteArray(16).also { random.nextBytes(it) }
    private val serverChallenge = ByteArray(16).also { random.nextBytes(it) }
    private var clientHash: ByteArray? = null

    /** The PIN typed on the phone: the AES key both sides share (SHA-256 of salt and PIN, its first 16 bytes). */
    fun pin(pin: String) {
        key = SecretKeySpec(sha256(salt + pin.toByteArray(Charsets.UTF_8)).copyOf(16), "AES")
    }

    /** The client's challenge: answered with a hash only a host knowing the PIN could make, and a challenge back. */
    fun clientChallenge(encrypted: ByteArray): ByteArray {
        val challenge = aes(Cipher.DECRYPT_MODE, encrypted)
        val hash = sha256(challenge + host.cert.signature + serverSecret)
        return aes(Cipher.ENCRYPT_MODE, hash + serverChallenge)
    }

    /** The client's answer to the host's challenge, kept to check; the host's secret, signed, in return. */
    fun serverChallengeResponse(encrypted: ByteArray): ByteArray {
        clientHash = aes(Cipher.DECRYPT_MODE, encrypted)
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(host.key); update(serverSecret) }.sign()
        return serverSecret + signature
    }

    /** The client's secret, signed: true when its signature and its answer both check out. */
    fun clientPairingSecret(data: ByteArray): Boolean {
        if (data.size <= 16) return false
        val secret = data.copyOfRange(0, 16)
        val signature = data.copyOfRange(16, data.size)
        val signed = runCatching {
            Signature.getInstance("SHA256withRSA").apply { initVerify(client.publicKey); update(secret) }.verify(signature)
        }.getOrDefault(false)
        val expected = sha256(serverChallenge + client.signature + secret)
        return signed && clientHash?.let { MessageDigest.isEqual(it.copyOf(expected.size), expected) } == true
    }

    private fun aes(mode: Int, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(mode, key ?: error("no PIN yet"))
        return cipher.doFinal(data)
    }

    companion object {
        fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun unhex(text: String): ByteArray =
            ByteArray(text.length / 2) { i -> text.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
