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
     * Nature of the singleton.
     */
    val singletonType: SingletonType

    /**
     * Size of the buffer to keep the received records.
     */
    val bufferSize: Int

    /**
     * Time to idle of a subscription. Once idle a subscription passed this duration, it is automatically cancelled.
     */
    val idleTimeout: Duration

    /**
     * Defines if the first subscriber will receive all the records from the beginning or only from now on.
     * When set to {@code false}, records before the first subscription are simply discarded.
     */
    val fromBeginning: Boolean
}

/**
 * Defines how a singleton [io.evolue.api.steps.Step] will distribute the data to its children.
 */
enum class SingletonType() {
    /**
     * Each record is provided only once to a unique minion. The singleton acts as a FIFO-Provider.
     */
    UNICAST,

    /**
     * All the records are provided to all the minions. The singleton acts as a Pub/Sub-Provider.
     */
    BROADCAST

}