package io.qalipsis.core.head.web

import com.nimbusds.jose.JWSAlgorithm
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.config.TokenConfiguration
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.generator.claims.ClaimsGenerator
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGenerator
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorConfiguration
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey


@Factory
@Context
@Requires(property = "jwt.generators")
internal class JwtGeneratorFactory {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Singleton
    fun rSASignatureGeneratorConfiguration(
        configuration: JwtGenerationConfiguration
    ): RSASignatureGeneratorConfiguration {
        return TokenRsaSignatureGeneratorConfiguration(configuration)
    }

    @Singleton
    fun rsaSignatureGenerator(configuration: RSASignatureGeneratorConfiguration): SignatureGeneratorConfiguration {
        return RSASignatureGenerator(configuration)
    }

}

/**
 * Utility class to generate JWT using accepted or not accepted.
 * You can inject it into integration tests to verify the security compliance of the REST endpoints.
 *
 * @author Eric Jessé
 */
@Singleton
class JwtGenerator(
    @Named("valid") configurationForValidToken: JwtGenerationConfiguration,
    @Named("invalid") configurationForInvalidToken: JwtGenerationConfiguration,
    claimsGenerator: ClaimsGenerator
) {

    private val validJwtTokenGenerator = JwtTokenGenerator(
        RSASignatureGenerator(TokenRsaSignatureGeneratorConfiguration(configurationForValidToken)), null,
        claimsGenerator
    )

    private val invalidJwtTokenGenerator = JwtTokenGenerator(
        RSASignatureGenerator(TokenRsaSignatureGeneratorConfiguration(configurationForInvalidToken)), null,
        claimsGenerator
    )

    /**
     * Generates a JWT considered as valid, in the sense that the private key used to generate is compliant
     * with the public key to validate the token on the web server.
     */
    fun generateValidToken(
        name: String,
        roles: Collection<String> = emptySet(),
        vararg claims: Pair<String, Any>
    ): String {
        return validJwtTokenGenerator.generateToken(TokenAuthentication(name, roles, claims.toMap()), null).get()
    }

    /**
     * Generates a JWT considered as invalid, in the sense that the private key used to generate is NOT compliant
     * with the public key to validate the token on the web server.
     */
    fun generateInvalidToken(
        name: String,
        roles: Collection<String> = emptySet(),
        vararg claims: Pair<String, Any>
    ): String {
        return invalidJwtTokenGenerator.generateToken(TokenAuthentication(name, roles, claims.toMap()), null).get()
    }

}

/**
 * Implementation of [Authentication] to generate a JWT.
 *
 * @author Eric Jessé
 */
private data class TokenAuthentication(
    private val name: String,
    private val roles: Collection<String>,
    private val claims: Map<String, Any> = emptyMap()
) : Authentication {

    override fun getName() = name

    override fun getRoles() = roles

    override fun getAttributes() = mapOf(
        TokenConfiguration.DEFAULT_NAME_KEY to name
    ) + claims
}

/**
 * Actual configuration of the signature.
 */
private class TokenRsaSignatureGeneratorConfiguration(configuration: JwtGenerationConfiguration) :
    RSASignatureGeneratorConfiguration {

    private val rsaPrivateKey: RSAPrivateKey

    private val rsaPublicKey: RSAPublicKey

    private val jwsAlgorithm = configuration.jwsAlgorithm

    init {
        val keyPair = KeyPairProvider.fromClassPath(configuration.path)
        rsaPrivateKey = keyPair.first
        rsaPublicKey = keyPair.second
    }

    override fun getPublicKey() = rsaPublicKey

    override fun getPrivateKey() = rsaPrivateKey

    override fun getJwsAlgorithm(): JWSAlgorithm = jwsAlgorithm

}

/**
 * Class to load the configuration from the configuration file.
 */
@Requires(property = "jwt.generators")
@EachProperty("jwt.generators", primary = "valid")
class JwtGenerationConfiguration(@Parameter val name: String) {

    var path = ""

    var jwsAlgorithm = JWSAlgorithm.RS256
}
