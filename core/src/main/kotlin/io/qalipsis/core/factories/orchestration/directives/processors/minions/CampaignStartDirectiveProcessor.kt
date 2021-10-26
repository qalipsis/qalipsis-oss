package io.qalipsis.core.factories.orchestration.directives.processors.minions

import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.directives.CampaignStartDirective
import io.qalipsis.core.factories.orchestration.ScenariosRegistry
import jakarta.inject.Singleton
import org.slf4j.event.Level

@Singleton
internal class CampaignStartDirectiveProcessor(
        private val scenariosRegistry: ScenariosRegistry,
        private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<CampaignStartDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is CampaignStartDirective && directive.scenarioId in scenariosRegistry
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun process(directive: CampaignStartDirective) {
        minionsKeeper.startCampaign(directive.campaignId, directive.scenarioId)
    }
}
