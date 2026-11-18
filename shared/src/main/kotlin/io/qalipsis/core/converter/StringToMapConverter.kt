/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

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
class StringToMapConverter : TypeConverter<CharSequence, Map<String, String>>, TypeConverterRegistrar {

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