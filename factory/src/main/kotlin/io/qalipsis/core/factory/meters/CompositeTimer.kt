/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters

import io.qalipsis.api.meters.Timer
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.function.Supplier

/**
 * Composite class to encapsulate the [Timer]s at the scenario and campaign
 * level in order to update both at the same time.
 *
 * This [Timer] should be seen as the [scenarioLevelTimer] by the calling application.
 * The meter registries are the only one aware of the existence of the [campaignLevelTimer]
 * and will ask for its publication when required.
 *
 * This instance of [Timer] is not known by the instances of QALIPSIS measurement publisher.
 *
 * @author Joël Valère
 */
internal data class CompositeTimer(
    private val scenarioLevelTimer: Timer,
    private val campaignLevelTimer: Timer,
) : Timer by scenarioLevelTimer {

    override fun record(amount: Long, unit: TimeUnit?) {
        scenarioLevelTimer.record(amount, unit)
        campaignLevelTimer.record(amount, unit)
    }

    override fun record(duration: Duration) {
        scenarioLevelTimer.record(duration)
        campaignLevelTimer.record(duration)
    }

     fun <T : Any?> record(f: Supplier<T>): T? {
        val s: Long = System.nanoTime()
        return try {
            f.get()
        } finally {
            val e: Long = System.nanoTime()
            record(e - s, NANOSECONDS)
        }
    }

    fun record(f: Runnable) {
        val s = System.nanoTime()
        try {
            f.run()
        } finally {
            val e = System.nanoTime()
            record(e - s, NANOSECONDS)
        }
    }

    fun <T : Any?> recordCallable(f: Callable<T>): T? {
        val s = System.nanoTime()
        return try {
            f.call()
        } finally {
            val e = System.nanoTime()
            record(e - s, NANOSECONDS)
        }
    }
}