package io.qalipsis.core.head.persistence.repository

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.core.head.persistence.repository.PostgresTestContainerConfiguration.testProperties
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * @author rklymenko
 */
@Testcontainers
@MicronautTest(environments = ["pgsql"])
internal abstract class PostgresqlTemplateTest : TestPropertyProvider {

    @RegisterExtension
    protected val testDispatcherProvider = TestDispatcherProvider()

    override fun getProperties() = pgsqlContainer.testProperties()

    companion object {

        @Container
        @JvmField
        val pgsqlContainer = PostgresTestContainerConfiguration.createContainer()

    }
}