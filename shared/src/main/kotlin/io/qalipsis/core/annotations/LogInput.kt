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

package io.qalipsis.core.annotations

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type
import io.qalipsis.core.interceptors.LoggingInterceptor
import org.slf4j.event.Level
import java.lang.annotation.Inherited

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@Around
@Type(LoggingInterceptor::class)
annotation class LogInput(
    /**
     * Level to trace normal input messages.
     */
    val level: Level = Level.TRACE,

    /**
     * Log the call stack.
     */
    val callstack: Boolean = false,

    /**
     * Level to trace the call stack.
     */
    val callstackLevel: Level = Level.TRACE
)
