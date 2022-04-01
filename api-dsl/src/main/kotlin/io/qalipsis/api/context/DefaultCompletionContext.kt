package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags

/**
 * Default implementation of [CompletionContext].
 *
 * @author Eric Jessé
 */
data class DefaultCompletionContext(
    override val campaignName: CampaignName,
    override val scenarioName: ScenarioName,
    override val minionId: MinionId,
    override val lastExecutedStepName: StepName,
    override val errors: List<StepError>
) : CompletionContext {

    private var eventTags = mapOf(
        "campaign" to campaignName,
        "minion" to minionId,
        "scenario" to scenarioName,
        "last-executed-step" to lastExecutedStepName,
    )

    private var metersTag = Tags.of(
        "campaign", campaignName,
        "scenario", scenarioName
    )

    override fun toEventTags() = eventTags

    override fun toMetersTags() = metersTag
}