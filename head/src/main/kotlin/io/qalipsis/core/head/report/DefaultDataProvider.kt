package io.qalipsis.core.head.report

import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.EventProvider
import io.qalipsis.api.report.MeterProvider
import io.qalipsis.api.report.query.QueryDescription
import jakarta.inject.Singleton
import javax.annotation.Nullable

/**
 * Default implementation of [DataProvider].
 *
 * @author Eric Jessé
 */
@Singleton
internal class DefaultDataProvider(
    @Nullable private val eventProvider: EventProvider?,
    @Nullable private val meterProvider: MeterProvider?
) : DataProvider {

    override fun searchNames(
        tenant: String,
        dataType: DataType,
        filters: Collection<String>,
        size: Int
    ): Collection<String> {
        return when (dataType) {
            DataType.EVENTS -> eventProvider?.searchNames(tenant, filters, size)
            DataType.METERS -> meterProvider?.searchNames(tenant, filters, size)
        }.orEmpty()
    }

    override fun listFields(tenant: String, dataType: DataType): Collection<DataField> {
        return when (dataType) {
            DataType.EVENTS -> eventProvider?.listFields(tenant)
            DataType.METERS -> meterProvider?.listFields(tenant)
        }.orEmpty()
    }

    override fun searchTagsAndValues(
        tenant: String,
        dataType: DataType,
        filters: Collection<String>,
        size: Int
    ): Map<String, Collection<String>> {
        return when (dataType) {
            DataType.EVENTS -> eventProvider?.searchTagsAndValues(tenant, filters, size)
            DataType.METERS -> meterProvider?.searchTagsAndValues(tenant, filters, size)
        }.orEmpty()
    }

    override fun createQuery(tenant: String, dataType: DataType, query: QueryDescription): String {
        return when (dataType) {
            DataType.EVENTS -> eventProvider?.createQuery(tenant, query)
            DataType.METERS -> meterProvider?.createQuery(tenant, query)
        }.orEmpty()
    }
}