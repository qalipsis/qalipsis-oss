package io.evolue.api

import io.evolue.api.kotlin.components.AssertionDataSource
import io.evolue.api.kotlin.components.Request
import io.evolue.api.kotlin.components.TestDataSource
import java.time.Duration

/**
 * A scenario defines the context of the execution of the machines for a given use case.
 *
 * @param S the type of the state machine, aka receiver of the test operations.
 * @param I the type of the input of the datasource.
 */
open class ScenarioSpec(val name: String, init: ScenarioSpec.() -> Unit) {

    /**
     * Test cases to execute in the given scenario.
     */
    private val actions = mutableListOf<ActionSpec<out Any?, out Any?, out Any?>>()

    /**
     * Creates and declares a new test case to execute in the parent scenario.
     */
    fun action(name: String = "${this.name}.test-${this.actions.size + 1}"): ActionSpec<Void, Any?, Void> {
        return ActionSpec(name)
    }
}

/**
 * Wrapper class to pass the data related to the execution of a test.
 */
data class TestExecutionContext(val iterationIndex: Long, val elapsedTime: Duration)

interface ExecutableSpec

/**
 * An action describes a test case to execute in a parent scenario.
 *
 * @param S the type of the state machine, aka receiver of the test operations.
 * @param I the type of the input of the datasource.
 */
class ActionSpec<I, R, O>(val name: String) : ExecutableSpec {

    internal var dataSource: TestDataSource<I>? = null

    internal var requestBuilder: (input: I, context: TestExecutionContext) -> Request<Any?>? = { _, _ -> null }

    internal var entitySupplier: ((input: I, response: R) -> O)? = null

    internal var mapper: ((input: I, output: O) -> Any?)? = null

    internal var checkBeforeConversion: (response: R) -> Boolean? = { _ -> true }

    internal var followers = mutableListOf<Follower>()

    fun <D> with(dataSource: TestDataSource<D>): ActionSpec<D, R, O> {
        this as ActionSpec<D, R, O>
        this.dataSource = dataSource
        return this
    }

    fun <U> request(requestBuilder: (input: I, context: TestExecutionContext) -> Request<U>?): ActionSpec<I, U, O> {
        this.requestBuilder = requestBuilder
        return this as ActionSpec<I, U, O>
    }

    /**
     * Validates the response before converting it into an entity.
     */
    fun check(checkBeforeConversion: (response: R) -> Boolean): ActionSpec<I, R, O> {
        this.checkBeforeConversion = checkBeforeConversion
        return this
    }

    /**
     * Defines the action to convert the response into an output.
     */
    fun <U> entity(conversion: (response: R) -> U): ActionSpec<I, R, U> {
        this.entitySupplier = entitySupplier
        return this as ActionSpec<I, R, U>
    }

    /**
     * Convert the output to a different entity.
     */
    fun <V> map(conversion: (input: I, output: O) -> V?): ActionSpec<I, R, V> {
        this.mapper = mapper
        return this as ActionSpec<I, R, V>
    }

    /**
     * Add another action as a follower.
     */
    fun then(name: String, init: ActionSpec<O, out Any?, out Any?>.() -> Unit): ActionSpec<I, R, O> {
        val action = ActionSpec<O, Any?, Void>(name)
        followers.add(Follower(action))
        action.init()
        return this
    }

    /**
     * Add another action as follower after a delay.
     */
    fun then(name: String, delay: Duration = Duration.ZERO, init: ActionSpec<O, out Any?, Unit>.() -> Unit): ActionSpec<I, R, O> {
        val action = ActionSpec<O, Any?, Unit>(name)
        followers.add(Follower(action, delay))
        action.init()
        return this
    }

    /**
     * Add another action combined with a datasource as a follower.
     */
    fun <U> combineAndThen(name: String, dataSource: TestDataSource<U>, init: ActionSpec<Pair<O, U>, out Any?, Unit>.() -> Unit): ActionSpec<I, R, O> {
        val action = ActionSpec<Pair<O, U>, Unit, Unit>(name)
        followers.add(Follower(action))
        action.with(dataSource)
        action.init()
        return this
    }

    /**
     * Add another action combined with a datasource as follower after a delay.
     */
    fun <U> combineAndThen(name: String, delay: Duration = Duration.ZERO, dataSource: TestDataSource<U>, init: ActionSpec<Triple<I, O, U>, out Any?, out Any?>.() -> Unit): ActionSpec<I, R, O> {
        val action = ActionSpec<Triple<I, O, U>, Any?, Void>(name)
        followers.add(Follower(action, delay))
        action.with(dataSource)
        action.init()
        return this
    }

