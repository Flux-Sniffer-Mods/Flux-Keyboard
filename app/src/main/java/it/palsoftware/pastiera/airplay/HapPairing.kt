package it.palsoftware.pastiera.airplay

import java.io.IOException
import java.util.UUID

/** What a PIN pairing leaves to connect again without one: both sides' long-term keys. */
internal data class HapCredentials(
    /** The receiver's Ed25519 public key. */
    val receiverKey: ByteArray,
    /** The receiver's pairing identifier. */
    val receiverId: ByteArray,
    /** This phone's Ed25519 seed. */
    val clientSeed: ByteArray,
    /** This phone's pairing identifier. */
    val clientId: ByteArray
) {
    fun encode(): String = listOf(receiverKey, receiverId, clientSeed, clientId).joinToString(":") { it.hex() }

    companion object {
        fun decode(text: String?): HapCredentials? {
            val parts = text?.split(':')?.takeIf { it.size == 4 } ?: return null
            return runCatching {
                val (a, b, c, d) = parts.map { it.unhex() }
                HapCredentials(a, b, c, d)
            }.getOrNull()
        }
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

internal fun String.unhex(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** The receiver turned the pairing down: [code] is HomeKit's (2 wrong PIN, 3 too many tries…). */
internal class PairingRefused(val code: Int) : IOException("pairing refused ($code)")

/**
 * HomeKit pairing over AirPlay (`/pair-setup`, `/pair-verify`), which gives the keys that
 * encrypt the session. Transient with the fixed PIN where the receiver lets anyone on the
 * network play; otherwise a PIN the TV shows, paired once ([HapCredentials]) and verified
 * after. The receiver's secret is the [secret] the session's keys are derived from.
 */
internal class HapPairing(private val connection: AirPlayConnection) {
    /** The shared secret from the last pairing or verification. */
    var secret: ByteArray? = null
        private set

    private var salt: ByteArray? = null
    private var serverKey: ByteArray? = null

    private fun post(path: String, body: ByteArray?, hkp: Int): Message {
        val answer = connection.request("POST", path, listOf(
            "User-Agent" to "AirPlay/320.20",
            "Connection" to "keep-alive",
            "X-Apple-HKP" to hkp,
            "Content-Type" to "application/octet-stream"
        ), body)
        if (answer.code == 470 || answer.code == 403) throw PairingRefused(answer.code)
        if (!answer.ok) throw IOException("$path: ${answer.first}")
        return answer
    }

    private fun tlv(answer: Message): Map<Int, ByteArray> {
        val tlv = Tlv8.decode(answer.body)
        tlv[Tlv8.ERROR]?.let { throw PairingRefused(it.firstOrNull()?.toInt() ?: -1) }
        return tlv
    }

    /** Pairs for this connection only; [PairingRefused] when the receiver wants its own PIN. */
    fun transient() {
        // Not every receiver answers this one well; the pairing itself says whether it's allowed
        runCatching { post("/pair-pin-start", null, HKP_TRANSIENT) }
        val m2 = tlv(post("/pair-setup", Tlv8.encode(
            Tlv8.METHOD to byteArrayOf(0),
            Tlv8.SEQ_NO to byteArrayOf(1),
            Tlv8.FLAGS to byteArrayOf(Tlv8.FLAG_TRANSIENT.toByte())
        ), HKP_TRANSIENT))
        val srp = SrpClient(TRANSIENT_PIN)
        srp.process(m2.need(Tlv8.SALT), m2.need(Tlv8.PUBLIC_KEY))
        tlv(post("/pair-setup", Tlv8.encode(
            Tlv8.SEQ_NO to byteArrayOf(3),
            Tlv8.PUBLIC_KEY to srp.publicKey,
            Tlv8.PROOF to srp.proof
        ), HKP_TRANSIENT))
        secret = srp.sessionKey
    }

    /** Asks the receiver to show a PIN; [finish] with what the person types. */
    fun start() {
        post("/pair-pin-start", null, HKP_PIN)
        val m2 = tlv(post("/pair-setup", Tlv8.encode(
            Tlv8.METHOD to byteArrayOf(0),
            Tlv8.SEQ_NO to byteArrayOf(1)
        ), HKP_PIN))
        salt = m2.need(Tlv8.SALT)
        serverKey = m2.need(Tlv8.PUBLIC_KEY)
    }

    /** The PIN shown on the TV: the credentials kept to connect again without it. */
    fun finish(pin: String): HapCredentials {
        val srp = SrpClient(pin.trim().padStart(4, '0'))
        srp.process(salt ?: throw IOException("not started"), serverKey ?: throw IOException("not started"))
        tlv(post("/pair-setup", Tlv8.encode(
            Tlv8.SEQ_NO to byteArrayOf(3),
            Tlv8.PUBLIC_KEY to srp.publicKey,
            Tlv8.PROOF to srp.proof
        ), HKP_PIN))
        val seed = HapCrypto.randomBytes(32)
        val ownKey = HapCrypto.edPublic(seed)
        val clientId = UUID.randomUUID().toString().uppercase().toByteArray()
        val signKey = HapCrypto.hkdf("Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info", srp.sessionKey)
        val encryptKey = HapCrypto.hkdf("Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info", srp.sessionKey)
        val signature = HapCrypto.edSign(seed, signKey + clientId + ownKey)
        val inner = Tlv8.encode(Tlv8.IDENTIFIER to clientId, Tlv8.PUBLIC_KEY to ownKey, Tlv8.SIGNATURE to signature)
        val m6 = tlv(post("/pair-setup", Tlv8.encode(
            Tlv8.SEQ_NO to byteArrayOf(5),
            Tlv8.ENCRYPTED_DATA to HapCrypto.seal(encryptKey, HapCrypto.nonce("PS-Msg05".toByteArray()), inner)
        ), HKP_PIN))
        val opened = HapCrypto.open(encryptKey, HapCrypto.nonce("PS-Msg06".toByteArray()), m6.need(Tlv8.ENCRYPTED_DATA))
            ?: throw IOException("pairing answer didn't decrypt")
        val device = Tlv8.decode(opened)
        secret = srp.sessionKey
        return HapCredentials(device.need(Tlv8.PUBLIC_KEY), device.need(Tlv8.IDENTIFIER), seed, clientId)
    }

    /** Proves this phone is the one paired before, and that the receiver is the one paired with. */
    fun verify(credentials: HapCredentials) {
        val own = HapCrypto.x25519()
        val m2 = tlv(post("/pair-verify", Tlv8.encode(
            Tlv8.SEQ_NO to byteArrayOf(1),
            Tlv8.PUBLIC_KEY to own.public
        ), HKP_PIN))
        val theirs = m2.need(Tlv8.PUBLIC_KEY)
        val shared = own.agree(theirs)
        val key = HapCrypto.hkdf("Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info", shared)
        val opened = HapCrypto.open(key, HapCrypto.nonce("PV-Msg02".toByteArray()), m2.need(Tlv8.ENCRYPTED_DATA))
            ?: throw IOException("verify answer didn't decrypt")
        val device = Tlv8.decode(opened)
        val id = device.need(Tlv8.IDENTIFIER)
        if (!id.contentEquals(credentials.receiverId) ||
            !HapCrypto.edVerify(credentials.receiverKey, theirs + id + own.public, device.need(Tlv8.SIGNATURE))
        ) throw PairingRefused(2)
        val signature = HapCrypto.edSign(credentials.clientSeed, own.public + credentials.clientId + theirs)
        val inner = Tlv8.encode(Tlv8.IDENTIFIER to credentials.clientId, Tlv8.SIGNATURE to signature)
        tlv(post("/pair-verify", Tlv8.encode(
            Tlv8.SEQ_NO to byteArrayOf(3),
            Tlv8.ENCRYPTED_DATA to HapCrypto.seal(key, HapCrypto.nonce("PV-Msg03".toByteArray()), inner)
        ), HKP_PIN))
        secret = shared
    }

    /** The two keys for a channel: (this side's sending key, its receiving key). */
    fun keys(salt: String, outInfo: String, inInfo: String): Pair<ByteArray, ByteArray> {
        val shared = secret ?: throw IOException("not paired")
        return HapCrypto.hkdf(salt, outInfo, shared) to HapCrypto.hkdf(salt, inInfo, shared)
    }

    private fun Map<Int, ByteArray>.need(tag: Int) = this[tag] ?: throw IOException("pairing answer without $tag")

    companion object {
        const val TRANSIENT_PIN = "3939"
        private const val HKP_PIN = 3
        private const val HKP_TRANSIENT = 4
    }
}
