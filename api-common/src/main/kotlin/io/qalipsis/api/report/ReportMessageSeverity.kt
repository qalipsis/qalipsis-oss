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

package io.qalipsis.api.report

/**
 * Severity of a [ReportMessage].
 *
 * @author Eric Jessé
 */
enum class ReportMessageSeverity {
    /**
     * Severity for messages that have no impact on the final result and are just for user information.
     */
    INFO,

    /**
     * Severity for issues that have no impact on the final result but could potentially have negative side effect.
     */
    WARN,

    /**
     * Severity for issues that will let the campaign continue until the end but will make the campaign fail.
     */
    ERROR,

    /**
     * Severity for issues that will immediately abort the campaign.
     */
    ABORT

}
