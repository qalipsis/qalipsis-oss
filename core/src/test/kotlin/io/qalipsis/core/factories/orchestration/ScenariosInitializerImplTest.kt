package io.qalipsis.core.factories.orchestration

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.micronaut.context.ApplicationContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verifyOrder
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.processors.ServicesLoader
import io.qalipsis.api.scenario.ConfiguredScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecificationsKeeper
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.api.steps.TubeStepSpecification
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedbackScenario
import io.qalipsis.core.factories.testScenario
import io.qalipsis.core.heads.campaigns.HeadDirectedAcyclicGraph
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class ScenariosInitializerImplTest {

    @RelaxedMockK
    lateinit var scenarioSpecificationsKeeper: ScenarioSpecificationsKeeper

    @RelaxedMockK
    lateinit var scenariosRegistry: ScenariosRegistry

    @RelaxedMockK
    lateinit var feedbackProducer: FeedbackProducer

    @RelaxedMockK
    lateinit var stepConverter1: StepSpecificationConverter<StepSpecification<Any?, Any?, *>>

    @RelaxedMockK
    lateinit var stepConverter2: StepSpecificationConverter<StepSpecification<Any?, Any?, *>>

    @RelaxedMockK
    lateinit var stepDecorator1: StepSpecificationDecoratorConverter<StepSpecification<Any?, Any?, *>>

    @RelaxedMockK
    lateinit var stepDecorator2: StepSpecificationDecoratorConverter<StepSpecification<Any?, Any?, *>>

    @RelaxedMockK
    lateinit var runner: Runner

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var applicationContext: ApplicationContext

    @SpyK
    var idGenerator = TestIdGenerator

    val scenariosInitializer: ScenariosInitializerImpl by lazy(LazyThreadSafetyMode.NONE) {
        spyk(
            ScenariosInitializerImpl(
                applicationContext, scenariosRegistry, scenarioSpecificationsKeeper,
                feedbackProducer,
                listOf(stepConverter1, stepConverter2), runner, minionsKeeper, idGenerator,
                listOf(stepDecorator1, stepDecorator2)
            )
        )
    }

    @AfterEach
    internal fun tearDown() {
        unmockkObject(ServicesLoader)
    }

    @Test
    internal fun `should decorate converted step`() = runBlockingTest {
        // given
        val scenarioSpecification: StepSpecificationRegistry = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val createdStep: Step<Any?, Any?> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)
        context.createdStep(createdStep)

        // when
        scenariosInitializer.decorateStep(context)

        // then
        coVerifyOnce {
            (stepDecorator1).decorate(refEq(context))
            (stepDecorator2).decorate(refEq(context))
        }
    }

    @Test
    internal fun `should not decorate unconverted step`() = runBlockingTest {
        // given
        val scenarioSpecification: StepSpecificationRegistry = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)

        // when
        scenariosInitializer.decorateStep(context)

        // then
        coVerifyNever {
            (stepDecorator1).decorate(refEq(context))
            (stepDecorator2).decorate(refEq(context))
        }
    }

    @Test
    internal fun `should convert single step`() = runBlockingTest {
        // given
        val scenarioSpecification: StepSpecificationRegistry = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)
        coEvery { stepConverter2.support(stepSpecification) } returns true

        // when
        scenariosInitializer.convertSingleStep(context)

        // then
        coVerifyOnce {
            (stepConverter1).support(refEq(stepSpecification))
            (stepConverter2).support(refEq(stepSpecification))
            scenariosInitializer.addMissingStepName(refEq(stepSpecification))
            (stepConverter2).convert<Any?, Any?>(refEq(context))
        }
        coVerifyNever {
            (stepConverter1).convert<Any?, Any?>(context)
        }
    }

    @Test
    internal fun `should not convert single step when no converter is found`() = runBlockingTest {
        // given
        val scenarioSpecification: StepSpecificationRegistry = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)

        // when
        scenariosInitializer.convertSingleStep(context)

        // then
        coVerifyOnce {
            (stepConverter1).support(refEq(stepSpecification))
            (stepConverter2).support(refEq(stepSpecification))
        }
        coVerifyNever {
            (stepConverter2).convert<Any?, Any?>(refEq(context))
            (stepConverter1).convert<Any?, Any?>(refEq(context))
        }
    }

    @Test
    internal fun `should convert steps and followers`() = runBlocking {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk(
            ScenarioSpecification::class, StepSpecificationRegistry::class
        )
        val scenario = testScenario()
        val stepSpecification1: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { directedAcyclicGraphId } returns "dag-1"
        }
        val stepSpecification2: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { directedAcyclicGraphId } returns "dag-2"

        }
        val stepSpecification3: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { nextSteps } returns mutableListOf(stepSpecification2)
            every { directedAcyclicGraphId } returns "dag-2"
        }
        val atomicInteger = AtomicInteger()
        coEvery { scenariosInitializer.convertSingleStep(any()) } answers {
            (firstArg() as StepCreationContext<*>).createdStep(relaxedMockk {
                every { id } returns "step-${atomicInteger.incrementAndGet()}"
            })
        }
        coEvery { scenariosInitializer.decorateStep(any()) } answers {}

        // when
        scenariosInitializer.convertSteps(
            scenarioSpecification, scenario, null,
            // Only the root steps are passed.
            listOf(stepSpecification1, stepSpecification3)
        )

        // then
        // Only two dags were created.
        assertEquals(1, scenario["dag-1"]!!.stepsCount)
        assertEquals(2, scenario["dag-2"]!!.stepsCount)
        // 3 steps were created.
        coVerifyExactly(3) {
            (scenariosInitializer).convertSingleStep(any())
            (scenariosInitializer).decorateStep(any())
        }
        coVerifyExactly(6) {
            (scenariosInitializer).injectDependencies(any())
        }
    }

    @Test
    internal fun `should not convert followers when step is not converted`() = runBlocking {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk(
            ScenarioSpecification::class, StepSpecificationRegistry::class
        )
        val scenario = spyk(testScenario())
        val stepSpecification1: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { name } returns "step-1"
            every { directedAcyclicGraphId } returns "dag-1"
        }
        val stepSpecification2: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { name } returns "step-2"
            every { directedAcyclicGraphId } returns "dag-2"

        }
        val stepSpecification3: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { name } returns "step-3"
            every { directedAcyclicGraphId } returns "dag-2"
            every { nextSteps } returns mutableListOf(stepSpecification2)
        }
        coEvery { scenariosInitializer.convertSingleStep(any()) } answers {
            val context: StepCreationContext<*> = firstArg()
            if (context.stepSpecification.name != "step-3") {
                context.createdStep(relaxedMockk {
                    every { id } returns context.stepSpecification.name
                })
            }
        }
        coEvery { scenariosInitializer.decorateStep(any()) } answers {}

        // when
        scenariosInitializer.convertSteps(
            scenarioSpecification, scenario, null,
            // Only the root steps are passed.
            listOf(stepSpecification1, stepSpecification3)
        )

        // then
        assertEquals(1, scenario["dag-1"]!!.stepsCount)
        assertEquals(0, scenario["dag-2"]!!.stepsCount)
        coVerifyExactly(2) {
            // 2 steps were tried.
            (scenariosInitializer).convertSingleStep(any())
            (scenariosInitializer).decorateStep(any())
            (scenariosInitializer).injectDependencies(any())
        }
    }

    @Test
    internal fun `should convert scenario`() {
        // given
        val scenarioRootSteps = emptyList<StepSpecification<*, *, *>>()
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk {
            every { dagsUnderLoad } returns listOf("")
            every { rampUpStrategy } returns relaxedMockk()
            every { retryPolicy } returns relaxedMockk()
            every { minionsCount } returns 123
            every { rootSteps } returns scenarioRootSteps
        }
        coEvery { scenariosInitializer.convertSteps(any(), any(), any(), any()) } answers {}

        // when
        val scenario = scenariosInitializer.convertScenario("my-scenario", scenarioSpecification)

        // then
        assertThat(scenario).all {
            prop(Scenario::id).isEqualTo("my-scenario")
            prop(Scenario::minionsCount).isEqualTo(123)
            prop(Scenario::rampUpStrategy).isSameAs(scenarioSpecification.rampUpStrategy)
            prop(Scenario::defaultRetryPolicy).isSameAs(scenarioSpecification.retryPolicy)
        }

        coVerifyOnce {
            scenariosInitializer.convertSteps(
                refEq(scenarioSpecification), refEq(scenario), isNull(),
                refEq(scenarioRootSteps as List<StepSpecification<Any?, Any?, *>>)
            )
        }
    }

    @Test
    internal fun `should not convert scenario without ramp-up`() {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk {
            every { rampUpStrategy } returns null
            every { retryPolicy } returns relaxedMockk()
            every { minionsCount } returns 123
            every { rootSteps } returns relaxedMockk()
        }

        // when
        assertThrows<InvalidSpecificationException> {
            scenariosInitializer.convertScenario("my-scenario", scenarioSpecification)
        }

        // then
        coVerifyNever {
            scenariosInitializer.convertSteps(any(), any(), any(), any())
        }
    }

    @Test
    internal fun `should load scenarios, convert them and publish feedback`() {
        // given
        val scenarioSpecification1: ConfiguredScenarioSpecification = relaxedMockk { }
        val scenarioSpecification2: ConfiguredScenarioSpecification = relaxedMockk { }
        mockkObject(ServicesLoader)
        every { ServicesLoader.loadServices<Any?>(any(), refEq(applicationContext)) } returns relaxedMockk()
        every { scenarioSpecificationsKeeper.asMap() } returns mapOf(
            "scenario-1" to scenarioSpecification1,
            "scenario-2" to scenarioSpecification2
        )
        val scenario1: Scenario = relaxedMockk { }
        val scenario2: Scenario = relaxedMockk { }
        every {
            scenariosInitializer.convertScenario(eq("scenario-1"), refEq(scenarioSpecification1))
        } returns scenario1
        every {
            scenariosInitializer.convertScenario(eq("scenario-2"), refEq(scenarioSpecification2))
        } returns scenario2
        val publishedScenarios = slot<Collection<Scenario>>()
        every { scenariosInitializer.publishScenarioCreationFeedback(capture(publishedScenarios)) } answers {}

        // when
        scenariosInitializer.init()

        // then
        verifyOrder {
            ServicesLoader.loadServices<Any>("scenarios", refEq(applicationContext))
            scenarioSpecificationsKeeper.asMap()
        }
        verifyOnce {
            scenariosInitializer.convertScenario(eq("scenario-2"), refEq(scenarioSpecification2))
            scenariosInitializer.convertScenario(eq("scenario-1"), refEq(scenarioSpecification1))
            scenariosInitializer.publishScenarioCreationFeedback(any())
        }
        publishedScenarios.captured.let {
            assertEquals(2, it.size)
            assertTrue(it.contains(scenario1))
            assertTrue(it.contains(scenario2))
        }
    }

    @Test
    internal fun `should publish feedback for all scenarios`() {
        // given
        val scenario1: Scenario = relaxedMockk {
            every { id } returns "scenario-1"
            every { minionsCount } returns 2
            every { dags } returns mutableListOf(
                relaxedMockk {
                    every { id } returns "dag-1"
                    every { isSingleton } returns false
                    every { isRoot } returns true
                    every { stepsCount } returns 12
                },
                relaxedMockk {
                    every { id } returns "dag-2"
                    every { isSingleton } returns true
                    every { isRoot } returns false
                    every { stepsCount } returns 4
                }
            )
        }
        val scenario2: Scenario = relaxedMockk {
            every { id } returns "scenario-2"
            every { minionsCount } returns 1
            every { dags } returns mutableListOf(
                relaxedMockk {
                    every { id } returns "dag-3"
                    every { isSingleton } returns false
                    every { isRoot } returns true
                    every { isUnderLoad } returns true
                    every { stepsCount } returns 42
                }
            )
        }
        val feedback = slot<FactoryRegistrationFeedback>()
        coEvery { feedbackProducer.publish(capture(feedback)) } answers {}

        // when
        scenariosInitializer.publishScenarioCreationFeedback(listOf(scenario1, scenario2))

        // then
        coVerifyOnce {
            feedbackProducer.publish(any())
        }
        assertThat(feedback.captured.scenarios).all {
            hasSize(2)
            index(0).all {
                prop(FactoryRegistrationFeedbackScenario::id).isEqualTo("scenario-1")
                prop(FactoryRegistrationFeedbackScenario::minionsCount).isEqualTo(2)
                prop(FactoryRegistrationFeedbackScenario::directedAcyclicGraphs).all {
                    hasSize(2)
                    index(0).all {
                        prop(HeadDirectedAcyclicGraph::id).isEqualTo("dag-1")
                        prop(HeadDirectedAcyclicGraph::isSingleton).isFalse()
                        prop(HeadDirectedAcyclicGraph::isRoot).isTrue()
                        prop(HeadDirectedAcyclicGraph::isUnderLoad).isFalse()
                        prop(HeadDirectedAcyclicGraph::numberOfSteps).isEqualTo(12)
                    }
                    index(1).all {
                        prop(HeadDirectedAcyclicGraph::id).isEqualTo("dag-2")
                        prop(HeadDirectedAcyclicGraph::isSingleton).isTrue()
                        prop(HeadDirectedAcyclicGraph::isRoot).isFalse()
                        prop(HeadDirectedAcyclicGraph::isUnderLoad).isFalse()
                        prop(HeadDirectedAcyclicGraph::numberOfSteps).isEqualTo(4)
                    }
                }
            }

            index(1).all {
                prop(FactoryRegistrationFeedbackScenario::id).isEqualTo("scenario-2")
                prop(FactoryRegistrationFeedbackScenario::minionsCount).isEqualTo(1)
                prop(FactoryRegistrationFeedbackScenario::directedAcyclicGraphs).all {
                    hasSize(1)
                    index(0).all {
                        prop(HeadDirectedAcyclicGraph::id).isEqualTo("dag-3")
                        prop(HeadDirectedAcyclicGraph::isSingleton).isFalse()
                        prop(HeadDirectedAcyclicGraph::isRoot).isTrue()
                        prop(HeadDirectedAcyclicGraph::isUnderLoad).isTrue()
                        prop(HeadDirectedAcyclicGraph::numberOfSteps).isEqualTo(42)
                    }
                }
            }
        }
    }

    @Test
    internal fun `should generate an error when converting a scenario without dag under load`() {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk(
            ScenarioSpecification::class, StepSpecificationRegistry::class
        ) {
            every { dagsUnderLoad } returns emptyList()
        }

        // when
        assertThrows<InvalidSpecificationException> {
            scenariosInitializer.convertScenario("my-scenario", scenarioSpecification)
        }
    }

    @Test
    internal fun `should add step name when empty`() {
        // given
        val stepSpecification: StepSpecification<Any?, Any?, *> = TubeStepSpecification<Any?>().also {
            it.scenario = relaxedMockk()
            it.name = ""
        }

        // when
        scenariosInitializer.addMissingStepName(stepSpecification)

        // then
        assertThat(stepSpecification.name.trim()).isNotEmpty()
    }

    @Test
    internal fun `should add step name when blank`() {
        // given
        val stepSpecification: StepSpecification<Any?, Any?, *> = TubeStepSpecification<Any?>().also {
            it.scenario = relaxedMockk()
            it.name = "     "
        }

        // when
        scenariosInitializer.addMissingStepName(stepSpecification)

        // then
        assertThat(stepSpecification.name.trim()).isNotEmpty()
    }

    @Test
    internal fun `should not overwrite step name when not blank`() {
        // given
        val stepSpecification: StepSpecification<Any?, Any?, *> = TubeStepSpecification<Any?>().also {
            it.scenario = relaxedMockk()
            it.name = "   a step for my name   "
        }

        // when
        scenariosInitializer.addMissingStepName(stepSpecification)

        // then
        assertThat(stepSpecification.name).isEqualTo("   a step for my name   ")
    }
}
