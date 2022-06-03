package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags

/**
 * Default implementation of [CompletionContext].
 *
 * @author Eric Jessé
 */
data class DefaultCompletionContext(
    override val campaignKey: CampaignKey,
    override val scenarioName: ScenarioName,
    override val minionId: MinionId,
    override val lastExecutedStepName: StepName,
    override val errors: List<StepError>
) : CompletionContext {

    private var eventTags = mapOf(
        "campaign" to campaignKey,
        "minion" to minionId,
        "scenario" to scenarioName,
        "last-executed-step" to lastExecutedStepName,
    )

    private var metersTag = Tags.of(
        "campaign", campaignKey,
        "scenario", scenarioName
    )

    override fun toEventTags() = eventTags

    override fun toMetersTags() = metersTag
}