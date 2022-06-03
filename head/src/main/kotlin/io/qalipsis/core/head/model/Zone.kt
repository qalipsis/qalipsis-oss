package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Details of a zone to return by REST.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Zone",
    title = "Zone of QALIPSIS",
    description = "Details of a zone in scenarios"
)
internal data class Zone(

    @field:Schema(description = "A unique identifier for the zone")
    @field:NotBlank
    @field:Size(min = 2, max = 3)
    val key: String,

    @field:Schema(description = "A complete name of the zone, generally the country")
    @field:NotBlank
    @field:Size(min = 3, max = 20)
    val title: String,

    @field:Schema(description = "A more detailed definition of the zone, generally the region, datacenter and the localization details")
    @field:NotBlank
    @field:Size(min = 3, max = 50)
    val description: String
){
    override fun toString(): String {
        return "Zone(key='$key', title='$title', description='$description')"
    }
}