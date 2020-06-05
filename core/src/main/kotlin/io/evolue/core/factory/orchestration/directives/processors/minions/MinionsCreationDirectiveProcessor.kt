package io.evolue.core.factory.orchestration.directives.processors.minions

import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.core.cross.driving.directives.MinionsCreationDirectiveReference
import io.evolue.core.factory.orchestration.MinionsKeeper
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal class MinionsCreationDirectiveProcessor(
    private val directiveRegistry: DirectiveRegistry,
    private val scenariosKeeper: ScenariosKeeper,
    private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<MinionsCreationDirectiveReference> {

    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsCreationDirectiveReference) {
            return scenariosKeeper.hasDag(directive.scenarioId, directive.dagId)
        }
        return false
    }

    override suspend fun process(directive: MinionsCreationDirectiveReference) {
        directiveRegistry.pop(directive)?.let { minionId ->
            GlobalScope.launch {
                minionsKeeper.create(directive.campaignId, directive.scenarioId, directive.dagId, minionId)
            }
        }
    }

}