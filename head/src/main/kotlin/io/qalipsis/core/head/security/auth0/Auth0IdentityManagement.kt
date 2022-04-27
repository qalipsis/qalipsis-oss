package io.qalipsis.core.head.security.auth0

import com.auth0.client.auth.AuthAPI
import com.auth0.client.mgmt.ManagementAPI
import com.auth0.exception.Auth0Exception
import com.auth0.json.mgmt.users.User
import io.micronaut.context.annotation.Requires
import io.qalipsis.core.head.security.IdentityManagement
import jakarta.inject.Singleton
import java.time.Instant

/**
 * There is an Auth0 implementation of [IdentityManagement] interface
 *
 * @author Palina Bril
 */

@Requires(property = "identity.manager", value = "auth0")
@Singleton
internal class Auth0IdentityManagement(
    val auth0Properties: Auth0Configuration
) : IdentityManagement {

    private var managementAPI: ManagementAPI? = null
    private val authAPI = AuthAPI(auth0Properties.domain, auth0Properties.clientId, auth0Properties.clientSecret)
    private var auth0Token: Auth0Token? = null
    private val ACCESS_TOKEN_EXPIRATION_BUFFER_SEC: Long = 10

    @Throws(Auth0Exception::class)
    fun getManagementAPI(): ManagementAPI? {
        val currentTimeEpochSecs = Instant.now().epochSecond
        if (auth0Token == null || auth0Token!!.expiresAt < currentTimeEpochSecs + ACCESS_TOKEN_EXPIRATION_BUFFER_SEC) {
            val tokenHolder = authAPI.requestToken(auth0Properties.apiIdentifier).execute()
            auth0Token = Auth0Token(tokenHolder.accessToken, tokenHolder.expiresIn)
            managementAPI = ManagementAPI(auth0Properties.domain, auth0Token!!.accessToken)
        }
        return managementAPI
    }

    @Throws(Auth0Exception::class)
    override suspend fun get(identityReference: String): User {
        return getManagementAPI()!!.users().get(identityReference, null).execute()
    }

    @Throws(Auth0Exception::class)
    override suspend fun save(user: User): User {
        return getManagementAPI()!!.users().create(user).execute()
    }

    @Throws(Auth0Exception::class)
    override suspend fun update(identityReference: String, user: User) {
        val authUser = User()
        authUser.username = user.username
        authUser.name = user.name
        getManagementAPI()!!.users().update(identityReference, authUser).execute()
        val userWithNewMail = User()
        userWithNewMail.email = user.email
        getManagementAPI()!!.users().update(identityReference, userWithNewMail).execute()
    }

    @Throws(Auth0Exception::class)
    override suspend fun delete(identityReference: String) {
        getManagementAPI()!!.users().delete(identityReference).execute()
    }

    @Throws(Auth0Exception::class)
    fun getUsers(): MutableList<User> {
        val users = getManagementAPI()!!.users().list(null).execute()
        return users.items
    }
}
