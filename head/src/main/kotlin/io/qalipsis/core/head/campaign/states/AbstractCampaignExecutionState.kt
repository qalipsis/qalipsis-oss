package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.context.CampaignId
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback

/**
 * Parent class of all implementations of [CampaignExecutionState].
 *
 * @author Eric Jessé
 */
internal abstract class AbstractCampaignExecutionState<C : CampaignExecutionContext>(
    override val campaignId: CampaignId
) : CampaignExecutionState<C> {

    var initialized: Boolean = false

    protected lateinit var context: C

    override val isCompleted: Boolean = false

    override fun inject(context: C) {
        this.context = context
    }

    override suspend fun init(): List<Directive> {
        return if (!initialized) {
            val directives = doInit()
            initialized = true
            directives
        } else {
            emptyList()
        }
    }

    protected open suspend fun doInit(): List<Directive> = emptyList()

    override suspend fun process(feedback: Feedback): CampaignExecutionState<C> {
        return doTransition(feedback)
    }

    protected open suspend fun doTransition(feedback: Feedback): CampaignExecutionState<C> {
        return this
    }


}