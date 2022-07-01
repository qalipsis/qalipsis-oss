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
import io.qalipsis.core.head.jdbc.entity.SharingMode
import io.qalipsis.core.head.model.SharingModeDataSeriesPatch
import org.junit.jupiter.api.Test

internal class SharingModeDataSeriesPatchTest {

    @Test
    internal fun `should update the data series sharingMode when different`() {
        // given
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 123,
            creatorId = 432,
            displayName = "the-name",
            sharingMode = SharingMode.READONLY,
            dataType = DataType.EVENTS,
            filters = setOf(
                DataSeriesFilterEntity("name", Operator.IS, "value")
            )
        )
        val patch = SharingModeDataSeriesPatch(SharingMode.NONE)

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isTrue()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.NONE)
    }

    @Test
    internal fun `should not update the data series sharingMode when equal`() {
        // given
        val dataSeriesEntity = DataSeriesEntity(
            reference = "my-data-series",
            tenantId = 123,
            creatorId = 432,
            displayName = "the-name",
            dataType = DataType.EVENTS,
            filters = setOf(
                DataSeriesFilterEntity("name", Operator.IS, "value")
            )
        )
        val patch = SharingModeDataSeriesPatch(SharingMode.READONLY)

        // when
        val result = patch.apply(dataSeriesEntity)

        // then
        assertThat(result).isFalse()
        assertThat(dataSeriesEntity).prop(DataSeriesEntity::sharingMode).isEqualTo(SharingMode.READONLY)
    }
}