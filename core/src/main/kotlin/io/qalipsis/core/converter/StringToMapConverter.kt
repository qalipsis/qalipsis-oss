package io.qalipsis.core.converter

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.convert.TypeConverterRegistrar
import io.qalipsis.api.logging.LoggerHelper.logger
import jakarta.inject.Singleton
import java.time.Instant
import java.util.Optional

/**
 * Converter from [CharSequence] to [Instant] to match controllers arguments.
 *
 * @author Eric Jessé
 */
@Singleton
internal class StringToMapConverter : TypeConverter<CharSequence, Map<String, String>>, TypeConverterRegistrar {

    override fun convert(
        `object`: CharSequence?,
        targetType: Class<Map<String, String>>,
        context: ConversionContext?
    ): Optional<Map<String, String>> {
        return if (`object` == null) {
            log.error { "Cannot parse null value as map" }
            Optional.empty()
        } else if (`object`.isBlank()) {
            Optional.of(emptyMap())
        } else {
            try {
                Optional.of(
                    `object`.split(",").map { it.trim() }
                        .map { it.substringBefore("=") to it.substringAfter("=") }
                        .toMap()
                )
            } catch (e: Exception) {
                log.error(e) { "Cannot parse ${`object`} as a Map: ${e.message}" }
                Optional.empty()
            }
        }
    }

    override fun register(conversionService: ConversionService<*>) {
        @Suppress("UNCHECKED_CAST")
        conversionService.addConverter(
            CharSequence::class.java,
            Map::class.java,
            this as TypeConverter<CharSequence, Map<*, *>>
        )
    }

    private companion object {

        @JvmStatic
        private val log = logger()

    }

}