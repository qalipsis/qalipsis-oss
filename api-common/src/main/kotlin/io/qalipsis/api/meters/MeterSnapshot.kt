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

import java.time.Instant

/**
 * Holds a snapshot of collected measurements over a given period for any given meter.
 *
 * @property meterId meter that is being measured
 * @property measurements list of measurements belonging to the meter sampled over a given time
 * @property timestamp represent the time instant in epoch seconds that the snapshot was taken
 *
 *  @author Francisca Eze
 */
interface MeterSnapshot {

    val meterId: Meter.Id

    val timestamp: Instant

    val measurements: Collection<Measurement>

    /**
     * Duplicates the [MeterSnapshot] with a different [meterId].
     */
    fun duplicate(
        meterId: Meter.Id = this.meterId
    ): MeterSnapshot

}