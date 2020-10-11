package io.qalipsis.core.factories.orchestration.directives.processors.minions

import io.qalipsis.api.context.MinionId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.directives.MinionsCreationDirectiveReference
import io.qalipsis.core.factories.orchestration.ScenariosRegistry
import org.slf4j.event.Level
import javax.inject.Singleton

@Singleton
internal class MinionsCreationDirectiveProcessor(
        private val directiveRegistry: DirectiveRegistry,
        private val scenariosRegistry: ScenariosRegistry,
        private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<MinionsCreationDirectiveReference> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsCreationDirectiveReference && scenariosRegistry[directive.scenarioId]?.contains(
                directive.dagId) == true
    }

    @LogInput(level = Level.DEBUG)
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
