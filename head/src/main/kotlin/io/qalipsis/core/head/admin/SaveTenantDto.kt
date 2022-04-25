package io.qalipsis.core.head.admin

import io.micronaut.core.annotation.Introspected
import java.time.Instant
import javax.validation.constraints.Size

@Introspected
open class SaveTenantDto(

    @field:Size(min = 1, max = 200) var displayName: String

)

class SaveTenantResponse(
    val displayName: String,
    val reference: String,
    val version: Instant?
)
