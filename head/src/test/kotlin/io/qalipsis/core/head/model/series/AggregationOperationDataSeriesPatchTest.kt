package io.qalipsis.core.head.model.series

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.report.query.QueryAggregationOperator
import io.qalipsis.api.report.query.QueryClauseOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.model.AggregationOperationDataSeriesPatch
import org.junit.jupiter.api.Test

internal class AggregationOperationDataSeriesPatchTest {

    @Test
    internal fun `should update the data series aggregationOperation when different`() {
        // given
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 123,
            creatorId = 432,
            displayName = "the-name",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            dataType = DataType.EVENTS,
            filters = setOf(
                DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value")
            )
        )
        val patch = AggregationOperationDataSeriesPatch(QueryAggregationOperator.COUNT)

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isTrue()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::aggregationOperation).isEqualTo(QueryAggregationOperator.COUNT)
    }

    @Test
    internal fun `should not update the data series aggregationOperation when equal`() {
        // given
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 1431,
            creatorId = 123,
            displayName = "the-name",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            dataType = DataType.EVENTS,
            filters = setOf(
                DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value")
            )
        )
        val patch = AggregationOperationDataSeriesPatch(QueryAggregationOperator.AVERAGE)

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isFalse()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::aggregationOperation)
            .isEqualTo(QueryAggregationOperator.AVERAGE)
    }
}