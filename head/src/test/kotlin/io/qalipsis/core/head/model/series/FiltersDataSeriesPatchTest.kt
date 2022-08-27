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