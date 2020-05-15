package io.evolue.core.factory.orchestration.directives.processors.minions

import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.core.cross.driving.directives.MinionsStartDirectiveReference
import io.evolue.core.factory.orchestration.MinionsKeeper
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant

internal class MinionsStartDirectiveProcessor(
    private val directiveRegistry: DirectiveRegistry,
    private val scenariosKeeper: ScenariosKeeper,
    private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<MinionsStartDirectiveReference> {

    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsStartDirectiveReference) {
            return scenariosKeeper.hasScenario(directive.scenarioId)
        }
        return false
    }

    override suspend fun process(directive: MinionsStartDirectiveReference) {
        directiveRegistry.list(directive).forEach {
            GlobalScope.launch {
                minionsKeeper.startMinionAt(it.minionId, Instant.ofEpochMilli(it.timestamp))
            }
        }
    }

}