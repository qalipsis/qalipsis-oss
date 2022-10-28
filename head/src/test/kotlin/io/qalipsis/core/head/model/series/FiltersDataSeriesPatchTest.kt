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

package io.qalipsis.core.head.model.series

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.model.DataSeriesFilter
import io.qalipsis.core.head.model.FiltersDataSeriesPatch
import io.qalipsis.core.head.report.DataType
import org.junit.jupiter.api.Test

internal class FiltersDataSeriesPatchTest {

    @Test
    internal fun `should update the data series filters when different`() {
        // given
        val filters = setOf(DataSeriesFilterEntity("name", QueryClauseOperator.IS_IN, "value"))
        val newFilters = setOf(
            DataSeriesFilter("name", QueryClauseOperator.IS_IN, "value"),
            DataSeriesFilter("second-filter", QueryClauseOperator.IS, "second-value")
        )
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 123,
            creatorId = 432,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            filters = filters
        )
        val patch = FiltersDataSeriesPatch(newFilters)

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isTrue()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::filters).isEqualTo(
            setOf(
                DataSeriesFilterEntity("name", QueryClauseOperator.IS_IN, "value"),
                DataSeriesFilterEntity("second-filter", QueryClauseOperator.IS, "second-value")
            )
        )
    }

    @Test
    internal fun `should not update the data series aggregationOperation when equal`() {
        // given
        val filters = setOf(
            DataSeriesFilter("name", QueryClauseOperator.IS, "value")
        )
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 123,
            creatorId = 432,
            displayName = "the-name",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            filters = setOf(DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value"))
        )
        val patch = FiltersDataSeriesPatch(filters)

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isFalse()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::filters)
            .isEqualTo(setOf(DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value")))
    }
}