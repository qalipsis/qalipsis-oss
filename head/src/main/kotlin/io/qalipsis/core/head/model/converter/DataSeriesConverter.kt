package io.qalipsis.core.head.model.converter

import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.model.DataSeries

/**
 * Interface of convertor for different data series formats.
 *
 * @author Francisca Eze
 */
internal interface DataSeriesConverter {

    /**
     * Converts from [DataSeriesEntity] to [DataSeries].
     */
    suspend fun convertToModel(dataSeriesEntity: DataSeriesEntity): DataSeries
}