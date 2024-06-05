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

package io.qalipsis.core.factory.meters

import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import java.time.Instant

/**
 * Implementation of [MeterSnapshot] to store meter measurement.
 *
 * @author Francisca Eze
 */
class MeterSnapshotImpl<T : Meter<T>>(
    override val timestamp: Instant,
    override val meter: T,
    override val measurements: Collection<Measurement>,
) : MeterSnapshot<T>