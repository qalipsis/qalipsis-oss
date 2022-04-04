package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

/**
 * Tenant details.
 *
 * @author Palina Bril
 */
@MappedEntity("tenant", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class TenantEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    @field:NotNull
    val creation: Instant,
    @field:NotBlank
    @field:Size(min = 1, max = 50)
    val reference: String,
    @field:NotBlank
    @field:Size(min = 1, max = 200)
    val displayName: String,
    val description: String?,
    val parent: Long?
) : Entity {

    constructor(
        creation: Instant = Instant.now(),
        reference: String,
        displayName: String,
        description: String? = null,
        parent: Long? = null
    ) : this(
        -1,
        Instant.EPOCH,
        creation, reference, displayName, description, parent
    )
}