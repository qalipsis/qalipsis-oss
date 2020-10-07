package io.qalipsis.core.factories.orchestration.directives.processors.minions

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.directives.CampaignStartDirective
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.core.factories.orchestration.MinionsKeeper
import io.qalipsis.core.factories.orchestration.ScenariosKeeper
import org.slf4j.event.Level
import javax.inject.Singleton

@Singleton
internal class CampaignStartDirectiveProcessor(
    private val scenariosKeeper: ScenariosKeeper,
    private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<CampaignStartDirective> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is CampaignStartDirective && scenariosKeeper.hasScenario(directive.scenarioId)
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
