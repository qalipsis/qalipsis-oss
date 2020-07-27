package io.evolue.core.factory.orchestration.directives.processors.minions

import io.evolue.api.context.MinionId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.cross.driving.directives.Directive
import io.evolue.core.cross.driving.directives.DirectiveRegistry
import io.evolue.core.cross.driving.directives.MinionsCreationDirectiveReference
import io.evolue.core.factory.orchestration.MinionsKeeper
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.directives.processors.DirectiveProcessor
import javax.inject.Singleton

@Singleton
internal class MinionsCreationDirectiveProcessor(
    private val directiveRegistry: DirectiveRegistry,
    private val scenariosKeeper: ScenariosKeeper,
    private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<MinionsCreationDirectiveReference> {

    @LogInputAndOutput
    override fun accept(directive: Directive): Boolean {
        if (directive is MinionsCreationDirectiveReference) {
            return scenariosKeeper.hasDag(directive.scenarioId, directive.dagId)
        }
        return false
    }

    override suspend fun process(directive: MinionsCreationDirectiveReference) {
        var minionId: MinionId?
        while (directiveRegistry.pop(directive).also { minionId = it } != null) {
            minionsKeeper.create(directive.campaignId, directive.scenarioId, directive.dagId, minionId!!)
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
