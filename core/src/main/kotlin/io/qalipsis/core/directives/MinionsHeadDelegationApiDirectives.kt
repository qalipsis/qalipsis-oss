package io.qalipsis.core.directives

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.executionprofile.ExecutionProfileConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Directives processed by the factories on behalf of the head.
 */

/**
 * Directive to prepare the creation of the minions for a given scenario. This directive can only be read once.
 * Hence, a [SingleUseDirective] with a single value is used, to remove the directive after the first read.
 *
 * It is published to the directive consumers as a [MinionsDeclarationDirectiveReference].
 */
@Serializable
@SerialName("md")
data class MinionsDeclarationDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val minionsCount: Int,
    override val channel: DispatcherChannel
) : SingleUseDirective<MinionsDeclarationDirectiveReference>(), CampaignManagementDirective {

    override var tenant: String = ""

    override fun toReference(key: DirectiveKey): MinionsDeclarationDirectiveReference {
        return MinionsDeclarationDirectiveReference(key, campaignKey, scenarioName)
    }
}

/**
 * Transportable representation of a [MinionsDeclarationDirective].
 */
@Serializable
@SerialName("mdRef")
data class MinionsDeclarationDirectiveReference(
    override val key: DirectiveKey,
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName
) : SingleUseDirectiveReference(), CampaignManagementDirective{

    override var tenant: String = ""
}

/**
 * Directive to calculate the ramp-up of the minions given the scenario strategy.
 */
@Serializable
@SerialName("mrp")
data class MinionsRampUpPreparationDirective(
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName,
    val executionProfileConfiguration: ExecutionProfileConfiguration = ExecutionProfileConfiguration(3000, 1.0),
    override val channel: DispatcherChannel
) : SingleUseDirective<MinionsRampUpPreparationDirectiveReference>(),
    CampaignManagementDirective {

    override var tenant: String = ""

    override fun toReference(key: DirectiveKey): MinionsRampUpPreparationDirectiveReference {
        return MinionsRampUpPreparationDirectiveReference(key, campaignKey, scenarioName)
    }
}

/**
 * Transportable representation of a [MinionsRampUpPreparationDirective].
 */
@Serializable
@SerialName("mrpRef")
data class MinionsRampUpPreparationDirectiveReference(
    override val key: DirectiveKey,
    override val campaignKey: CampaignKey,
    val scenarioName: ScenarioName
) : SingleUseDirectiveReference(), CampaignManagementDirective{

    override var tenant: String = ""
}
