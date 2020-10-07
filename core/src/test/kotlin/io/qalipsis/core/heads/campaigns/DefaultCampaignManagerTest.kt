package io.qalipsis.core.heads.campaigns

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.sameInstance
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.core.cross.directives.CampaignStartDirective
import io.qalipsis.api.orchestration.directives.DirectiveKey
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.core.cross.directives.MinionsCreationDirectiveReference
import io.qalipsis.core.cross.directives.MinionsCreationPreparationDirective
import io.qalipsis.core.cross.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.cross.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.utils.getProperty
import io.qalipsis.test.utils.setProperty
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * @author Eric Jessé
 */
@WithMockk
internal class DefaultCampaignManagerTest {

    @RelaxedMockK
    lateinit var feedbackConsumer: FeedbackConsumer

    @RelaxedMockK
    lateinit var scenarioRepository: HeadScenarioRepository

    @RelaxedMockK
    lateinit var directiveProducer: DirectiveProducer

    @Test
    @Timeout(3)
    internal fun `should start minions creation with same minions total when campaign a starts and minionsCountPerScenario is specified`() {
        // given
        val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )
        val scenario1 = HeadScenario("scen-1", 4, listOf())
        val scenario2 = HeadScenario("scen-2", 17, listOf())
        every { scenarioRepository.getAll(any<Collection<ScenarioId>>()) } returns listOf(scenario1, scenario2)
        val directives = mutableListOf<MinionsCreationPreparationDirective>()
        coEvery { directiveProducer.publish(capture(directives)) } answers {}
        val onCriticalFailure: (String) -> Unit = { _ -> println("Do something") }

        // when
        runBlocking {
            campaignManager.start(scenarios = listOf("scen-1", "scen-2"), onCriticalFailure = onCriticalFailure)
        }

