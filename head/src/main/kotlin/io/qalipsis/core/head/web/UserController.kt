package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
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
import io.qalipsis.core.head.model.UserCreationRequest
import io.qalipsis.core.head.security.User
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserPatch
import io.qalipsis.core.head.web.annotations.Tenant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty

/**
 * User management API.
 *
 * @author Palina Bril
 */
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD]),
    Requires(beans = [UserManagement::class])
)

@Controller("/users")
@Validated
@Version("1.0")
internal class UserController(
    private val userManagement: UserManagement
) {

    @Post
    @Operation(
        summary = "Create a new user",
        description = "Create a new user with the provided details and attach it to the contextual tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the successfully created user"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    suspend fun createUser(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Body @Valid user: UserCreationRequest
    ): HttpResponse<User> {
        return HttpResponse.ok(userManagement.create(tenant, user.toUser(tenant)))
    }

    @Get
    @Operation(
        summary = "List users",
        description = "Return the list of all the users attached to the tenant, containing their details and roles",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of users in the tenant"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    suspend fun listUsers(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String
    ): HttpResponse<List<User>> {
        return HttpResponse.ok(userManagement.findAll(tenant))
    }

    @Get("/{username}")
    @Operation(
        summary = "Retrieve a unique user",
        description = "Return a unique user attached to the tenant, containing its details and roles",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the user in the tenant"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    suspend fun getUser(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @Tenant tenant: String,
        @Parameter(
            description = "Username of the user to retrieve",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable username: String
    ): HttpResponse<User> {
        val user = userManagement.get(tenant, username)
        return if (user != null) {
            HttpResponse.ok(user)
        } else {
            HttpResponse.notFound()
        }
    }

    @Patch("/{username}")
    @Operation(
        summary = "Update a user",
        description = "Update the details and roles of a user for the contextual tenant",
        responses = [
            ApiResponse(responseCode = "200", description = "Updated user"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    suspend fun updateUser(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Username of the user to update",
            required = true,
            `in` = ParameterIn.PATH
        ) @PathVariable username: String,
        @NotEmpty @Body userPatches: List<@Valid UserPatch>
    ): HttpResponse<User> {
        val user = userManagement.get(tenant, username)
        return if (user != null) {
            HttpResponse.ok(userManagement.update(tenant, user, userPatches))
        } else {
            HttpResponse.notFound()
        }
    }

    @Delete("/{username}")
    @Operation(
        summary = "Delete a user",
        description = "Delete a user from the contextual tenant",
        responses = [
            ApiResponse(responseCode = "204", description = "Successful deletion "),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Missing rights to execute the operation"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    suspend fun deleteUser(
        @Parameter(
            name = "X-Tenant",
            description = "Contextual tenant",
            required = true,
            `in` = ParameterIn.HEADER
        ) @NotBlank @Tenant tenant: String,
        @Parameter(
            description = "Username of the user to delete",
            required = true,
            `in` = ParameterIn.PATH
        ) @NotBlank @PathVariable username: String
    ): HttpResponse<Unit> {
        userManagement.disable(tenant, username)
        return HttpResponse.noContent()
    }
}