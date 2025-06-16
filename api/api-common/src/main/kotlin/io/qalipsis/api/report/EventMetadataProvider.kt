/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.report

import io.micronaut.context.annotation.Requires
import io.micronaut.validation.Validated
import io.qalipsis.api.query.QueryDescription
import javax.validation.constraints.Max
import javax.validation.constraints.Positive

/**
 * Interface of data provider for [io.qalipsis.api.events.Event]s.
 * The implementation should be located in each plugin supporting the persistence of events.
 *
 * @author Eric Jessé
 */
@Requires(env = ["standalone", "head"])
@Validated
interface EventMetadataProvider {

    /**
     * Searches names of events in the tenant, matching the filters if specified.
     *
     * @param tenant the reference of the tenant owning the data
     * @param filters the different filters (potentially with wildcard *) the names should match
     * @param size the maximum count of results to return
     */
    suspend fun searchNames(
        tenant: String,
        filters: Collection<String>,
        @Positive @Max(100) size: Int
    ): Collection<String>

    /**
     * List all the fields that can be used for aggregation of data on events.
     *
     * @param tenant the reference of the tenant owning the data
     * @param eventName the name of the event for which the fields are to be listed, if known
     */
    suspend fun listFields(tenant: String, eventName: String? = null): Collection<DataField>

    /**
     * Searches tags matching the potential filters and provide also values.
     *
     * @param tenant the reference of the tenant owning the data
     * @param eventName the name of the event for which the tags and values are to be listed, if known
     * @param filters the different filters (potentially with wildcard *) the tags names should match
     * @param size the maximum count of results of tags names and values for each name
     */
    suspend fun searchTagsAndValues(
        tenant: String,
        eventName: String? = null,
        filters: Collection<String>,
        @Positive @Max(100) size: Int
    ): Map<String, Collection<String>>

    /**
     * Prepares the query on events and returns it wrapped into a JSON object containing potential additional metadata.
     */
    suspend fun createQuery(tenant: String?, query: QueryDescription): String

}