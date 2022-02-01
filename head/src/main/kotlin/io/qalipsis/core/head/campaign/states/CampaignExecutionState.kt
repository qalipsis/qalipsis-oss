package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.DirectiveFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper

/**
 * State of a campaign execution.
 *
 * @author Eric Jessé
 */
internal interface CampaignExecutionState {

    /**
     * Specifies whether the current state is a completion state and has no other next than itself.
     */
    val isCompleted: Boolean

    /**
     * Configuration of the current campaign.
     */
    val campaignId: CampaignId

    /**
     * Performs the operations implies by the creation of the state.
     *
     * @return the directives to publish after the state was initialized
     */
    suspend fun init(
        factoryService: FactoryService,
        campaignReportStateKeeper: CampaignReportStateKeeper,
        idGenerator: IdGenerator
    ): List<Directive>

    /**
     * Processes the received [Directive] and returns the resulting [CampaignExecutionState].
     *
     * @return the state consecutive to the processing of the directive on this state
     */
    suspend fun process(directive: Directive): CampaignExecutionState

    /**
     * Processes the received non [DirectiveFeedback] and returns the resulting [CampaignExecutionState].
     *
     * @return the state consecutive to the processing of the feedback on this state
     */
    suspend fun process(feedback: Feedback): CampaignExecutionState


}
