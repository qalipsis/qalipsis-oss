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

package io.qalipsis.api

/**
 * Constants to name the different executors.
 *
 * @author Eric Jessé
 */
object Executors {

    const val GLOBAL_EXECUTOR_NAME = "global"

    const val CAMPAIGN_EXECUTOR_NAME = "campaign"

    const val IO_EXECUTOR_NAME = "io"

    const val BACKGROUND_EXECUTOR_NAME = "background"

    const val ORCHESTRATION_EXECUTOR_NAME = "orchestration"
}