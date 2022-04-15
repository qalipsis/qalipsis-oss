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
 * User details.
 * @property username  public identified of a user (globally unique, might be the email address). A first record
 * is created into the table, with the username  “qalipsis”. This corresponds to the “system” user.
 *
 * @author Palina Bril
 */
@MappedEntity("user", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class UserEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    @field:NotNull
    val creation: Instant,
    @field:NotBlank
    @field:Size(min = 1, max = 150)
    val username: String
) : Entity {

    constructor(
        username: String
    ) : this(
        -1,
        Instant.EPOCH,
        Instant.now(), username
    )
}