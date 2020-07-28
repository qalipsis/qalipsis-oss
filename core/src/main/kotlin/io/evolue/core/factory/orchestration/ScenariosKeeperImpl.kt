package io.evolue.core.factory.orchestration

import com.google.common.annotations.VisibleForTesting
import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.orchestration.Scenario
import io.evolue.api.processors.ServicesLoader
import io.evolue.api.retry.NoRetryPolicy
import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.scenario.ReadableScenarioSpecification
import io.evolue.api.scenario.ScenarioSpecificationsKeeper
import io.evolue.api.steps.SingletonStepSpecification
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.api.steps.StepSpecificationDecoratorConverter
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedback
import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedbackDirectedAcyclicGraph
import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedbackScenario
import io.evolue.core.cross.driving.feedback.FeedbackProducer
import io.evolue.core.factory.StartupFactoryComponent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import javax.inject.Singleton

/**
 * Default implementation of [ScenariosKeeper].
 *
 * @author Eric Jessé
 */
@Singleton
internal class ScenariosKeeperImpl(
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
        ServicesLoader.loadServices<Any>("scenarios")
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

    @VisibleForTesting
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

    @VisibleForTesting
    internal fun convertScenario(scenarioId: ScenarioId,
        scenarioSpecification: ReadableScenarioSpecification): Scenario {
        val rampUpStrategy = scenarioSpecification.rampUpStrategy ?: throw InvalidSpecificationException(
            "The scenario ${scenarioId} requires a ramp-up strategy")
        val defaultRetryPolicy = scenarioSpecification.retryPolicy ?: NoRetryPolicy()
        val scenario = Scenario(scenarioId, rampUpStrategy = rampUpStrategy, defaultRetryPolicy = defaultRetryPolicy,
            minionsCount = scenarioSpecification.minionsCount)

        val dags = ConcurrentHashMap(mutableMapOf<String, DirectedAcyclicGraph>())
        runBlocking {
            @Suppress("UNCHECKED_CAST")
            convertSteps(scenarioSpecification, scenario, dags, null,
                scenarioSpecification.rootSteps as List<StepSpecification<Any?, Any?, *>>)
        }
        return scenario
    }

    @VisibleForTesting
    internal suspend fun convertSteps(scenarioSpecification: ReadableScenarioSpecification,
        scenario: Scenario,
        dags: MutableMap<String, DirectedAcyclicGraph>,
        parentStep: Step<*, *>?,
        stepsSpecifications: List<StepSpecification<Any?, Any?, *>>) {
        if (stepsSpecifications.size == 1) {
            runBlocking {
                convertStepRecursively(scenarioSpecification, scenario, dags, parentStep, stepsSpecifications[0])
            }
        } else {
            stepsSpecifications.map { stepSpecification ->
                GlobalScope.launch {
                    convertStepRecursively(scenarioSpecification, scenario, dags, parentStep, stepSpecification)
                }
            }.forEach { job ->
                job.join()
            }
        }
    }

    private suspend fun convertStepRecursively(
        scenarioSpecification: ReadableScenarioSpecification,
        scenario: Scenario,
        dags: MutableMap<String, DirectedAcyclicGraph>,
        parentStep: Step<*, *>?,
        stepSpecification: StepSpecification<Any?, Any?, *>) {

        // Get or create the DAG to attach the step.
        val dag = dags.computeIfAbsent(stepSpecification.directedAcyclicGraphId!!) { dagId ->
            DirectedAcyclicGraph(dagId, scenario, scenarioStart = false,
                singleton = stepSpecification is SingletonStepSpecification)
        }

        val context =
            StepCreationContextImpl(scenarioSpecification as MutableScenarioSpecification, dag, stepSpecification)
        convertSingleStep(context)
        decorateStep(context)

        context.createdStep?.let { step ->
            dag.addStep(step)
            parentStep?.let { ps -> (ps as Step<*, Any?>).next.add(step as Step<Any?, *>) }

            convertSteps(scenarioSpecification, scenario, dags, step,
                stepSpecification.nextSteps as List<StepSpecification<Any?, Any?, *>>)
        }
    }

    @VisibleForTesting
    internal suspend fun convertSingleStep(context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
        stepSpecificationConverters
            .firstOrNull { it.support(context.stepSpecification) }
            ?.let { converter ->
                (converter as StepSpecificationConverter<StepSpecification<Any?, Any?, *>>).convert<Any?, Any?>(context)
            }
    }

    @VisibleForTesting
    internal suspend fun decorateStep(context: StepCreationContextImpl<StepSpecification<Any?, Any?, *>>) {
        context.createdStep?.let {
            stepSpecificationDecoratorConverters
                .map { converter -> converter as StepSpecificationDecoratorConverter<StepSpecification<Any?, Any?, *>> }
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


    companion object {

        @JvmStatic
        private val log = logger()
    }
}
