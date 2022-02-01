package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper

/**
 * Parent class of all implementations of [CampaignExecutionState].
 *
 * @author Eric Jessé
 */
internal abstract class AbstractCampaignExecutionState(
    override val campaignId: CampaignId
) : CampaignExecutionState {

    var initialized: Boolean = false

    protected lateinit var factoryService: FactoryService

    protected lateinit var idGenerator: IdGenerator

    protected lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    override val isCompleted: Boolean = false

    override suspend fun init(
        factoryService: FactoryService,
        campaignReportStateKeeper: CampaignReportStateKeeper,
        idGenerator: IdGenerator
    ): List<Directive> {
        this.factoryService = factoryService
        this.campaignReportStateKeeper = campaignReportStateKeeper
        this.idGenerator = idGenerator

        return if (!initialized) {
            val directives = doInit()
            initialized = true
            directives
        } else {
            emptyList()
        }
    }

    protected open suspend fun doInit(): List<Directive> = emptyList()

    override suspend fun process(directive: Directive): CampaignExecutionState {
        return doTransition(directive)
    }

    protected open suspend fun doTransition(directive: Directive): CampaignExecutionState {
        return this
    }

    override suspend fun process(feedback: Feedback): CampaignExecutionState {
        return doTransition(feedback)
    }

    protected open suspend fun doTransition(feedback: Feedback): CampaignExecutionState {
        return this
    }
}