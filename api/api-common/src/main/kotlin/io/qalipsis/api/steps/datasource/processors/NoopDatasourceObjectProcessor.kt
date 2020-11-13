package io.qalipsis.api.steps.datasource.processors

import io.qalipsis.api.steps.datasource.DatasourceObjectProcessor
import java.util.concurrent.atomic.AtomicLong

/**
 * No op processor that simply returns the received value.
 *
 * @author Eric Jessé
 */
class NoopDatasourceObjectProcessor<O> : DatasourceObjectProcessor<O, O> {

    override fun process(offset: AtomicLong, readObject: O) = readObject

}
