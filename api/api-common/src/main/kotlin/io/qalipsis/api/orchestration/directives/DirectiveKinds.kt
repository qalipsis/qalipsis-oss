package io.qalipsis.api.orchestration.directives

import cool.graph.cuid.Cuid
import java.util.LinkedList

typealias DirectiveKey = String

/**
 * A Directive is sent from the header to the factories to notify them of operations to perform.
 */
abstract class Directive(
    val key: DirectiveKey = Cuid.createCuid()
) {
    override fun toString(): String {
        return "${this::class.simpleName}(key=$key)"
    }
}

/**
 * Kind of [Directive] containing all the relevant information to process the directive.
 *
 */
abstract class DescriptiveDirective : Directive()

/**
 * Kind of [Directive] containing a key to read the actual directive from the cache.
 *
 */
abstract class DirectiveReference(key: DirectiveKey) : Directive(key)

interface ReferencableDirective<T : DirectiveReference> {

    fun toReference(): T
}

/**
 * Kind of [Directive] containing a value that can be read only once.
 * Those directives are stored in the [DirectiveRegistry] until they are read.
 *
 * They are published to the directive consumers as a [SingleUseDirectiveReference].
 */
abstract class SingleUseDirective<T, R : SingleUseDirectiveReference<T>>(
    /**
     * Values to initialize the queue.
     */
    val value: T
) : Directive(), ReferencableDirective<R>

/**
 * Transportable representation of a [SingleUseDirective].
 */
abstract class SingleUseDirectiveReference<T>(key: DirectiveKey) : DirectiveReference(key)

/**
 * Kind of [Directive] containing a queue of values to pop.
 * Those directives are stored in the [DirectiveRegistry] until the queue is a empty.
 *
 * They are published to the directive consumers as a [QueueDirectiveReference].
 */
abstract class QueueDirective<T, R : QueueDirectiveReference<T>>(
    /**
     * Values to initialize the queue.
     */
    values: List<T>
) : Directive(), ReferencableDirective<R> {

    val queue: LinkedList<T> = LinkedList<T>(values)
}

/**
 * Transportable representation of a [QueueDirective].
 */
abstract class QueueDirectiveReference<T>(key: DirectiveKey) : DirectiveReference(key)

/**
 * Kind of [Directive] containing a full set of data to process.
 *
 * They are published to the directive consumers as a [ListDirectiveReference].
 *
 */
abstract class ListDirective<T, R : ListDirectiveReference<T>>(
    /**
     * Values to initialize the set.
     */
    values: List<T>
) : Directive(), ReferencableDirective<R> {

    val set: List<T> = values
}

/**
 * Transportable representation of a [ListDirective].
 */
abstract class ListDirectiveReference<T>(key: DirectiveKey) : DirectiveReference(key)
