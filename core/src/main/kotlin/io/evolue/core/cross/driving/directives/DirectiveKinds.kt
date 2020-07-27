package io.evolue.core.cross.driving.directives

import cool.graph.cuid.Cuid
import java.util.LinkedList

internal typealias DirectiveKey = String

/**
 * A Directive is sent from the header to the factories to notify them of operations to perform.
 */
internal abstract class Directive(
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
internal abstract class DescriptiveDirective : Directive()

/**
 * Kind of [Directive] containing a key to read the actual directive from the cache.
 *
 */
internal abstract class DirectiveReference(key: DirectiveKey) : Directive(key)

internal interface ReferencableDirective<T : DirectiveReference> {

    fun toReference(): T
}

/**
 * Kind of [Directive] containing a value that can be read only once.
 * Those directives are stored in the [DirectiveRegistry] until they are read.
 *
 * They are published to the directive consumers as a [SingleUseDirectiveReference].
 */
internal abstract class SingleUseDirective<T, R : SingleUseDirectiveReference<T>>(
    /**
     * Values to initialize the queue.
     */
    val value: T
) : Directive(), ReferencableDirective<R>

/**
 * Transportable representation of a [SingleUseDirective].
 */
internal abstract class SingleUseDirectiveReference<T>(key: DirectiveKey) : DirectiveReference(key)

/**
 * Kind of [Directive] containing a queue of values to pop.
 * Those directives are stored in the [DirectiveRegistry] until the queue is a empty.
 *
 * They are published to the directive consumers as a [QueueDirectiveReference].
 */
internal abstract class QueueDirective<T, R : QueueDirectiveReference<T>>(
    /**
     * Values to initialize the queue.
     */
    values: List<T>
) : Directive(), ReferencableDirective<R> {

    internal val queue: LinkedList<T> = LinkedList<T>(values)
}

/**
 * Transportable representation of a [QueueDirective].
 */
internal abstract class QueueDirectiveReference<T>(key: DirectiveKey) : DirectiveReference(key)

/**
 * Kind of [Directive] containing a full set of data to process.
 *
 * They are published to the directive consumers as a [ListDirectiveReference].
 *
 */
internal abstract class ListDirective<T, R : ListDirectiveReference<T>>(
    /**
     * Values to initialize the set.
     */
    values: List<T>
) : Directive(), ReferencableDirective<R> {

    internal val set: List<T> = values
}

/**
 * Transportable representation of a [ListDirective].
 */
internal abstract class ListDirectiveReference<T>(key: DirectiveKey) : DirectiveReference(key)
