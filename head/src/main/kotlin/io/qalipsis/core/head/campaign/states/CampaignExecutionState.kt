package io.qalipsis.core.head.campaign.states

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.DirectiveFeedback
import io.qalipsis.core.feedbacks.Feedback

/**
 * State of a campaign execution.
 *
 * @param C The type of the context the state requires for its execution.
 *
 * @author Eric Jessé
 */
internal interface CampaignExecutionState<C : CampaignExecutionContext> {

    /**
     * Specifies whether the current state is a completion state and has no other next than itself.
     */
    val isCompleted: Boolean

    /**
     * ID of the current campaign.
     */
    val campaignKey: CampaignKey

    fun inject(context: C)

    /**
     * Performs the operations implies by the creation of the state.
     *
     * @return the directives to publish after the state was initialized
     */
    suspend fun init(): List<Directive>

    /**
     * Processes the received non [DirectiveFeedback] and returns the resulting [CampaignExecutionState].
     *
     * @return the state consecutive to the processing of the feedback on this state
     */
    suspend fun process(feedback: Feedback): CampaignExecutionState<C>

    /**
     * Receives [AbortRunningCampaign] and returns the [AbortingState].
     *
     * @return the [AbortingState] with abortConfiguration and error message
     */
    suspend fun abort(abortConfiguration: AbortRunningCampaign): CampaignExecutionState<C>

}
