package io.qalipsis.core.factories.events

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.events.EventsPublisher
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import javax.inject.Inject


internal class EventsAndPublisherIntegrationTest {

    @Nested
    @MicronautTest(environments = ["nopublisher"])
    inner class NoPublisher {

        @Inject
        private lateinit var applicationContext : ApplicationContext

        /**
         * Uses the resource application-nopublisher.yml to load the configuration.
         */
        @Test
        @Timeout(10)
        internal fun `should start without publisher`() {
            assertThat(applicationContext.getBeansOfType(EventsPublisher::class.java)).isEmpty()
            assertThat(applicationContext.getBean(EventsLogger::class.java)).all {
                typedProp<Collection<EventsPublisher>>("publishers").isEmpty()
                prop("rootLevel").isEqualTo(EventLevel.INFO)
                prop("enabled").isEqualTo(false)
                typedProp<List<Pair<String, EventLevel>>>("declaredLevels").transform { it.toMap() }.all {
                    hasSize(3)
                    key("the.first").isEqualTo(EventLevel.INFO)
                    key("the.second.level").isEqualTo(EventLevel.DEBUG)
                    key("the.third.level").isEqualTo(EventLevel.OFF)
                }
            }
        }
    }

    @Nested
    @MicronautTest(environments = ["slf4j-publisher"])
    inner class Slf4jPublisher {

        @Inject
        private lateinit var applicationContext : ApplicationContext

        /**
         * Uses the resource application-slf4j-publisher.yml to load the configuration.
         */
        @Test
        @Timeout(10)
        internal fun `should start with the slf4j publisher`() {
            assertThat(applicationContext.getBeansOfType(EventsPublisher::class.java)).all {
                hasSize(1)
                any { it.isInstanceOf(Slf4JEventsPublisher::class) }
            }
            assertThat(applicationContext.getBean(EventsLogger::class.java)).all {
                typedProp<Collection<EventsPublisher>>("publishers").all {
                    hasSize(1)
                    any { it.isInstanceOf(Slf4JEventsPublisher::class) }
                }
                prop("rootLevel").isEqualTo(EventLevel.ERROR)
                prop("enabled").isEqualTo(true)
                typedProp<List<Pair<String, EventLevel>>>("declaredLevels").transform { it.toMap() }.all {
                    hasSize(4)
                    key("the.first").isEqualTo(EventLevel.WARN)
                    key("the.second.level").isEqualTo(EventLevel.INFO)
                    key("the.second.other.level").isEqualTo(EventLevel.DEBUG)
                    key("the.third.level").isEqualTo(EventLevel.ERROR)
                }
            }
        }
    }
}
