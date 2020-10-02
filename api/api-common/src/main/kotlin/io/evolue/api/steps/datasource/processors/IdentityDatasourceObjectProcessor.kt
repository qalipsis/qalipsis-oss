package io.evolue.api.steps.datasource.processors

import io.evolue.api.steps.datasource.DatasourceObjectProcessor

/**
 * No op processor that simply returns the received value.
 *
 * @author Eric Jessé
 */
class IdentityDatasourceObjectProcessor<O> : DatasourceObjectProcessor<O, O> {

    override fun process(offset: Long, readObject: O) = readObject

}
