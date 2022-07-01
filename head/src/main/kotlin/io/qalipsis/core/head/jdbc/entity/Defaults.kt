package io.qalipsis.core.head.jdbc.entity

import io.qalipsis.core.head.model.Profile
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.security.User

/**
 * Constants for the default values.
 *
 * @author Eric Jessé
 */
internal object Defaults {

    /**
     * Username of QALIPSIS default user.
     */
    const val USER = "_qalipsis_"

    /**
     * Reference of QALIPSIS default tenant.
     */
    const val TENANT = "_qalipsis_ten_"

    /**
     * Profile of QALIPSIS default user.
     */
    val PROFILE = Profile(
        user = User(tenant = TENANT, username = USER),
        tenants = setOf(Tenant(TENANT, ""))
    )
}