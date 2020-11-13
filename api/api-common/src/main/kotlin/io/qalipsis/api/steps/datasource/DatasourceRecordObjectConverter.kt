package io.qalipsis.api.steps.datasource

import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.atomic.AtomicLong

/**
 * Wraps the object received from the datasource as a [DatasourceRecord].
 *
 * @author Eric Jessé
 */
class DatasourceRecordObjectConverter<R> : DatasourceObjectConverter<R, DatasourceRecord<R>> {

    override suspend fun supply(offset: AtomicLong, value: R, output: SendChannel<DatasourceRecord<R>>) {
        output.send(DatasourceRecord(offset.getAndIncrement(), value))
    }

}