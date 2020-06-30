package io.evolue.core.head.campaign

import io.evolue.api.context.ScenarioId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.core.cross.configuration.ENV_VOLATILE
import io.evolue.core.head.persistence.inmemory.InMemoryRepository
import io.micronaut.context.annotation.Requires
import javax.inject.Singleton

/**
 * Component to store and provides scenarios from all the factories.
 * The component automatically consumes the feedback from the factories to store all the scenarios.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_VOLATILE])
internal class InMemoryScenariosRepository : InMemoryRepository<HeadScenario, ScenarioId>(), HeadScenarioRepository
