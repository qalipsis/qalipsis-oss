package io.evolue.core.factories.orchestration.directives.processors.minions

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.annotations.LogInput
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.cross.directives.CampaignStartDirective
import io.evolue.api.orchestration.directives.Directive
import io.evolue.api.orchestration.directives.DirectiveProcessor
import io.evolue.core.factories.orchestration.MinionsKeeper
import io.evolue.core.factories.orchestration.ScenariosKeeper
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
