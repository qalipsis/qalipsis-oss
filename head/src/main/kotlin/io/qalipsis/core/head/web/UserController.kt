package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.security.AddRoleUserPatch
import io.qalipsis.core.head.security.CreateUserPatch
import io.qalipsis.core.head.security.DeleteRoleUserPatch
import io.qalipsis.core.head.security.DisplayNameUserPatch
import io.qalipsis.core.head.security.EmailAddressUserPatch
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserPatch
import io.qalipsis.core.head.security.UsernameUserPatch
import io.qalipsis.core.head.security.entity.QalipsisUser
import io.qalipsis.core.head.security.entity.RoleName
import io.qalipsis.core.head.web.requestAnnotation.Tenant
import javax.validation.Valid

@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Validated
@Controller("/users")
internal class UserController(
    private val userManagement: UserManagement
) {

    @Post
    suspend fun createUser(
        @Tenant tenant: String, @Body @Valid qalipsisUser: QalipsisUser
    ): HttpResponse<QalipsisUser> {
        return HttpResponse.ok(userManagement.create(tenant, qalipsisUser))
    }

    @Get
    suspend fun listUsers(@Tenant tenant: String): HttpResponse<List<QalipsisUser>> {
        return HttpResponse.ok(userManagement.getAll(tenant))
    }

    @Get("/{qalipsisUserName}")
    suspend fun getUser(@Tenant tenant: String, @PathVariable qalipsisUserName: String): HttpResponse<QalipsisUser> {
        return HttpResponse.ok(userManagement.get(tenant, qalipsisUserName))
    }

    @Patch("/{qalipsisUserName}")
    suspend fun updateUser(
        @Tenant tenant: String, @PathVariable qalipsisUserName: String, @Body @Valid userPatches: List<CreateUserPatch>
    ): HttpResponse<QalipsisUser> {
        val usualUserPatches = convertUserPatches(tenant, userPatches)
        val qalipsisUser = userManagement.get(tenant, qalipsisUserName)
        return HttpResponse.ok(userManagement.save(tenant, qalipsisUser!!, usualUserPatches))
    }

    @Delete("/{qalipsisUserName}")
    suspend fun deleteUser(@Tenant tenant: String, @PathVariable qalipsisUserName: String): HttpResponse<Void> {
        userManagement.delete(tenant, qalipsisUserName)
        return HttpResponse.ok()
    }

    private fun convertUserPatches(tenant: String, userPatches: List<CreateUserPatch>): List<UserPatch> {
        return userPatches.map {
            when (it.name) {
                "username" -> UsernameUserPatch(it.newValue)
                "displayName" -> DisplayNameUserPatch(it.newValue)
                "email" -> EmailAddressUserPatch(it.newValue)
                "addRole" -> AddRoleUserPatch(tenant, RoleName.valueOf(it.newValue.uppercase()))
                "deleteRole" -> DeleteRoleUserPatch(tenant, RoleName.valueOf(it.newValue.uppercase()))
                else -> throw NoSuchElementException("No userPatch with name ${it.name} exists.")
            }

        }
    }
}