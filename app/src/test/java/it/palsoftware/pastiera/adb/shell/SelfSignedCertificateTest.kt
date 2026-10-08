package it.palsoftware.pastiera.adb.shell

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class SelfSignedCertificateTest {
    @Test
    fun parsesAndVerifiesWithItsOwnKey() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val bytes = SelfSignedCertificate.create(pair.public.encoded, pair.private, "Flux Keyboard")
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream()) as X509Certificate
        cert.verify(pair.public)
        cert.checkValidity()
        assertEquals(pair.public, cert.publicKey)
        assertEquals("CN=Flux Keyboard", cert.subjectX500Principal.name)
    }
}
