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

package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import java.util.concurrent.atomic.AtomicLong

/**
 * General purpose step to read data from a source sequentially, transform and forward.
 *
 * @property reader the reader providing the raw values in an iterative way
 * @property processor validates and transforms the raw values individually
 * @property converter converts the raw values to forward them individually to next steps
 *
 * @param R raw type returned by [reader]
 * @param T intermediate type from [processor] to [converter]
 * @param O output type returned by the step
 *
 * @author Eric Jessé
 */
class SequentialDatasourceStep<R, T, O>(
    name: StepName,
    private val reader: DatasourceIterativeReader<R>,
    private val processor: DatasourceObjectProcessor<R, T>,
    private val converter: DatasourceObjectConverter<T, O>
) : AbstractStep<Unit, O>(name, null) {

    private val rowIndex = AtomicLong()

    override suspend fun start(context: StepStartStopContext) {
        log.trace { "Starting datasource reader for step $name" }
        rowIndex.set(0)
        converter.start(context)
        processor.start(context)
        reader.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        log.trace { "Stopping datasource reader for step $name" }
        kotlin.runCatching {
            reader.stop(context)
        }
        kotlin.runCatching {
            processor.stop(context)
        }
        kotlin.runCatching {
            converter.stop(context)
        }
        log.trace { "Datasource reader stopped for step $name" }
    }

    override suspend fun execute(context: StepContext<Unit, O>) {
        log.trace { "Sequential datasource reader for step $name" }
        if (reader.hasNext()) {
            val value = processor.process(rowIndex, reader.next())
            log.trace { "Received one record" }
            converter.supply(rowIndex, value, context)
        } else {
            context.isTail = true
        }
    }

    companion object {

        private val log = logger()

    }
}
