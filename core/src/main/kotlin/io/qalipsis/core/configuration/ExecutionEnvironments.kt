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

package io.qalipsis.core.configuration

/**
 * Set of environments available to configure QALIPSIS at startup.
 *
 * @author Eric Jessé
 */
object ExecutionEnvironments {
    /**
     * Enables for in-memory repositories instead of persistent ones.
     */
    const val TRANSIENT = "transient"

    /**
     * Automatically starts a campaign when the instance is ready. Is only applicable in
     * conjunction with [STANDALONE].
     */
    const val AUTOSTART = "autostart"

    /**
     * Starts an instance with head and factory running aside in the same JVM.
     */
    const val STANDALONE = "standalone"

    /**
     * Enables a factory when it is unique in the cluster.
     */
    const val SINGLE_FACTORY = "single-factory"

    /**
     * Enables a head when it is unique in the cluster.
     */
    const val SINGLE_HEAD = "single-head"

    /**
     * Enables the integration with Redis to support data caching and messaging.
     */
    const val REDIS = "redis"

    /**
     * Enables the integration with PostgreSQL to support data persistence.
     */
    const val POSTGRESQL = "pgsql"

    /**
     * Enables the default configuration for a head.
     */
    const val HEAD = "head"

    /**
     * Enables the default configuration for a factory.
     */
    const val FACTORY = "factory"

    /**
     * Distributed streaming platform property configuration.
     */
    const val DISTRIBUTED_STREAMING_PLATFORM_PROPERTY = "distributed.streaming.platform"
}
