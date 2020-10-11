package io.qalipsis.core.factories.orchestration.directives.processors.minions

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.directives.CampaignStartDirective
import io.qalipsis.core.factories.orchestration.ScenariosRegistry
import org.slf4j.event.Level
import javax.inject.Singleton

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

    companion object {

        @JvmStatic
        private val log = logger()
    }

}
