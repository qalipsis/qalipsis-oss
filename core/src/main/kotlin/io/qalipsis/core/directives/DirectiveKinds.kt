package io.qalipsis.core.directives

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


typealias DirectiveKey = String
typealias DispatcherChannel = String

/**
 * A Directive is sent from the head to the factories to notify them of operations to perform.
 *
 * @property channel name of the channel to send this directive to, which defaults to the broadcast channel when blank
 */
@Serializable
@Polymorphic
abstract class Directive {

    @Transient
    open val channel: DispatcherChannel = ""
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

    fun toReference(key: DirectiveKey): T
}

/**
 * Kind of [Directive] containing a value that can be read only once.
 * Those directives are stored in the [DirectiveRegistry] until they are read.
 *
 * They are published to the directive consumers as a [SingleUseDirectiveReference].
 */
@Serializable
abstract class SingleUseDirective<R : SingleUseDirectiveReference>(
) : Directive(), ReferencableDirective<R>

/**
 * Transportable representation of a [SingleUseDirective].
 */
@Serializable
abstract class SingleUseDirectiveReference : DirectiveReference() {
    abstract val key: DirectiveKey
}
