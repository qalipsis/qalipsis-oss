package io.qalipsis.core.heads.campaigns

import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.heads.persistence.Repository

/**
 * Repository for [ScenarioSummary]s.
 *
 * @author Eric Jessé
 */
interface ScenarioSummaryRepository : Repository<ScenarioSummary, ScenarioId>