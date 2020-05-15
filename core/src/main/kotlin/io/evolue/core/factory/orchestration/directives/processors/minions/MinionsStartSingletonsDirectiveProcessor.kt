package io.evolue.core.factory.orchestration.directives.processors.minions

import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.MinionsStartSingletonsDirective
import io.evolue.core.factory.orchestration.MinionsKeeper
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor

internal class MinionsStartSingletonsDirectiveProcessor(
    private val scenariosKeeper: ScenariosKeeper,
    private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<MinionsStartSingletonsDirective> {

    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsStartSingletonsDirective) {
            return scenariosKeeper.hasScenario(directive.scenarioId)
        }
        return false
    }

    override suspend fun process(directive: MinionsStartSingletonsDirective) {
        minionsKeeper.startSingletons(directive.scenarioId)
    }

}