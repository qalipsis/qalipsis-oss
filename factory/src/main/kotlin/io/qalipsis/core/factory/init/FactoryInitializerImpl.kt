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

package io.qalipsis.core.factory.init

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.micronaut.validation.Validated
import io.qalipsis.api.Executors
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.NoRetryPolicy
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.scenario.ConfiguredScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecificationsKeeper
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
import io.qalipsis.core.factory.orchestration.ScenarioImpl
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.factory.orchestration.StepUtils.type
import io.qalipsis.core.factory.steps.MinionsKeeperAware
import io.qalipsis.core.factory.steps.PipeStep
import io.qalipsis.core.factory.steps.RunnerAware
import io.qalipsis.core.lifetime.ExitStatusException
import io.qalipsis.core.lifetime.FactoryStartupComponent
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.annotation.Nullable
import javax.annotation.PreDestroy
import javax.validation.Valid

/**
 * Default implementation of [ScenariosInitializer].
 *
 * @author Eric Jessé
 */
@Singleton
@Validated
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class FactoryInitializerImpl(
    @Named(Executors.GLOBAL_EXECUTOR_NAME) private val coroutineDispatcher: CoroutineDispatcher,
    private val initializationContext: InitializationContext,
    private val scenarioRegistry: ScenarioRegistry,
    private val scenarioSpecificationsKeeper: ScenarioSpecificationsKeeper,
    private val stepSpecificationConverters: List<StepSpecificationConverter<*>>,
    stepSpecificationDecoratorConverters: List<StepSpecificationDecoratorConverter<*>>,
    private val runner: Runner,
    private val minionsKeeper: MinionsKeeper,
    private val idGenerator: IdGenerator,
    private val dagTransitionStepFactory: DagTransitionStepFactory,
    private val factoryChannel: FactoryChannel,
    private val factoryConfiguration: FactoryConfiguration,
    @Nullable private val handshakeBlocker: HandshakeBlocker?,
    @PositiveDuration
    @Property(name = "scenario.conversion.timeout", defaultValue = "5s")
    private val conversionTimeout: Duration,
    @Property(name = "scenario.allow-empty", defaultValue = "false")
    private val allowEmptyScenario: Boolean = false,
) : ScenariosInitializer, FactoryStartupComponent {

    init {
        require(stepSpecificationConverters.isNotEmpty()) { "No step specification converter was found" }
        require(stepSpecificationDecoratorConverters.isNotEmpty()) { "No step decorator converter was found" }
    }

    /**
     * Collection of DAGs accessible by scenario and DAG ID.
     */
    private val dagsByScenario: MutableMap<ScenarioName, MutableMap<DirectedAcyclicGraphName, DirectedAcyclicGraph>> =
        ConcurrentHashMap()

    // Sort the decorator converters in the expected order.
    private val stepSpecificationDecoratorConverters = stepSpecificationDecoratorConverters.sortedBy { it.order }

    private lateinit var scenarioSpecs: Map<ScenarioName, ScenarioSpecification>

    override fun getStartupOrder() = Ordered.LOWEST_PRECEDENCE

    override fun init() {
        try {
            refresh()
        } catch (e: Exception) {
            if (e is IllegalArgumentException || e.cause is IllegalArgumentException) {
                log.error { "${e.message}" }
            } else {
                log.error(e) { "${e.message}" }
            }
            handshakeBlocker?.cancel()
            throw e
        }
    }

    override fun refresh() {
        CompletableFuture.supplyAsync {
            try {
                log.info { "Refreshing the scenarios specifications" }
                scenarioSpecificationsKeeper.reload()

                scenarioSpecs = scenarioSpecificationsKeeper.scenariosSpecifications

                if (scenarioSpecs.isEmpty() && !allowEmptyScenario) {
                    throw ExitStatusException(IllegalArgumentException("No enabled scenario could be found"), 102)
                }

                val allScenarios = mutableListOf<Scenario>()
                scenarioSpecs.forEach { (scenarioName, scenarioSpecification) ->
                    scenarioSpecification as ConfiguredScenarioSpecification
                    require(scenarioSpecification.size <= factoryConfiguration.campaign.maxScenarioStepSpecificationsCount)
                    { "The maximal number of steps specifications in a step scenario should not exceed ${factoryConfiguration.campaign.maxScenarioStepSpecificationsCount}, but was ${scenarioSpecification.size}" }

                    log.info { "Converting the scenario specification $scenarioName" }
                    val scenario = convertScenario(scenarioName, scenarioSpecification)
                    allScenarios.add(scenario)
                    scenario.dags.forEach { dag ->
                        dagsByScenario.computeIfAbsent(scenarioName) { ConcurrentHashMap() }[dag.name] = dag
                    }
                    log.info { "Conversion of the scenario specification $scenarioName is complete" }
                }
                runBlocking(coroutineDispatcher) {
                    initializationContext.startHandshake(allScenarios)
                }
                Result.success(Unit)
            } catch (e: ExitStatusException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(ExitStatusException(e, 103))
            }
        }.get(conversionTimeout.toMillis(), TimeUnit.MILLISECONDS).getOrThrow()
    }

    @PreDestroy
    fun destroy() {
        runBlocking(coroutineDispatcher) {
            log.info { "Stopping all the scenarios..." }
            dagsByScenario.keys.mapNotNull(scenarioRegistry::get).forEach(Scenario::destroy)
            log.info { "All the scenarios were stopped..." }
        }
    }

    @LogInputAndOutput
    @KTestable
    protected fun convertScenario(
        scenarioName: ScenarioName,
        scenarioSpecification: ConfiguredScenarioSpecification
    ): Scenario {
        if (scenarioSpecification.dagsUnderLoad.isEmpty()) {
            throw InvalidSpecificationException(
                "There is no main branch defined in scenario $scenarioName, please prefix at least one root branch with 'start()'"
            )
        }

        val executionProfile = scenarioSpecification.executionProfile ?: throw InvalidSpecificationException(
            "The scenario $scenarioName requires an execution profile"
        )
        val defaultRetryPolicy = scenarioSpecification.retryPolicy ?: NoRetryPolicy()
        val scenario = ScenarioImpl(
            scenarioName,
            executionProfile = executionProfile,
            defaultRetryPolicy = defaultRetryPolicy,
            minionsCount = scenarioSpecification.minionsCount,
            factoryChannel = factoryChannel
        )
        scenarioRegistry.add(scenario)

        runBlocking(coroutineDispatcher) {
            @Suppress("UNCHECKED_CAST")
            convertSteps(
                scenarioSpecification, scenario, null, null,
                scenarioSpecification.rootSteps as List<StepSpecification<Any?, Any?, *>>
            )
        }
        require(scenario.dags.size >= scenarioSpecification.dagsCount) {
            "Not all the DAGs were created, only ${scenario.dags.joinToString(", ") { it.name }} were found"
        }
        return scenario
    }

    @LogInput
    internal suspend fun convertSteps(
        scenarioSpecification: ConfiguredScenarioSpecification,
        scenario: Scenario,
        parentStep: Step<*, *>?,
        parentDag: DirectedAcyclicGraph?,
        stepsSpecifications: List<StepSpecification<Any?, Any?, *>>
    ) {
        decorateStepForDagEndIfRequired(parentDag, stepsSpecifications, parentStep)
        coroutineScope {
            stepsSpecifications.map { stepSpecification ->

                launch {
                    convertStepRecursively(
                        scenarioSpecification,
                        scenario,
                        parentDag,
                        parentStep,
                        stepSpecification
                    )
                }
            }
        }.forEach { job -> job.join() }
    }

    /**
     * Verifies whether the DAG is coming to its end and adds a convenient step
     */
    private suspend fun decorateStepForDagEndIfRequired(
        parentDag: DirectedAcyclicGraph?,
        stepsSpecifications: List<StepSpecification<Any?, Any?, *>>,
        parentStep: Step<*, *>?
    ) {
        if (parentDag != null && parentStep != null && stepsSpecifications.isEmpty()) {
            // Adds a PipeStep for security of concurrency between the executed steps and the completion of the DAG.
            // TODO This workaround should be removed in a later version.
            val pipeStep = PipeStep<Any?>("__${buildNewStepName()}")
            parentDag.addStep(pipeStep)
            parentStep.addNext(pipeStep)

            // Adds a DeadEndStep step at the end of a DAG to notify that there is nothing after.
            val deadEndStep =
                dagTransitionStepFactory.createDeadEnd("__${buildNewStepName()}" + "_dead-end", parentDag.name)
            parentDag.addStep(deadEndStep)
            pipeStep.addNext(deadEndStep)
        }
    }

    private suspend fun convertStepRecursively(
        scenarioSpecification: ConfiguredScenarioSpecification,
        scenario: Scenario,
        parentDag: DirectedAcyclicGraph?,
        parentStep: Step<*, *>?,
        stepSpecification: StepSpecification<Any?, Any?, *>
    ) {
        log.debug {
            "Creating step ${stepSpecification.name.takeIf(String::isNotBlank) ?: "<no specified name>"} specified by ${stepSpecification::class.qualifiedName} with parent ${parentStep?.name ?: "<ROOT>"} in DAG ${stepSpecification.directedAcyclicGraphName}"
        }

        val actualParent =
            if (parentDag != null && parentStep != null && stepSpecification.directedAcyclicGraphName != parentDag.name) {
                // Adds a PipeStep for security of concurrency between the executed steps and the completion of the DAG.
                // TODO This workaround should be removed in a later version.
                val pipeStep = PipeStep<Any?>("__${buildNewStepName()}_pre-dag-transition")
                parentDag.addStep(pipeStep)
                parentStep.addNext(pipeStep)

                // Adds a DagTransitionStep step at the end of a DAG to notify the change to a new DAG.
                val dagTransitionStep = dagTransitionStepFactory.createTransition(
                    "__${buildNewStepName()}_transition",
                    parentDag.name,
                    stepSpecification.directedAcyclicGraphName
                )
                parentDag.addStep(dagTransitionStep)
                pipeStep.addNext(dagTransitionStep)
                dagTransitionStep
            } else {
                parentStep
            }

        // Get or create the DAG to attach the step.
        val dag = scenario.createIfAbsent(stepSpecification.directedAcyclicGraphName) { dagId ->
            createNewDag(dagId, stepSpecification, scenarioSpecification, parentDag, scenario)
        }

        val context =
            StepCreationContextImpl(scenarioSpecification as StepSpecificationRegistry, dag, stepSpecification)
        convertSingleStep(context)

        // Injects the dependencies on the converted step.
        context.createdStep?.let(this::injectDependencies)

        decorateStep(context)
        context.createdStep?.let { step ->
            // Injects the dependencies on the decorated step, additionally to the converted one.
            injectDependencies(step)
            step.init()
            dag.addStep(step)
            actualParent?.let { ps ->
                @Suppress("UNCHECKED_CAST")
                (ps as Step<*, Any?>).addNext(step as Step<Any?, *>)
            }

            @Suppress("UNCHECKED_CAST")
            convertSteps(
                scenarioSpecification, scenario, step, dag,
                stepSpecification.nextSteps as List<StepSpecification<Any?, Any?, *>>
            )
        }
    }

    // FIXME Write unit tests cases here.
    @KTestable
    private fun createNewDag(
        dagId: DirectedAcyclicGraphName,
        stepSpecification: StepSpecification<Any?, Any?, *>,
        scenarioSpecification: ConfiguredScenarioSpecification,
        parentDag: DirectedAcyclicGraph?,
        scenario: Scenario
    ): DirectedAcyclicGraph {
        // DAGs holding a singleton step, or descendant of a singleton DAG not under load are considered as singleton.
        val isSingleton = (stepSpecification as? SingletonStepSpecification)?.isReallySingleton == true
                || (parentDag?.isSingleton == true && !parentDag.isUnderLoad)
        val isUnderLoad = dagId in scenarioSpecification.dagsUnderLoad
        // Root DAGs are the one without parent DAG, or being under load with a singleton parent.
        val isRoot = parentDag == null || (isUnderLoad && parentDag.isSingleton)

        return DirectedAcyclicGraph(
            dagId, scenario,
            isRoot = isRoot,
            isSingleton = isSingleton,
            isUnderLoad = isUnderLoad,
            tags = stepSpecification.tags
        )
    }

    /**
     * Inject relevant dependencies in the step.
     */
    private fun injectDependencies(step: Step<*, *>) {
        if (step is MinionsKeeperAware) {
            step.minionsKeeper = minionsKeeper
        }
        if (step is RunnerAware) {
            step.runner = runner
        }
    }

    suspend fun convertSingleStep(@Valid context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
        stepSpecificationConverters
            .firstOrNull {
                try {
                    it.support(context.stepSpecification)
                } catch (e: Exception) {
                    log.error(e) { e.message }
                    false
                }
            }?.let { converter ->
                addMissingStepName(context.stepSpecification)
                @Suppress("UNCHECKED_CAST")
                (converter as StepSpecificationConverter<StepSpecification<Any?, Any?, *>>).convert<Any?, Any?>(context)
            }
    }

    /**
     * Adds a random name to the step if none was specified.
     */
    private fun addMissingStepName(spec: StepSpecification<Any?, Any?, *>) {
        if (spec.name.isBlank()) {
            // Generates a name with an underscore to specify an anonymous one.
            spec.name = "_${spec.type}-${buildNewStepName()}"
        } else if (spec.name == "__") {
            // If the user specified a hidden name, the hidden aspect is kept.
            spec.name = "__${spec.type}-${buildNewStepName()}"
        }
    }

    private fun buildNewStepName() = "${idGenerator.short()}"

    private suspend fun decorateStep(context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
        context.createdStep?.let {
            // ErrorProcessingStep should not be decorated.
            if (context.createdStep !is ErrorProcessingStep<*, *>) {
                stepSpecificationDecoratorConverters
                    .map { converter ->
                        @Suppress("UNCHECKED_CAST")
                        converter as StepSpecificationDecoratorConverter<StepSpecification<Any?, Any?, *>>
                    }
                    .forEach { converter -> converter.decorate(context) }
            }
        }
    }


    companion object {

        @JvmStatic
        private val log = logger()
    }
}