    /**
     * Add an assertion as a follower.
     */
    fun assert(name: String, init: AssertionSpec<I, R, O, Unit>.() -> Unit): ActionSpec<I, R, O> {
        val assertion = AssertionSpec<I, R, O, Unit>(name)
        followers.add(Follower(assertion))
        assertion.init()
        return this
    }

    /**
     * Add an assertion as a follower.
     */
    fun correlate(name: String, init: CorrelationSpec<I, Unit, R, O>.() -> Unit): ActionSpec<I, R, O> {
        val matching = CorrelationSpec<I, Unit, R, O>(name)
        matching.init()
        followers.add(Follower(matching.assertion))
        return this
    }
}

/**
 * An action describes a test case to execute in a parent scenario.
 *
 * @param S the type of the state machine, aka receiver of the test operations.
 * @param I the type of the input of the datasource.
 */
class CorrelationSpec<I, U, R, O>(val name: String) {

    internal var dataSource: AssertionDataSource<U>? = null

    internal var dataSourceKeyExtractor: ((U?) -> Any?)? = null

    internal var actionKeyExtractor: ((I?, R?, O?) -> Any?)? = null

    internal val assertion = AssertionSpec<Pair<out I, out U>, R, O, Unit>(name)

    fun <D> with(dataSource: AssertionDataSource<D>): CorrelationSpec<I, D, R, O> {
        this as CorrelationSpec<I, D, R, O>
        this.dataSource = dataSource
        return this
    }

    fun key(dataSourceKeyExtractor: (U?) -> Any?): CorrelationSpec<I, U, R, O> {
        this.dataSourceKeyExtractor = dataSourceKeyExtractor
        return this
    }

    fun actionKey(actionKeyExtractor: (I?, R?, O?) -> Any?): CorrelationSpec<I, U, R, O> {
        this.actionKeyExtractor = actionKeyExtractor
        return this
    }

    fun assert(init: AssertionSpec<Pair<I, U>, R, O, Unit>.() -> Unit): AssertionSpec<Pair<I, U>, R, O, Unit> {
        assertion.init()
        return assertion
    }
}

/**
 * An action describes a test case to execute in a parent scenario.
 *
 * @param S the type of the state machine, aka receiver of the test operations.
 * @param I the type of the input of the datasource.
 */
class AssertionSpec<I, R, O, Z>(val name: String) : ExecutableSpec {

    internal var timeout = Duration.ZERO

    internal var supplier: ((I, R, O, Metrics) -> Unit)? = null

    internal var mapper: ((I, R, O) -> Any?)? = null

    internal var followers = mutableListOf<Follower>()

    fun timeout(timeout: Duration): AssertionSpec<I, R, O, Z> {
        this.timeout = timeout
        return this
    }

    fun verify(supplier: (I, R, O, Metrics) -> Unit): AssertionSpec<I, R, O, Z> {
        this.supplier = supplier
        return this
    }

    fun <U> map(mapper: (I, R, O) -> U): AssertionSpec<I, R, O, U> {
        this as AssertionSpec<I, R, O, U>
        this.mapper = mapper
        return this
    }

    /**
     * Add another action as a follower.
     */
    fun then(name: String, init: ActionSpec<Z, out Any?, out Any?>.() -> Unit): AssertionSpec<I, R, O, Z> {
        val action = ActionSpec<Z, Any?, Void>(name)
        followers.add(Follower(action))
        action.init()
        return this
    }

    /**
     * Add another action as follower after a delay.
     */
    fun then(name: String, delay: Duration = Duration.ZERO, init: ActionSpec<Z, out Any?, Unit>.() -> Unit): AssertionSpec<I, R, O, Z> {
        val action = ActionSpec<Z, Any?, Unit>(name)
        followers.add(Follower(action, delay))
        action.init()
        return this
    }

    /**
     * Add another action combined with a datasource as a follower.
     */
    fun <U> combineAndThen(name: String, dataSource: TestDataSource<U>, init: ActionSpec<Pair<Z, U>, out Any?, Unit>.() -> Unit): AssertionSpec<I, R, O, Z> {
        val action = ActionSpec<Pair<Z, U>, Unit, Unit>(name)
        followers.add(Follower(action))
        action.with(dataSource)
        action.init()
        return this
    }

    /**
     * Add another action combined with a datasource as follower after a delay.
     */
    fun <U> combineAndThen(name: String, delay: Duration = Duration.ZERO, dataSource: TestDataSource<U>, init: ActionSpec<Pair<Z, U>, out Any?, out Any?>.() -> Unit): AssertionSpec<I, R, O, Z> {
        val action = ActionSpec<Pair<Z, U>, Any?, Void>(name)
        followers.add(Follower(action, delay))
        action.with(dataSource)
        action.init()
        return this
    }
}

class Follower(executableSpec: ExecutableSpec, delay: Duration = Duration.ZERO)

data class Metrics(val duration: Duration)