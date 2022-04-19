package io.qalipsis.core.head.security

import io.qalipsis.core.head.jdbc.entity.UserEntity
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

interface UserPatch {
    /**
     * Applies a change on the [UserEntity] and returns true if and only if the change was actually
     * performed.
     */
    fun apply(user: UserEntity): Boolean
}

internal class DisplayNameUserPatch(
    @field:NotBlank @field:Size(
        min = 1,
        max = 150
    ) private val newDisplayName: String
) :
    UserPatch {
    override fun apply(user: UserEntity): Boolean {
        return if (user.displayName != newDisplayName) {
            user.displayName = newDisplayName
            true
        } else {
            false
        }
    }
}

internal class EmailAddressUserPatch(@field:NotBlank @field:Email private val newEmailAddress: String) : UserPatch {
    override fun apply(user: UserEntity): Boolean {
        return if (user.emailAddress != newEmailAddress) {
            user.emailAddress = newEmailAddress
            true
        } else {
            false
        }
    }
}

internal class UsernameUserPatch(@field:NotBlank @field:Size(min = 1, max = 150) internal val newUsername: String) :
    UserPatch {
    override fun apply(user: UserEntity): Boolean {
        return if (user.username != newUsername) {
            user.username = newUsername
            true
        } else {
            false
        }
    }
}