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

package io.qalipsis.api.annotations

/**
 * Annotation to mark a method as a scenario specification. The function will be executed at startup
 * of the factory to load the specification. The method does not have to return something. Any returned value
 * will be ignored.
 *
 * <code>
 * @Scenario
 * fun createMyScenario() {
 *   scenario("my-scenario) {
 *          // Configure you scenario here.
 *      }
 *      // Then add steps.
 *      .justDo { context ->
 *          // ...
 *      }
 * }
 * <code>
 *
 * @author Eric Jessé
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Scenario(

    /**
     * Unique identifier or the scenario, should be kebab-cased, ex: `this-is-my-scenario`.
     */
    val name: String = "",

    /**
     * Display name or user-friendly description of the scenario, defaults to an empty string.
     */
    val description: String = "",

    /**
     * Version of the scenario, should be a dot-separated version, defaults to `0.<compilation-instant>`.
     */
    val version: String = ""

)