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

package io.qalipsis.api.states

import io.qalipsis.api.context.MinionId

/**
 * Interface to share mutable states between steps. The implementations can be backed by in-memory or persistent caches.
 *
 * @author Eric Jessé
 */
interface SharedStateRegistry {

    suspend fun set(definition: SharedStateDefinition, payload: Any?)

    suspend fun set(values: Map<SharedStateDefinition, Any?>)

    suspend fun <T> get(definition: SharedStateDefinition): T?

    suspend fun <T> remove(definition: SharedStateDefinition): T?

    suspend fun get(definitions: Iterable<SharedStateDefinition>): Map<String, Any?>

    suspend fun remove(definitions: Iterable<SharedStateDefinition>): Map<String, Any?>

    suspend fun contains(definition: SharedStateDefinition): Boolean

    suspend fun clear()

    suspend fun clear(minionIds: Collection<MinionId>)

}
