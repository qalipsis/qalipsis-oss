package io.qalipsis.runtime.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameAs
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import assertk.assertions.startsWith
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.directives.SingleUseDirectiveReference
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.directives.TestSingleUseDirective
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Registry of the special purpose directive. This class is in the runtime module
 * because it requires dependencies from both the head and factory modules.
 *
 * @author Gabriel Moraes
 */
@WithMockk
@ExperimentalLettuceCoroutinesApi
internal class RedisDirectiveRegistryIntegrationTest : AbstractRedisIntegrationTest() {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var registry: RedisDirectiveRegistry

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        connection.sync().flushdb()
    }

    @Test
    @Timeout(10)
    internal fun saveAndReadSingleUseDirective() = testDispatcherProvider.run {
        // given
        val directive = TestSingleUseDirective(100)

        // when
        val prepared = registry.prepareBeforeSend("the-channel", directive)

        // then
        assertThat(prepared).isInstanceOf(SingleUseDirectiveReference::class).all {
            prop(SingleUseDirectiveReference::key).startsWith("the-channel:")
            isNotSameAs(directive)
        }
        assertThat(registry.prepareAfterReceived(prepared)).isEqualTo(directive)
        // The directive can be read only once.
        assertThat(registry.prepareAfterReceived(prepared)).isNull()
    }

    @Test
    @Timeout(10)
    internal fun saveThenGetAndDeleteStandardDirective() = testDispatcherProvider.run {
        // given
        val directive = TestDescriptiveDirective(100)

        // when
        val prepared = registry.prepareBeforeSend("the-channel", directive)

        // then
        assertThat(prepared).isSameAs(directive)
        assertThat(registry.prepareAfterReceived(prepared)).isSameAs(prepared)
        // The directive can be read several times.
        assertThat(registry.prepareAfterReceived(prepared)).isSameAs(prepared)
    }
}