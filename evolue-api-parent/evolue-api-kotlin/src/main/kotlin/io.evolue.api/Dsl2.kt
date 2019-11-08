package io.evolue.api

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream


interface ObservableSpec<T> {

    fun filter(rule: (input: T) -> Boolean): ObservableSpec<T>

    fun validate(rule: (input: T) -> Unit): ObservableSpec<T>

    fun <V> stream(rule: (input: T) -> Stream<V>): ObservableSpec<V>

    fun <V> map(rule: (input: T) -> V): ObservableSpec<V>

    fun delay(value: Long): ObservableSpec<T>

    fun delay(value: Duration): ObservableSpec<T>

    fun complete(): ObservableSpec<out Iterable<T>>

    fun <V> correlate(observable: ObservableSpec<V>): ObservableSpec<V>
}

interface ReactiveActionSpec<T, R, O> : ActionObservableSpec<T, R, O> {

    fun configure(init: ReactiveActionSpec<T, R, O>.() -> Unit): ActionObservableSpec<T, R, O>

}

interface ActionObservableSpec<T, R, O> : ObservableSpec<ActionOutput<T, R, O>> {

    fun <V, W> action(name: String, request: Request<Pair<T, O>, V, W>): ReactiveActionSpec<Pair<T, O>, V, W>

    fun <V> mapAction(conversion: (T, O) -> V): ActionObservableSpec<T, R, V>

    fun <I, V> assert(name: String, assertion: Assertion<I, T, R, O, V>): AssertionObservableSpec<I, T, R, O, V>

    fun parallel(init: ActionObservableSpec<T, R, O>.() -> Unit): ActionObservableSpec<T, R, O>

}

interface AssertionObservableSpec<I, AI, AR, AO, O> : ObservableSpec<AssertionOutput<I, AI, AR, AO, O>> {

    fun <V> mapAssertion(conversion: (I, O) -> V): AssertionObservableSpec<I, AI, AR, AO, V>

    fun <V, W> action(name: String, request: Request<ActionAfterAssertion<AI, AO, I, O>, V, W>): ReactiveActionSpec<ActionAfterAssertion<AI, AO, I, O>, V, W>

    fun parallel(init: AssertionObservableSpec<I, AI, AR, AO, O>.() -> Unit): AssertionObservableSpec<I, AI, AR, AO, O>
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

interface Processor<I, V> {

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

class MockedActionSpec<I, R, O> : ObserverSpec<I>, ReactiveActionSpec<I, R, O> {
    override fun parallel(init: ActionObservableSpec<I, R, O>.() -> Unit): ActionObservableSpec<I, R, O> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <V> map(rule: (input: ActionOutput<I, R, O>) -> V): ObservableSpec<V> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <V, W> action(name: String, request: Request<Pair<I, O>, V, W>): ReactiveActionSpec<Pair<I, O>, V, W> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <V> mapAction(conversion: (I, O) -> V): ActionObservableSpec<I, R, V> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <V> stream(rule: (input: ActionOutput<I, R, O>) -> Stream<V>): ObservableSpec<V> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun filter(rule: (input: ActionOutput<I, R, O>) -> Boolean): ObservableSpec<ActionOutput<I, R, O>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun validate(rule: (input: ActionOutput<I, R, O>) -> Unit): ObservableSpec<ActionOutput<I, R, O>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun delay(value: Long): ObservableSpec<ActionOutput<I, R, O>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun delay(value: Duration): ObservableSpec<ActionOutput<I, R, O>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun complete(): ObservableSpec<out Iterable<ActionOutput<I, R, O>>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <V> correlate(observable: ObservableSpec<V>): ObservableSpec<V> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <V, W> assert(name: String, assertion: Assertion<V, I, R, O, W>): AssertionObservableSpec<V, I, R, O, W> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSubscribe(d: ObservableSpec<out I>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onNext(t: I) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onError(e: Throwable) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onComplete() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onFailure() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class MockedRequest<I, R, O> : Request<I, R, O> {

    override suspend fun execute(input: I): CompletableFuture<R> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun entity(response: R): O {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}


fun <I, R, O> action(name: String, request: Request<I, R, O>): ReactiveActionSpec<I, R, O> {
    return MockedActionSpec()
}
