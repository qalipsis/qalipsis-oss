package io.evolue.sample.simple

import io.evolue.api.annotations.Scenario
import io.evolue.api.rampup.more
import io.evolue.api.scenario.scenario
import io.evolue.api.steps.constantPace
import io.evolue.api.steps.execute
import io.evolue.api.steps.map
import io.evolue.api.steps.returns
import io.evolue.api.steps.shelve
import io.evolue.api.steps.unshelve
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 *
 * Example to create a very simple scenario that just displays several times "I'm the minion X and starting at YYY" as upper case.
 *
 * @author Eric Jessé
 */
class HelloWorldScenario {

    val minions = 50000
    var start = System.currentTimeMillis()

    @Scenario
    fun myScenario() {
        scenario("hello-world") {
            minionsCount = minions
            rampUp {
                more(200, 10, 2.0, 1000)
            }
        }
            .returns<String> { context ->
                val now = System.currentTimeMillis()
                "Hello World! I'm the minion ${context.minionId}"
            }.configure {
                name = "entry"
            }
            .shelve { input -> mapOf("started at" to System.currentTimeMillis()) }
            .map { str -> str!!.toUpperCase() }.configure {
                name = "map-1"
            }
            .constantPace(Duration.ofMillis(100))
            .unshelve<String, Long>("started at")
            .execute<Pair<String, Long?>, Unit> { ctx ->
                val input = ctx.input.receive()
                logger.info("${input.first} and started after ${input.second!! - start} ms")
            }
            .configure {
                name = "log"
                iterations = 2
            }
    }

    companion object {

        @JvmStatic
        private val logger = LoggerFactory.getLogger(HelloWorldScenario::class.java)
    }
}
