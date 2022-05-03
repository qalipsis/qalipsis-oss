package io.qalipsis.core.head.security.impl

import io.micronaut.context.annotation.Requires
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

    override suspend fun get(username: String): UserIdentity {
        return UserIdentity(username = "qalipsis", name = "qalipsis", email = "foo@bar.com")
    }

    override suspend fun save(tenantName: String, user: UserIdentity): UserIdentity {
        return UserIdentity(username = "qalipsis", name = "qalipsis", email = "foo@bar.com")
    }

    override suspend fun delete(username: String, identityReference: String) {
    }

    override suspend fun update(tenantName: String, identityReference: String, user: UserIdentity) {
    }
}