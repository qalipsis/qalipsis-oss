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
@MicronautTest(environments = [ExecutionEnvironments.POSTGRESQL])
internal abstract class PostgresqlTemplateTest : TestPropertyProvider {

    @JvmField
    @RegisterExtension
    final val testDispatcherProvider = TestDispatcherProvider()

    override fun getProperties() = pgsqlContainer.testProperties()

    companion object {

        @Container
        @JvmField
        val pgsqlContainer = PostgresTestContainerConfiguration.createContainer()

    }
}