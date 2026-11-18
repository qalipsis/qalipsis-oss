/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.meters

/**
 * Custom measurement metric adapted for measurements involving percentiles.
 * Holds the value of a statistical percentile, its observed measurement
 * alongside, the measured statistic.
 *
 * @property observationPoint a double value representing a statistical percentile[Statistic.PERCENTILE].
 *  It describes a point of interest in a statistical distribution to be evaluated.
 *
 * @author Francisca Eze
 */
class DistributionMeasurementMetric(
    override val value: Double,
    override val statistic: Statistic,
    val observationPoint: Double,
) : Measurement