package io.evolue.core.factory.orchestration.directives.processors.minions

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.core.cross.driving.directives.MinionsStartDirectiveReference
import io.evolue.core.factory.orchestration.MinionsKeeper
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import java.time.Instant
import javax.inject.Singleton

@Singleton
internal class MinionsStartDirectiveProcessor(
    private val directiveRegistry: DirectiveRegistry,
    private val scenariosKeeper: ScenariosKeeper,
    private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<MinionsStartDirectiveReference> {

    @LogInputAndOutput
    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsStartDirectiveReference) {
            return scenariosKeeper.hasScenario(directive.scenarioId)
        }
        return false
    }

    override suspend fun process(directive: MinionsStartDirectiveReference) {
        directiveRegistry.list(directive).forEach {
            minionsKeeper.startMinionAt(it.minionId, Instant.ofEpochMilli(it.timestamp))
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }

}
