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

package io.qalipsis.core.head.report

import io.qalipsis.api.query.QueryDescription
import io.qalipsis.api.report.DataField
import io.qalipsis.api.report.EventMetadataProvider
import io.qalipsis.api.report.MeterMetadataProvider
import io.qalipsis.core.head.jdbc.entity.Defaults
import jakarta.annotation.Nullable
import jakarta.inject.Singleton

/**
 * Default implementation of [DataProvider].
 *
 * @author Eric Jessé
 */
@Singleton
class DefaultDataProvider(
    @Nullable private val eventProvider: EventMetadataProvider?,
    @Nullable private val meterProvider: MeterMetadataProvider?
) : DataProvider {

    override suspend fun searchNames(
        tenant: String,
        dataType: DataType,
        campaignKey: String?,
        filters: Collection<String>,
        size: Int
    ): Collection<String> {
        return when (dataType) {
            DataType.EVENTS -> eventProvider?.searchNames(tenant, campaignKey, filters, size)
            DataType.METERS -> meterProvider?.searchNames(tenant, campaignKey, filters, size)
        }.orEmpty()
    }

    override suspend fun listFields(tenant: String, dataType: DataType, name: String?): Collection<DataField> {
        return when (dataType) {
            DataType.EVENTS -> eventProvider?.listFields(tenant, eventName = name)
            DataType.METERS -> meterProvider?.listFields(tenant, meterName = name)
        }.orEmpty()
    }

    override suspend fun searchTagsAndValues(
        tenant: String,
        dataType: DataType,
        name: String?,
        filters: Collection<String>,
        size: Int
    ): Map<String, Collection<String>> {
        return when (dataType) {
            DataType.EVENTS -> eventProvider?.searchTagsAndValues(tenant, name, filters, size)
            DataType.METERS -> meterProvider?.searchTagsAndValues(tenant, name, filters, size)
        }.orEmpty()
    }

    override suspend fun createQuery(tenant: String, dataType: DataType, query: QueryDescription): String {
        return when (dataType) {
            DataType.EVENTS -> eventProvider?.createQuery(tenant.takeUnless { it == Defaults.TENANT }, query)
            DataType.METERS -> meterProvider?.createQuery(tenant.takeUnless { it == Defaults.TENANT }, query)
        }.orEmpty()
    }
}