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
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.core.head.jdbc.entity.DataSeriesEntity
import io.qalipsis.core.head.jdbc.entity.DataSeriesFilterEntity
import io.qalipsis.core.head.model.TimeframeUnitDataSeriesPatch
import io.qalipsis.core.head.report.DataType
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
                DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value")
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
                DataSeriesFilterEntity("name", QueryClauseOperator.IS, "value")
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