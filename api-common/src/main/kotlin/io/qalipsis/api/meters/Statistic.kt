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
 * Describes the possibilities of values contained in a measurement.
 *
 *  @property TOTAL represents the total of the amount recorded.
 *  @property TOTAL_TIME represents the sum total of time recorded in the base unit time.
 *  @property COUNT represents the rate per second for calls.
 *  @property MAX represents the maximum amount recorded.
 *  @property VALUE instantaneous values at any given time.
 *  @property MEAN represents the average value within a given set of recorded amount.
 *  @property PERCENTILE expresses where an observation falls in a range of other observations.
 *
 *  @author Francisca Eze
 */
enum class Statistic(val value: String) {
    TOTAL("total"),
    TOTAL_TIME("total_time"),
    COUNT("count"),
    MAX("max"),
    VALUE("value"),
    MEAN("mean"),
    PERCENTILE("percentile"),
}