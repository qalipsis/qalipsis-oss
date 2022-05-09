package io.qalipsis.core.head.security.auth0

import com.auth0.json.mgmt.Role
import io.qalipsis.core.head.security.RoleName

/**
 * Creates the unique role name for the tenant.
 */
internal fun RoleName.forTenant(tenant: String) = "$tenant:${this.publicName}"

/**
 * Extracts the [RoleName] from a Auth0 [Role].
 */
internal fun Role.asRoleName(tenant: String) = RoleName.fromPublicName(name.substringAfter("$tenant:"))