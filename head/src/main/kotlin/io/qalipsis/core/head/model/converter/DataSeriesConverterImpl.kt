/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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