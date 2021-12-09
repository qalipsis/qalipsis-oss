package io.qalipsis.api.orchestration.directives

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import java.util.LinkedList


typealias DirectiveKey = String
typealias DispatcherChannel = String

/**
 * A Directive is sent from the head to the factories to notify them of operations to perform.
 */
@Serializable
@Polymorphic
abstract class Directive{
    abstract val key: DirectiveKey
    abstract val channel: DispatcherChannel
    override fun toString(): String {
        return "${this::class.simpleName}(key=$key)"
    }
}

/**
 * Kind of [Directive] containing all the relevant information to process the directive.
 *
 */
@Serializable
abstract class DescriptiveDirective : Directive()

/**
 * Kind of [Directive] containing a key to read the actual directive from the cache.
 *
 */
@Serializable
abstract class DirectiveReference : Directive()

interface ReferencableDirective<T : DirectiveReference> {

    fun toReference(): T
}

/**
 * Kind of [Directive] containing a value that can be read only once.
 * Those directives are stored in the [DirectiveRegistry] until they are read.
 *
 * They are published to the directive consumers as a [SingleUseDirectiveReference].
 */
@Serializable
abstract class SingleUseDirective<T, R : SingleUseDirectiveReference<T>>(
) : Directive(), ReferencableDirective<R>{
    /**
     * Values to be consumed only once.
     */
    abstract val value: T
}

/**
 * Transportable representation of a [SingleUseDirective].
 */
@Serializable
abstract class SingleUseDirectiveReference<T> : DirectiveReference()

/**
 * Kind of [Directive] containing a queue of values to pop.
 * Those directives are stored in the [DirectiveRegistry] until the queue is a empty.
 *
 * They are published to the directive consumers as a [QueueDirectiveReference].
 */
@Serializable
abstract class QueueDirective<T, R : QueueDirectiveReference<T>>(
) : Directive(), ReferencableDirective<R> {

    val queue: Collection<T> by lazy { LinkedList(values) }

    /**
     * Values to initialize the queue.
     */
    abstract val values: List<T>
}

/**
 * Transportable representation of a [QueueDirective].
 */
@Serializable
abstract class QueueDirectiveReference<T> : DirectiveReference()

/**
 * Kind of [Directive] containing a full set of data to process.
 *
 * They are published to the directive consumers as a [ListDirectiveReference].
 *
 */
@Serializable
abstract class ListDirective<T, R : ListDirectiveReference<T>>(
) : Directive(), ReferencableDirective<R> {

    val set: List<T> by lazy { values }

    /**
     * Values to initialize the set.
     */
    abstract  val values: List<T>

}

/**
 * Transportable representation of a [ListDirective].
 */
@Serializable
abstract class ListDirectiveReference<T> : DirectiveReference()