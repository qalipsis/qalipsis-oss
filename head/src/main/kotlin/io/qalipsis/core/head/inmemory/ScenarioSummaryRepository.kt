package io.qalipsis.core.head.inmemory

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.persistence.Repository

/**
 * Repository for [ScenarioSummary]s.
 *
 * @author Eric Jessé
 */
interface ScenarioSummaryRepository : Repository<ScenarioSummary, ScenarioId>