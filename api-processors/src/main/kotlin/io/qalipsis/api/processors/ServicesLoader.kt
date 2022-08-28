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

package io.qalipsis.api.processors

import io.qalipsis.api.processors.injector.Injector
import io.qalipsis.api.services.ServicesFiles

/**
 *
 * @author Eric Jessé
 */
object ServicesLoader {

    /**
     * Loads the scenarios passing the injector as parameter.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> loadScenarios(injector: Injector): Collection<T> {
        return this.javaClass.classLoader.getResources("META-INF/qalipsis/scenarios")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { loaderClass ->
                try {
                    Class.forName(loaderClass).getConstructor(Injector::class.java)
                        .newInstance(injector) as T
                } catch (e: NoSuchMethodException) {
                    Class.forName(loaderClass).getConstructor().newInstance() as T
                }
            }
    }

    /**
     * Loads the profiles defined in the plugins.
     */
    fun loadPlugins(): Collection<String> {
        return this.javaClass.classLoader.getResources("META-INF/qalipsis/plugin")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

}
