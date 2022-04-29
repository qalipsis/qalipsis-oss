package io.qalipsis.core.head.security.auth0

import com.auth0.client.auth.AuthAPI
import com.auth0.client.mgmt.ManagementAPI
import com.auth0.exception.Auth0Exception
import com.auth0.json.mgmt.users.User
import io.micronaut.context.annotation.Requires
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.entity.UserIdentity
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * API for making calls to our application for saving, getting and so on data to/from Auth0
     */
    private var managementAPI: ManagementAPI? = null

    /**
     * API for getting and updating tokens
     */
    private val authAPI = AuthAPI(auth0Properties.domain, auth0Properties.clientId, auth0Properties.clientSecret)

    /**
     * Token details for Auth0
     */
    @Volatile
    private var auth0Token: Auth0Token? = null

    /**
     * Extra time between token expiration and time to make a call.
     * The experation itself is calculated in AuthToken in the field expiresAt
     */
    private val expirationBufferSec: Long = 10
    private val managementApiCreationMutex = Mutex()

    @Throws(Auth0Exception::class)
    suspend fun getManagementAPI(): ManagementAPI {
        val currentTimeEpochSecs = Instant.now().epochSecond
        if (auth0Token == null || auth0Token!!.expiresAt < currentTimeEpochSecs + expirationBufferSec) {
            managementApiCreationMutex.withLock {
                if (auth0Token == null || auth0Token!!.expiresAt < currentTimeEpochSecs + expirationBufferSec) {
                    val tokenHolder = authAPI.requestToken(auth0Properties.apiIdentifier).execute()
                    auth0Token = Auth0Token(tokenHolder.accessToken, tokenHolder.expiresIn)
                    managementAPI = ManagementAPI(auth0Properties.domain, auth0Token!!.accessToken)
                }
            }
        }
        if (managementAPI == null) {
            throw Auth0Exception("The identity management API could not be contacted")
        }
        return managementAPI!!
    }

    @Throws(Auth0Exception::class)
    override suspend fun get(identityReference: String): UserIdentity {
        val authUser = getManagementAPI().users().get(identityReference, null).execute()
        return UserIdentity(
            username = authUser.username,
            email = authUser.email,
            name = authUser.name,
            email_verified = authUser.isEmailVerified,
            user_id = authUser.id
        )
    }

    @Throws(Auth0Exception::class)
    override suspend fun save(user: UserIdentity): UserIdentity {
        val authUser = User()
        authUser.email = user.email
        authUser.username = user.username
        authUser.name = user.name
        authUser.isEmailVerified = user.email_verified
        authUser.setVerifyEmail(user.verify_email)
        authUser.setPassword((user.password).toCharArray())
        authUser.setConnection(user.connection)
        val userWithId = getManagementAPI().users().create(authUser).execute()
        return UserIdentity(
            username = userWithId.username,
            email = userWithId.email,
            name = userWithId.name,
            email_verified = userWithId.isEmailVerified,
            user_id = userWithId.id
        )
    }

    @Throws(Auth0Exception::class)
    override suspend fun update(identityReference: String, user: UserIdentity) {
        val authUser = User()
        authUser.username = user.username
        authUser.name = user.name
        getManagementAPI().users().update(identityReference, authUser).execute()
        val userWithNewMail = User()
        userWithNewMail.email = user.email
        getManagementAPI().users().update(identityReference, userWithNewMail).execute()
    }

    @Throws(Auth0Exception::class)
    override suspend fun delete(identityReference: String) {
        getManagementAPI().users().delete(identityReference).execute()
    }

    @Throws(Auth0Exception::class)
    suspend fun getUsers(): MutableList<User> {
        val users = getManagementAPI().users().list(null).execute()
        return users.items
    }
}
