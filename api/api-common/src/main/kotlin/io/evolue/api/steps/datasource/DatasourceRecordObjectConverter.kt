package io.evolue.api.steps.datasource

/**
 * Wraps the object received from the datasource as a [DatasourceRecord].
 *
 * @author Eric Jessé
 */
class DatasourceRecordObjectConverter<R> : DatasourceObjectConverter<R, DatasourceRecord<R>> {

    override fun supply(offset: Long, value: R) = DatasourceRecord(offset, value)

}