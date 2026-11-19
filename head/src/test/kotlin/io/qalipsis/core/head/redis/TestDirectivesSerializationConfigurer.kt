/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.redis

import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.serialization.SerializationConfigurer
import jakarta.inject.Singleton
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic

/**
 * Registers the test directives in the closed set of directives that can be transported.
 */
@Singleton
internal class TestDirectivesSerializationConfigurer : SerializationConfigurer {

    override fun configure(serializersModuleBuilder: SerializersModuleBuilder) {
        serializersModuleBuilder.polymorphic(Directive::class) {
            subclass(TestDescriptiveDirective::class, TestDescriptiveDirective.serializer())
        }
    }
}
