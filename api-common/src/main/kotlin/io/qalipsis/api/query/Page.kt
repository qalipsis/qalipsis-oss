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

package io.qalipsis.api.query

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Page of records.
 *
 * @property page index of the current page
 * @property totalPages total count of pages matching the criteria
 * @property totalElements total count of elements matching the criteria
 * @property elements list of elements in the current page
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Page",
    title = "Page of the result",
    description = "Details of a page of records"
)
data class Page<T>(
    @field:Schema(description = "The index of the current page")
    val page: Int,

    @field:Schema(description = "The total count of pages matching the criteria")
    val totalPages: Int,

    @field:Schema(description = "The total count of elements matching the criteria")
    val totalElements: Long,

    @field:Schema(description = "The list of elements in the current page")
    val elements: List<T>
)