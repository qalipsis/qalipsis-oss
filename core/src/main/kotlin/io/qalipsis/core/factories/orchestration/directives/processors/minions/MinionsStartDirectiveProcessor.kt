package io.qalipsis.core.factories.orchestration.directives.processors.minions

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.directives.MinionsStartDirectiveReference
import io.qalipsis.core.factories.orchestration.ScenariosRegistry
import org.slf4j.event.Level
import java.time.Instant
import javax.inject.Singleton

@Singleton
internal class MinionsStartDirectiveProcessor(
        private val directiveRegistry: DirectiveRegistry,
        private val scenariosRegistry: ScenariosRegistry,
        private val minionsKeeper: MinionsKeeper
) : DirectiveProcessor<MinionsStartDirectiveReference> {

    @LogInputAndOutput(level = Level.DEBUG)
    override fun accept(directive: Directive): Boolean {
        return directive is MinionsStartDirectiveReference && directive.scenarioId in scenariosRegistry
    }

    @LogInput(level = Level.DEBUG)
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
