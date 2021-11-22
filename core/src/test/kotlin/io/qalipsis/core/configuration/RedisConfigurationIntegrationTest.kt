package io.qalipsis.core.configuration

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isNotSameAs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.qalipsis.core.configuration.RedisConfiguration.Companion.PUB_SUB
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

internal class RedisConfigurationIntegrationTest : AbstractRedisIntegrationTest() {

    @Inject
    private lateinit var applicationContext: ApplicationContext

    @Test
    internal fun `should create beans for the coroutines commands of the stateful and pub sub connections`() {
        assertThat(applicationContext.getBeansOfType(RedisCoroutinesCommands::class.java)).hasSize(2)

        val defaultCommands = applicationContext.getBean(RedisCoroutinesCommands::class.java)
        val pubsubCommands = applicationContext.getBean(RedisCoroutinesCommands::class.java, Qualifiers.byName(PUB_SUB))

        assertThat(pubsubCommands).isNotSameAs(defaultCommands)
    }
}