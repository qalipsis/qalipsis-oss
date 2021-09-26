package io.qalipsis.core.factories.orchestration

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.validation.Validated
import io.qalipsis.api.annotations.VisibleForTest
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.factories.StartupFactoryComponent
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.processors.ServicesLoader
import io.qalipsis.api.retry.NoRetryPolicy
import io.qalipsis.api.scenario.ConfiguredScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecificationsKeeper
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedbackDirectedAcyclicGraph
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedbackScenario
import io.qalipsis.core.factories.steps.MinionsKeeperAware
import io.qalipsis.core.factories.steps.RunnerAware
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Singleton
import javax.validation.Valid

/**
 * Default implementation of [ScenariosInitializer].
 *
 * @author Eric Jessé
 */
@Singleton
@Validated
internal class ScenariosInitializerImpl(
    private val applicationContext: ApplicationContext,
    private val scenariosRegistry: ScenariosRegistry,
    private val scenarioSpecificationsKeeper: ScenarioSpecificationsKeeper,
    private val feedbackProducer: FeedbackProducer,
    private val stepSpecificationConverters: List<StepSpecificationConverter<*>>,
    stepSpecificationDecoratorConverters: List<StepSpecificationDecoratorConverter<*>>,
    private val runner: Runner,
    private val minionsKeeper: MinionsKeeper,
    private val idGenerator: IdGenerator,
    @PositiveDuration @Property(
        name = "campaign.step.start-timeout",
        defaultValue = "30s"
    ) private val stepStartTimeout: Duration
) : ScenariosInitializer, StartupFactoryComponent {

    /**
     * Collection of DAGs accessible by scenario and DAG ID.
     */
    private val dagsByScenario: MutableMap<ScenarioId, MutableMap<DirectedAcyclicGraphId, DirectedAcyclicGraph>> =
        ConcurrentHashMap()

    // Sort the decorator converters in the expected order.
    private val stepSpecificationDecoratorConverters = stepSpecificationDecoratorConverters.sortedBy { it.order }

    @PostConstruct
    fun init() {
        // Load all the scenarios specifications into memory.
        ServicesLoader.loadServices<Any>("scenarios", applicationContext)

        // Fetch and convert them.
        val foundScenarios = scenarioSpecificationsKeeper.asMap()
        if (foundScenarios.isEmpty()) {
            // FIXME Trigger an error if there is no scenario found, in order to kill this useless instance.
            publishScenarioCreationFeedback(emptyList())
        } else {
            val allScenarios = mutableListOf<Scenario>()
            foundScenarios.forEach { (scenarioId, scenarioSpecification) ->
                try {
                    val scenario = convertScenario(scenarioId, scenarioSpecification)
                    allScenarios.add(scenario)
                    scenario.dags.forEach { dag ->
                        dagsByScenario.computeIfAbsent(scenarioId) { mutableMapOf() }[dag.id] = dag
                    }
                } catch (e: Exception) {
                    log.error(e) { e.message }
                    throw e
                }
            }
            publishScenarioCreationFeedback(allScenarios)
        }

    }

    @PreDestroy
    fun destroy() {
        runBlocking {
            dagsByScenario.keys.mapNotNull(scenariosRegistry::get).forEach(Scenario::destroy)
        }
    }

    @VisibleForTest
    internal fun publishScenarioCreationFeedback(scenarios: Collection<Scenario>) {
        val feedbackScenarios = scenarios.map { scenario ->
            val feedbackDags = scenario.dags.map {
                FactoryRegistrationFeedbackDirectedAcyclicGraph(
                    it.id, it.isSingleton, it.isRoot, it.isUnderLoad, it.stepsCount
                )
            }
            FactoryRegistrationFeedbackScenario(
                scenario.id,
                scenario.minionsCount,
                feedbackDags
            )
        }

        runBlocking {
            log.trace { "Sending feedback: $feedbackScenarios to $feedbackProducer" }
            feedbackProducer.publish(FactoryRegistrationFeedback(feedbackScenarios))
        }
    }

    @VisibleForTest
    @LogInputAndOutput
    internal fun convertScenario(
        scenarioId: ScenarioId,
        scenarioSpecification: ConfiguredScenarioSpecification
    ): Scenario {
        if (scenarioSpecification.dagsUnderLoad.isEmpty()) {
            throw InvalidSpecificationException(
                "There is no main branch defined in scenario $scenarioId, please prefix at least one root branch with 'start()'"
            )
        }

        val rampUpStrategy = scenarioSpecification.rampUpStrategy ?: throw InvalidSpecificationException(
            "The scenario $scenarioId requires a ramp-up strategy"
        )
        val defaultRetryPolicy = scenarioSpecification.retryPolicy ?: NoRetryPolicy()
        val scenario =
            ScenarioImpl(
                scenarioId, rampUpStrategy = rampUpStrategy, defaultRetryPolicy = defaultRetryPolicy,
                minionsCount = scenarioSpecification.minionsCount, feedbackProducer, stepStartTimeout
            )
        scenariosRegistry.add(scenario)

        runBlocking {
            @Suppress("UNCHECKED_CAST")
            convertSteps(
                scenarioSpecification, scenario, null,
                scenarioSpecification.rootSteps as List<StepSpecification<Any?, Any?, *>>
            )
        }
        require(scenario.dags.size >= scenarioSpecification.dagsCount) {
            "Not all the DAGs were created, only ${scenario.dags.joinToString(", ") { it.id }} were found"
        }
        return scenario
    }

    @VisibleForTest
    @LogInput
    internal suspend fun convertSteps(
        scenarioSpecification: ConfiguredScenarioSpecification,
        scenario: Scenario,
        parentStep: Step<*, *>?,
        stepsSpecifications: List<StepSpecification<Any?, Any?, *>>
    ) {

        stepsSpecifications.map { stepSpecification ->
            GlobalScope.launch {
                convertStepRecursively(scenarioSpecification, scenario, parentStep, stepSpecification)
            }
        }.forEach { job ->
            job.join()
        }
    }

    private suspend fun convertStepRecursively(
        scenarioSpecification: ConfiguredScenarioSpecification,
        scenario: Scenario,
        parentStep: Step<*, *>?,
        stepSpecification: StepSpecification<Any?, Any?, *>
    ) {
        log.debug {
            "Creating step ${stepSpecification.name} specified by a ${stepSpecification::class} with parent ${parentStep?.id ?: "<ROOT>"} in DAG ${stepSpecification.directedAcyclicGraphId}"
        }

        // Get or create the DAG to attach the step.
        val dag = scenario.createIfAbsent(stepSpecification.directedAcyclicGraphId) { dagId ->
            DirectedAcyclicGraph(
                dagId, scenario,
                isRoot = (parentStep == null),
                isSingleton = stepSpecification is SingletonStepSpecification,
                isUnderLoad = (dagId in scenarioSpecification.dagsUnderLoad)
            )
        }

        val context =
            StepCreationContextImpl(scenarioSpecification as StepSpecificationRegistry, dag, stepSpecification)
        convertSingleStep(context)
        context.createdStep?.let(this::injectDependencies)

        decorateStep(context)
        context.createdStep?.let { step ->
            injectDependencies(step)
            step.init()
            dag.addStep(step)
            parentStep?.let { ps ->
                @Suppress("UNCHECKED_CAST")
                (ps as Step<*, Any?>).addNext(step as Step<Any?, *>)
            }

            @Suppress("UNCHECKED_CAST")
            convertSteps(
                scenarioSpecification, scenario, step,
                stepSpecification.nextSteps as List<StepSpecification<Any?, Any?, *>>
            )
        }
    }

    /**
     * Inject relevant dependencies in the step.
     */
    @VisibleForTest
    fun injectDependencies(step: Step<*, *>) {
        if (step is MinionsKeeperAware) {
            step.minionsKeeper = minionsKeeper
        }
        if (step is RunnerAware) {
            step.runner = runner
        }
    }

    @VisibleForTest
    internal suspend fun convertSingleStep(@Valid context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
        stepSpecificationConverters
            .firstOrNull { it.support(context.stepSpecification) }
            ?.let { converter ->
                addMissingStepName(context.stepSpecification)
                @Suppress("UNCHECKED_CAST")
                (converter as StepSpecificationConverter<StepSpecification<Any?, Any?, *>>).convert<Any?, Any?>(context)
            }
    }

    /**
     * Adds a random name to the step if none was specified.
     */
    @VisibleForTest
    fun addMissingStepName(spec: StepSpecification<Any?, Any?, *>) {
        if (spec.name.isBlank()) {
            spec.name = "_${idGenerator.short()}"
        }
    }

    @VisibleForTest
    internal suspend fun decorateStep(context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
        context.createdStep?.let {
            stepSpecificationDecoratorConverters
                .map { converter ->
                    @Suppress("UNCHECKED_CAST")
                    converter as StepSpecificationDecoratorConverter<StepSpecification<Any?, Any?, *>>
                }
                .forEach { converter -> converter.decorate(context) }
        }
    }


    companion object {

        @JvmStatic
        private val log = logger()
    }
}
