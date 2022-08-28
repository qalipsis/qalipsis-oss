/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.report

import io.micronaut.context.annotation.Requires
import io.micronaut.validation.Validated
import io.qalipsis.api.query.QueryDescription
import javax.validation.constraints.Max
import javax.validation.constraints.Positive

/**
 * Interface of data provider for [io.micrometer.core.instrument.Meter]s.
 * The implementation should be located in each plugin supporting the persistence of meters.
 *
 * @author Eric Jessé
 */
@Requires(env = ["standalone", "head"])
@Validated
interface MeterMetadataProvider {

    /**
     * Searches names of meters in the tenant, matching the filters if specified.
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
     * List all the fields that can be used for aggregation of data on meters.
     *
     * @param tenant the reference of the tenant owning the data
     */
    suspend fun listFields(tenant: String): Collection<DataField>

    /**
     * Searches tags matching the potential filters and provide also values.
     *
     * @param tenant the reference of the tenant owning the data
     * @param filters the different filters (potentially with wildcard *) the tags names should match
     * @param size the maximum count of results of tags names and values for each name
     */
    suspend fun searchTagsAndValues(
        tenant: String,
        filters: Collection<String>,
        @Positive @Max(100) size: Int
    ): Map<String, Collection<String>>

    /**
     * Prepares the query on meters and returns it wrapped into a JSON object containing potential additional metadata.
     */
    suspend fun createQuery(tenant: String, query: QueryDescription): String

}