package io.evolue.api

import io.mockk.mockk
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream


interface ObservableSpec<T> {

    // Only forward data matching the filter.
    fun filter(rule: (input: T) -> Boolean): ObservableSpec<T>

    // Values not matching the filter trigger errors.
    fun validate(rule: (input: T) -> Boolean): ObservableSpec<T>

    // Do something with the potential errors from validation or processing.
    fun catchError(e: Throwable): ObservableSpec<T>

    // Convert an iterable input (T being iterable) into a stream.
    fun <V> stream(rule: (input: T) -> Stream<V>): ObservableSpec<V>

    fun <V> mapEntry(rule: (input: T) -> V): ObservableSpec<V>

    fun delay(value: Long): ObservableSpec<T>

    fun delay(value: Duration): ObservableSpec<T>

    // Wait for the observable being completed.
    fun complete(): ObservableSpec<out Iterable<T>>

    // Correlate the observable with another one to provide data from two different sources.
    fun <K, V> correlate(observable: ObservableSpec<V>, leftKey: (input: T) -> K, rightKey: (input: V) -> K): ObservableSpec<Pair<T, V>>
}

interface ActionSpec<T, R, O> : ActionObservableSpec<T, R, O> {

    var timeout: Duration

    var tags: Map<String, Any>

    var selector: List<String>

    fun configure(init: ActionSpec<T, R, O>.() -> Unit): ActionObservableSpec<T, R, O>

}

interface AssertionSpec<I, AI, AR, AO, O> : AssertionObservableSpec<I, AI, AR, AO, O> {

    fun configure(init: AssertionSpec<I, AI, AR, AO, O>.() -> Unit): AssertionObservableSpec<I, AI, AR, AO, O>

}

interface ActionObservableSpec<T, R, O> : ObservableSpec<ActionOutput<T, R, O>> {

    fun <V, W> action(name: String, request: Request<Pair<T, O>, V, W>): ActionObservableSpec<Pair<T, O>, V, W>

    fun <V, W> action(name: String, requestBuilder: (input: T, output: O) -> Request<Pair<T, O>, V, W>): ActionSpec<Pair<T, O>, V, W>

    fun <V> mapInput(conversion: (T) -> V): ActionObservableSpec<V, R, O>

    fun <V> map(conversion: (O) -> V): ActionObservableSpec<T, R, V>

    fun <V> assert(name: String, assertion: Assertion<Unit, T, R, O, V>): AssertionObservableSpec<Unit, T, R, O, V>

    fun <V> assert(name: String, assertion: (input: T, response: R, output: O) -> V): AssertionSpec<Unit, T, R, O, V>

    fun parallel(init: ActionObservableSpec<T, R, O>.() -> Unit): ActionObservableSpec<T, R, O>

    override fun delay(value: Long): ActionObservableSpec<T, R, O>

    override fun delay(value: Duration): ActionObservableSpec<T, R, O>

}

interface AssertionObservableSpec<I, AI, AR, AO, O> : ObservableSpec<AssertionOutput<I, AI, AR, AO, O>> {

    fun <V> mapAssertion(conversion: (I, O) -> V): AssertionObservableSpec<I, AI, AR, AO, V>

    fun <V, W> action(name: String, request: Request<ActionAfterAssertion<AI, AO, I, O>, V, W>): ActionObservableSpec<ActionAfterAssertion<AI, AO, I, O>, V, W>

    fun <V, W> action(name: String, requestBuilder: (actionInput: AI, actionOutput: AO, assertInput: I, assertOutput: O) -> Request<ActionAfterAssertion<AI, AO, I, O>, V, W>): ActionSpec<ActionAfterAssertion<AI, AO, I, O>, V, W>

    fun parallel(init: AssertionObservableSpec<I, AI, AR, AO, O>.() -> Unit): AssertionObservableSpec<I, AI, AR, AO, O>

    fun <V, W> mapInput(conversion: (I, AI) -> Pair<V, W>): AssertionObservableSpec<V, W, AR, AO, O>

    fun <V, W> map(conversion: (AO, O) -> Pair<V, W>): AssertionObservableSpec<I, AI, AR, V, W>

    override fun delay(value: Long): AssertionObservableSpec<I, AI, AR, AO, O>

    override fun delay(value: Duration): AssertionObservableSpec<I, AI, AR, AO, O>
}

data class ActionAfterAction<I, O>(val previousActionInput: I, val previousActionOutput: O)

data class ActionAfterAssertion<AI, AO, SI, SO>(val previousActionInput: AI, val previousActionOutput: AO, val assertionInput: SI, val assertionOutput: SO)

interface ObserverSpec<T> {

    fun onSubscribe(d: ObservableSpec<out T>)

    fun onNext(t: T)

    fun onError(e: Throwable)

    fun onComplete()

    fun onFailure()
}

interface Request<I, R, O> {

    suspend fun execute(input: I): CompletableFuture<R>

    fun check(response: R) = true

    fun entity(response: R): O

}

interface Assertion<I, AI, AR, AO, O> {

    suspend fun execute(input: I): CompletableFuture<O>
}

data class ActionOutput<I, R, O>(val input: I, val response: R, val output: O)

data class AssertionOutput<I, AI, AR, AO, O>(val input: I, val actionInput: ActionOutput<AI, AR, AO>, val output: O)

fun <R, O> action(name: String, requestBuilder: () -> Request<Unit, R, O>): ActionSpec<Unit, R, O> {
    return mockk()
}

fun <I, R, O> anyRequest(): Request<I, R, O> {
    return mockk()
}

fun <I, R, O> otherRequest(): Request<I, R, O> {
    return mockk()
}

fun <I, R, O> anotherRequest(): Request<I, R, O> {
    return mockk()
}