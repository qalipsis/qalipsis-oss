package io.qalipsis.core.head.security.auth0

import io.qalipsis.core.head.security.AddRoleUserPatch
import io.qalipsis.core.head.security.Auth0Patch
import io.qalipsis.core.head.security.DeleteRoleUserPatch
import io.qalipsis.core.head.security.DisplayNameUserPatch
import io.qalipsis.core.head.security.EmailAddressUserPatch
import io.qalipsis.core.head.security.EmailAuth0Patch
import io.qalipsis.core.head.security.NameAuth0Patch
import io.qalipsis.core.head.security.RoleAuth0Patch
import io.qalipsis.core.head.security.UserPatch
import io.qalipsis.core.head.security.UsernameAuth0Patch
import io.qalipsis.core.head.security.UsernameUserPatch

/**
 * Convertor from [UserPatch] to [Auth0Patch].
 *
 * @author Palina Bril
 */
internal class Auth0PatchConverter(private val userPatches: Collection<UserPatch>) {

    fun convert(): List<Auth0Patch> {
        return userPatches.map {
            when (it) {
                is UsernameUserPatch -> UsernameAuth0Patch(it.newUsername)
                is EmailAddressUserPatch -> EmailAuth0Patch(it.newEmailAddress)
                is DisplayNameUserPatch -> NameAuth0Patch(it.newDisplayName)
                is AddRoleUserPatch -> {
                    if (it.applied) RoleAuth0Patch() else null
                }
                is DeleteRoleUserPatch -> RoleAuth0Patch()
                else -> throw NoSuchElementException("No such userPatch exist - ${it}")
            }
        }.filterNotNull().toList()
    }
}
