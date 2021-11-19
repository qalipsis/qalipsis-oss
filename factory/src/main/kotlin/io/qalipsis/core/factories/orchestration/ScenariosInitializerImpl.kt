package io.qalipsis.core.factories.orchestration

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.validation.Validated
import io.qalipsis.api.Executors
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
import io.qalipsis.core.factories.steps.MinionsKeeperAware
import io.qalipsis.core.factories.steps.RunnerAware
import io.qalipsis.core.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.feedbacks.FactoryRegistrationFeedbackDirectedAcyclicGraph
import io.qalipsis.core.feedbacks.FactoryRegistrationFeedbackScenario
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
import javax.annotation.PreDestroy
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
    @Named(Executors.GLOBAL_EXECUTOR_NAME) private val coroutineDispatcher: CoroutineDispatcher,
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
    ) private val stepStartTimeout: Duration,
    @PositiveDuration @Property(
        name = "scenario.conversion.timeout",
        defaultValue = "10s"
    ) private val conversionTimeout: Duration
) : ScenariosInitializer, StartupFactoryComponent {

    /**
     * Collection of DAGs accessible by scenario and DAG ID.
     */
    private val dagsByScenario: MutableMap<ScenarioId, MutableMap<DirectedAcyclicGraphId, DirectedAcyclicGraph>> =
        ConcurrentHashMap()

    // Sort the decorator converters in the expected order.
    private val stepSpecificationDecoratorConverters = stepSpecificationDecoratorConverters.sortedBy { it.order }

    private lateinit var scenarioSpecs: Map<ScenarioId, ConfiguredScenarioSpecification>

    override fun getStartupOrder() = Int.MAX_VALUE

    override fun init() {
        refresh()
    }

    override fun refresh() {
        CompletableFuture.supplyAsync {
            try {
                scenarioSpecificationsKeeper.clear()
                log.info { "Refreshing the scenarios specifications" }
                // Load all the scenarios specifications into memory.
                ServicesLoader.loadServices<Any>("scenarios", applicationContext)

                scenarioSpecs = scenarioSpecificationsKeeper.asMap()
                val allScenarios = mutableListOf<Scenario>()
                scenarioSpecs.forEach { (scenarioId, scenarioSpecification) ->
                    log.info { "Converting the scenario specification $scenarioId" }
                    val scenario = convertScenario(scenarioId, scenarioSpecification)
                    allScenarios.add(scenario)
                    scenario.dags.forEach { dag ->
                        dagsByScenario.computeIfAbsent(scenarioId) { ConcurrentHashMap() }[dag.id] = dag
                    }
                }
                publishScenarioCreationFeedback(allScenarios)
                Result.success(Unit)
            } catch (e: Exception) {
                log.error(e) { "${e.message}" }
                Result.failure(e)
            }
        }.get(conversionTimeout.toMillis(), TimeUnit.MILLISECONDS).getOrThrow()
    }

    @PreDestroy
    fun destroy() {
        runBlocking(coroutineDispatcher) {
            log.info { "Stopping all the scenarios..." }
            dagsByScenario.keys.mapNotNull(scenariosRegistry::get).forEach(Scenario::destroy)
            log.info { "All the scenarios were stopped..." }
        }
    }

    private fun publishScenarioCreationFeedback(scenarios: Collection<Scenario>) {
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

        runBlocking(coroutineDispatcher) {
            val feedback = FactoryRegistrationFeedback(feedbackScenarios)
            log.trace { "Sending feedback: $feedback to $feedbackProducer" }
            feedbackProducer.publish(feedback)
        }
    }

    @LogInputAndOutput
    @KTestable
    protected fun convertScenario(
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

        runBlocking(coroutineDispatcher) {
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

    @LogInput
    internal suspend fun convertSteps(
        scenarioSpecification: ConfiguredScenarioSpecification,
        scenario: Scenario,
        parentStep: Step<*, *>?,
        stepsSpecifications: List<StepSpecification<Any?, Any?, *>>
    ) {
        coroutineScope {
            stepsSpecifications.map { stepSpecification ->
                launch {
                    convertStepRecursively(scenarioSpecification, scenario, parentStep, stepSpecification)
                }
            }
        }.forEach { job -> job.join() }
    }

    private suspend fun convertStepRecursively(
        scenarioSpecification: ConfiguredScenarioSpecification,
        scenario: Scenario,
        parentStep: Step<*, *>?,
        stepSpecification: StepSpecification<Any?, Any?, *>
    ) {
        log.debug {
            "Creating step ${stepSpecification.name.takeIf(String::isNotBlank) ?: "<no specified name>"} specified by ${stepSpecification::class.qualifiedName} with parent ${parentStep?.id ?: "<ROOT>"} in DAG ${stepSpecification.directedAcyclicGraphId}"
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
        // Injects the dependencies on the converted step.
        context.createdStep?.let(this::injectDependencies)

        decorateStep(context)
        context.createdStep?.let { step ->
            // Injects the dependencies on the decorated step, additionally to the converted one.
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
    private fun injectDependencies(step: Step<*, *>) {
        if (step is MinionsKeeperAware) {
            step.minionsKeeper = minionsKeeper
        }
        if (step is RunnerAware) {
            step.runner = runner
        }
    }

    private suspend fun convertSingleStep(@Valid context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
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
    private fun addMissingStepName(spec: StepSpecification<Any?, Any?, *>) {
        if (spec.name.isBlank()) {
            spec.name = "_${idGenerator.short()}"
        }
    }

    private suspend fun decorateStep(context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
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
