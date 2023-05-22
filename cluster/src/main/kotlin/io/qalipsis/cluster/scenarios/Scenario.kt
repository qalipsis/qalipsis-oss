package io.qalipsis.cluster.scenarios

import io.micronaut.core.annotation.Introspected
import javax.validation.constraints.NotBlank

/**
 *  Model of a scenario
 *
 * @author Luis Silva
 */
@Introspected
data class Scenario(
    @NotBlank val name: String,
    val description: String?,
    @NotBlank val version: String
)
