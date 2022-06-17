package io.qalipsis.core.head.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton

/**
 * Model to send the configuration information required for the frontend.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "SecurityConfiguration",
    title = "Details of the SecurityConfiguration necessary for the frontend"
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "strategy",
    defaultImpl = DisabledSecurityConfiguration::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DisabledSecurityConfiguration::class, name = "DISABLED")
)
internal interface SecurityConfiguration

@Singleton
@Requires(missingBeans = [SecurityConfiguration::class])
@Introspected
internal class DisabledSecurityConfiguration : SecurityConfiguration
