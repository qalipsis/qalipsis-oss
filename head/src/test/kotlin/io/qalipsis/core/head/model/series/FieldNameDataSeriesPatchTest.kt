package io.qalipsis.core.head.model.series

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.model.FieldNameDataSeriesPatch
import io.qalipsis.core.head.report.DataType
import org.junit.jupiter.api.Test

internal class FieldNameDataSeriesPatchTest {

    @Test
    internal fun `should update the data series fieldName when different`() {
        // given
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 123,
            creatorId = 432,
            displayName = "the-name",
            color = "color",
            fieldName = "field",
            dataType = DataType.EVENTS,
            filters = setOf(
                DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value")
            )
        )
        val patch = FieldNameDataSeriesPatch("new-field")

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isTrue()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::fieldName).isEqualTo("new-field")
    }

    @Test
    internal fun `should not update the data series color when equal`() {
        // given
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 123,
            creatorId = 432,
            displayName = "the-name",
            color = "the-color",
            fieldName = "field",
            dataType = DataType.EVENTS,
            filters = setOf(
                DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value")
            )
        )
        val patch = FieldNameDataSeriesPatch("field")

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isFalse()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::fieldName).isEqualTo("field")
    }
}