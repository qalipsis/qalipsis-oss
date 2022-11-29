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

package io.qalipsis.core.head.jdbc.repository

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.repository.PostgresTestContainerConfiguration.testProperties
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * @author rklymenko
 */
@Testcontainers
@MicronautTest(
    environments = [ExecutionEnvironments.POSTGRESQL],
    startApplication = false,
    transactional = false
)
internal abstract class PostgresqlTemplateTest : TestPropertyProvider {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    override fun getProperties() = pgsqlContainer.testProperties()

    companion object {

        @Container
        @JvmField
        val pgsqlContainer = PostgresTestContainerConfiguration.createContainer()

    }
}