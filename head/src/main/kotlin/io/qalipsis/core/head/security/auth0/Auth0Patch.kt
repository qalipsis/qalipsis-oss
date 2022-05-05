package io.qalipsis.core.head.security

import com.auth0.json.mgmt.users.User
import io.qalipsis.core.head.security.entity.QalipsisUser
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Interface that has several implementations, each one being in charge of changing only one aspect of a user
 */
internal interface Auth0Patch {
    /**
     * Applies a change on the [QalipsisUser] and returns true if and only if the change was actually
     * performed.
     */
    fun apply(user: User): Boolean
}

/**
 * Implementation of the [Auth0Patch] interface, that is in charge of changing name property of a user
 */
internal class NameAuth0Patch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    private val newDisplayName: String
) : Auth0Patch {
    override fun apply(user: User): Boolean {
        return if (user.name != newDisplayName.trim()) {
            user.name = newDisplayName.trim()
            true
        } else {
            false
        }
    }
}

/**
 * Implementation of the [Auth0Patch] interface, that is in charge of changing email property of a user
 */
internal class EmailAuth0Patch(
    @field:NotBlank @field:Email
    private val newEmailAddress: String
) : Auth0Patch {
    override fun apply(user: User): Boolean {
        return if (user.email != newEmailAddress.trim()) {
            user.email = newEmailAddress.trim()
            true
        } else {
            false
        }
    }
}

/**
 * Implementation of the [Auth0Patch] interface, that is in charge of changing username property of a user
 */
internal class UsernameAuth0Patch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    internal val newUsername: String
) : Auth0Patch {
    override fun apply(user: User): Boolean {
        return if (user.username != newUsername.trim()) {
            user.username = newUsername.trim()
            true
        } else {
            false
        }
    }
}

/**
 * Implementation of the [Auth0Patch] interface, that is in charge of changing roles property of a user
 */
internal class RoleAuth0Patch : Auth0Patch {
    override fun apply(user: User): Boolean {
        return false
    }
}