package io.evolue.plugins.jackson

import com.fasterxml.jackson.databind.ObjectReader
import io.evolue.api.steps.datasource.DatasourceIterativeReader
import java.io.InputStreamReader

/**
 * Implementation of a [DatasourceIterativeReader] for Jackson.
 *
 * @author Eric Jessé
 */
internal class JacksonDatasourceIterativeReader<R>(
        private val inputStreamReader: InputStreamReader,
        private val objectReader: ObjectReader
) : DatasourceIterativeReader<R> {

    lateinit var iterator: Iterator<R>

    override fun start() {
        iterator = objectReader.readValues(inputStreamReader)
    }

    override fun hasNext() = iterator.hasNext()

    override fun next() = iterator.next()

}