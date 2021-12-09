package io.qalipsis.core.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import javax.validation.constraints.NotBlank


@ConfigurationProperties("core")
internal interface CoreConfiguration {

    /**
     * Key used to store all directives pending feedback status.
     */
    @get:NotBlank
    val pendingKey: String
}