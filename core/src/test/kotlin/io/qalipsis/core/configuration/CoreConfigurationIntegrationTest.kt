package io.qalipsis.core.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@MicronautTest
internal class CoreConfigurationIntegrationTest {

    @Test
    @PropertySource(
        Property(name = "core.pending-key", value = "the-key"),
    )
    @MicronautTest(packages = ["io.qalipsis.core"])
    @Timeout(10)
    internal fun `should create the configuration with specified values`(coreConfiguration: CoreConfiguration) {
        assertThat(coreConfiguration).all {
            prop(CoreConfiguration::pendingKey).isEqualTo("the-key")
        }
    }
}
