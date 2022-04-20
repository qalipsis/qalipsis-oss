package io.qalipsis.core.head.security.impl

import io.micronaut.context.annotation.Requires
import io.qalipsis.core.head.security.Auth0Patch
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.entity.UserIdentity
import jakarta.inject.Singleton

/**
 * There is a default implementation of [IdentityManagement] interface that encapsulate
 *
 * @author Palina Bril
 */
@Requires(property = "identity.manager", notEquals = "auth0")
@Singleton
internal class IdentityManagementImpl : IdentityManagement {

    override suspend fun get(tenant: String, username: String): UserIdentity {
        return UserIdentity(username = "qalipsis", name = "qalipsis", email = "foo@bar.com")
    }

    override suspend fun save(tenant: String, user: UserIdentity): UserIdentity {
        return UserIdentity(username = "qalipsis", name = "qalipsis", email = "foo@bar.com")
    }

    override suspend fun delete(username: String, identityReference: String) {
    }

    override suspend fun update(
        tenant: String, identityReference: String, user: UserIdentity, userPatches: List<Auth0Patch>
    ) {
    }
}