package digital.rosenberger.commons.converter

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.convert.TypeConverterRegistrar
import io.qalipsis.api.logging.LoggerHelper.logger
import jakarta.inject.Singleton
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Optional

/**
 * Converter from [CharSequence] to [Instant] to match controllers arguments.
 *
 * @author Eric Jessé
 */
@Singleton
internal class StringToInstantConverter : TypeConverter<CharSequence, Instant>, TypeConverterRegistrar {

    override fun convert(
        `object`: CharSequence?,
        targetType: Class<Instant>?,
        context: ConversionContext?
    ): Optional<Instant> {
        return try {
            Optional.of(Instant.parse(`object`))
        } catch (e: Exception) {
            try {
                Optional.of(ZonedDateTime.parse(`object`).toInstant())
            } catch (e: Exception) {
                log.error(e) { "Cannot parse ${`object`} as an Instant: ${e.message}" }
                Optional.empty()
            }
        }
    }

    override fun register(conversionService: ConversionService<*>) {
        conversionService.addConverter(CharSequence::class.java, Instant::class.java, this)
    }

    private companion object {

        @JvmStatic
        private val log = logger()

    }
}