package io.qalipsis.core.head.model.series

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.jdbc.entity.DataType
import io.qalipsis.core.head.jdbc.entity.Operator
import io.qalipsis.core.head.model.TimeframeUnitDataSeriesPatch
import org.junit.jupiter.api.Test
import java.time.Duration

internal class TimeframeUnitDataSeriesPatchTest {

    @Test
    internal fun `should update the data series timeframeUnit when different`() {
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
                DataSeriesFilterEntity("name", Operator.IS, "value")
            ),
            timeframeUnitMs = 1_000
        )
        val patch = TimeframeUnitDataSeriesPatch(Duration.ofMillis(2_500))

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isTrue()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::timeframeUnitMs).isEqualTo(2_500)
    }

    @Test
    internal fun `should not update the data series timeframeUnit when equal`() {
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
                DataSeriesFilterEntity("name", Operator.IS, "value")
            ),
            timeframeUnitMs = 1_000
        )
        val patch = TimeframeUnitDataSeriesPatch(Duration.ofMillis(1_000))

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isFalse()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::timeframeUnitMs).isEqualTo(1_000)
    }
}