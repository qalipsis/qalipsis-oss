package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

@Introspected
internal data class TenantCreation(
    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val displayName: String
)

@Introspected
internal data class Tenant(
    val displayName: String,
    val reference: String,
    val version: Instant
)