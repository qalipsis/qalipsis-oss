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

package io.qalipsis.api.meters

/**
 * A measurement sampled from any given meter. It contains an enum representing the value measured and a corresponding
 * value parameter to hold the measurement.
 *
 * @property value the value returned by the measurement
 * @property statistic describe the possibilities of options that can be measured
 *
 * @author Francisca Eze
 */
interface Measurement {

    val value: Double

    val statistic: Statistic
}