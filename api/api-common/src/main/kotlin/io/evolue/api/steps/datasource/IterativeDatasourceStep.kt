package io.evolue.api.steps.datasource

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepError
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.steps.AbstractStep

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
        id: StepId,
        private val reader: DatasourceIterativeReader<R>,
        private val processor: DatasourceObjectProcessor<R, T>,
        private val converter: DatasourceObjectConverter<T, O>
) : AbstractStep<Unit, O>(id, null) {

    override suspend fun start() {
        log.trace("Starting datasource reader for step $id")
        reader.start()
    }

    override suspend fun stop() {
        log.trace("Stopping datasource reader for step $id")
        reader.stop()
        log.trace("Datasource reader stopped for step $id")
    }

    override suspend fun execute(context: StepContext<Unit, O>) {
        log.trace("Iterating datasource reader for step $id")
        var rowIndex = 0L
        while (reader.hasNext()) {
            try {
                val value = processor.process(rowIndex, reader.next())
                context.output.send(converter.supply(rowIndex, value))
            } catch (e: Exception) {
                context.errors.add(StepError(DatasourceException(rowIndex, e.message)))
            } finally {
                rowIndex++
            }
        }
        context.completed = true
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
