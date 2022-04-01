package io.qalipsis.core.head.inmemory

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.core.campaigns.ScenarioSummary

/**
 * Repository for [ScenarioSummary]s.
 *
 * @author Eric Jessé
 */
interface ScenarioSummaryRepository : InMemoryRepository<ScenarioSummary, ScenarioName>