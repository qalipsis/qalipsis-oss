package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

/**
 * External representation of a scenario.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Scenario details",
    title = "Details of a scenario to execute in campaigns"
)
internal data class Scenario(
    @field:Schema(description = "Last stored update of the scenario", required = true)
    val version: Instant,

    @field:NotBlank
    @field:Size(min = 2, max = 255)
    @field:Schema(description = "Display name of the scenario", required = true)
    val name: String,

    @field:Positive
    @field:Max(1000000)
    @field:Schema(description = "Number of minions executed in the scenario", required = true)
    val minionsCount: Int
)
