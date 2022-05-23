package io.qalipsis.core.head.web

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.security.Security
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Utility class to provide a pair of private/public keys from a unique private key.
 *
 * @author Eric Jessé
 */
internal object KeyPairProvider {

    private val converter = JcaPEMKeyConverter()

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    fun fromClassPath(privateKeyPath: String): Pair<RSAPrivateKey, RSAPublicKey> {
        val actualPath = privateKeyPath.takeIf { it.startsWith("/") } ?: "/${privateKeyPath}"
        return PEMParser(KeyPairProvider::class.java.getResourceAsStream(actualPath)!!.reader()).use {
            val permParser = PEMParser(it)
            val pemKeyPair = permParser.readObject() as PEMKeyPair
            // Convert to Java (JCA) Format
            val keyPair = converter.getKeyPair(pemKeyPair)
            keyPair.private as RSAPrivateKey to keyPair.public as RSAPublicKey
        }
    }
}