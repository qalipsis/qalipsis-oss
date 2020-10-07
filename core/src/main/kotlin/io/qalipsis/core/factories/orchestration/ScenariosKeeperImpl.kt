package io.qalipsis.core.factories.orchestration

import io.qalipsis.api.annotations.VisibleForTest
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.factories.StartupFactoryComponent
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.processors.ServicesLoader
import io.qalipsis.api.retry.NoRetryPolicy
import io.qalipsis.api.scenario.MutableScenarioSpecification
import io.qalipsis.api.scenario.ReadableScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecificationsKeeper
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.cross.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedbackDirectedAcyclicGraph
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedbackScenario
import io.micronaut.context.ApplicationContext
import io.micronaut.validation.Validated
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Singleton
import javax.validation.Valid

/**
 * Default implementation of [ScenariosKeeper].
 *
 * @author Eric Jessé
 */
@Singleton
@Validated
internal class ScenariosKeeperImpl(
        private val applicationContext: ApplicationContext,
        private val scenarioSpecificationsKeeper: ScenarioSpecificationsKeeper,
        private val feedbackProducer: FeedbackProducer,
        private val stepSpecificationConverters: List<StepSpecificationConverter<*>>,
        stepSpecificationDecoratorConverters: List<StepSpecificationDecoratorConverter<*>>
) : ScenariosKeeper, StartupFactoryComponent {

    /**
     * Collection of DAGs accessible by scenario and DAG ID.
     */
    private val dagsByScenario: MutableMap<ScenarioId, MutableMap<DirectedAcyclicGraphId, DirectedAcyclicGraph>> =
        ConcurrentHashMap()

    private val scenarios: MutableMap<ScenarioId, Scenario> = ConcurrentHashMap()

    // Sort the decorator converters in the expected order.
    private val stepSpecificationDecoratorConverters = stepSpecificationDecoratorConverters.sortedBy { it.order }

    @PostConstruct
    fun init() {
        // Load all the scenarios specifications into memory.
        ServicesLoader.loadServices<Any>("scenarios", applicationContext)
        // Fetch and convert them.
        scenarioSpecificationsKeeper.asMap().forEach { (scenarioId, scenarioSpecification) ->
            try {
                val scenario = convertScenario(scenarioId, scenarioSpecification)
                scenarios[scenarioId] = scenario
                scenario.dags.forEach { dag ->
                    dagsByScenario.computeIfAbsent(scenarioId) { mutableMapOf() }[dag.id] = dag
                }
            } catch (e: Exception) {
                log.error(e.message, e)
                throw e
            }
        }

        publishScenarioCreationFeedback(scenarios.values)
    }

    @PreDestroy
    fun destroy() {
        runBlocking {
            dagsByScenario.values.flatMap { it.values }.map { it.rootStep.get() }.forEach {
                destroyStepRecursively(it)
            }
        }
    }

    private suspend fun destroyStepRecursively(step: Step<*, *>) {
        step.destroy()
        step.next.forEach {
            destroyStepRecursively(it)
        }
    }

    @VisibleForTest
    internal fun publishScenarioCreationFeedback(scenarios: Collection<Scenario>) {
        val feedbackScenarios = scenarios.map { scenario ->
            val feedbackDags = scenario.dags.map {
                FactoryRegistrationFeedbackDirectedAcyclicGraph(
                        it.id, it.singleton, it.scenarioStart, it.stepsCount
                )
            }
            FactoryRegistrationFeedbackScenario(
                    scenario.id,
                    scenario.minionsCount,
                    feedbackDags
            )
        }

        runBlocking {
            log.trace("Sending feedback: $feedbackScenarios to $feedbackProducer")
            feedbackProducer.publish(FactoryRegistrationFeedback(feedbackScenarios))
        }
    }

    @VisibleForTest
    internal fun convertScenario(scenarioId: ScenarioId,
                                 scenarioSpecification: ReadableScenarioSpecification): Scenario {
        val rampUpStrategy = scenarioSpecification.rampUpStrategy ?: throw InvalidSpecificationException(
                "The scenario $scenarioId requires a ramp-up strategy")
        val defaultRetryPolicy = scenarioSpecification.retryPolicy ?: NoRetryPolicy()
        val scenario = Scenario(scenarioId, rampUpStrategy = rampUpStrategy, defaultRetryPolicy = defaultRetryPolicy,
                minionsCount = scenarioSpecification.minionsCount)

        val dags = ConcurrentHashMap(mutableMapOf<String, DirectedAcyclicGraph>())
        runBlocking {
            @Suppress("UNCHECKED_CAST")
            convertSteps(scenarioSpecification, scenario, dags, null,
                    scenarioSpecification.rootSteps as List<StepSpecification<Any?, Any?, *>>)
        }
        require(scenario.dags.size >= scenarioSpecification.dagsCount) {
            "Not all the DAGs were created, only ${
                scenario.dags.joinToString(", ") { it.id }
            } were found"
        }
        return scenario
    }

    @VisibleForTest
    internal suspend fun convertSteps(scenarioSpecification: ReadableScenarioSpecification,
                                      scenario: Scenario,
                                      dags: MutableMap<String, DirectedAcyclicGraph>,
                                      parentStep: Step<*, *>?,
                                      stepsSpecifications: List<StepSpecification<Any?, Any?, *>>) {

        stepsSpecifications.map { stepSpecification ->
            GlobalScope.launch {
                convertStepRecursively(scenarioSpecification, scenario, dags, parentStep, stepSpecification)
            }
        }.forEach { job ->
            job.join()
        }
    }

    private suspend fun convertStepRecursively(
            scenarioSpecification: ReadableScenarioSpecification,
            scenario: Scenario,
            dags: MutableMap<String, DirectedAcyclicGraph>,
            parentStep: Step<*, *>?,
            stepSpecification: StepSpecification<Any?, Any?, *>) {
        log.debug(
                "Creating step ${stepSpecification.name ?: "<undefined>"} specified by a ${stepSpecification::class} with parent ${parentStep?.id ?: "<root>"} in DAG ${stepSpecification.directedAcyclicGraphId}")

        // Get or create the DAG to attach the step.
        val dag = dags.computeIfAbsent(stepSpecification.directedAcyclicGraphId!!) { dagId ->
            DirectedAcyclicGraph(dagId, scenario, scenarioStart = (parentStep == null),
                    singleton = stepSpecification is SingletonStepSpecification)
        }

        val context =
            StepCreationContextImpl(scenarioSpecification as MutableScenarioSpecification, dag, stepSpecification)
        convertSingleStep(context)
        decorateStep(context)

        context.createdStep?.let { step ->
            step.init()
            dag.addStep(step)
            parentStep?.let { ps ->
                @Suppress("UNCHECKED_CAST")
                (ps as Step<*, Any?>).addNext(step as Step<Any?, *>)
            }

            @Suppress("UNCHECKED_CAST")
            convertSteps(scenarioSpecification, scenario, dags, step,
                    stepSpecification.nextSteps as List<StepSpecification<Any?, Any?, *>>)
        }
    }

    @VisibleForTest
    internal suspend fun convertSingleStep(@Valid context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
        stepSpecificationConverters
            .firstOrNull { it.support(context.stepSpecification) }
            ?.let { converter ->
                @Suppress("UNCHECKED_CAST")
                (converter as StepSpecificationConverter<StepSpecification<Any?, Any?, *>>).convert<Any?, Any?>(context)
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

    @LogInputAndOutput
    override fun hasScenario(scenarioId: ScenarioId): Boolean = scenarios.containsKey(scenarioId)

    @LogInputAndOutput
    override fun getScenario(scenarioId: ScenarioId): Scenario? = scenarios[scenarioId]

    @LogInputAndOutput
    override fun hasDag(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): Boolean =
        dagsByScenario[scenarioId]?.containsKey(dagId) ?: false

    @LogInputAndOutput
    override fun getDag(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): DirectedAcyclicGraph? =
        dagsByScenario[scenarioId]?.get(dagId)

    @LogInput(level = Level.DEBUG)
    override fun startScenario(campaignId: CampaignId, scenarioId: ScenarioId) {
        runBlocking {
            dagsByScenario[scenarioId]!!.values.forEach { dag ->
                val feedback = CampaignStartedForDagFeedback(
                        scenarioId = scenarioId,
                        dagId = dag.id,
                        campaignId = campaignId,
                        status = FeedbackStatus.IN_PROGRESS
                )
                log.trace("Sending feedback: $feedback")
                feedbackProducer.publish(feedback)

                startStepRecursively(dag.rootStep.get())

                val completionFeedback = CampaignStartedForDagFeedback(
                        scenarioId = scenarioId,
                        dagId = dag.id,
                        campaignId = campaignId,
                        status = FeedbackStatus.COMPLETED
                )
                log.trace("Sending feedback: $completionFeedback")
                feedbackProducer.publish(completionFeedback)
            }
        }
    }

    private suspend fun startStepRecursively(step: Step<*, *>) {
        step.start()
        step.next.forEach {
            startStepRecursively(it)
        }
    }

    @LogInput(level = Level.DEBUG)
    override fun stopScenario(campaignId: CampaignId, scenarioId: ScenarioId) {
        runBlocking {
            dagsByScenario[scenarioId]!!.values.map { it.rootStep.get() }.forEach {
                stopStepRecursively(it)
            }
        }
    }

    private suspend fun stopStepRecursively(step: Step<*, *>) {
        step.stop()
        step.next.forEach {
            stopStepRecursively(it)
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
