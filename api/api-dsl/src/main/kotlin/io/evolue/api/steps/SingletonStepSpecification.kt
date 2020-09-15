package io.evolue.api.steps

import java.time.Duration

/**
 * Interface to mark a [StepSpecification] as a singleton. A singleton step is one that is not executed in minions-related context
 * but acts on his own.
 *
 * It is often the case for the datasource steps, which are not executed once per minion
 * (implying potentially millions of requests) but only once periodically, broadcasting its result to all the minions.
 *
 * @author Eric Jessé
 */
interface SingletonStepSpecification<INPUT : Any?, OUTPUT : Any?, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF> {

    /**
     * Configuration of the singleton.
     */
    val singletonConfiguration: SingletonConfiguration

}

data class SingletonConfiguration(
        /**
         * Nature of the singleton.
         */
        var type: SingletonType,

        /**
         * Size of the buffer to keep data before the minions are ready to read them.
         * Unlimited by default.
         */
        var bufferSize: Int = -1,

        /**
         * Time to idle of a subscription. Once idle a subscription passed this duration, it is automatically cancelled.
         * When set to `Duration.ZERO` (default) or less, there is no timeout.
         */
        var idleTimeout: Duration = Duration.ZERO
)

/**
 * Defines how a singleton [io.evolue.api.steps.Step] will distribute the data to its children.
 *
 * @author Eric Jessé
 */
enum class SingletonType {
    /**
     * Each record is provided only once to a unique minion. The singleton acts as a FIFO-Provider.
     */
    UNICAST,

    /**
     * All the records are provided to all the minions. The singleton acts as a Pub/Sub-Provider.
     */
    BROADCAST,

    /**
     * Like [BROADCAST] but looping to the beginning when reaching the end of the values.
     */
    LOOP

}

/**
 * Interface for singleton that can forward the data by distributing each message only one to the first requiring minions.
 *
 * @author Eric Jessé
 */
interface UnicastSpecification {

    /**
     * Distributes each record to a unique one minion. The distribution is fair: each minion will get the next available
     * record in the order of asking.
     *
     * @param bufferSize Size of the buffer to keep the data before the first subscription.
     * @param idleTimeout Time to idle of a subscription.
     */
    fun forwardOnce(bufferSize: Int = -1, idleTimeout: Duration = Duration.ZERO)

}


/**
 * Interface for singleton that can forward the data by distributing each message to all the minions.
 *
 * @author Eric Jessé
 */
interface BroadcastSpecification {

    /**
     * Distributes each record to all the minions. The first record a minion will get in the latest generated by
     * the source at that moment.
     *
     * For each minion, a cursor is kept in order to evaluate the position of the next record to read. If a given minion
     * did not polled a value twice in a time frame defined by [idleTimeout], the cursor is cancelled and any
     * further poll will fail.
     *
     * A value of `Duration.ZERO` (default) defines no limitation but implies more memory consumption.
     *
     * @param bufferSize Size of the buffer to keep to pass past data to new subscriptions.
     * @param idleTimeout Time to idle of a subscription.
     */
    fun broadcast(bufferSize: Int = -1, idleTimeout: Duration = Duration.ZERO)

}

/**
 * Interface for singleton that can forward the data again and again to all the minions.
 *
 * @author Eric Jessé
 */
interface LoopableSpecification {

    /**
     * Distributes each record to a unique one minion. The distribution is fair: each minion will get the next available
     * record in the order of asking.
     *
     * For each minion, a cursor is kept in order to evaluate the position of the next record to read. If a given minion
     * did not polled a value twice in a time frame defined by [idleTimeout], the cursor is cancelled and any
     * further poll will fail.
     *
     * A value of `Duration.ZERO` (default) defines no limitation but implies more memory consumption.
     *
     * @param idleTimeout Time to idle of a subscription.
     */
    fun loop(idleTimeout: Duration = Duration.ZERO)

}