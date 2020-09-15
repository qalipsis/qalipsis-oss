package io.evolue.core.factory.orchestration.directives.processors.minions

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.annotations.LogInput
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.cross.driving.directives.CampaignStartDirective
import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.factory.orchestration.MinionsKeeper
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
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
