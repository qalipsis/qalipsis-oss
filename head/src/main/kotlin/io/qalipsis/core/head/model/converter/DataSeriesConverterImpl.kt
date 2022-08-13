package io.qalipsis.core.head.model.converter

import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesFilter
import jakarta.inject.Singleton

/**
 * Converts for different formats around data series
 *
 * @author Francisca Eze
 */
@Singleton
internal class DataSeriesConverterImpl: DataSeriesConverter {

    override suspend fun convertToModel(dataSeriesEntity: DataSeriesEntity): DataSeries {
        return DataSeries(
            displayName = dataSeriesEntity.displayName,
            sharingMode = dataSeriesEntity.sharingMode,
            dataType = dataSeriesEntity.dataType,
            color = dataSeriesEntity.color,
            filters = dataSeriesEntity.filters.map { dataSeriesFilterEntity ->
                DataSeriesFilter(
                    dataSeriesFilterEntity.name,
                    dataSeriesFilterEntity.operator,
                    dataSeriesFilterEntity.value)
            }.toSet(),
            fieldName = dataSeriesEntity.fieldName,
            aggregationOperation = dataSeriesEntity.aggregationOperation,
            timeframeUnit = dataSeriesEntity.timeframeUnitAsDuration,
            displayFormat = dataSeriesEntity.displayFormat
        )

    }

}