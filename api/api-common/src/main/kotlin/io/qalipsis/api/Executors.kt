/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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