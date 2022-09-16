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

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.mockk.impl.annotations.InjectMockKs
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.report.SharingMode
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
class DataSeriesConverterImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()


    @InjectMockKs
    private lateinit var converter: DataSeriesConverterImpl

    @Test
    fun `should convert to model`() = testDispatcherProvider.runTest {
        //given
        val dataSeriesEntity = DataSeriesEntity(
            reference = "QALI-1",
            displayName = "NewData",
            dataType = DataType.EVENTS,
            color = "Red",
            filters = emptySet(),
            fieldName = "dataSeriesFieldName",
            creatorId = 1,
            displayFormat = "Full",
            tenantId = 1L,
        )

        //when
        val result = converter.convertToModel(dataSeriesEntity)

        //then
        assertThat(result).isDataClassEqualTo(
            DataSeries(
                displayName = "NewData",
                dataType = DataType.EVENTS,
                color = "Red",
                filters = emptySet(),
                fieldName = "dataSeriesFieldName",
                displayFormat = "Full",
                sharingMode = SharingMode.READONLY,
                timeframeUnit = null,
                aggregationOperation= QueryAggregationOperator.COUNT
            )
        )
    }

}
