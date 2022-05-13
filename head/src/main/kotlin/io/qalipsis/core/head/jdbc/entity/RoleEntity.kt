package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.core.head.security.RoleName
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Existing roles in the system.
 *
 * @property tenant tenant for which the role exists.
 * @property name name of the role as in [RoleName]
 *
 * @author Eric Jessé
 */
@MappedEntity("role", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class RoleEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,

    @field:Version
    val version: Instant,

    @field:NotBlank
    @field:Size(max = 50)
    val tenant: String,

    val name: RoleName,

    @field:NotBlank
    @field:Size(max = 50)
    val reference: String
) : Entity {

    constructor(tenant: String, name: RoleName, reference: String = "$tenant:${name.publicName}") : this(
        -1,
        Instant.EPOCH,
        tenant,
        name,
        reference
    )
}