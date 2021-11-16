package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepOutput
import java.util.concurrent.atomic.AtomicLong

/**
 * Wraps the object received from the datasource as a [DatasourceRecord].
 *
 * @author Eric Jessé
 */
class DatasourceRecordObjectConverter<R> : DatasourceObjectConverter<R, DatasourceRecord<R>> {

    override suspend fun supply(offset: AtomicLong, value: R, output: StepOutput<DatasourceRecord<R>>) {
        output.send(DatasourceRecord(offset.getAndIncrement(), value))
    }

}
