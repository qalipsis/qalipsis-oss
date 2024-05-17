/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.api.executionprofile

/**
 * A strategy configuration indicator determining how to end the scenario.
 */
enum class CompletionMode {

    /**
     * No minion can be restarted if the remaining time is less
     * than the elapsed time to execute the scenario.
     */
    HARD,

    /**
     * Restart the minions unless the end of the latest stage is reached.
     */
    GRACEFUL
}