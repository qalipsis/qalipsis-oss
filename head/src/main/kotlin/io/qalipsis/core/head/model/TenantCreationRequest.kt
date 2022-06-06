package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Model to create a new tenant.
 *
 * @author Sandro Mamukelashvili
 */
@Introspected
@Schema(name = "Tenant creation request", title = "Details for the creation of a new tenant into QALIPSIS")
internal data class TenantCreationRequest(
    @field:Schema(description = "Unique identifier to assign to the tenant, when no value or a blank value is provided, one is generated")
    @field:Size(max = 50)
    val reference: String?,

    @field:Schema(description = "Name of the tenant for display")
    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val displayName: String
)

