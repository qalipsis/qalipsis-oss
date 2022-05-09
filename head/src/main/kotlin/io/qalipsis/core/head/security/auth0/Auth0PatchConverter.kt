package io.qalipsis.core.head.security.auth0

import io.qalipsis.core.head.security.AddRoleUserPatch
import io.qalipsis.core.head.security.DisplayNameUserPatch
import io.qalipsis.core.head.security.EmailAddressUserPatch
import io.qalipsis.core.head.security.RemoveRoleUserPatch
import io.qalipsis.core.head.security.UserPatch
import io.qalipsis.core.head.security.UsernameUserPatch
import jakarta.inject.Singleton

/**
 * Convertor from [UserPatch] to [Auth0Patch].
 *
 * @author Palina Bril
 */
@Singleton
internal class Auth0PatchConverter(
    private val operations: Auth0Operations
) {

    fun convert(tenant: String, userPatches: Collection<UserPatch>): Collection<Auth0Patch> {
        return userPatches.mapNotNull { patch ->
            when (patch) {
                is UsernameUserPatch -> UsernameAuth0Patch(patch.newUsername)
                is EmailAddressUserPatch -> EmailAuth0Patch(patch.newEmailAddress)
                is DisplayNameUserPatch -> NameAuth0Patch(patch.newDisplayName)
                is AddRoleUserPatch -> AddRoleAuth0Patch(tenant, patch.rolesToAssign, operations)
                is RemoveRoleUserPatch -> RemoveRoleAuth0Patch(tenant, patch.rolesToRemove, operations)
                else -> null
            }
        }
    }
}
