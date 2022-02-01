package io.qalipsis.core.head.inmemory

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.campaigns.ScenarioSummary

/**
 * Repository for [ScenarioSummary]s.
 *
 * @author Eric Jessé
 */
interface ScenarioSummaryRepository : InMemoryRepository<ScenarioSummary, ScenarioId>