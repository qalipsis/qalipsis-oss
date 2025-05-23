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

package io.qalipsis.core.head.factory

import io.qalipsis.core.handshake.RegistrationDirectedAcyclicGraph
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphEntity
import io.qalipsis.core.head.jdbc.entity.DirectedAcyclicGraphTagEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphRepository
import io.qalipsis.core.head.jdbc.repository.DirectedAcyclicGraphTagRepository
import io.qalipsis.core.head.jdbc.repository.ScenarioRepository
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList

@Singleton
internal class ScenarioDetailsUpdaterImpl(
    private val scenarioRepository: ScenarioRepository,
    private val directedAcyclicGraphRepository: DirectedAcyclicGraphRepository,
    private val directedAcyclicGraphTagRepository: DirectedAcyclicGraphTagRepository,
) : ScenarioDetailsUpdater {

    override suspend fun saveOrUpdateScenarios(
        tenantReference: String,
        registrationScenarios: List<RegistrationScenario>,
        existingFactory: FactoryEntity,
        deleteAbsentScenarios: Boolean
    ) {
        val inputScenarios = registrationScenarios.associateBy { it.name }.toMutableMap()
        val existingScenarios = scenarioRepository.findByFactoryId(tenantReference, existingFactory.id)

        val (keptScenarios, obsoleteScenarios) = existingScenarios.partition { it.name in inputScenarios.keys }
        if (deleteAbsentScenarios && obsoleteScenarios.isNotEmpty()) {
            scenarioRepository.deleteAll(obsoleteScenarios)
        }
        val scenariosToKeep = mutableListOf<ScenarioEntity>()
        if (keptScenarios.isNotEmpty()) {
            directedAcyclicGraphRepository.deleteByScenarioIdIn(keptScenarios.map { it.id })
            directedAcyclicGraphTagRepository.deleteByDirectedAcyclicGraphIdIn(keptScenarios.flatMap { it.dags }
                .map { it.id })

            scenariosToKeep += scenarioRepository.updateAll(keptScenarios.map {
                it.copy(
                    enabled = true,
                    defaultMinionsCount = inputScenarios.remove(it.name)!!.minionsCount,
                    dags = emptyList()
                )
            }).toList()
        }
        if (inputScenarios.isNotEmpty()) {
            scenariosToKeep += scenarioRepository.saveAll(inputScenarios.map { (_, scenario) ->
                ScenarioEntity(
                    factoryId = existingFactory.id,
                    scenarioName = scenario.name,
                    scenarioDescription = scenario.description,
                    scenarioVersion = scenario.version,
                    builtAt = scenario.builtAt,
                    defaultMinionsCount = scenario.minionsCount
                )
            }).toList()
        }

        val scenariosToKeepByName = scenariosToKeep.associateBy { it.name }
        val dagsToSave = prepareDagsOfLivingScenarios(registrationScenarios, scenariosToKeepByName)
        saveDagsAndTags(dagsToSave, registrationScenarios.flatMap { it.directedAcyclicGraphs })
    }

    /**
     * Converts [DirectedAcyclicGraphSummary] of the scenarios to a collection of [DirectedAcyclicGraphEntity].
     */
    private fun prepareDagsOfLivingScenarios(
        registrationScenarios: List<RegistrationScenario>,
        existingScenariosToKeepByName: Map<String, ScenarioEntity>
    ): List<DirectedAcyclicGraphEntity> {
        return registrationScenarios.flatMap { scenario ->
            scenario.directedAcyclicGraphs.map { dag ->
                DirectedAcyclicGraphEntity(
                    scenarioId = existingScenariosToKeepByName[scenario.name]!!.id,
                    name = dag.name,
                    singleton = dag.isSingleton,
                    underLoad = dag.isUnderLoad,
                    numberOfSteps = dag.numberOfSteps,
                    root = dag.isRoot
                )
            }
        }
    }

    /**
     * Saves actual [DirectedAcyclicGraphEntity] and [DirectedAcyclicGraphTagEntity] entities.
     */
    private suspend fun saveDagsAndTags(
        dagsToSave: List<DirectedAcyclicGraphEntity>,
        registrationDags: List<RegistrationDirectedAcyclicGraph>
    ) {
        if (dagsToSave.isNotEmpty()) {
            val dagsEntities = directedAcyclicGraphRepository.saveAll(dagsToSave).toList().associateBy { it.name }
            val dagsTagsToSave = registrationDags.flatMap { dag ->
                dag.tags.map { (key, value) ->
                    DirectedAcyclicGraphTagEntity(
                        dagId = dagsEntities[dag.name]!!.id,
                        selectorKey = key,
                        selectorValue = value
                    )
                }
            }
            if (dagsTagsToSave.isNotEmpty()) {
                directedAcyclicGraphTagRepository.saveAll(dagsTagsToSave).collect()
            }
        }
    }

}