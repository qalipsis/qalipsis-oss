/*
 * Copyright 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.api.meters

/**
 * Possibilities of [io.qalipsis.api.meters.Meter] that are available.
 *
 * @property COUNTER tracks monotonically increasing values
 * @property GAUGE tracks values that go up and down
 * @property TIMER track a large number of short running events
 * @property DISTRIBUTION_SUMMARY tracks the statistical distribution of events
 * @property STATISTICS tracks the sum total statistical distribution of events across the application
 * @property RATE measures the ratio between two independently tracked measurements
 * @property THROUGHPUT tracks the number of hits measured per a configured unit of time
 * @author Francisca Eze
 */
enum class MeterType(val value: String) {
    COUNTER("counter"),
    GAUGE("gauge"),
    TIMER("timer"),
    DISTRIBUTION_SUMMARY("summary"),
    STATISTICS("statistics"),
    RATE("rate"),
    THROUGHPUT("throughput")
}