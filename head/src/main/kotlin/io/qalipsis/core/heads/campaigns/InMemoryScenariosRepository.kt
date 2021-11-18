package io.qalipsis.core.heads.campaigns

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments.ENV_VOLATILE
import io.qalipsis.core.heads.persistence.inmemory.InMemoryRepository
import jakarta.inject.Singleton

/**
 * Component to store and provides scenarios from all the factories.
 * The component automatically consumes the feedback from the factories to store all the scenarios.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ENV_VOLATILE])
internal class InMemoryScenariosRepository : InMemoryRepository<ScenarioSummary, ScenarioId>(),
    ScenarioSummaryRepository
