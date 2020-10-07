package io.qalipsis.api.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.states.SharedStateAwareStep
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.actions.Action

/**
 * Parent step for any executing an action. The step implements [SharedStateAwareStep] in order to
 * be able to share information like sessions when running an action.
 *
 * @see Action
 * @author Eric Jessé
 */
abstract class ActionStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy,
    private val actionBuilder: (suspend (context: StepContext<I, O>, sharedStateRegistry: SharedStateRegistry) -> Action)
) : AbstractStep<I, O>(id, retryPolicy), SharedStateAwareStep<I, O> {

    protected lateinit var sharedStateRegistry: SharedStateRegistry

    override suspend fun execute(context: StepContext<I, O>) {
        val action = actionBuilder(context, sharedStateRegistry)
        action.execute()
    }

    override fun set(registry: SharedStateRegistry) {
        sharedStateRegistry = registry
    }
}