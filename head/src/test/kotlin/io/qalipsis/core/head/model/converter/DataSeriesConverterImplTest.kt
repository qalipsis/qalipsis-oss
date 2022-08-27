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
