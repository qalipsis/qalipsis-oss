/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.query

import io.micronaut.core.annotation.Introspected
import java.time.Duration
import java.time.Instant

/**
 * Description of the context and additional clauses for the execution of prepared aggregation queries
 * for time-series data.
 *
 * @property campaignsReferences references of all the campaigns that generated the data
 * @property scenariosNames names of all the scenarios that generated the data, defaults to empty for all the scenarios of the selected campaigns
 * @property from start of the data to aggregate, defaults to null for all the data of the selected campaigns until [until]
 * @property until end of the data to aggregate, defaults to null for all the data of the selected campaigns since [from]
 * @property aggregationTimeframe duration of the time-buckets to perform the aggregations, defaults to null for the one set in each pre-configured query
 *
 * @author Eric Jessé
 */
@Introspected
data class AggregationQueryExecutionContext(
    val campaignsReferences: Set<String>,
    val scenariosNames: Set<String> = emptySet(),
    val from: Instant,
    val until: Instant,
    val aggregationTimeframe: Duration
)
