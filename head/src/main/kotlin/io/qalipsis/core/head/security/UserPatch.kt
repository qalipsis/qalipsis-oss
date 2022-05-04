package io.qalipsis.core.head.security

import io.qalipsis.core.head.security.entity.QalipsisUser
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Interface that has several implementations, each one being in charge of changing only one aspect of a user
 */
interface UserPatch {
    /**
     * Applies a change on the [QalipsisUser] and returns true if and only if the change was actually
     * performed.
     */
    fun apply(user: QalipsisUser): Boolean
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing displayName property of a user
 */
internal class DisplayNameUserPatch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    private val newDisplayName: String
) : UserPatch {
    override fun apply(user: QalipsisUser): Boolean {
        return if (user.name != newDisplayName.trim()) {
            user.name = newDisplayName.trim()
            true
        } else {
            false
        }
    }
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing emailAddress property of a user
 */
internal class EmailAddressUserPatch(
    @field:NotBlank @field:Email
    private val newEmailAddress: String
) : UserPatch {
    override fun apply(user: QalipsisUser): Boolean {
        return if (user.email != newEmailAddress.trim()) {
            user.email = newEmailAddress.trim()
            true
        } else {
            false
        }
    }
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing username property of a user
 */
internal class UsernameUserPatch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    internal val newUsername: String
) : UserPatch {
    override fun apply(user: QalipsisUser): Boolean {
        return if (user.username != newUsername.trim()) {
            user.username = newUsername.trim()
            true
        } else {
            false
        }
    }
}