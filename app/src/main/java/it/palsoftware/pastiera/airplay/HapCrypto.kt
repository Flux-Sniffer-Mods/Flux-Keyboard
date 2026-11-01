package it.palsoftware.pastiera.airplay

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/** The cryptography HomeKit pairing uses, as AirPlay receivers expect it. */
internal object HapCrypto {
    val random = SecureRandom()

    fun randomBytes(size: Int) = ByteArray(size).also { random.nextBytes(it) }

    /** HKDF-SHA512, 32 bytes: the keys each side derives from a shared secret. */
    fun hkdf(salt: String, info: String, secret: ByteArray): ByteArray {
        val generator = HKDFBytesGenerator(SHA512Digest())
        generator.init(HKDFParameters(secret, salt.toByteArray(), info.toByteArray()))
        return ByteArray(32).also { generator.generateBytes(it, 0, it.size) }
    }

    /** A 12-byte nonce from a shorter one (a counter, or a label like "PS-Msg05"): zeros in front. */
    fun nonce(short: ByteArray): ByteArray = ByteArray(12 - short.size) + short

    fun counterNonce(counter: Long): ByteArray = nonce(ByteArray(8) { (counter shr (8 * it)).toByte() })

    fun seal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray =
        chacha(true, key, nonce, plain, aad)

    /** Null when it doesn't open (wrong key, or changed on the way). */
    fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray? = null): ByteArray? =
        runCatching { chacha(false, key, nonce, sealed, aad) }.getOrNull()

    private fun chacha(encrypt: Boolean, key: ByteArray, nonce: ByteArray, input: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(input.size))
        val n = cipher.processBytes(input, 0, input.size, out, 0)
        val total = n + cipher.doFinal(out, n)
        return if (total == out.size) out else out.copyOf(total)
    }

    class X25519Pair(val private: X25519PrivateKeyParameters) {
        val public: ByteArray = private.generatePublicKey().encoded
        fun agree(peer: ByteArray): ByteArray = ByteArray(32).also {
            X25519Agreement().apply { init(private) }.calculateAgreement(X25519PublicKeyParameters(peer, 0), it, 0)
        }
    }

    fun x25519() = X25519Pair(X25519PrivateKeyParameters(random))

    fun edPublic(seed: ByteArray): ByteArray = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded

    fun edSign(seed: ByteArray, message: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, Ed25519PrivateKeyParameters(seed, 0))
        update(message, 0, message.size)
        generateSignature()
    }

    fun edVerify(public: ByteArray, message: ByteArray, signature: ByteArray): Boolean = runCatching {
        Ed25519Signer().run {
            init(false, Ed25519PublicKeyParameters(public, 0))
            update(message, 0, message.size)
            verifySignature(signature)
        }
    }.getOrDefault(false)
}

/**
 * The client side of SRP-6a as HomeKit pairing does it (3072-bit group, SHA-512, user
 * "Pair-Setup", the PIN as password), number for number as the receivers check it: numbers hashed
 * at their shortest unless padded to the group's size, the salt as it came.
 */
internal class SrpClient(private val pin: String, private val a: BigInteger = BigInteger(1, HapCrypto.randomBytes(32))) {
    /** The client's public value A (sent in M3). */
    lateinit var publicKey: ByteArray
        private set
    /** The proof M (sent in M3). */
    lateinit var proof: ByteArray
        private set
    /** The shared session key K: what the pairing's keys are derived from. */
    lateinit var sessionKey: ByteArray
        private set

    fun process(salt: ByteArray, serverPublic: ByteArray) {
        val b = BigInteger(1, serverPublic)
        val publicA = G.modPow(a, N)
        val k = int(sha(bytes(N), pad(G)))
        val x = int(sha(salt, sha("$USER:$pin".toByteArray())))
        val u = int(sha(pad(publicA), pad(b)))
        val v = G.modPow(x, N)
        val secret = b.subtract(k.multiply(v)).mod(N).modPow(a.add(u.multiply(x)), N)
        val key = sha(bytes(secret))
        val nXorG = int(sha(bytes(N))).xor(int(sha(bytes(G))))
        proof = sha(bytes(nXorG), bytes(int(sha(USER.toByteArray()))), salt, bytes(publicA), bytes(b), key)
        publicKey = bytes(publicA)
        sessionKey = key
    }

    private fun sha(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        parts.forEach { digest.update(it) }
        return digest.digest()
    }

    private fun int(hash: ByteArray) = BigInteger(1, hash)

    private fun pad(value: BigInteger): ByteArray = bytes(value).let { ByteArray(SIZE - it.size) + it }

    companion object {
        private const val USER = "Pair-Setup"
        internal val N = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
                "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
                "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
                "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF", 16
        )
        private val G = BigInteger.valueOf(5)
        private val SIZE = bytes(N).size

        /** A number's bytes at their shortest (no sign byte). */
        fun bytes(value: BigInteger): ByteArray {
            val raw = value.toByteArray()
            return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        }
    }
}
