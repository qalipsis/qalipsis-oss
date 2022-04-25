package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Interface that has several implementations, each one being in charge of changing only one aspect of a user
 */
interface UserPatch {
    /**
     * Applies a change on the [UserEntity] and returns true if and only if the change was actually
     * performed.
     */
    fun apply(user: UserEntity): Boolean
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing displayName property of a user
 */
internal class DisplayNameUserPatch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    private val newDisplayName: String
) : UserPatch {
    override fun apply(user: UserEntity): Boolean {
        return if (user.displayName != newDisplayName.trim()) {
            user.displayName = newDisplayName.trim()
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
    override fun apply(user: UserEntity): Boolean {
        return if (user.emailAddress != newEmailAddress.trim()) {
            user.emailAddress = newEmailAddress.trim()
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
    override fun apply(user: UserEntity): Boolean {
        return if (user.username != newUsername.trim()) {
            user.username = newUsername.trim()
            true
        } else {
            false
        }
    }
}