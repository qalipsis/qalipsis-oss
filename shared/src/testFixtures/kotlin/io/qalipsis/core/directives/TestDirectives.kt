/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.directives

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("testSingleUseDirective")
data class TestSingleUseDirective(
    val value: Int = 1,
    override val channel: DispatcherChannel = ""
) : SingleUseDirective<TestSingleUseDirectiveReference>() {

    override fun toReference(key: DirectiveKey): TestSingleUseDirectiveReference {
        return TestSingleUseDirectiveReference(key)
    }
}

@Serializable
@SerialName("testSingleUseDirectiveReference")
data class TestSingleUseDirectiveReference(
    override val key: DirectiveKey
) : SingleUseDirectiveReference()

@Serializable
@SerialName("testDescriptiveDirective")
data class TestDescriptiveDirective(
    val value: Int = 1,
    override val channel: DispatcherChannel = ""
) : DescriptiveDirective()