        // then
        coVerifyOrder {
            scenarioRepository.getAll(eq(listOf("scen-1", "scen-2")))
            directiveProducer.publish(any())
            directiveProducer.publish(any())
        }
        assertThat(directives, allOf(
                hasSize(equalTo(2)),
                anyElement(allOf(
                        isA<MinionsCreationPreparationDirective>(),
                        has(MinionsCreationPreparationDirective::scenarioId, equalTo("scen-1")),
                        has(MinionsCreationPreparationDirective::value, equalTo(10))
                )),
                anyElement(allOf(
                        isA<MinionsCreationPreparationDirective>(),
                        has(MinionsCreationPreparationDirective::scenarioId, equalTo("scen-2")),
                        has(MinionsCreationPreparationDirective::value, equalTo(10))
                ))
        ))
        val scenarios = campaignManager.getProperty<Map<ScenarioId, HeadScenario>>("scenarios")
        assertThat(scenarios, allOf(
                equalTo(mapOf("scen-1" to scenario1, "scen-2" to scenario2))
        ))
        val usedOnCriticalFailure = campaignManager.getProperty<(String) -> Unit>("onCriticalFailure")
        assertThat(usedOnCriticalFailure, sameInstance(onCriticalFailure))
    }

    @Test
    @Timeout(3)
    internal fun `should start minions creation with same minions total when campaign a starts and minionsCountFactor is specified`() {
        // given
        val campaignManager = DefaultCampaignManager(
                minionsCountFactor = 2.0, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )
        val scenario1 = HeadScenario("scen-1", 4, listOf())
        val scenario2 = HeadScenario("scen-2", 17, listOf())
        every { scenarioRepository.getAll(any<Collection<ScenarioId>>()) } returns listOf(scenario1, scenario2)
        val directives = mutableListOf<MinionsCreationPreparationDirective>()
        coEvery { directiveProducer.publish(capture(directives)) } answers {}
        val onCriticalFailure: (String) -> Unit = { _ -> println("Do something") }

        // when
        runBlocking {
            campaignManager.start(scenarios = listOf("scen-1", "scen-2"), onCriticalFailure = onCriticalFailure)
        }

        // then
        coVerifyOrder {
            scenarioRepository.getAll(eq(listOf("scen-1", "scen-2")))
            directiveProducer.publish(any())
            directiveProducer.publish(any())
        }
        assertThat(directives, allOf(
                hasSize(equalTo(2)),
                anyElement(allOf(
                        isA<MinionsCreationPreparationDirective>(),
                        has(MinionsCreationPreparationDirective::scenarioId, equalTo("scen-1")),
                        has(MinionsCreationPreparationDirective::value, equalTo(8))
                )),
                anyElement(allOf(
                        isA<MinionsCreationPreparationDirective>(),
                        has(MinionsCreationPreparationDirective::scenarioId, equalTo("scen-2")),
                        has(MinionsCreationPreparationDirective::value, equalTo(34))
                ))
        ))
        val scenarios = campaignManager.getProperty<Map<ScenarioId, HeadScenario>>("scenarios")
        assertThat(scenarios, allOf(
                equalTo(mapOf("scen-1" to scenario1, "scen-2" to scenario2))
        ))
        val usedOnCriticalFailure = campaignManager.getProperty<(String) -> Unit>("onCriticalFailure")
        assertThat(usedOnCriticalFailure, sameInstance(onCriticalFailure))
    }

    @Test
    @Timeout(3)
    internal fun `should accept MinionsCreationDirectiveReference`() {
        // given
        val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )

        // when
        Assertions.assertTrue(campaignManager.accept(MinionsCreationDirectiveReference("", "", "", "")))
    }

    @Test
    @Timeout(3)
    internal fun `should not accept other directives`() {
        // given
        val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )

        // when
        Assertions.assertTrue(campaignManager.accept(MinionsCreationDirectiveReference("", "", "", "")))
    }

    @Test
    @Timeout(3)
    internal fun `should store the directives`() {
        // given
        val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )
        val directive = MinionsCreationDirectiveReference("directive", "", "", "")

        // when
        runBlocking {
            campaignManager.process(directive)
        }

        val directivesInProgress =
            campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress")
        Assertions.assertSame(directive, directivesInProgress["directive"]!!.directive)
    }

    @Test
    @Timeout(3)
    internal fun `should process MinionsCreationPreparationDirective in progress when the directive exists`() {
        // given
        val countDownLatch = CountDownLatch(1)
        val directive = MinionsCreationPreparationDirective("directive", "", 123)
        val campaignManager = spyk(DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )) {
            coEvery {
                receivedMinionsCreationPreparationFeedback(any(), any())
            } answers { countDownLatch.countDown() }
        }
        val directivesInProgress =
            campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress")
        directivesInProgress[directive.key] = DefaultCampaignManager.DirectiveInProgress(directive)
        val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.IN_PROGRESS)

        // when
        runBlocking {
            campaignManager.processDirectiveFeedback(directiveFeedback)
        }

        // then
        countDownLatch.await()
        coVerifyOnce {
            campaignManager.receivedMinionsCreationPreparationFeedback(directiveFeedback, directive)
        }
        // The directive is still in the cache.
        Assertions.assertTrue(directivesInProgress.containsKey(directive.key))
    }

    @Test
    @Timeout(3)
    internal fun `should process done MinionsCreationPreparationDirective when the directive exists`() {
        // given
        val countDownLatch = CountDownLatch(1)
        val directive = MinionsCreationPreparationDirective("directive", "", 123)
        val campaignManager = spyk(DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )) {
            coEvery {
                receivedMinionsCreationPreparationFeedback(any(), any())
            } answers { countDownLatch.countDown() }
        }
        val directivesInProgress =
            campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress")
        directivesInProgress[directive.key] = DefaultCampaignManager.DirectiveInProgress(directive)
        val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.COMPLETED)

        // when
        runBlocking {
            campaignManager.processDirectiveFeedback(directiveFeedback)
        }

        // then
        countDownLatch.await()
        coVerifyOnce {
            campaignManager.receivedMinionsCreationPreparationFeedback(directiveFeedback, directive)
        }
    }

    @Test
    @Timeout(3)
    internal fun `should ignore MinionsCreationPreparationDirective when the directive does not exist`() {
        // given
        val countDownLatch = CountDownLatch(1)
        val campaignManager = spyk(DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )) {
            coEvery {
                receivedMinionsCreationPreparationFeedback(any(), any())
            } answers { countDownLatch.countDown() }
        }
        val directiveFeedback = DirectiveFeedback("", "my-directive", FeedbackStatus.COMPLETED)

        // when
        runBlocking {
            campaignManager.processDirectiveFeedback(directiveFeedback)
        }

        // then
        countDownLatch.await(50, TimeUnit.MILLISECONDS)
        coVerifyNever {
            campaignManager.receivedMinionsCreationPreparationFeedback(any(), any())
        }
    }

    @Test
    @Timeout(3)
    internal fun `should process MinionsCreationDirectiveReference in progress when the directive exists`() {
        // given
        val countDownLatch = CountDownLatch(1)
        val directive = MinionsCreationDirectiveReference("directive", "", "", "")
        val campaignManager = spyk(DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )) {
            coEvery {
                receiveMinionsCreationDirectiveFeedback(any(), any())
            } answers { countDownLatch.countDown() }
        }
        val directivesInProgress =
            campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress")
        directivesInProgress[directive.key] = DefaultCampaignManager.DirectiveInProgress(directive)
        val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.IN_PROGRESS)

        // when
        runBlocking {
            campaignManager.processDirectiveFeedback(directiveFeedback)
        }

        // then
        countDownLatch.await()
        coVerifyOnce {
            campaignManager.receiveMinionsCreationDirectiveFeedback(directiveFeedback, directive)
        }
        // The directive is still in the cache.
        Assertions.assertTrue(directivesInProgress.containsKey(directive.key))
    }

    @Test
    @Timeout(3)
    internal fun `should process done MinionsCreationDirectiveReference when the directive exists`() {
        // given
        val countDownLatch = CountDownLatch(1)
        val directive = MinionsCreationDirectiveReference("directive", "", "", "")
        val campaignManager = spyk(DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )) {
            coEvery {
                receiveMinionsCreationDirectiveFeedback(any(), any())
            } answers { countDownLatch.countDown() }
        }
        val directivesInProgress =
            campaignManager.getProperty<MutableMap<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress")
        directivesInProgress[directive.key] = DefaultCampaignManager.DirectiveInProgress(directive)
        val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.COMPLETED)

        // when
        runBlocking {
            campaignManager.processDirectiveFeedback(directiveFeedback)
        }

        // then
        countDownLatch.await()
        coVerifyOnce {
            campaignManager.receiveMinionsCreationDirectiveFeedback(directiveFeedback, directive)
        }
    }

    @Test
    @Timeout(3)
    internal fun `should ignore MinionsCreationDirectiveReference when the directive does not exist`() {
        // given
        val countDownLatch = CountDownLatch(1)
        val campaignManager = spyk(DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )) {
            coEvery {
                receiveMinionsCreationDirectiveFeedback(any(), any())
            } answers { countDownLatch.countDown() }
        }
        val directiveFeedback = DirectiveFeedback("", "my-directive", FeedbackStatus.COMPLETED)

        // when
        runBlocking {
            campaignManager.processDirectiveFeedback(directiveFeedback)
        }

        // then
        countDownLatch.await(50, TimeUnit.MILLISECONDS)
        coVerifyNever {
            campaignManager.receiveMinionsCreationDirectiveFeedback(any(), any())
        }
    }

    @Test
    @Timeout(3)
    internal fun `should process failed feedback for MinionsCreationPreparationDirective`() {
        // given
        val countDownLatch = CountDownLatch(1)
        val onCriticalFailure: (String) -> Unit = { _ -> countDownLatch.countDown() }
        val directive = MinionsCreationPreparationDirective("directive", "", 123)
        val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )
        campaignManager.setProperty("onCriticalFailure", onCriticalFailure)
        val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.FAILED)

        // when
        runBlocking {
            campaignManager.receivedMinionsCreationPreparationFeedback(directiveFeedback, directive)
        }

        // then
        // Since the count down latch was decremented, the conCriticalFailure operation was called.
        countDownLatch.await()
    }

    @Test
    @Timeout(3)
    internal fun `should process failed feedback for MinionsCreationDirectiveReference`() {
        // given
        val countDownLatch = CountDownLatch(1)
        val onCriticalFailure: (String) -> Unit = { _ -> countDownLatch.countDown() }
        val directive = MinionsCreationDirectiveReference("directive", "", "", "")
        val campaignManager = DefaultCampaignManager(
                10, feedbackConsumer = feedbackConsumer, scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )
        campaignManager.setProperty("onCriticalFailure", onCriticalFailure)
        val directiveFeedback = DirectiveFeedback("", directive.key, FeedbackStatus.FAILED)

        // when
        runBlocking {
            campaignManager.receiveMinionsCreationDirectiveFeedback(directiveFeedback, directive)
        }

        // then
        // Since the count down latch was decremented, the conCriticalFailure operation was called.
        countDownLatch.await()
    }

    @Test
    @Timeout(3)
    internal fun `should trigger campaign start when all the scenarios are ready`() {
        // given
        val campaignManager = DefaultCampaignManager(
                10, speedFactor = 2.87, startOffsetMs = 876, feedbackConsumer = feedbackConsumer,
                scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )

        val directive1 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-1", "dag-1")
        val directive2 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-1", "dag-2")
        val directive3 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-2", "dag-1")
        val directive4 = MinionsCreationDirectiveReference("directive", "camp-1", "scen-2", "dag-2")

        val scenario1 =
            HeadScenario("scen-1", 4, listOf(HeadDirectedAcyclicGraph("dag-1"), HeadDirectedAcyclicGraph("dag-2")))
        val scenario2 =
            HeadScenario("scen-2", 17, listOf(HeadDirectedAcyclicGraph("dag-1"), HeadDirectedAcyclicGraph("dag-2")))
        val scenarios = campaignManager.getProperty<MutableMap<ScenarioId, HeadScenario>>("scenarios")
        scenarios[scenario1.id] = scenario1
        scenarios[scenario2.id] = scenario2
        val publishedDirectives = mutableListOf<CampaignStartDirective>()
        coEvery { directiveProducer.publish(capture(publishedDirectives)) } answers {}
        val readyDagsByScenario =
            campaignManager.getProperty<MutableMap<ScenarioId, MutableCollection<DirectedAcyclicGraphId>>>(
                    "readyDagsByScenario")
        readyDagsByScenario["scen-1"] = concurrentSet()
        readyDagsByScenario["scen-2"] = concurrentSet()

        // when
        runBlocking {
            campaignManager.receiveMinionsCreationDirectiveFeedback(DirectiveFeedback("", "", FeedbackStatus.COMPLETED),
                    directive1)
            campaignManager.receiveMinionsCreationDirectiveFeedback(DirectiveFeedback("", "", FeedbackStatus.COMPLETED),
                    directive2)
            campaignManager.receiveMinionsCreationDirectiveFeedback(DirectiveFeedback("", "", FeedbackStatus.COMPLETED),
                    directive3)
        }

        // then
        val readyScenarios = campaignManager.getProperty<Set<ScenarioId>>("readyScenarios")

        // Only one scenario is ready.
        Assertions.assertEquals(setOf("scen-1"), readyScenarios)

        // when
        runBlocking {
            campaignManager.receiveMinionsCreationDirectiveFeedback(DirectiveFeedback("", "", FeedbackStatus.COMPLETED),
                    directive4)
        }

        assertThat(publishedDirectives).all {
            hasSize(equalTo(2))
            any {
                isInstanceOf(MinionsRampUpPreparationDirective::class)
                prop(MinionsRampUpPreparationDirective::campaignId).isEqualTo("camp-1")
                prop(MinionsRampUpPreparationDirective::scenarioId).isEqualTo("scen-1")
            }
            any {
                isInstanceOf(MinionsRampUpPreparationDirective::class)
                prop(MinionsRampUpPreparationDirective::campaignId).isEqualTo("camp-1")
                prop(MinionsRampUpPreparationDirective::scenarioId).isEqualTo("scen-2")
            }
        }

        val directivesInProgress =
            campaignManager.getProperty<Map<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress")
        Assertions.assertEquals(2, directivesInProgress.size)
    }

    @Test
    @Timeout(3)
    internal fun `should trigger ramp-up when all the DAGs are started`() {
        // given
        val campaignManager = DefaultCampaignManager(
                10, speedFactor = 2.87, startOffsetMs = 876, feedbackConsumer = feedbackConsumer,
                scenarioRepository = scenarioRepository,
                directiveProducer = directiveProducer
        )

        val feedback1 = CampaignStartedForDagFeedback("camp-1", "scen-1", "dag-1", FeedbackStatus.COMPLETED)
        val feedback2 = CampaignStartedForDagFeedback("camp-1", "scen-1", "dag-2", FeedbackStatus.COMPLETED)
        val feedback3 = CampaignStartedForDagFeedback("camp-1", "scen-2", "dag-1", FeedbackStatus.COMPLETED)
        val feedback4 = CampaignStartedForDagFeedback("camp-1", "scen-2", "dag-2", FeedbackStatus.COMPLETED)

        val scenario1 =
            HeadScenario("scen-1", 4, listOf(HeadDirectedAcyclicGraph("dag-1"), HeadDirectedAcyclicGraph("dag-2")))
        val scenario2 =
            HeadScenario("scen-2", 17, listOf(HeadDirectedAcyclicGraph("dag-1"), HeadDirectedAcyclicGraph("dag-2")))
        val startedDagsByScenario =
            campaignManager.getProperty<MutableMap<ScenarioId, MutableCollection<DirectedAcyclicGraphId>>>(
                    "startedDagsByScenario")
        startedDagsByScenario["scen-1"] = concurrentSet()
        startedDagsByScenario["scen-2"] = concurrentSet()

        val scenarios = campaignManager.getProperty<MutableMap<ScenarioId, HeadScenario>>("scenarios")
        scenarios[scenario1.id] = scenario1
        scenarios[scenario2.id] = scenario2
        val publishedDirectives = mutableListOf<MinionsRampUpPreparationDirective>()
        coEvery { directiveProducer.publish(capture(publishedDirectives)) } answers {}

        // when
        runBlocking {
            campaignManager.processFeedBack(feedback1)
            campaignManager.processFeedBack(feedback2)
            campaignManager.processFeedBack(feedback3)
        }

        // then
        val startedScenarios = campaignManager.getProperty<Set<ScenarioId>>("startedScenarios")

        // Only one scenario is ready.
        Assertions.assertEquals(setOf("scen-1"), startedScenarios)

        // when
        runBlocking {
            campaignManager.processFeedBack(feedback4)
        }

        assertThat(publishedDirectives).all {
            hasSize(equalTo(2))
            any {
                isInstanceOf(MinionsRampUpPreparationDirective::class)
                prop(MinionsRampUpPreparationDirective::campaignId).isEqualTo("camp-1")
                prop(MinionsRampUpPreparationDirective::scenarioId).isEqualTo("scen-1")
                prop(MinionsRampUpPreparationDirective::startOffsetMs).isEqualTo(876L)
                prop(MinionsRampUpPreparationDirective::speedFactor).isEqualTo(2.87)
            }
            any {
                isInstanceOf(MinionsRampUpPreparationDirective::class)
                prop(MinionsRampUpPreparationDirective::campaignId).isEqualTo("camp-1")
                prop(MinionsRampUpPreparationDirective::scenarioId).isEqualTo("scen-2")
                prop(MinionsRampUpPreparationDirective::startOffsetMs).isEqualTo(876L)
                prop(MinionsRampUpPreparationDirective::speedFactor).isEqualTo(2.87)
            }
        }

        val directivesInProgress =
            campaignManager.getProperty<Map<DirectiveKey, DefaultCampaignManager.DirectiveInProgress<*>>>(
                    "directivesInProgress")
        Assertions.assertEquals(2, directivesInProgress.size)
    }


}
