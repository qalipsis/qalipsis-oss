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

package io.qalipsis.api.context

import io.micrometer.core.instrument.Tags

interface MonitoringTags {

    /**
     * Converts the context to a map that can be used as tags for logged events.
     */
    fun toEventTags(): Map<String, String>

    /**
     * Converts the context to a map that can be used as tags for meters. The tags should not contain
     * any detail about the minion, but remains at the level of step, scenario and campaign.
     */
    fun toMetersTags(): Tags
}