package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import java.util.concurrent.atomic.AtomicLong

/**
 * General purpose step to read data from a source, transform and forward.
 *
 * @property reader the reader providing the raw values in an iterative way
 * @property processor validates and transforms the raw values individually
 * @property converter converts the raw values to forward them individually to next steps
 *
 * @param R raw type returned by [reader]
 * @param O output type returned by the step
 *
 * @author Eric Jessé
 */
class IterativeDatasourceStep<R, T, O>(
    name: StepName,
    private val reader: DatasourceIterativeReader<R>,
    private val processor: DatasourceObjectProcessor<R, T>,
    private val converter: DatasourceObjectConverter<T, O>
) : AbstractStep<Unit, O>(name, null) {

    override suspend fun start(context: StepStartStopContext) {
        log.trace { "Starting datasource reader for step $name" }
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
        context.isTail = false
        log.trace { "Iterating datasource reader for step $name" }
        val rowIndex = AtomicLong()
        while (reader.hasNext()) {
            try {
                val value = processor.process(rowIndex, reader.next())
                log.trace { "Received one record" }
                converter.supply(rowIndex, value, context)
            } catch (e: Exception) {
                context.addError(StepError(DatasourceException(rowIndex.get() - 1, e.message), this.name))
            }
        }

        context.isTail = true
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
