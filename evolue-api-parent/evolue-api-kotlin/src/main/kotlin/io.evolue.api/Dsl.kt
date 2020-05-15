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

interface ActionObservableSpec<INPUT, RESPONSE> : ObservableSpec<ActionOutput<INPUT, RESPONSE>> {

    fun <R2> action(name: String, request: Request<RESPONSE, R2>): ActionObservableSpec<RESPONSE, R2>

    fun <R2> action(name: String, requestBuilder: (input: RESPONSE) -> Request<RESPONSE, R2>): ActionSpec<RESPONSE, R2>

    fun <OUTPUT> map(conversion: (INPUT, RESPONSE) -> OUTPUT): ActionObservableSpec<INPUT, OUTPUT>

    fun <OUTPUT> assert(name: String, assertion: Assertion<ActionOutput<INPUT, RESPONSE>, OUTPUT>): AssertionObservableSpec<RESPONSE, OUTPUT>

    fun <OUTPUT> assert(name: String, assertion: (ActionOutput<INPUT, RESPONSE>) -> OUTPUT): AssertionSpec<RESPONSE, OUTPUT>

    fun parallel(init: ActionObservableSpec<INPUT, RESPONSE>.() -> Unit): ActionObservableSpec<INPUT, RESPONSE>

    override fun filter(rule: (input: ActionOutput<INPUT, RESPONSE>) -> Boolean): ActionObservableSpec<INPUT, RESPONSE>

    override fun delay(value: Long): ActionObservableSpec<INPUT, RESPONSE>

    override fun delay(value: Duration): ActionObservableSpec<INPUT, RESPONSE>

}

interface ActionSpec<INPUT, RESPONSE> : ActionObservableSpec<INPUT, RESPONSE> {

    var timeout: Duration

    var tags: Map<String, Any>

    var selector: List<String>

    fun configure(init: ActionSpec<INPUT, RESPONSE>.() -> Unit): ActionObservableSpec<INPUT, RESPONSE>

}

interface AssertionSpec<INPUT, OUTPUT> : AssertionObservableSpec<INPUT, OUTPUT> {

    fun configure(init: AssertionSpec<INPUT, OUTPUT>.() -> Unit): AssertionObservableSpec<INPUT, OUTPUT>

}


interface AssertionObservableSpec<INPUT, OUTPUT> : ObservableSpec<AssertionOutput<INPUT, OUTPUT>> {

    fun <OUTPUT2> map(conversion: (INPUT, OUTPUT) -> OUTPUT2): AssertionObservableSpec<INPUT, OUTPUT2>

    fun <RESPONSE> action(name: String, request: Request<OUTPUT, RESPONSE>): ActionObservableSpec<OUTPUT, RESPONSE>

    fun <RESPONSE> action(name: String, requestBuilder: (OUTPUT) -> Request<OUTPUT, RESPONSE>): ActionObservableSpec<OUTPUT, RESPONSE>

    fun parallel(init: AssertionObservableSpec<INPUT, OUTPUT>.() -> Unit): AssertionObservableSpec<INPUT, OUTPUT>

    override fun delay(value: Long): AssertionObservableSpec<INPUT, OUTPUT>

    override fun delay(value: Duration): AssertionObservableSpec<INPUT, OUTPUT>
}

interface ObserverSpec<T> {

    fun onSubscribe(d: ObservableSpec<out T>)

    fun onNext(t: T)

    fun onError(e: Throwable)

    fun onComplete()

    fun onFailure()
}

interface Request<I, R> {

    suspend fun execute(input: I): CompletableFuture<R>

    fun check(response: R) = true
}

interface Assertion<INPUT, OUTPUT> {

    suspend fun execute(input: INPUT): CompletableFuture<OUTPUT>
}

data class ActionOutput<I, R>(val input: I, val response: R)

data class AssertionOutput<INPUT, OUTPUT>(val input: INPUT, val output: OUTPUT)

fun <R> action(name: String, requestBuilder: () -> Request<Unit, R>): ActionSpec<Unit, R> {
    return mockk()
}

fun <R> anyRequest(): Request<Unit, R> {
    return mockk()
}

fun <I> otherRequest(value: I): Request<I, String> {
    return mockk()
}

fun <I> anotherRequest(value: I): Request<I, Map<String, String>> {
    return mockk()
}