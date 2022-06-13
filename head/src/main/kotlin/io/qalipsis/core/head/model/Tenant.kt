package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Tenant, representing an organization in QALIPSIS.
 */
@Introspected
@Schema(name = "Tenant", title = "A Tenant represents an organization in QALIPSIS")
internal data class Tenant(
    @field:Schema(description = "Unique identifier of the tenant")
    val reference: String,

    @field:Schema(description = "Name of the tenant for display")
    val displayName: String
)