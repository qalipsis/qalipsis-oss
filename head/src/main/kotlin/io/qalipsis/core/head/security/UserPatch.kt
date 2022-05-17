package io.qalipsis.core.head.security

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.core.annotation.Introspected
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
    JsonSubTypes.Type(value = DisplayNameUserPatch::class, name = DisplayNameUserPatch.TYPE),
    JsonSubTypes.Type(value = EmailAddressUserPatch::class, name = EmailAddressUserPatch.TYPE),
    JsonSubTypes.Type(value = UsernameUserPatch::class, name = UsernameUserPatch.TYPE),
    JsonSubTypes.Type(value = AddRoleUserPatch::class, name = AddRoleUserPatch.TYPE),
    JsonSubTypes.Type(value = RemoveRoleUserPatch::class, name = RemoveRoleUserPatch.TYPE)
)
@Introspected
internal interface UserPatch {
    /**
     * Applies a change on the [UserEntity] and returns true if and only if the change was actually
     * performed.
     */
    fun apply(user: UserEntity): Boolean = false

    val type: String
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing displayName property of a user
 */
@Introspected
internal class DisplayNameUserPatch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    internal val newDisplayName: String
) : UserPatch {

    override val type: String = TYPE

    override fun apply(user: UserEntity): Boolean {
        return if (user.displayName != newDisplayName.trim()) {
            user.displayName = newDisplayName.trim()
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "displayName"
    }
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing emailAddress property of a user
 */
@Introspected
internal class EmailAddressUserPatch(
    @field:NotBlank @field:Email
    internal val newEmailAddress: String
) : UserPatch {

    override val type: String = TYPE

    override fun apply(user: UserEntity) = false

    companion object {
        const val TYPE = "email"
    }
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing username property of a user
 */
internal class UsernameUserPatch(
    @field:NotBlank @field:Size(min = 1, max = 150)
    internal val newUsername: String
) : UserPatch {

    override val type: String = TYPE

    override fun apply(user: UserEntity): Boolean {
        return if (user.username != newUsername.trim()) {
            user.username = newUsername.trim()
            true
        } else {
            false
        }
    }

    companion object {
        const val TYPE = "username"
    }
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing roles property of a user
 */
@Introspected
internal class AddRoleUserPatch(
    @field:NotEmpty
    var rolesToAssign: Collection<RoleName>
) : UserPatch {

    override val type: String = TYPE

    override fun apply(user: UserEntity) = false

    companion object {
        const val TYPE = "assign"
    }
}

/**
 * Implementation of the [UserPatch] interface, that is in charge of changing roles property of a user
 */
@Introspected
internal class RemoveRoleUserPatch(
    @field:NotEmpty
    var rolesToRemove: Collection<RoleName>
) : UserPatch {

    override val type: String = TYPE

    override fun apply(user: UserEntity) = false

    companion object {
        const val TYPE = "unassign"
    }
}
