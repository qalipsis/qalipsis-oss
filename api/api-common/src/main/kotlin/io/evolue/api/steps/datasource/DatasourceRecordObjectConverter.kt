package io.evolue.api.steps.datasource

import kotlinx.coroutines.channels.SendChannel

/**
 * Wraps the object received from the datasource as a [DatasourceRecord].
 *
 * @author Eric Jessé
 */
class DatasourceRecordObjectConverter<R> : DatasourceObjectConverter<R, DatasourceRecord<R>> {

    override suspend fun supply(offset: Long, value: R, output: SendChannel<DatasourceRecord<R>>) {
        output.send(DatasourceRecord(offset, value))
    }

}