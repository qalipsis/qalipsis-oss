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

package io.qalipsis.runtime.executors

import io.qalipsis.api.coroutines.CoroutineScopeProvider
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher

internal class SimpleCoroutineScopeProvider(
    override val global: CoroutineScope,
    override val campaign: CoroutineScope,
    override val io: CoroutineScope,
    override val background: CoroutineScope,
    override val orchestration: CoroutineScope
) : CoroutineScopeProvider {

    override fun close() {
        listOf(global, campaign, io, background, orchestration)
            .forEach { scope ->
                val context = scope.coroutineContext
                if (context is ExecutorCoroutineDispatcher) {
                    log.info { "Closing the coroutine dispatcher ${context}" }
                    tryAndLogOrNull(log) {
                        context.close()
                    }
                }
            }
        log.info { "All the coroutine dispatchers were closed" }
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}