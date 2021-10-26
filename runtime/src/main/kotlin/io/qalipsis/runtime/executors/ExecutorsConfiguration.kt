package io.qalipsis.runtime.executors

import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Configuration for the different executors.
 *
 * @author Eric Jessé
 */
@ConfigurationProperties("executors")
internal class ExecutorsConfiguration {

    lateinit var global: String

    lateinit var campaign: String

    lateinit var io: String

    lateinit var background: String

    lateinit var orchestration: String

}