package io.qalipsis.core.head.security

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.qalipsis.core.head.jdbc.entity.UserEntity
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Size

/**
 * Interface that has several implementations, each one being in charge of changing only one aspect of a user
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DisplayNameUserPatch::class, name = "displayName"),
    JsonSubTypes.Type(value = EmailAddressUserPatch::class, name = "email"),
    JsonSubTypes.Type(value = UsernameUserPatch::class, name = "username"),
    JsonSubTypes.Type(value = AddRoleUserPatch::class, name = "addRole"),
    JsonSubTypes.Type(value = RemoveRoleUserPatch::class, name = "removeRole")
)
internal interface UserPatch {
    /**
     * Applies a change on the [UserEntity] and returns true if and only if the change was actually
     * performed.
     */
    fun apply(user: UserEntity): Boolean = false
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing displayName property of a user
 */
internal class DisplayNameUserPatch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    internal val newDisplayName: String
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
    internal val newEmailAddress: String
) : UserPatch {

    override fun apply(user: UserEntity) = false
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

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing roles property of a user
 */
internal class AddRoleUserPatch(
    @field:NotEmpty
    var rolesToAssign: Collection<RoleName>
) : UserPatch {

    override fun apply(user: UserEntity) = false
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing roles property of a user
 */
internal class RemoveRoleUserPatch(
    @field:NotEmpty
    var rolesToRemove: Collection<RoleName>
) : UserPatch {

    override fun apply(user: UserEntity) = false
}