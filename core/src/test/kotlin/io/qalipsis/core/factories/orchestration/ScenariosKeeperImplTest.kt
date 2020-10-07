package io.qalipsis.core.factories.orchestration

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.processors.ServicesLoader
import io.qalipsis.api.scenario.MutableScenarioSpecification
import io.qalipsis.api.scenario.ReadableScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecificationsKeeper
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.api.sync.Slot
import io.qalipsis.core.cross.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.utils.getProperty
import io.micronaut.context.ApplicationContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class ScenariosKeeperImplTest {

    @RelaxedMockK
    lateinit var scenarioSpecificationsKeeper: ScenarioSpecificationsKeeper

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
    lateinit var applicationContext: ApplicationContext

    lateinit var scenariosKeeper: ScenariosKeeperImpl

    @BeforeEach
    internal fun setUp() {
        scenariosKeeper = spyk(
                ScenariosKeeperImpl(applicationContext, scenarioSpecificationsKeeper, feedbackProducer,
                        listOf(stepConverter1, stepConverter2),
                        listOf(stepDecorator1, stepDecorator2)))
    }

    @AfterEach
    internal fun tearDown() {
        unmockkObject(ServicesLoader)
    }

    @Test
    internal fun `should decorate converted step`() {
        // given
        val scenarioSpecification: MutableScenarioSpecification = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val createdStep: Step<Any?, Any?> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)
        context.createdStep(createdStep)

        // when
        runBlocking {
            scenariosKeeper.decorateStep(context)
        }

        // then
        coVerifyOnce {
            (stepDecorator1).decorate(context)
            (stepDecorator2).decorate(context)
        }
    }

    @Test
    internal fun `should not decorate unconverted step`() {
        // given
        val scenarioSpecification: MutableScenarioSpecification = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)

        // when
        runBlocking {
            scenariosKeeper.decorateStep(context)
        }

        // then
        coVerifyNever {
            (stepDecorator1).decorate(context)
            (stepDecorator2).decorate(context)
        }
    }

    @Test
    internal fun `should convert single step`() {
        // given
        val scenarioSpecification: MutableScenarioSpecification = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)
        coEvery { stepConverter2.support(stepSpecification) } returns true

        // when
        runBlocking {
            scenariosKeeper.convertSingleStep(context)
        }

        // then
        coVerifyOnce {
            (stepConverter1).support(stepSpecification)
            (stepConverter2).support(stepSpecification)
            (stepConverter2).convert<Any?, Any?>(context)
        }
        coVerifyNever {
            (stepConverter1).convert<Any?, Any?>(context)
        }
    }

    @Test
    internal fun `should not convert single step when no converter is found`() {
        // given
        val scenarioSpecification: MutableScenarioSpecification = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)

        // when
        runBlocking {
            scenariosKeeper.convertSingleStep(context)
        }

        // then
        coVerifyOnce {
            (stepConverter1).support(stepSpecification)
            (stepConverter2).support(stepSpecification)
        }
        coVerifyNever {
            (stepConverter2).convert<Any?, Any?>(context)
            (stepConverter1).convert<Any?, Any?>(context)
        }
    }

    @Test
    internal fun `should convert steps and followers`() {
        // given
        val scenarioSpecification: ReadableScenarioSpecification = relaxedMockk(
                ScenarioSpecification::class, MutableScenarioSpecification::class
        )
        val scenario: Scenario = relaxedMockk { }
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
        coEvery { scenariosKeeper.convertSingleStep(any()) } answers {
            (firstArg() as StepCreationContext<*>).createdStep(relaxedMockk {
                every { id } returns "step-${atomicInteger.incrementAndGet()}"
            })
        }
        coEvery { scenariosKeeper.decorateStep(any()) } answers {}
        val dags = ConcurrentHashMap(mutableMapOf<String, DirectedAcyclicGraph>())

        // when
        runBlocking {
            scenariosKeeper.convertSteps(scenarioSpecification, scenario, dags, null,
                    // Only the root steps are passed.
                    listOf(stepSpecification1, stepSpecification3))
        }

        // then
        // Only two dags were created.
        assertEquals(1, dags["dag-1"]!!.stepsCount)
        assertEquals(2, dags["dag-2"]!!.stepsCount)
        coVerifyExactly(3) {
            // 3 steps were created.
            scenario.addStep(any(), any())
            (scenariosKeeper).convertSingleStep(any())
            (scenariosKeeper).decorateStep(any())
        }
    }

    @Test
    internal fun `should not convert followers when step is not converted`() {
        // given
        val scenarioSpecification: ReadableScenarioSpecification = relaxedMockk(
                ScenarioSpecification::class, MutableScenarioSpecification::class
        )
        val scenario: Scenario = relaxedMockk { }
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
        coEvery { scenariosKeeper.convertSingleStep(any()) } answers {
            val context: StepCreationContext<*> = firstArg()
            if (context.stepSpecification.name != "step-3") {
                context.createdStep(relaxedMockk {
                    every { id } returns context.stepSpecification.name!!
                })
            }
        }
        coEvery { scenariosKeeper.decorateStep(any()) } answers {}
        val dags = ConcurrentHashMap(mutableMapOf<String, DirectedAcyclicGraph>())

        // when
        runBlocking {
            scenariosKeeper.convertSteps(scenarioSpecification, scenario, dags, null,
                    // Only the root steps are passed.
                    listOf(stepSpecification1, stepSpecification3))
        }

        // then
        // Only two dags were created.
        assertEquals(2, dags.size)
        assertEquals(1, dags["dag-1"]!!.stepsCount)
        assertEquals(0, dags["dag-2"]!!.stepsCount)
        coVerifyExactly(2) {
            // 2 steps were tried.
            (scenariosKeeper).convertSingleStep(any())
            (scenariosKeeper).decorateStep(any())
        }
        coVerifyOnce {
            // 1 step only succeeded.
            scenario.addStep(any(), any())
        }
    }

    @Test
    internal fun `should convert scenario`() {
        // given
        val scenarioRootSteps = emptyList<StepSpecification<*, *, *>>()
        val scenarioSpecification: ReadableScenarioSpecification = relaxedMockk {
            every { rampUpStrategy } returns relaxedMockk()
            every { retryPolicy } returns relaxedMockk()
            every { minionsCount } returns 123
            every { rootSteps } returns scenarioRootSteps
        }
        coEvery { scenariosKeeper.convertSteps(any(), any(), any(), any(), any()) } answers {}

        // when
        val scenario = scenariosKeeper.convertScenario("my-scenario", scenarioSpecification)

        // then
        with(scenario) {
            assertEquals("my-scenario", id)
            assertEquals(123, minionsCount)
            assertSame(scenarioSpecification.rampUpStrategy, rampUpStrategy)
            assertSame(scenarioSpecification.retryPolicy, defaultRetryPolicy)
        }

        coVerifyOnce {
            scenariosKeeper.convertSteps(refEq(scenarioSpecification), refEq(scenario), not(isNull()), isNull(),
                    refEq(scenarioRootSteps as List<StepSpecification<Any?, Any?, *>>))
        }
    }

    @Test
    internal fun `should not convert scenario without ramp-up`() {
        // given
        val scenarioSpecification: ReadableScenarioSpecification = relaxedMockk {
            every { rampUpStrategy } returns null
            every { retryPolicy } returns relaxedMockk()
            every { minionsCount } returns 123
            every { rootSteps } returns relaxedMockk()
        }

        // when
        assertThrows<InvalidSpecificationException> {
            scenariosKeeper.convertScenario("my-scenario", scenarioSpecification)
        }

        // then
        coVerifyNever {
            scenariosKeeper.convertSteps(any(), any(), any(), any(), any())
        }
    }

    @Test
    internal fun `should load scenarios, convert them and publish feedback`() {
        // given
        val scenarioSpecification1: ReadableScenarioSpecification = relaxedMockk { }
        val scenarioSpecification2: ReadableScenarioSpecification = relaxedMockk { }
        mockkObject(ServicesLoader)
        every { ServicesLoader.loadServices<Any?>(any(), refEq(applicationContext)) } returns relaxedMockk()
        every { scenarioSpecificationsKeeper.asMap() } returns mapOf(
                "scenario-1" to scenarioSpecification1,
                "scenario-2" to scenarioSpecification2
        )
        val scenario1: Scenario = relaxedMockk { }
        val scenario2: Scenario = relaxedMockk { }
        every { scenariosKeeper.convertScenario(eq("scenario-1"), refEq(scenarioSpecification1)) } returns scenario1
        every { scenariosKeeper.convertScenario(eq("scenario-2"), refEq(scenarioSpecification2)) } returns scenario2
        val publishedScenarios = slot<Collection<Scenario>>()
        every { scenariosKeeper.publishScenarioCreationFeedback(capture(publishedScenarios)) } answers {}

        // when
        scenariosKeeper.init()

        // then
        verifyOrder {
            ServicesLoader.loadServices<Any>("scenarios", refEq(applicationContext))
            scenarioSpecificationsKeeper.asMap()
        }
        verifyOnce {
            scenariosKeeper.convertScenario(eq("scenario-2"), refEq(scenarioSpecification2))
            scenariosKeeper.convertScenario(eq("scenario-1"), refEq(scenarioSpecification1))
            scenariosKeeper.publishScenarioCreationFeedback(any())
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
                        every { singleton } returns false
                        every { scenarioStart } returns true
                        every { stepsCount } returns 12
                    },
                    relaxedMockk {
                        every { id } returns "dag-2"
                        every { singleton } returns true
                        every { scenarioStart } returns false
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
                        every { singleton } returns true
                        every { scenarioStart } returns true
                        every { stepsCount } returns 42
                    }
            )
        }
        val feedback = slot<FactoryRegistrationFeedback>()
        coEvery { feedbackProducer.publish(capture(feedback)) } answers {}

        // when
        scenariosKeeper.publishScenarioCreationFeedback(listOf(scenario1, scenario2))

        // then
        coVerifyOnce {
            feedbackProducer.publish(any())
        }
        feedback.captured.scenarios.let {
            assertEquals(2, it.size)
            with(it[0]) {
                assertEquals("scenario-1", id)
                assertEquals(2, minionsCount)
                with(directedAcyclicGraphs) {
                    assertEquals(2, size)
                    with(this[0]) {
                        assertEquals("dag-1", id)
                        assertEquals(false, singleton)
                        assertEquals(true, scenarioStart)
                        assertEquals(12, numberOfSteps)
                    }
                    with(this[1]) {
                        assertEquals("dag-2", id)
                        assertEquals(true, singleton)
                        assertEquals(false, scenarioStart)
                        assertEquals(4, numberOfSteps)
                    }
                }
            }
            with(it[1]) {
                assertEquals("scenario-2", id)
                assertEquals(1, minionsCount)
                with(directedAcyclicGraphs) {
                    assertEquals(1, size)
                    with(this[0]) {
                        assertEquals("dag-3", id)
                        assertEquals(true, singleton)
                        assertEquals(true, scenarioStart)
                        assertEquals(42, numberOfSteps)
                    }
                }
            }
        }
    }

    @Test
    internal fun `should destroy all steps`() {
        // given
        val mockedSteps = mutableListOf<Step<*, *>>()
        buildDagsByScenario(mockedSteps)

        // when
        scenariosKeeper.destroy()

        // then
        mockedSteps.forEach { step ->
            coVerifyOnce {
                step.destroy()
            }
        }
    }

    @Test
    internal fun `should start all steps`() {
        // given
        val mockedSteps = mutableListOf<Step<*, *>>()
        buildDagsByScenario(mockedSteps)
        val calledStart = AtomicInteger(0)
        mockedSteps.forEach {
            coEvery { it.start() } answers {
                calledStart.incrementAndGet()
                Unit
            }
        }

        // when
        scenariosKeeper.startScenario("camp-1", "scen-1")

        // then
        val expectedCalls = mockedSteps.size - 1 // All but the one of scen-2
        assertEquals(expectedCalls, calledStart.get())
        coVerifyOnce {
            feedbackProducer.publish(eq(CampaignStartedForDagFeedback(
                    "camp-1", "scen-1", "dag-1", FeedbackStatus.IN_PROGRESS
            )))
            feedbackProducer.publish(eq(CampaignStartedForDagFeedback(
                    "camp-1", "scen-1", "dag-2", FeedbackStatus.IN_PROGRESS
            )))
            feedbackProducer.publish(eq(CampaignStartedForDagFeedback(
                    "camp-1", "scen-1", "dag-1", FeedbackStatus.COMPLETED
            )))
            feedbackProducer.publish(eq(CampaignStartedForDagFeedback(
                    "camp-1", "scen-1", "dag-2", FeedbackStatus.COMPLETED
            )))
        }

        // when
        scenariosKeeper.startScenario("camp-1", "scen-2")

        // then
        assertEquals(mockedSteps.size, calledStart.get())
        coVerifyOnce {
            feedbackProducer.publish(eq(CampaignStartedForDagFeedback(
                    "camp-1", "scen-2", "dag-1", FeedbackStatus.IN_PROGRESS
            )))
            feedbackProducer.publish(eq(CampaignStartedForDagFeedback(
                    "camp-1", "scen-2", "dag-1", FeedbackStatus.COMPLETED
            )))
        }
    }

    @Test
    internal fun `should stop all steps`() {
        // given
        val mockedSteps = mutableListOf<Step<*, *>>()
        buildDagsByScenario(mockedSteps)
        val calledStop = AtomicInteger(0)
        mockedSteps.forEach {
            coEvery { it.stop() } answers {
                calledStop.incrementAndGet()
                Unit
            }
        }

        // when
        scenariosKeeper.stopScenario("camp-1", "scen-1")

        // then
        val expectedCalls = mockedSteps.size - 1 // All but the one of scen-2
        assertEquals(expectedCalls, calledStop.get())

        // when
        scenariosKeeper.stopScenario("camp-1", "scen-2")

        // then
        assertEquals(mockedSteps.size, calledStop.get())
    }

    private fun buildDagsByScenario(mockedSteps: MutableList<Step<*, *>>) {
        val dagsByScenario =
            scenariosKeeper.getProperty<MutableMap<ScenarioId, MutableMap<DirectedAcyclicGraphId, DirectedAcyclicGraph>>>(
                    "dagsByScenario")
        dagsByScenario.clear()
        dagsByScenario["scen-1"] = mutableMapOf(
                "dag-1" to relaxedMockk {
                    every { id } returns "dag-1"
                    every { rootStep } returns Slot(relaxedMockk {
                        mockedSteps.add(this)
                        every { next } returns mutableListOf(
                                relaxedMockk { mockedSteps.add(this) },
                                relaxedMockk {
                                    mockedSteps.add(this)
                                    every { next } returns mutableListOf(
                                            relaxedMockk {
                                                mockedSteps.add(this)
                                            }
                                    )
                                }
                        )
                    })
                },
                "dag-2" to relaxedMockk {
                    every { id } returns "dag-2"
                    every { rootStep } returns Slot(relaxedMockk { mockedSteps.add(this) })
                }
        )

        dagsByScenario["scen-2"] = mutableMapOf(
                "dag-1" to relaxedMockk {
                    every { id } returns "dag-1"
                    every { rootStep } returns Slot(relaxedMockk { mockedSteps.add(this) })
                }
        )
    }
}
