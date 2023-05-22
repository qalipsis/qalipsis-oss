/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.feedbacks


import io.qalipsis.core.feedbacks.FeedbackStatus.COMPLETED
import io.qalipsis.core.feedbacks.FeedbackStatus.FAILED
import io.qalipsis.core.feedbacks.FeedbackStatus.IGNORED
import io.qalipsis.core.feedbacks.FeedbackStatus.IN_PROGRESS
import kotlinx.serialization.Serializable

/**
 * A [Feedback] is sent from the factories to the headers to notify them of the status of an operation.
 */
@Serializable
abstract class Feedback

/**
 * Status of a feedback to a task.
 *
 * @property IN_PROGRESS the task is currently being processed by its executor
 * @property IGNORED the task is not relevant for its executor
 * @property COMPLETED the task was completed successfully
 * @property FAILED the task execution failed
 *
 * @author Eric Jessé
 */
enum class FeedbackStatus(val isDone: Boolean) {
    IN_PROGRESS(false),
    IGNORED(true),
    COMPLETED(true),
    FAILED(true)
}
