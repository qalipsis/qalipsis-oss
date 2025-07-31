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

package io.qalipsis.core.factory.orchestration

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryShutdownDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.lifetime.ProcessExitCodeSupplier
import jakarta.inject.Singleton
import java.util.Optional

/**
 * Process blocker to wait forever and keep the factory alive, in order to execute campaign on demands.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(property = "dry-run.enabled", notEquals = StringUtils.TRUE),
    Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.AUTOSTART])
)
class WaitingForeverBlocker : ProcessExitCodeSupplier, DirectiveListener<FactoryShutdownDirective> {

    private val latch = Latch(true)

    override fun getOrder() = Int.MIN_VALUE

    override suspend fun await(): Optional<Int> {
        latch.await()
        return Optional.empty()
    }

    override fun accept(directive: Directive): Boolean {
        return directive is FactoryShutdownDirective
    }

    override suspend fun notify(directive: FactoryShutdownDirective) {
        latch.cancel()
    }

}